package com.mine;

import com.mine.client.concurrent.DataQueueManager;
import com.mine.client.network.ConnectionState;
import com.mine.client.network.MineTcpClient;
import com.mine.common.MinePacket;
import com.mine.common.PacketStreamDecoder;
import com.mine.common.Protocol;
import com.mine.gateway.GatewayServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 端到端联调测试：真实 TCP、二进制协议、指令 ACK、脏报文、断线重连。
 * 运行：java -Djava.awt.headless=true -cp out/classes:out/test-classes \
 *       com.mine.GatewayClientIntegrationTest
 */
public final class GatewayClientIntegrationTest {

    private static int checks;

    private GatewayClientIntegrationTest() {
    }

    public static void main(String[] args) throws Exception {
        GatewayServer gateway = new GatewayServer("127.0.0.1", 0, 0, 200);
        DataQueueManager queue = new DataQueueManager(3000);
        MineTcpClient client = new MineTcpClient(queue);
        GatewayServer restarted = null;
        try {
            gateway.start();
            final int port = gateway.getPort();
            System.out.println("[TEST] 网关监听端口 " + port);

            final CountDownLatch connected = new CountDownLatch(1);
            final AtomicReference<ConnectionState> state =
                    new AtomicReference<ConnectionState>(ConnectionState.DISCONNECTED);
            client.setListener((newState, message) -> {
                state.set(newState);
                if (newState == ConnectionState.CONNECTED) {
                    connected.countDown();
                }
            });
            check(client.start("127.0.0.1", port, "127.0.0.1"), "客户端连接应启动并绑定本机源 IP");
            check(connected.await(8, TimeUnit.SECONDS), "客户端应在 8 秒内连接");
            check(state.get() == ConnectionState.CONNECTED, "连接状态应为 CONNECTED");
            check("127.0.0.1".equals(client.getActualLocalIp()),
                    "客户端应绑定本机源 IP 127.0.0.1，实际=" + client.getActualLocalIp());

            Map<Integer, MinePacket> firstPackets = new HashMap<Integer, MinePacket>();
            long deadline = System.currentTimeMillis() + 8000L;
            while (System.currentTimeMillis() < deadline && firstPackets.size() < 4) {
                MinePacket packet = queue.poll(200, TimeUnit.MILLISECONDS);
                if (packet != null && packet.isTelemetry()) {
                    firstPackets.put(packet.getDataType(), packet);
                }
            }
            check(firstPackets.size() == 4, "应收到 4 类遥测数据，实际=" + firstPackets.keySet());
            Set<Integer> types = new HashSet<Integer>(firstPackets.keySet());
            check(types.contains(1) && types.contains(2) && types.contains(3) && types.contains(4),
                    "遥测类型不完整");
            for (MinePacket packet : firstPackets.values()) {
                MinePacket decoded = MinePacket.decode(packet.encode());
                check(decoded.equals(packet), "接收报文重新编码/CRC 校验失败");
            }

            client.send(Protocol.heartbeat());
            check(waitForAck(queue, 0, 4000L) != null, "心跳应收到网关 ACK");

            client.sendCommand(Protocol.DEVICE_GAS, Protocol.CMD_SET_THRESHOLD, 1.0f);
            MinePacket thresholdAck = waitForAck(queue, Protocol.DEVICE_GAS, 4000L);
            check(thresholdAck != null && thresholdAck.getValue() == 1.0f, "阈值指令 ACK 错误");

            client.sendCommand(Protocol.DEVICE_GAS, Protocol.CMD_TRIGGER_ACCIDENT, 2.8f);
            MinePacket triggerAck = waitForAck(queue, Protocol.DEVICE_GAS, 4000L);
            check(triggerAck != null && triggerAck.getValue() == 1.0f, "事故指令 ACK 错误");
            MinePacket highGas = waitForGasAbove(queue, 1.0, 9000L);
            check(highGas != null, "瓦斯事故模拟应推送超限数据");

            testDirtyPacketRecovery(gateway, port);

            long before = client.snapshot().getReceivedPackets();
            final GatewayServer first = gateway;
            gateway.stop();
            gateway = null;
            check(waitUntil(() -> !client.isConnected(), 5000L), "网关关闭后客户端应检测断线");
            check(waitUntil(() -> state.get() == ConnectionState.RECONNECTING
                    || state.get() == ConnectionState.CONNECTING, 5000L), "应进入自动重连状态");

            for (int attempt = 0; attempt < 12 && restarted == null; attempt++) {
                try {
                    GatewayServer candidate = new GatewayServer("127.0.0.1", port, 0, 200);
                    candidate.start();
                    restarted = candidate;
                } catch (IOException e) {
                    Thread.sleep(400L);
                }
            }
            check(restarted != null, "网关应在原端口重启");
            check(waitUntil(client::isConnected, 20000L), "网关恢复后客户端应自动重连");
            check("127.0.0.1".equals(client.getActualLocalIp()),
                    "重连后应继续绑定本机源 IP 127.0.0.1");
            final long baseline = before;
            check(waitUntil(() -> client.snapshot().getReceivedPackets() > baseline + 4, 12000L),
                    "重连后应继续接收遥测数据");
            System.out.println("[TEST] 自动重连后累计接收 "
                    + client.snapshot().getReceivedPackets() + " 帧");
            checks++;
            System.out.println("[PASS] GatewayClientIntegrationTest 全部 " + checks + " 项检查通过");
        } finally {
            client.shutdown();
            if (gateway != null) {
                gateway.stop();
            }
            if (restarted != null) {
                restarted.stop();
            }
            if (!client.isRunning()) {
                // 等待日志刷出
                Thread.sleep(200L);
            }
        }
        System.exit(0);
    }

    private static void testDirtyPacketRecovery(GatewayServer gateway, int port) throws Exception {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(5000);
        try {
            OutputStream out = socket.getOutputStream();
            MinePacket heartbeat = Protocol.heartbeat();
            byte[] valid = heartbeat.encode();
            byte[] corrupted = valid.clone();
            corrupted[7] ^= (byte) 0x7F;

            out.write(new byte[]{0x00, 0x11, 0x22});
            out.write(corrupted);
            out.write(valid);
            out.flush();

            InputStream in = socket.getInputStream();
            PacketStreamDecoder decoder = new PacketStreamDecoder();
            byte[] buffer = new byte[2048];
            List<MinePacket> packets = new ArrayList<MinePacket>();
            long deadline = System.currentTimeMillis() + 5000L;
            while (System.currentTimeMillis() < deadline) {
                int read = in.read(buffer);
                if (read < 0) {
                    break;
                }
                packets.clear();
                decoder.feed(buffer, 0, read, packets);
                for (MinePacket packet : packets) {
                    if (packet.isAck() && packet.getDeviceId() == Protocol.DEVICE_BROADCAST) {
                        boolean detected = false;
                        long serverCrc = 0;
                        for (int i = 0; i < 20 && !detected; i++) {
                            for (com.mine.gateway.ClientSession session : gateway.getSessions()) {
                                if (session.getCrcErrors() > 0) {
                                    detected = true;
                                    serverCrc = session.getCrcErrors();
                                    break;
                                }
                            }
                            if (!detected) {
                                Thread.sleep(30L);
                            }
                        }
                        check(detected, "网关应检测到注入的 CRC 错误");
                        checks++;
                        System.out.println("[TEST] 脏报文恢复通过，网关 CRC 错误计数=" + serverCrc);
                        return;
                    }
                }
            }
            throw new AssertionError("脏报文后网关未恢复处理合法心跳");
        } finally {
            socket.close();
        }
    }

    private static MinePacket waitForAck(DataQueueManager queue, int target, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            MinePacket packet = queue.poll(150, TimeUnit.MILLISECONDS);
            if (packet != null && packet.isAck() && packet.getDeviceId() == target) {
                return packet;
            }
        }
        return null;
    }

    private static MinePacket waitForGasAbove(DataQueueManager queue, double threshold, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            MinePacket packet = queue.poll(150, TimeUnit.MILLISECONDS);
            if (packet != null && packet.getDataType() == Protocol.TYPE_GAS
                    && packet.getValue() > threshold) {
                return packet;
            }
        }
        return null;
    }

    private interface Condition {
        boolean test();
    }

    private static boolean waitUntil(Condition condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.test()) {
                return true;
            }
            Thread.sleep(80L);
        }
        return condition.test();
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

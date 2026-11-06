package com.mine.gateway;

import com.mine.common.MinePacket;
import com.mine.common.Protocol;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 仿真网关服务端：监听指定 IP:PORT，接受多个上位机连接，广播 4 路传感器数据，
 * 接收并执行控制指令，支持随机丢包与手动断开等故障模拟。
 */
public class GatewayServer {

    private final String bindHost;
    private final int requestedPort;
    private final int initialPeriodMs;
    private final SensorHub sensorHub;
    private final CopyOnWriteArrayList<ClientSession> sessions = new CopyOnWriteArrayList<ClientSession>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger nextSessionId = new AtomicInteger();
    private final AtomicLong offeredPackets = new AtomicLong();
    private final AtomicLong heartbeatCount = new AtomicLong();

    private volatile int dropPercent;
    private volatile ServerSocket serverSocket;
    private volatile Thread acceptThread;
    private volatile long startedAt;

    public GatewayServer(String bindHost, int port, int dropPercent, int periodMs) {
        this.bindHost = bindHost == null || bindHost.trim().isEmpty()
                ? Protocol.DEFAULT_HOST : bindHost.trim();
        this.requestedPort = port;
        this.dropPercent = clampDrop(dropPercent);
        this.initialPeriodMs = (int) SensorHub.clampPeriod(periodMs);
        this.sensorHub = new SensorHub(new PacketPublisher() {
            @Override
            public void publish(MinePacket packet) {
                broadcast(packet);
            }
        }, this.initialPeriodMs);
    }

    /** 绑定端口并启动 accept 线程与传感器线程；绑定失败会抛出异常。 */
    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        try {
            socket.bind(new InetSocketAddress(bindHost, requestedPort), 64);
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // ignore
            }
            throw e;
        }
        this.serverSocket = socket;
        this.startedAt = System.currentTimeMillis();
        this.running.set(true);
        sensorHub.start();

        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        }, "gateway-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        GatewayLog.info("SERVER", "网关已启动，监听 " + bindHost + ":" + socket.getLocalPort()
                + "，采样周期 " + initialPeriodMs + " ms，模拟丢包率 " + dropPercent + "%");
        printReachableAddresses(socket.getLocalPort());
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                ClientSession session = new ClientSession(this, socket, nextSessionId.incrementAndGet());
                sessions.add(session);
                session.start();
                GatewayLog.info("SERVER", "上位机接入：客户端#" + session.getSessionId()
                        + " " + session.getRemoteAddress() + "，当前连接数 " + sessions.size());
            } catch (SocketException e) {
                if (running.get()) {
                    GatewayLog.error("SERVER", "accept 失败: " + e.getMessage());
                }
            } catch (IOException e) {
                if (running.get()) {
                    GatewayLog.error("SERVER", "accept 异常: " + e.getMessage());
                }
            }
        }
    }

    /** 采集线程调用：向所有在线会话广播一帧数据（会话层可模拟随机丢包）。 */
    public void broadcast(MinePacket packet) {
        if (!running.get()) {
            return;
        }
        int currentDrop = dropPercent;
        for (ClientSession session : sessions) {
            offeredPackets.incrementAndGet();
            session.enqueue(packet, currentDrop);
        }
    }

    /** 处理客户端上行报文（心跳、指令），并回送 ACK。 */
    void handlePacket(ClientSession session, MinePacket packet) {
        if (packet.isTelemetry()) {
            GatewayLog.warn("SESSION", "客户端#" + session.getSessionId()
                    + " 上传了遥测类型报文，已忽略");
            return;
        }
        if (packet.getDataType() == Protocol.CMD_HEARTBEAT) {
            heartbeatCount.incrementAndGet();
            session.enqueueDirect(Protocol.ack(packet, true));
            return;
        }
        if (packet.getDataType() == Protocol.CMD_ACK) {
            return;
        }

        int target = packet.getDeviceId();
        int command = packet.getDataType();
        float value = packet.getValue();
        boolean success;
        switch (command) {
            case Protocol.CMD_TRIGGER_ACCIDENT:
                success = sensorHub.triggerAccident(target, value);
                break;
            case Protocol.CMD_RESET_ACCIDENT:
                success = sensorHub.resetAccident(target);
                break;
            case Protocol.CMD_SET_THRESHOLD:
                success = sensorHub.setThreshold(target, value);
                break;
            case Protocol.CMD_SET_PERIOD:
                success = sensorHub.setPeriod((int) value);
                break;
            default:
                success = false;
                GatewayLog.warn("SESSION", "客户端#" + session.getSessionId()
                        + " 发送未知指令 0x" + String.format("%02X", command));
                break;
        }
        GatewayLog.info("CMD", "客户端#" + session.getSessionId() + " → "
                + Protocol.typeName(command) + " target=" + target
                + " value=" + String.format(Locale.ROOT, "%.2f", value)
                + (success ? " [执行成功]" : " [执行失败]"));
        session.enqueueDirect(Protocol.ack(packet, success));
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        GatewayLog.info("SERVER", "正在关闭网关...");
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
            // ignore
        }
        sensorHub.stop();
        disconnectAll("网关关闭");
        if (acceptThread != null) {
            acceptThread.interrupt();
        }
        GatewayLog.info("SERVER", "网关已停止");
    }

    public void awaitTermination() throws InterruptedException {
        while (running.get()) {
            Thread.sleep(300L);
        }
    }

    public int disconnectAll(String reason) {
        int count = 0;
        for (ClientSession session : sessions) {
            if (session.isOpen()) {
                session.close(reason);
                count++;
            }
        }
        if (count > 0) {
            GatewayLog.warn("SERVER", "已主动断开 " + count + " 个客户端连接（" + reason + "）");
        }
        return count;
    }

    void removeSession(ClientSession session) {
        sessions.remove(session);
    }

    public String statusSummary() {
        long uptime = startedAt == 0 ? 0 : (System.currentTimeMillis() - startedAt) / 1000L;
        long sent = 0;
        long queueDropped = 0;
        long networkDropped = 0;
        int queued = 0;
        for (ClientSession session : sessions) {
            sent += session.getSentPackets();
            queueDropped += session.getDroppedQueue();
            networkDropped += session.getDroppedNetwork();
            queued += session.getSendQueueSize();
        }
        return "运行 " + uptime + "s，客户端 " + sessions.size()
                + " 个，采样轮次 " + sensorHub.getRoundCount()
                + "，生成 " + sensorHub.getGeneratedCount() + " 帧"
                + "，已发送 " + sent + " 帧，发送队列 " + queued
                + "，随机丢包 " + networkDropped + "，队列溢出丢弃 " + queueDropped
                + "，心跳 " + heartbeatCount.get()
                + "，当前采样周期 " + sensorHub.getSamplePeriodMs() + " ms"
                + "，丢包率 " + dropPercent + "%";
    }

    private void printReachableAddresses(int actualPort) {
        if (!"0.0.0.0".equals(bindHost) && !"::".equals(bindHost)) {
            return;
        }
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address) {
                        GatewayLog.info("SERVER", "局域网可连接地址：" + address.getHostAddress()
                                + ":" + actualPort + " (" + networkInterface.getDisplayName() + ")");
                    }
                }
            }
        } catch (Exception e) {
            GatewayLog.warn("SERVER", "枚举本机地址失败: " + e.getMessage());
        }
    }

    private static int clampDrop(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    public void setDropPercent(int percent) {
        this.dropPercent = clampDrop(percent);
        GatewayLog.info("SERVER", "模拟丢包率设置为 " + dropPercent + "%");
    }

    public int getDropPercent() {
        return dropPercent;
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getPort() {
        ServerSocket socket = serverSocket;
        return socket == null ? requestedPort : socket.getLocalPort();
    }

    public String getBindHost() {
        return bindHost;
    }

    public SensorHub getSensorHub() {
        return sensorHub;
    }

    public List<ClientSession> getSessions() {
        return Collections.unmodifiableList(new ArrayList<ClientSession>(sessions));
    }

    public long getOfferedPackets() {
        return offeredPackets.get();
    }

    public long getHeartbeatCount() {
        return heartbeatCount.get();
    }

    public long getStartedAt() {
        return startedAt;
    }
}

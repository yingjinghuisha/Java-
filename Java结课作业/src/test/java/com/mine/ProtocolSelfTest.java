package com.mine;

import com.mine.client.concurrent.BusinessThreadPool;
import com.mine.client.concurrent.DataQueueManager;
import com.mine.client.service.DeviceManager;
import com.mine.client.service.DeviceSnapshot;
import com.mine.common.Crc16Util;
import com.mine.common.CrcMismatchException;
import com.mine.common.IpRange;
import com.mine.common.MinePacket;
import com.mine.common.PacketFormatException;
import com.mine.common.PacketStreamDecoder;
import com.mine.common.Protocol;
import com.mine.common.SensorType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 协议、拆包、CRC、并发容器与线程池自检（无需 JUnit）。
 * 运行：java -cp out/classes:out/test-classes com.mine.ProtocolSelfTest
 */
public final class ProtocolSelfTest {

    private static int checks;

    private ProtocolSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        testCrc();
        testEncodeDecode();
        testStreamDecoder();
        testCommands();
        testIpRange();
        testDeviceManager();
        testQueue();
        testThreadPoolSaturation();
        testConcurrentEncodeDecode();
        System.out.println("[PASS] ProtocolSelfTest 全部 " + checks + " 项检查通过");
    }

    private static void testCrc() {
        int crc = Crc16Util.calcCrc16("123456789".getBytes(StandardCharsets.US_ASCII), 9);
        check(crc == 0x29B1, "CRC-16/CCITT-FALSE 标准向量应为 0x29B1，实际 " + Crc16Util.toHex(crc));
        byte[] buffer = new byte[2];
        Crc16Util.writeCrc16(buffer, 0, 0xABCD);
        check((buffer[0] & 0xFF) == 0xAB && (buffer[1] & 0xFF) == 0xCD, "CRC 大端写入错误");
        check(Crc16Util.readCrc16(buffer, 0) == 0xABCD, "CRC 大端读取错误");
    }

    private static void testEncodeDecode() throws Exception {
        MinePacket packet = new MinePacket(Protocol.DEVICE_GAS, Protocol.TYPE_GAS, 1.234f, 1710000000123L);
        byte[] encoded = packet.encode();
        check(encoded.length == Protocol.PACKET_LENGTH, "报文长度应为 19");
        check((encoded[0] & 0xFF) == 0xAA && (encoded[1] & 0xFF) == 0x55, "帧头应为 AA55");
        MinePacket decoded = MinePacket.decode(encoded);
        check(packet.equals(decoded), "编码/解码应保持一致");
        check(decoded.getCrc() != 0, "解码后应得到 CRC");

        byte[] corrupted = encoded.clone();
        corrupted[10] ^= 0x5A;
        try {
            MinePacket.decode(corrupted);
            fail("损坏报文应抛出 CrcMismatchException");
        } catch (CrcMismatchException expected) {
            checks++;
        }
        try {
            MinePacket.decode(new byte[]{0x11, 0x22});
            fail("短报文应抛出 PacketFormatException");
        } catch (PacketFormatException expected) {
            checks++;
        }
    }

    private static void testStreamDecoder() throws Exception {
        MinePacket a = new MinePacket(1, 1, 0.31f, 1000L);
        MinePacket b = new MinePacket(2, 2, -27.5f, 1001L);
        byte[] aBytes = a.encode();
        byte[] bBytes = b.encode();
        byte[] stream = new byte[aBytes.length + bBytes.length + 4];
        stream[0] = 0x33;
        stream[1] = 0x44;
        System.arraycopy(aBytes, 0, stream, 2, aBytes.length);
        System.arraycopy(bBytes, 0, stream, 2 + aBytes.length, bBytes.length);
        stream[stream.length - 2] = (byte) 0xAA;

        PacketStreamDecoder decoder = new PacketStreamDecoder();
        List<MinePacket> out = new ArrayList<MinePacket>();
        for (int i = 0; i < stream.length; i += 5) {
            int length = Math.min(5, stream.length - i);
            decoder.feed(stream, i, length, out);
        }
        check(out.size() == 2, "粘包/半包拆包应得到 2 帧，实际 " + out.size());
        check(out.get(0).equals(a) && out.get(1).equals(b), "拆包内容不一致");
        check(decoder.getGarbageBytes() >= 2, "应统计到前导脏字节");

        byte[] corruptedB = bBytes.clone();
        corruptedB[7] ^= (byte) 0xFF;
        out.clear();
        decoder.feed(corruptedB, 0, corruptedB.length, out);
        out.clear();
        decoder.feed(bBytes, 0, bBytes.length, out);
        check(out.size() == 1 && out.get(0).equals(b), "CRC 错误后应能重同步到下一帧");
        check(decoder.getCrcErrors() >= 1, "CRC 错误计数应增加");
    }

    private static void testCommands() throws Exception {
        MinePacket heartbeat = Protocol.heartbeat();
        check(heartbeat.getDataType() == Protocol.CMD_HEARTBEAT, "心跳类型错误");
        MinePacket ack = Protocol.ack(heartbeat, true);
        check(ack.isAck() && ack.getValue() == 1F, "ACK 构造错误");
        check(ack.getTimestamp() == heartbeat.getTimestamp(), "ACK 应回显请求时间戳");
        MinePacket decodedAck = MinePacket.decode(ack.encode());
        check(decodedAck.isAck() && decodedAck.getValue() == 1F, "ACK 编解码错误");
        MinePacket threshold = Protocol.command(1, Protocol.CMD_SET_THRESHOLD, 1.2f);
        check(threshold.isCommand() && threshold.isTelemetry() == false, "指令标识错误");
        check(SensorType.GAS.isAlarm(1.2, 1.0), "瓦斯高于阈值应告警");
        check(SensorType.FLOW.isAlarm(18.0, 20.0), "流量低于阈值应告警");
        check(!SensorType.VACUUM.isAlarm(-28.0, -20.0), "负压正常时不应告警");
    }

    private static void testIpRange() {
        check(IpRange.isAllowedHost("172.20.205.0"), "网段起始地址应允许");
        check(IpRange.isAllowedHost("172.20.224.255"), "网段结束地址应允许");
        check(IpRange.isAllowedHost("172.20.210.37"), "网段中间地址应允许");
        check(!IpRange.isAllowedHost("172.20.204.255"), "网段前一个地址应拒绝");
        check(!IpRange.isAllowedHost("172.20.225.0"), "网段后一个地址应拒绝");
        check(!IpRange.isAllowedHost("192.168.1.10"), "其他私网地址应拒绝");
        check(IpRange.isAllowedHost("127.0.0.1"), "本机回环地址应允许用于调试");
        check(IpRange.isAllowedHost("localhost"), "localhost 应允许用于调试");
        check(!IpRange.isAllowedHost("172.20.205.256"), "非法八位组应拒绝");
        check(IpRange.validateHost("192.168.1.10") != null, "超出范围应返回可展示的错误信息");
        check(IpRange.validateLocalIp("") == null, "本机 IP 留空应表示自动");
        check(IpRange.validateLocalIp("172.20.210.37") == null, "网段内的本机 IP 应允许");
        check(IpRange.validateLocalIp("192.168.1.10") != null, "网段外的本机 IP 应拒绝");
        check(IpRange.validateLocalIp("127.0.0.1") == null, "本机回环 IP 应允许用于调试");
    }

    private static void testDeviceManager() throws Exception {
        DeviceManager manager = new DeviceManager();
        MinePacket gasHigh = new MinePacket(1, 1, 1.55f, 2000L);
        DeviceSnapshot snapshot = manager.update(gasHigh);
        check(snapshot != null && snapshot.isAlarm(), "瓦斯超限快照应告警");
        check(manager.alarmCount() == 1, "告警设备计数错误");

        MinePacket stale = new MinePacket(1, 1, 2.0f, 1000L);
        check(manager.update(stale) == null, "时间戳落后的数据应被拒绝");
        check(Math.abs(manager.getSnapshot(1).getValue() - 1.55) < 1e-6, "旧数据不应覆盖新值");

        manager.setThreshold(1, 3.0);
        check(!manager.getSnapshot(1).isAlarm(), "提高阈值后应解除告警");

        final int writers = 4;
        final int iterations = 3000;
        ExecutorService pool = Executors.newFixedThreadPool(writers + 4);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        for (int id = 1; id <= writers; id++) {
            final int deviceId = id;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        manager.update(new MinePacket(deviceId, deviceId, 0.5f + (i % 10) * 0.01f,
                                3000L + i));
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            });
        }
        for (int r = 0; r < 4; r++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        List<DeviceSnapshot> snapshots = manager.snapshots();
                        if (snapshots.size() < 4) {
                            throw new AssertionError("快照数量异常");
                        }
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        check(pool.awaitTermination(20, TimeUnit.SECONDS), "并发设备读写超时");
        check(failure.get() == null, "并发设备读写异常: " + failure.get());
        check(manager.snapshots().size() == 4, "应保持 4 个设备");
    }

    private static void testQueue() {
        DataQueueManager queue = new DataQueueManager(3);
        for (int i = 0; i < 6; i++) {
            queue.offer(new MinePacket(1, 1, i, 1000L + i));
        }
        check(queue.size() <= 3, "有界队列不应超容量");
        check(queue.getDropped() >= 3, "队列满应统计丢弃");
        check(queue.getPeakSize() <= 3, "峰值不应超过容量");
    }

    private static void testThreadPoolSaturation() throws Exception {
        final BusinessThreadPool pool = new BusinessThreadPool();
        final CountDownLatch started = new CountDownLatch(5);
        final CountDownLatch release = new CountDownLatch(1);
        Runnable blocker = () -> {
            started.countDown();
            try {
                release.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        // 3 个核心线程
        for (int i = 0; i < 3; i++) {
            pool.execute(blocker);
        }
        // 填满 ArrayBlockingQueue(256)
        for (int i = 0; i < 256; i++) {
            pool.execute(blocker);
        }
        // 队列满后继续提交，线程池扩容到 max=5
        pool.execute(blocker);
        pool.execute(blocker);
        check(started.await(5, TimeUnit.SECONDS), "业务线程池核心/最大线程未按预期启动");
        for (int i = 0; i < 400; i++) {
            pool.execute(() -> {
                // 空任务，队列已满，应触发 CallerRunsPolicy
            });
        }
        check(pool.getCallerRuns() > 0, "队列饱和后 CallerRunsPolicy 应生效");
        check(pool.getPoolSize() <= 5, "线程数不应超过最大线程数");
        release.countDown();
        pool.shutdown();
        check(pool.isShutdown(), "线程池应关闭");
    }

    private static void testConcurrentEncodeDecode() throws Exception {
        final int threads = 8;
        final int perThread = 2000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger ok = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        for (int t = 0; t < threads; t++) {
            final int seed = t;
            executor.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        int type = (i % 4) + 1;
                        MinePacket packet = new MinePacket(type, type,
                                seed + i * 0.001f, 100000L + i);
                        MinePacket decoded = MinePacket.decode(packet.encode());
                        if (!packet.equals(decoded)) {
                            throw new AssertionError("并发编解码不一致");
                        }
                        ok.incrementAndGet();
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            });
        }
        start.countDown();
        executor.shutdown();
        check(executor.awaitTermination(20, TimeUnit.SECONDS), "并发编解码超时");
        check(failure.get() == null, "并发编解码异常: " + failure.get());
        check(ok.get() == threads * perThread, "并发编解码数量错误");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            fail(message);
        }
    }

    private static void fail(String message) {
        throw new AssertionError(message);
    }

}

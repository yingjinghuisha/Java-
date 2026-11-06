package com.mine;

import com.mine.client.concurrent.BusinessThreadPool;
import com.mine.client.concurrent.DataQueueManager;
import com.mine.client.service.AlarmEvent;
import com.mine.client.service.AlarmLevel;
import com.mine.client.service.AlarmManager;
import com.mine.client.service.CsvExporter;
import com.mine.client.service.DeviceManager;
import com.mine.client.service.DeviceSnapshot;
import com.mine.client.service.SensorDataService;
import com.mine.client.service.SensorUiListener;
import com.mine.common.MinePacket;
import com.mine.common.Protocol;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** 业务层自检：告警边沿、恢复、ACK、线程池消费。 */
public final class ServiceSelfTest {

    private static int checks;

    private ServiceSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        DataQueueManager queue = new DataQueueManager(100);
        BusinessThreadPool pool = new BusinessThreadPool();
        DeviceManager deviceManager = new DeviceManager();
        AlarmManager alarmManager = new AlarmManager();
        final List<AlarmEvent> alarms = new CopyOnWriteArrayList<AlarmEvent>();
        final CountDownLatch firstAlarm = new CountDownLatch(1);
        final CountDownLatch recovery = new CountDownLatch(1);
        final AtomicInteger uiUpdates = new AtomicInteger();
        final AtomicReference<String> callbackThread = new AtomicReference<String>();
        final List<Boolean> acks = new CopyOnWriteArrayList<Boolean>();

        alarmManager.start(event -> {
            alarms.add(event);
            if (event.getLevel() == AlarmLevel.CRITICAL) {
                firstAlarm.countDown();
            }
            if (event.getLevel() == AlarmLevel.RECOVERY) {
                recovery.countDown();
            }
        });

        SensorDataService service = new SensorDataService(queue, pool, deviceManager,
                alarmManager, new SensorUiListener() {
                    @Override
                    public void onSensorUpdate(DeviceSnapshot snapshot, MinePacket packet,
                                               boolean alarmActivated, boolean alarmCleared) {
                        callbackThread.compareAndSet(null, Thread.currentThread().getName());
                        uiUpdates.incrementAndGet();
                    }

                    @Override
                    public void onGatewayAck(MinePacket ack, boolean success) {
                        acks.add(success);
                    }

                    @Override
                    public void onDeviceOffline(DeviceSnapshot snapshot) {
                    }

                    @Override
                    public void onServiceError(String message) {
                        throw new AssertionError(message);
                    }
                });
        service.start();

        long base = System.currentTimeMillis();
        queue.offer(new MinePacket(Protocol.DEVICE_GAS, Protocol.TYPE_GAS, 0.4f, base));
        queue.offer(new MinePacket(Protocol.DEVICE_GAS, Protocol.TYPE_GAS, 1.8f, base + 1));
        check(firstAlarm.await(5, TimeUnit.SECONDS), "瓦斯超限应产生 CRITICAL 告警");
        check(deviceManager.getSnapshot(Protocol.DEVICE_GAS).isAlarm(), "设备快照应处于告警态");
        check(alarmManager.getCriticalCount() == 1, "严重告警计数应为 1");
        check(alarms.size() >= 1 && alarms.get(0).getLevel() == AlarmLevel.CRITICAL,
                "告警队列级别错误");
        waitUntil(() -> uiUpdates.get() >= 2, 5000L);
        check(uiUpdates.get() >= 2, "业务层应回调 GUI 更新");
        check(callbackThread.get() != null && callbackThread.get().startsWith("biz-worker"),
                "业务处理应运行在线程池工作线程，实际=" + callbackThread.get());

        queue.offer(new MinePacket(Protocol.DEVICE_GAS, Protocol.TYPE_GAS, 0.2f, base + 2));
        check(recovery.await(5, TimeUnit.SECONDS), "恢复正常应产生 RECOVERY 事件");
        check(!deviceManager.getSnapshot(Protocol.DEVICE_GAS).isAlarm(), "设备快照应恢复正常");

        queue.offer(new MinePacket(Protocol.DEVICE_GAS, Protocol.CMD_ACK, 1f, base + 3));
        waitUntil(() -> !acks.isEmpty(), 3000L);
        check(acks.size() == 1 && acks.get(0), "应回调网关 ACK 成功");

        Path csv = Files.createTempFile("mine-alarm-test", ".csv");
        try {
            int exported = CsvExporter.exportAlarms(csv, alarms);
            String content = new String(Files.readAllBytes(csv), StandardCharsets.UTF_8);
            check(exported == alarms.size(), "CSV 导出条数应一致");
            check(content.startsWith("\uFEFF序号,时间,级别,设备ID,传感器类型,数值,单位,阈值,告警信息"),
                    "CSV 应带 UTF-8 BOM 与表头");
            check(content.contains("严重告警") && content.contains("恢复正常"),
                    "CSV 应包含严重告警与恢复记录");
            check(content.contains("瓦斯浓度超限"), "CSV 应包含告警消息");
        } finally {
            Files.deleteIfExists(csv);
        }

        List<DeviceSnapshot> offline = service.markAllDevicesOffline();
        check(offline.size() >= 4, "全部设备应标记离线");
        for (DeviceSnapshot snapshot : offline) {
            if (snapshot.isOnline()) {
                throw new AssertionError("设备仍在线: " + snapshot.getDeviceId());
            }
        }
        checks++;

        service.stop();
        alarmManager.stop();
        pool.shutdown();
        System.out.println("[PASS] ServiceSelfTest 全部 " + checks + " 项检查通过");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void waitUntil(BooleanSupplier condition, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(30L);
        }
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }
}

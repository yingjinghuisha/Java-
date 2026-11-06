package com.mine.client.service;

import com.mine.client.concurrent.BusinessThreadPool;
import com.mine.client.concurrent.DataQueueManager;
import com.mine.common.MinePacket;
import com.mine.common.Protocol;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 业务处理核心（消费者）：
 * 报文入队 → 分发线程 → 业务线程池并发处理 → 更新设备/产生告警 → 回调 GUI。
 */
public class SensorDataService {

    public static final long OFFLINE_TIMEOUT_MS = 8000L;
    private static final long MONITOR_INTERVAL_SECONDS = 2L;

    private final DataQueueManager dataQueue;
    private final BusinessThreadPool threadPool;
    private final DeviceManager deviceManager;
    private final AlarmManager alarmManager;
    private final SensorUiListener uiListener;
    private final ScheduledExecutorService monitor;
    private final ConcurrentHashMap<Integer, Boolean> alarmStates =
            new ConcurrentHashMap<Integer, Boolean>();
    private final ConcurrentHashMap<Integer, SerialExecutor> serialExecutors =
            new ConcurrentHashMap<Integer, SerialExecutor>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile Thread dispatcherThread;

    public SensorDataService(DataQueueManager dataQueue, BusinessThreadPool threadPool,
                             DeviceManager deviceManager, AlarmManager alarmManager,
                             SensorUiListener uiListener) {
        this.dataQueue = dataQueue;
        this.threadPool = threadPool;
        this.deviceManager = deviceManager;
        this.alarmManager = alarmManager;
        this.uiListener = uiListener;
        ThreadFactory factory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "device-offline-monitor");
                thread.setDaemon(true);
                return thread;
            }
        };
        this.monitor = Executors.newSingleThreadScheduledExecutor(factory);
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        dispatcherThread = new Thread(new Runnable() {
            @Override
            public void run() {
                dispatchLoop();
            }
        }, "packet-dispatcher");
        dispatcherThread.setDaemon(true);
        dispatcherThread.start();
        monitor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                checkOfflineDevices();
            }
        }, MONITOR_INTERVAL_SECONDS, MONITOR_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void dispatchLoop() {
        while (running.get()) {
            try {
                final MinePacket packet = dataQueue.poll(250L, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    continue;
                }
                try {
                    if (packet.isTelemetry()) {
                        // 同一测点按设备串行、不同设备并行，避免线程池乱序导致旧值/告警回退
                        serialExecutor(packet.getDeviceId()).execute(new Runnable() {
                            @Override
                            public void run() {
                                processPacket(packet);
                            }
                        });
                    } else {
                        threadPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                processPacket(packet);
                            }
                        });
                    }
                } catch (RejectedExecutionException e) {
                    processPacket(packet);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    void processPacket(MinePacket packet) {
        try {
            if (packet.getDataType() == Protocol.CMD_ACK) {
                if (packet.getDeviceId() != Protocol.DEVICE_BROADCAST) {
                    uiListener.onGatewayAck(packet, packet.getValue() >= 0.5F);
                }
                return;
            }
            if (packet.isCommand()) {
                return;
            }
            DeviceSnapshot snapshot = deviceManager.update(packet);
            if (snapshot == null) {
                return;
            }
            boolean currentAlarm = snapshot.isAlarm();
            Boolean previous = alarmStates.put(snapshot.getDeviceId(), currentAlarm);
            boolean activated = currentAlarm && !Boolean.TRUE.equals(previous);
            boolean cleared = !currentAlarm && Boolean.TRUE.equals(previous);

            if (activated) {
                alarmManager.publish(AlarmEvent.critical(snapshot));
            } else if (cleared) {
                alarmManager.publish(AlarmEvent.recovery(snapshot));
            }
            uiListener.onSensorUpdate(snapshot, packet, activated, cleared);
        } catch (Throwable t) {
            uiListener.onServiceError("报文处理异常: " + t);
        }
    }

    private SerialExecutor serialExecutor(int deviceId) {
        SerialExecutor executor = serialExecutors.get(deviceId);
        if (executor != null) {
            return executor;
        }
        SerialExecutor created = new SerialExecutor();
        SerialExecutor previous = serialExecutors.putIfAbsent(deviceId, created);
        return previous == null ? created : previous;
    }

    private void checkOfflineDevices() {
        if (!running.get()) {
            return;
        }
        try {
            List<DeviceSnapshot> offline = deviceManager.markStale(OFFLINE_TIMEOUT_MS);
            for (DeviceSnapshot snapshot : offline) {
                alarmStates.remove(snapshot.getDeviceId());
                alarmManager.publish(AlarmEvent.offline(snapshot));
                uiListener.onDeviceOffline(snapshot);
            }
        } catch (Throwable t) {
            uiListener.onServiceError("设备离线巡检异常: " + t);
        }
    }

    /**
     * 连接断开时由 GUI 调用，立即把设备置为离线并返回全部快照。
     * 该操作不会重复产生离线告警，连接状态由网络层日志记录。
     */
    public List<DeviceSnapshot> markAllDevicesOffline() {
        alarmStates.clear();
        dataQueue.clear();
        return deviceManager.markAllOffline();
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        monitor.shutdownNow();
        Thread thread = dispatcherThread;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * 单设备串行执行器：把一个测点的任务按到达顺序串行执行，
     * 不同测点之间仍由线程池并行处理。
     */
    private final class SerialExecutor {
        private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<Runnable>();
        private final AtomicBoolean active = new AtomicBoolean(false);

        void execute(final Runnable task) {
            tasks.offer(task);
            if (active.compareAndSet(false, true)) {
                scheduleNext();
            }
        }

        private void scheduleNext() {
            try {
                threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Runnable task;
                            while ((task = tasks.poll()) != null) {
                                try {
                                    task.run();
                                } catch (Throwable t) {
                                    uiListener.onServiceError("设备串行任务异常: " + t);
                                }
                            }
                        } finally {
                            active.set(false);
                            if (!tasks.isEmpty() && active.compareAndSet(false, true)) {
                                scheduleNext();
                            }
                        }
                    }
                });
            } catch (RejectedExecutionException e) {
                active.set(false);
            }
        }
    }

    public DeviceManager getDeviceManager() {
        return deviceManager;
    }

    public AlarmManager getAlarmManager() {
        return alarmManager;
    }
}

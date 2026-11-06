package com.mine.gateway;

import com.mine.common.MinePacket;
import com.mine.common.Protocol;
import com.mine.common.SensorType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 多路虚拟传感器采集中心。
 *
 * <p>4 个传感器各自拥有独立线程；每轮采集前调用 {@link CyclicBarrier#await()}
 * 对齐，Barrier 的 action 统一写入本轮时间戳，因此同一组数据共享同一时间戳，
 * 模拟“同一时刻采样”。采集结果交给 {@link PacketPublisher} 广播。</p>
 */
public class SensorHub {

    private static final int SENSOR_COUNT = 4;
    private static final long MIN_PERIOD_MS = 200L;
    private static final long MAX_PERIOD_MS = 5000L;

    private final PacketPublisher publisher;
    private final Map<Integer, VirtualSensor> sensors = new LinkedHashMap<Integer, VirtualSensor>();
    private final List<Thread> workerThreads = new ArrayList<Thread>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong roundCount = new AtomicLong();
    private final AtomicLong generatedCount = new AtomicLong();

    private volatile long samplePeriodMs;
    private volatile long roundTimestamp;
    private volatile CyclicBarrier barrier;

    public SensorHub(PacketPublisher publisher, int initialPeriodMs) {
        this.publisher = publisher;
        this.samplePeriodMs = clampPeriod(initialPeriodMs);

        sensors.put(Protocol.DEVICE_GAS, new VirtualSensor(this, Protocol.DEVICE_GAS,
                SensorType.GAS, 0.35, 0.025, 20240001L));
        sensors.put(Protocol.DEVICE_VACUUM, new VirtualSensor(this, Protocol.DEVICE_VACUUM,
                SensorType.VACUUM, -28.0, 1.1, 20240002L));
        sensors.put(Protocol.DEVICE_FLOW, new VirtualSensor(this, Protocol.DEVICE_FLOW,
                SensorType.FLOW, 26.0, 1.0, 20240003L));
        sensors.put(Protocol.DEVICE_TEMP, new VirtualSensor(this, Protocol.DEVICE_TEMP,
                SensorType.TEMP, 24.0, 0.45, 20240004L));
    }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        barrier = new CyclicBarrier(SENSOR_COUNT, new Runnable() {
            @Override
            public void run() {
                roundTimestamp = System.currentTimeMillis();
                roundCount.incrementAndGet();
            }
        });
        for (VirtualSensor sensor : sensors.values()) {
            Thread thread = new Thread(sensor, "gateway-sensor-" + sensor.getType().getTag());
            thread.setDaemon(true);
            workerThreads.add(thread);
            thread.start();
        }
        GatewayLog.info("SENSOR", "4 路虚拟传感器已启动，采样周期 " + samplePeriodMs
                + " ms，CyclicBarrier 同步采样");
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        for (Thread thread : workerThreads) {
            thread.interrupt();
        }
        for (Thread thread : workerThreads) {
            try {
                thread.join(1200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        workerThreads.clear();
        GatewayLog.info("SENSOR", "虚拟传感器线程已停止");
    }

    /**
     * 传感器线程主循环：睡一个周期 → Barrier 对齐 → 采样 → 发布。
     */
    void sensorLoop(VirtualSensor sensor) {
        while (running.get()) {
            try {
                Thread.sleep(samplePeriodMs);
                barrier.await(12L, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (TimeoutException e) {
                if (!running.get()) {
                    break;
                }
                // 周期刚被修改时各路可能短暂错位，重置 Barrier 后继续
                barrier.reset();
                GatewayLog.warn("SENSOR", "采样同步超时，已重置 CyclicBarrier");
                continue;
            } catch (BrokenBarrierException e) {
                if (!running.get()) {
                    break;
                }
                continue;
            }
            if (!running.get()) {
                break;
            }
            try {
                long timestamp = roundTimestamp;
                MinePacket packet = sensor.nextPacket(timestamp);
                generatedCount.incrementAndGet();
                publisher.publish(packet);
            } catch (RuntimeException e) {
                GatewayLog.error("SENSOR", "采样/发布异常: " + e.getMessage());
            }
        }
    }

    public boolean setPeriod(int periodMs) {
        if (periodMs < MIN_PERIOD_MS || periodMs > MAX_PERIOD_MS) {
            GatewayLog.warn("SENSOR", "采样周期超出范围 " + MIN_PERIOD_MS + "~" + MAX_PERIOD_MS + " ms");
            return false;
        }
        samplePeriodMs = periodMs;
        GatewayLog.info("SENSOR", "采样周期调整为 " + periodMs + " ms（4 路同步生效）");
        return true;
    }

    public boolean setThreshold(int targetDevice, double threshold) {
        if (!Double.isFinite(threshold)) {
            return false;
        }
        if (targetDevice == Protocol.DEVICE_BROADCAST) {
            for (VirtualSensor sensor : sensors.values()) {
                sensor.setThreshold(threshold);
            }
            GatewayLog.info("SENSOR", "全部传感器阈值调整为 " + threshold);
            return true;
        }
        VirtualSensor sensor = sensors.get(targetDevice);
        if (sensor == null) {
            return false;
        }
        sensor.setThreshold(threshold);
        GatewayLog.info("SENSOR", sensor.getType().getDisplayName() + " 阈值调整为 "
                + sensor.getType().formatWithUnit(sensor.getThreshold()));
        return true;
    }

    public boolean triggerAccident(int targetDevice, double peak) {
        VirtualSensor sensor = targetDevice == Protocol.DEVICE_BROADCAST
                ? sensors.get(Protocol.DEVICE_GAS)
                : sensors.get(targetDevice);
        if (sensor == null) {
            return false;
        }
        double actualPeak = peak > 0 ? peak : sensor.getType().getDefaultThreshold() * 2.8;
        sensor.triggerAccident(actualPeak);
        return true;
    }

    public boolean resetAccident(int targetDevice) {
        if (targetDevice == Protocol.DEVICE_BROADCAST) {
            for (VirtualSensor sensor : sensors.values()) {
                sensor.resetAccident();
            }
            return true;
        }
        VirtualSensor sensor = sensors.get(targetDevice);
        if (sensor == null) {
            return false;
        }
        sensor.resetAccident();
        return true;
    }

    public long getGeneratedCount() {
        return generatedCount.get();
    }

    public long getRoundCount() {
        return roundCount.get();
    }

    public long getSamplePeriodMs() {
        return samplePeriodMs;
    }

    public boolean isRunning() {
        return running.get();
    }

    public Map<Integer, VirtualSensor> getSensors() {
        return Collections.unmodifiableMap(sensors);
    }

    public static long clampPeriod(long period) {
        return Math.max(MIN_PERIOD_MS, Math.min(MAX_PERIOD_MS, period));
    }
}

package com.mine.client.service;

import com.mine.common.SensorType;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 单个传感器设备状态。
 *
 * <p>读多写少：GUI 高频读取快照，网络/业务线程低频写入。使用
 * ReentrantReadWriteLock 保证一致性与吞吐。</p>
 */
public class SensorDevice {

    private final int deviceId;
    private final SensorType type;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private double value;
    private double threshold;
    private long timestamp;
    private long lastUpdateMillis;
    private boolean online;
    private long sampleCount;

    public SensorDevice(int deviceId, SensorType type, double threshold) {
        this.deviceId = deviceId;
        this.type = type;
        this.threshold = threshold;
    }

    /**
     * 写入一帧数据。
     *
     * <p>线程池并发处理时，较旧的报文可能后到达业务线程。这里用采集时间戳
     * 做单调性校验，拒绝落后数据，避免“旧值覆盖新值”。</p>
     *
     * @return true 表示数据被接受
     */
    public boolean update(double newValue, long packetTimestamp, long localNow) {
        lock.writeLock().lock();
        try {
            if (sampleCount > 0 && packetTimestamp < timestamp) {
                return false;
            }
            this.value = newValue;
            this.timestamp = packetTimestamp;
            this.lastUpdateMillis = localNow;
            this.online = true;
            this.sampleCount++;
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void setThreshold(double newThreshold) {
        lock.writeLock().lock();
        try {
            this.threshold = type.clamp(newThreshold);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public double getThreshold() {
        lock.readLock().lock();
        try {
            return threshold;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** 超时未上报则标记离线，返回是否发生状态变化。 */
    public boolean markOfflineIfStale(long now, long timeoutMs) {
        lock.writeLock().lock();
        try {
            if (online && lastUpdateMillis > 0 && now - lastUpdateMillis > timeoutMs) {
                online = false;
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean markOffline() {
        lock.writeLock().lock();
        try {
            if (!online) {
                return false;
            }
            online = false;
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public DeviceSnapshot snapshot() {
        lock.readLock().lock();
        try {
            return new DeviceSnapshot(deviceId, type, value, threshold, timestamp,
                    lastUpdateMillis, online, sampleCount);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getDeviceId() {
        return deviceId;
    }

    public SensorType getType() {
        return type;
    }
}

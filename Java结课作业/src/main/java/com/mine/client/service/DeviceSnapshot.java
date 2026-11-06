package com.mine.client.service;

import com.mine.common.SensorType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 传感器状态一致性快照：在读写锁保护下生成，之后不可变，供 GUI 安全使用。 */
public final class DeviceSnapshot {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final int deviceId;
    private final SensorType type;
    private final double value;
    private final double threshold;
    private final long timestamp;
    private final long lastUpdateMillis;
    private final boolean online;
    private final long sampleCount;

    public DeviceSnapshot(int deviceId, SensorType type, double value, double threshold,
                          long timestamp, long lastUpdateMillis, boolean online, long sampleCount) {
        this.deviceId = deviceId;
        this.type = type;
        this.value = value;
        this.threshold = threshold;
        this.timestamp = timestamp;
        this.lastUpdateMillis = lastUpdateMillis;
        this.online = online;
        this.sampleCount = sampleCount;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public SensorType getType() {
        return type;
    }

    public double getValue() {
        return value;
    }

    public double getThreshold() {
        return threshold;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getLastUpdateMillis() {
        return lastUpdateMillis;
    }

    public boolean isOnline() {
        return online;
    }

    public long getSampleCount() {
        return sampleCount;
    }

    public boolean hasData() {
        return lastUpdateMillis > 0;
    }

    public boolean isAlarm() {
        return online && hasData() && type.isAlarm(value, threshold);
    }

    public String getValueText() {
        return hasData() ? type.format(value) : "--";
    }

    public String getValueWithUnit() {
        return hasData() ? type.formatWithUnit(value) : "-- " + type.getUnit();
    }

    public String getThresholdText() {
        return type.format(threshold);
    }

    public String getThresholdExpression() {
        return type.thresholdExpression(threshold);
    }

    public String getLastReportText() {
        if (lastUpdateMillis <= 0) {
            return "--";
        }
        return TIME_FORMAT.format(Instant.ofEpochMilli(lastUpdateMillis));
    }

    public String getStatusText() {
        if (!online) {
            return "离线";
        }
        return isAlarm() ? "告警" : "正常";
    }

    public String getDeviceTag() {
        return type.getTag();
    }

    public String describe() {
        return "#" + deviceId + " " + type.getDisplayName() + " " + getValueWithUnit();
    }
}

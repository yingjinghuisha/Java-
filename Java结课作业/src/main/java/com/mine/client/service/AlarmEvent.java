package com.mine.client.service;

import com.mine.common.SensorType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 告警事件：由业务线程产生，经告警队列异步送到 GUI 与 CSV。 */
public final class AlarmEvent {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final long timestamp;
    private final int deviceId;
    private final SensorType type;
    private final double value;
    private final double threshold;
    private final AlarmLevel level;
    private final String message;

    public AlarmEvent(long timestamp, int deviceId, SensorType type, double value,
                      double threshold, AlarmLevel level, String message) {
        this.timestamp = timestamp;
        this.deviceId = deviceId;
        this.type = type == null ? SensorType.UNKNOWN : type;
        this.value = value;
        this.threshold = threshold;
        this.level = level;
        this.message = message;
    }

    public static AlarmEvent critical(DeviceSnapshot snapshot) {
        String text = snapshot.getType().getAlarmText() + "：当前 "
                + snapshot.getType().formatWithUnit(snapshot.getValue())
                + "，阈值 " + snapshot.getType().thresholdExpression(snapshot.getThreshold())
                + "（设备 #" + snapshot.getDeviceId() + "）";
        return new AlarmEvent(System.currentTimeMillis(), snapshot.getDeviceId(),
                snapshot.getType(), snapshot.getValue(), snapshot.getThreshold(),
                AlarmLevel.CRITICAL, text);
    }

    public static AlarmEvent recovery(DeviceSnapshot snapshot) {
        String text = snapshot.getType().getDisplayName() + "恢复正常：当前 "
                + snapshot.getType().formatWithUnit(snapshot.getValue())
                + "，阈值 " + snapshot.getType().thresholdExpression(snapshot.getThreshold())
                + "（设备 #" + snapshot.getDeviceId() + "）";
        return new AlarmEvent(System.currentTimeMillis(), snapshot.getDeviceId(),
                snapshot.getType(), snapshot.getValue(), snapshot.getThreshold(),
                AlarmLevel.RECOVERY, text);
    }

    public static AlarmEvent offline(DeviceSnapshot snapshot) {
        String text = snapshot.getType().getDisplayName() + "离线：超过设定时间未收到数据（设备 #"
                + snapshot.getDeviceId() + "），最后数值 " + snapshot.getValueWithUnit();
        return new AlarmEvent(System.currentTimeMillis(), snapshot.getDeviceId(),
                snapshot.getType(), snapshot.getValue(), snapshot.getThreshold(),
                AlarmLevel.WARNING, text);
    }

    public static AlarmEvent info(String message) {
        return new AlarmEvent(System.currentTimeMillis(), 0, SensorType.UNKNOWN,
                0.0, 0.0, AlarmLevel.INFO, message);
    }

    public long getTimestamp() {
        return timestamp;
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

    public AlarmLevel getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public String getTimeText() {
        return TIME_FORMAT.format(Instant.ofEpochMilli(timestamp));
    }

    public String getShortTimeText() {
        return TIME_FORMAT.format(Instant.ofEpochMilli(timestamp)).substring(11);
    }

    public String getValueText() {
        if (type == SensorType.UNKNOWN) {
            return "";
        }
        return String.format(Locale.ROOT, "%." + type.getDecimals() + "f", value);
    }

    public String getCsvLine(int index) {
        StringBuilder sb = new StringBuilder();
        sb.append(index).append(',')
                .append(escape(getTimeText())).append(',')
                .append(escape(level.getLabel())).append(',')
                .append(deviceId).append(',')
                .append(escape(type.getDisplayName())).append(',')
                .append(getValueText()).append(',')
                .append(escape(type.getUnit())).append(',')
                .append(type == SensorType.UNKNOWN ? "" : type.format(threshold)).append(',')
                .append(escape(message));
        return sb.toString();
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        String escaped = text.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")
                || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    @Override
    public String toString() {
        return "[" + level.getLabel() + "] " + message;
    }
}

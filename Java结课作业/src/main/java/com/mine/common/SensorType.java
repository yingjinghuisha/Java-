package com.mine.common;

import java.util.Locale;

/**
 * 传感器类型元数据：编码、单位、默认阈值、告警方向、显示精度。
 *
 * <p>告警方向：HIGH 表示数值高于阈值时告警；LOW 表示数值低于阈值时告警。
 * 负压以负值表示（如 -28 kPa），阈值 -20 kPa 时，-28 正常、-18 告警，
 * 语义为“负压不足”，因此仍按 HIGH 处理。</p>
 */
public enum SensorType {

    GAS(Protocol.TYPE_GAS, "瓦斯", "瓦斯浓度", "%", 1.00, AlarmDirection.HIGH,
            2, 0.0, 5.0, 0.05, "GAS-01", "瓦斯浓度超限"),
    VACUUM(Protocol.TYPE_VACUUM, "负压", "抽采负压", "kPa", -20.0, AlarmDirection.HIGH,
            1, -45.0, 0.0, 1.0, "VAC-02", "抽采负压不足"),
    FLOW(Protocol.TYPE_FLOW, "流量", "抽采流量", "m³/min", 20.0, AlarmDirection.LOW,
            1, 0.0, 60.0, 1.0, "FLOW-03", "抽采流量偏低"),
    TEMP(Protocol.TYPE_TEMP, "温度", "环境温度", "°C", 30.0, AlarmDirection.HIGH,
            1, -10.0, 60.0, 0.5, "TEMP-04", "环境温度超限"),
    UNKNOWN(-1, "未知", "未知传感器", "", 0.0, AlarmDirection.HIGH,
            2, -1.0e9, 1.0e9, 1.0, "UNKNOWN", "未知告警");

    public enum AlarmDirection {
        HIGH, LOW
    }

    private final int code;
    private final String shortName;
    private final String displayName;
    private final String unit;
    private final double defaultThreshold;
    private final AlarmDirection direction;
    private final int decimals;
    private final double safeMin;
    private final double safeMax;
    private final double thresholdStep;
    private final String tag;
    private final String alarmText;

    SensorType(int code, String shortName, String displayName, String unit,
               double defaultThreshold, AlarmDirection direction, int decimals,
               double safeMin, double safeMax, double thresholdStep,
               String tag, String alarmText) {
        this.code = code;
        this.shortName = shortName;
        this.displayName = displayName;
        this.unit = unit;
        this.defaultThreshold = defaultThreshold;
        this.direction = direction;
        this.decimals = decimals;
        this.safeMin = safeMin;
        this.safeMax = safeMax;
        this.thresholdStep = thresholdStep;
        this.tag = tag;
        this.alarmText = alarmText;
    }

    public int getCode() {
        return code;
    }

    public String getShortName() {
        return shortName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUnit() {
        return unit;
    }

    public double getDefaultThreshold() {
        return defaultThreshold;
    }

    public AlarmDirection getDirection() {
        return direction;
    }

    public int getDecimals() {
        return decimals;
    }

    public double getSafeMin() {
        return safeMin;
    }

    public double getSafeMax() {
        return safeMax;
    }

    public double getThresholdStep() {
        return thresholdStep;
    }

    public String getTag() {
        return tag;
    }

    public String getAlarmText() {
        return alarmText;
    }

    public boolean isKnown() {
        return this != UNKNOWN;
    }

    public boolean isAlarm(double value, double threshold) {
        if (Double.isNaN(value)) {
            return false;
        }
        return direction == AlarmDirection.HIGH ? value >= threshold : value <= threshold;
    }

    public String format(double value) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    public String formatWithUnit(double value) {
        return format(value) + " " + unit;
    }

    public String thresholdExpression(double threshold) {
        String op = direction == AlarmDirection.HIGH ? "≥" : "≤";
        return op + " " + format(threshold) + " " + unit;
    }

    public double clamp(double value) {
        if (Double.isNaN(value)) {
            return safeMin;
        }
        return Math.max(safeMin, Math.min(safeMax, value));
    }

    public static SensorType fromCode(int code) {
        for (SensorType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return UNKNOWN;
    }

    public static SensorType fromDeviceId(int deviceId) {
        switch (deviceId) {
            case Protocol.DEVICE_GAS:
                return GAS;
            case Protocol.DEVICE_VACUUM:
                return VACUUM;
            case Protocol.DEVICE_FLOW:
                return FLOW;
            case Protocol.DEVICE_TEMP:
                return TEMP;
            default:
                return UNKNOWN;
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}

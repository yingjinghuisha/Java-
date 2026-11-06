package com.mine.client.service;

/** 告警级别。 */
public enum AlarmLevel {

    CRITICAL("严重告警"),
    WARNING("预警"),
    RECOVERY("恢复正常"),
    INFO("信息");

    private final String label;

    AlarmLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isCounted() {
        return this == CRITICAL || this == WARNING;
    }
}

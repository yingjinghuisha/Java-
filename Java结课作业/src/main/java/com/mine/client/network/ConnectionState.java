package com.mine.client.network;

/** 客户端连接状态。 */
public enum ConnectionState {

    DISCONNECTED("未连接"),
    CONNECTING("正在连接"),
    CONNECTED("网关在线"),
    RECONNECTING("自动重连中");

    private final String label;

    ConnectionState(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isOnline() {
        return this == CONNECTED;
    }
}

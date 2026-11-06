package com.mine.client.network;

/**
 * 连接状态回调。注意：回调运行在网络线程，GUI 实现必须自行切换到 EDT。
 */
@FunctionalInterface
public interface ClientConnectionListener {

    void onStateChanged(ConnectionState state, String message);
}

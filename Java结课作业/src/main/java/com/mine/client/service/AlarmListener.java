package com.mine.client.service;

/**
 * 告警回调。运行在告警分发线程，GUI 实现需使用 SwingUtilities.invokeLater。
 */
@FunctionalInterface
public interface AlarmListener {

    void onAlarm(AlarmEvent event);
}

package com.mine.client.service;

import com.mine.common.MinePacket;

/**
 * 业务层到 GUI 的回调接口。
 *
 * <p>所有方法运行在线程池工作线程或定时线程，<b>实现方必须使用
 * SwingUtilities.invokeLater</b> 切换到 EDT 后再更新 Swing 组件。</p>
 */
public interface SensorUiListener {

    void onSensorUpdate(DeviceSnapshot snapshot, MinePacket packet,
                        boolean alarmActivated, boolean alarmCleared);

    void onGatewayAck(MinePacket ack, boolean success);

    void onDeviceOffline(DeviceSnapshot snapshot);

    void onServiceError(String message);
}

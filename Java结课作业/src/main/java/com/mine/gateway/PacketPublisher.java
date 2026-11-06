package com.mine.gateway;

import com.mine.common.MinePacket;

/** 传感器数据出口（由 SensorHub 注入，解耦采集与网络广播）。 */
@FunctionalInterface
interface PacketPublisher {

    void publish(MinePacket packet);
}

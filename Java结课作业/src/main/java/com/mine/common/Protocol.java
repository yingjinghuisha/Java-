package com.mine.common;

/**
 * 系统统一协议常量。
 *
 * <p>帧结构（固定 19 字节，大端序）：</p>
 * <pre>
 * +--------+----------+------+--------+-----------+-------+
 * | 0xAA55 | deviceId | type | value  | timestamp |  CRC  |
 * | 2 B    | 2 B      | 1 B  | 4 B    | 8 B       | 2 B   |
 * +--------+----------+------+--------+-----------+-------+
 * </pre>
 *
 * <p>type 的 0x01~0x04 为遥测数据；0x80 及以上为控制指令/应答。
 * 指令帧仍然复用 19 字节结构：deviceId 表示目标设备（0 表示广播），
 * value 表示指令参数，timestamp 表示请求时间/序号。</p>
 */
public final class Protocol {

    private Protocol() {
    }

    // ---------------- 帧结构 ----------------
    public static final int HEADER_0 = 0xAA;
    public static final int HEADER_1 = 0x55;
    public static final int PACKET_LENGTH = 19;

    public static final int OFFSET_HEADER = 0;
    public static final int OFFSET_DEVICE_ID = 2;
    public static final int OFFSET_TYPE = 4;
    public static final int OFFSET_VALUE = 5;
    public static final int OFFSET_TIMESTAMP = 9;
    public static final int OFFSET_CRC = 17;

    // ---------------- 默认网络参数 ----------------
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8888;

    // ---------------- 设备编号 ----------------
    public static final int DEVICE_BROADCAST = 0;
    public static final int DEVICE_GAS = 1;
    public static final int DEVICE_VACUUM = 2;
    public static final int DEVICE_FLOW = 3;
    public static final int DEVICE_TEMP = 4;

    // ---------------- 遥测数据类型 ----------------
    public static final int TYPE_GAS = 0x01;
    public static final int TYPE_VACUUM = 0x02;
    public static final int TYPE_FLOW = 0x03;
    public static final int TYPE_TEMP = 0x04;

    // ---------------- 控制指令 ----------------
    /** 触发瓦斯突增事故，deviceId 为目标设备，value 为峰值浓度。 */
    public static final int CMD_TRIGGER_ACCIDENT = 0x81;
    /** 修改告警阈值，deviceId 为目标设备，value 为新阈值。 */
    public static final int CMD_SET_THRESHOLD = 0x82;
    /** 修改全局采样周期（毫秒），deviceId 为 0，value 为周期。 */
    public static final int CMD_SET_PERIOD = 0x83;
    /** 解除事故模拟，deviceId 为目标设备或 0。 */
    public static final int CMD_RESET_ACCIDENT = 0x84;
    /** 客户端心跳，deviceId 为 0，timestamp 为发送时刻。 */
    public static final int CMD_HEARTBEAT = 0x90;
    /** 网关应答，value=1 成功 / 0 失败，timestamp 回显请求时间。 */
    public static final int CMD_ACK = 0x91;

    /** 是否为控制类报文。 */
    public static boolean isCommand(int type) {
        return (type & 0xFF) >= 0x80;
    }

    /** 是否为遥测数据报文。 */
    public static boolean isTelemetry(int type) {
        return type >= TYPE_GAS && type <= TYPE_TEMP;
    }

    public static boolean isHeader(byte b0, byte b1) {
        return (b0 & 0xFF) == HEADER_0 && (b1 & 0xFF) == HEADER_1;
    }

    public static String typeName(int type) {
        switch (type) {
            case TYPE_GAS:
                return "瓦斯浓度";
            case TYPE_VACUUM:
                return "抽采负压";
            case TYPE_FLOW:
                return "抽采流量";
            case TYPE_TEMP:
                return "环境温度";
            case CMD_TRIGGER_ACCIDENT:
                return "触发事故";
            case CMD_SET_THRESHOLD:
                return "修改阈值";
            case CMD_SET_PERIOD:
                return "修改采样周期";
            case CMD_RESET_ACCIDENT:
                return "解除事故";
            case CMD_HEARTBEAT:
                return "心跳";
            case CMD_ACK:
                return "网关应答";
            default:
                return String.format("未知类型(0x%02X)", type & 0xFF);
        }
    }

    /** 构造控制指令帧。 */
    public static MinePacket command(int targetDevice, int commandType, float value) {
        if (!isCommand(commandType)) {
            throw new IllegalArgumentException("不是控制指令类型: " + commandType);
        }
        return new MinePacket(targetDevice, commandType, value, System.currentTimeMillis());
    }

    public static MinePacket heartbeat() {
        return new MinePacket(DEVICE_BROADCAST, CMD_HEARTBEAT, 0F, System.currentTimeMillis());
    }

    /** 生成对某条指令的应答帧。 */
    public static MinePacket ack(MinePacket request, boolean success) {
        return new MinePacket(request.getDeviceId(), CMD_ACK, success ? 1F : 0F, request.getTimestamp());
    }

    public static String deviceName(int deviceId) {
        switch (deviceId) {
            case DEVICE_GAS:
                return "瓦斯传感器";
            case DEVICE_VACUUM:
                return "负压传感器";
            case DEVICE_FLOW:
                return "流量传感器";
            case DEVICE_TEMP:
                return "温度传感器";
            case DEVICE_BROADCAST:
                return "广播";
            default:
                return "设备#" + deviceId;
        }
    }
}

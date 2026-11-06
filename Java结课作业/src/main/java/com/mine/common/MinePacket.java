package com.mine.common;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.Objects;

/**
 * 矿山二进制报文实体（不可变）。
 *
 * <p>编码与解码均使用大端序（Big Endian），与工业现场常用协议保持一致。</p>
 */
public final class MinePacket {

    private final int deviceId;
    private final int dataType;
    private final float value;
    private final long timestamp;
    private final int crc;

    /**
     * 构造待发送报文，CRC 在 {@link #encode()} 时计算。
     */
    public MinePacket(int deviceId, int dataType, float value, long timestamp) {
        this(deviceId, dataType, value, timestamp, 0);
    }

    public MinePacket(int deviceId, int dataType, float value, long timestamp, int crc) {
        if (deviceId < 0 || deviceId > 0xFFFF) {
            throw new IllegalArgumentException("deviceId 必须在 0~65535 之间: " + deviceId);
        }
        if (dataType < 0 || dataType > 0xFF) {
            throw new IllegalArgumentException("dataType 必须在 0~255 之间: " + dataType);
        }
        this.deviceId = deviceId;
        this.dataType = dataType;
        this.value = value;
        this.timestamp = timestamp;
        this.crc = crc & 0xFFFF;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public int getDataType() {
        return dataType;
    }

    public float getValue() {
        return value;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getCrc() {
        return crc;
    }

    public boolean isCommand() {
        return Protocol.isCommand(dataType);
    }

    public boolean isTelemetry() {
        return Protocol.isTelemetry(dataType);
    }

    public boolean isAck() {
        return dataType == Protocol.CMD_ACK;
    }

    public SensorType getSensorType() {
        return SensorType.fromCode(dataType);
    }

    /**
     * 对象 → 二进制帧（19 字节）。
     */
    public byte[] encode() {
        byte[] data = new byte[Protocol.PACKET_LENGTH];
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) Protocol.HEADER_0);
        buf.put((byte) Protocol.HEADER_1);
        buf.putShort((short) deviceId);
        buf.put((byte) dataType);
        buf.putFloat(value);
        buf.putLong(timestamp);
        int calculated = Crc16Util.calcCrc16(data, Protocol.OFFSET_CRC);
        Crc16Util.writeCrc16(data, Protocol.OFFSET_CRC, calculated);
        return data;
    }

    /**
     * 二进制帧 → 对象，并完成帧头/长度/CRC/数值合法性校验。
     *
     * @throws PacketFormatException 格式或 CRC 错误
     */
    public static MinePacket decode(byte[] data) throws PacketFormatException {
        return decode(data, 0, data == null ? 0 : data.length);
    }

    public static MinePacket decode(byte[] data, int offset, int length) throws PacketFormatException {
        if (data == null) {
            throw new PacketFormatException("报文为空");
        }
        if (offset < 0 || length < Protocol.PACKET_LENGTH || offset + length > data.length) {
            throw new PacketFormatException("报文长度不足，至少需要 " + Protocol.PACKET_LENGTH + " 字节");
        }
        if (!Protocol.isHeader(data[offset], data[offset + 1])) {
            throw new PacketFormatException("帧头错误，期望 AA 55");
        }
        int expected = Crc16Util.calcCrc16(data, offset, Protocol.OFFSET_CRC);
        int actual = Crc16Util.readCrc16(data, offset + Protocol.OFFSET_CRC);
        if (expected != actual) {
            throw new CrcMismatchException(expected, actual);
        }
        ByteBuffer buf = ByteBuffer.wrap(data, offset, length).order(ByteOrder.BIG_ENDIAN);
        buf.position(offset + 2);
        int deviceId = buf.getShort() & 0xFFFF;
        int type = buf.get() & 0xFF;
        float value = buf.getFloat();
        long timestamp = buf.getLong();
        if (!Float.isFinite(value)) {
            throw new PacketFormatException("数据非法（NaN/Infinity）");
        }
        return new MinePacket(deviceId, type, value, timestamp, actual);
    }

    /** 重新对当前字段编码并计算 CRC。 */
    public int calculateCrc() {
        return Crc16Util.calcCrc16(encode(), Protocol.OFFSET_CRC);
    }

    public String describe() {
        if (isCommand()) {
            return String.format(Locale.ROOT, "%s device=%d value=%.2f ts=%d",
                    Protocol.typeName(dataType), deviceId, value, timestamp);
        }
        SensorType type = getSensorType();
        return String.format(Locale.ROOT, "%s %s", Protocol.deviceName(deviceId), type.formatWithUnit(value));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MinePacket)) {
            return false;
        }
        MinePacket that = (MinePacket) o;
        return deviceId == that.deviceId
                && dataType == that.dataType
                && Float.compare(that.value, value) == 0
                && timestamp == that.timestamp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId, dataType, value, timestamp);
    }

    @Override
    public String toString() {
        return "MinePacket{" + describe() + ", crc=" + Crc16Util.toHex(crc) + '}';
    }
}

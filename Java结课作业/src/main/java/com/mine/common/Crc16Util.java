package com.mine.common;

/**
 * CRC-16/CCITT-FALSE 校验工具。
 *
 * <p>参数：poly=0x1021，init=0xFFFF，refin=false，refout=false，xorout=0x0000。
 * 标准测试向量：ASCII "123456789" 的校验值为 0x29B1。</p>
 *
 * <p>实现采用 256 项查表法，避免逐位循环，适合高频二进制报文校验。</p>
 */
public final class Crc16Util {

    private static final int POLYNOMIAL = 0x1021;
    private static final int INITIAL_VALUE = 0xFFFF;
    private static final int[] TABLE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int crc = i << 8;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ POLYNOMIAL;
                } else {
                    crc <<= 1;
                }
                crc &= 0xFFFF;
            }
            TABLE[i] = crc;
        }
    }

    private Crc16Util() {
    }

    /** 计算 data 前 len 个字节的 CRC16。 */
    public static int calcCrc16(byte[] data, int len) {
        return calcCrc16(data, 0, len);
    }

    /** 计算 data[offset, offset+len) 的 CRC16。 */
    public static int calcCrc16(byte[] data, int offset, int len) {
        if (data == null) {
            throw new IllegalArgumentException("data 不能为空");
        }
        if (offset < 0 || len < 0 || offset + len > data.length) {
            throw new IndexOutOfBoundsException("CRC 计算范围越界");
        }
        int crc = INITIAL_VALUE;
        for (int i = offset; i < offset + len; i++) {
            int index = ((crc >>> 8) ^ (data[i] & 0xFF)) & 0xFF;
            crc = ((crc << 8) ^ TABLE[index]) & 0xFFFF;
        }
        return crc;
    }

    /** 将 CRC 写入目标数组的高字节在前（大端）。 */
    public static void writeCrc16(byte[] data, int offset, int crc) {
        data[offset] = (byte) ((crc >>> 8) & 0xFF);
        data[offset + 1] = (byte) (crc & 0xFF);
    }

    /** 从目标数组按大端读取 CRC。 */
    public static int readCrc16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    public static String toHex(int crc) {
        return String.format("0x%04X", crc & 0xFFFF);
    }
}

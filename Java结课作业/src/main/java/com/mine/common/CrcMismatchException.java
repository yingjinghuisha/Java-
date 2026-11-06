package com.mine.common;

/** CRC 校验失败，说明报文在传输/解析过程中损坏。 */
public class CrcMismatchException extends PacketFormatException {

    private static final long serialVersionUID = 1L;

    private final int expected;
    private final int actual;

    public CrcMismatchException(int expected, int actual) {
        super("CRC 校验失败：报文内=" + Crc16Util.toHex(actual)
                + "，重新计算=" + Crc16Util.toHex(expected));
        this.expected = expected;
        this.actual = actual;
    }

    public int getExpected() {
        return expected;
    }

    public int getActual() {
        return actual;
    }
}

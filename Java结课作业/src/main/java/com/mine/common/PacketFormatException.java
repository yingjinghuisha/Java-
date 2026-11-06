package com.mine.common;

/** 报文格式错误（帧头、长度、CRC、数值非法等）。 */
public class PacketFormatException extends Exception {

    private static final long serialVersionUID = 1L;

    public PacketFormatException(String message) {
        super(message);
    }

    public PacketFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}

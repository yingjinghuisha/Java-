package com.mine.gateway;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** 网关控制台日志（带时间戳，便于联调演示）。 */
final class GatewayLog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private GatewayLog() {
    }

    static void info(String tag, String message) {
        print("INFO ", tag, message);
    }

    static void warn(String tag, String message) {
        print("WARN ", tag, message);
    }

    static void error(String tag, String message) {
        print("ERROR", tag, message);
    }

    private static void print(String level, String tag, String message) {
        System.out.println("[" + LocalTime.now().format(TIME) + "] [" + level + "] ["
                + tag + "] " + message);
    }
}

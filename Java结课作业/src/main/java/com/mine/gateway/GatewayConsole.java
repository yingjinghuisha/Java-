package com.mine.gateway;

import com.mine.common.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 网关交互式控制台，用于答辩现场演示：触发事故、改周期、模拟丢包/断线。
 */
public class GatewayConsole {

    private final GatewayServer server;
    private volatile Thread thread;

    public GatewayConsole(GatewayServer server) {
        this.server = server;
    }

    public void start() {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "gateway-console");
        thread.setDaemon(true);
        thread.start();
        printHelp();
    }

    private void loop() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (server.isRunning()) {
            try {
                System.out.print("gateway> ");
                System.out.flush();
                String line = reader.readLine();
                if (line == null) {
                    return;
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (!handle(line)) {
                    return;
                }
            } catch (IOException e) {
                GatewayLog.error("CONSOLE", "读取控制台输入失败: " + e.getMessage());
                return;
            } catch (RuntimeException e) {
                GatewayLog.error("CONSOLE", "命令执行失败: " + e.getMessage());
            }
        }
    }

    private boolean handle(String line) {
        String[] parts = line.split("\\s+");
        String cmd = parts[0].toLowerCase();
        switch (cmd) {
            case "help":
            case "?":
                printHelp();
                return true;
            case "status":
                System.out.println("  " + server.statusSummary());
                return true;
            case "clients":
                listClients();
                return true;
            case "accident":
                double peak = parts.length > 1 ? parseDouble(parts[1], 2.8) : 2.8;
                server.getSensorHub().triggerAccident(Protocol.DEVICE_GAS, peak);
                return true;
            case "reset":
                server.getSensorHub().resetAccident(Protocol.DEVICE_BROADCAST);
                return true;
            case "threshold":
                if (parts.length < 3) {
                    System.out.println("  用法: threshold <deviceId 0-4> <value>，0 表示全部");
                    return true;
                }
                int device = (int) parseDouble(parts[1], 0);
                double threshold = parseDouble(parts[2], Double.NaN);
                if (Double.isNaN(threshold)) {
                    System.out.println("  阈值格式错误");
                } else {
                    boolean ok = server.getSensorHub().setThreshold(device, threshold);
                    System.out.println("  " + (ok ? "阈值已下发" : "设备编号或阈值非法"));
                }
                return true;
            case "period":
                if (parts.length < 2) {
                    System.out.println("  用法: period <毫秒 200~5000>");
                    return true;
                }
                int period = (int) parseDouble(parts[1], -1);
                boolean ok = server.getSensorHub().setPeriod(period);
                System.out.println("  " + (ok ? "采样周期已更新为 " + period + " ms" : "周期超出范围"));
                return true;
            case "drop":
                if (parts.length < 2) {
                    System.out.println("  用法: drop <0~100>");
                    return true;
                }
                int percent = (int) parseDouble(parts[1], -1);
                if (percent < 0 || percent > 100) {
                    System.out.println("  丢包率必须在 0~100 之间");
                } else {
                    server.setDropPercent(percent);
                }
                return true;
            case "disconnect":
                int count = server.disconnectAll("控制台模拟断线");
                System.out.println("  已断开 " + count + " 个连接，客户端应自动重连");
                return true;
            case "sensors":
                for (int id = 1; id <= 4; id++) {
                    VirtualSensor sensor = server.getSensorHub().getSensors().get(id);
                    if (sensor != null) {
                        System.out.println("  #" + id + " " + sensor.getType().getDisplayName()
                                + " 当前=" + sensor.getType().formatWithUnit(sensor.getCurrentValue())
                                + " 阈值=" + sensor.getType().formatWithUnit(sensor.getThreshold())
                                + (sensor.isAccidentActive() ? " [事故模拟中]" : ""));
                    }
                }
                return true;
            case "quit":
            case "exit":
                server.stop();
                return false;
            default:
                System.out.println("  未知命令: " + cmd + "，输入 help 查看帮助");
                return true;
        }
    }

    private void listClients() {
        List<ClientSession> sessions = server.getSessions();
        if (sessions.isEmpty()) {
            System.out.println("  当前没有客户端连接");
            return;
        }
        for (ClientSession session : sessions) {
            System.out.println("  客户端#" + session.getSessionId()
                    + " " + session.getRemoteAddress()
                    + " 已发送=" + session.getSentPackets()
                    + " 队列=" + session.getSendQueueSize()
                    + " 随机丢包=" + session.getDroppedNetwork()
                    + " 队列丢弃=" + session.getDroppedQueue()
                    + " CRC错误=" + session.getCrcErrors());
        }
    }

    private static double parseDouble(String text, double fallback) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static void printHelp() {
        System.out.println();
        System.out.println("  ┌──────────────── 网关演示控制台 ────────────────┐");
        System.out.println("  │ help                          查看命令帮助      │");
        System.out.println("  │ status                        查看运行统计      │");
        System.out.println("  │ clients                       查看在线客户端    │");
        System.out.println("  │ sensors                       查看 4 路传感器    │");
        System.out.println("  │ accident [峰值]               触发瓦斯突增事故  │");
        System.out.println("  │ reset                         解除事故模拟      │");
        System.out.println("  │ period <毫秒>                 修改全局采样周期  │");
        System.out.println("  │ threshold <设备号> <阈值>     修改告警阈值      │");
        System.out.println("  │ drop <0-100>                  设置模拟丢包率    │");
        System.out.println("  │ disconnect                    主动断开所有客户端 │");
        System.out.println("  │ quit / exit                   关闭网关          │");
        System.out.println("  └────────────────────────────────────────────────┘");
        System.out.println();
    }
}

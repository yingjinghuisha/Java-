package com.mine.gateway;

import com.mine.common.Protocol;

import java.io.IOException;

/**
 * 仿真网关入口。
 *
 * <p>用法示例：</p>
 * <pre>
 *   java -cp out/classes com.mine.gateway.GatewayMain
 *   java -cp out/classes com.mine.gateway.GatewayMain --host 0.0.0.0 --port 8888 --drop 5 --period 500
 * </pre>
 */
public final class GatewayMain {

    private GatewayMain() {
    }

    public static void main(String[] args) {
        String host = Protocol.DEFAULT_HOST;
        int port = Protocol.DEFAULT_PORT;
        int drop = 0;
        int period = 500;
        boolean console = true;

        try {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--host":
                        host = requireValue(args, ++i, "--host");
                        break;
                    case "--port":
                        port = Integer.parseInt(requireValue(args, ++i, "--port"));
                        break;
                    case "--drop":
                        drop = Integer.parseInt(requireValue(args, ++i, "--drop"));
                        break;
                    case "--period":
                        period = Integer.parseInt(requireValue(args, ++i, "--period"));
                        break;
                    case "--no-console":
                        console = false;
                        break;
                    case "--help":
                    case "-h":
                        printUsage();
                        return;
                    default:
                        System.err.println("未知参数: " + arg);
                        printUsage();
                        System.exit(2);
                        return;
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println("参数错误: " + e.getMessage());
            printUsage();
            System.exit(2);
            return;
        }

        if (port < 1 || port > 65535) {
            System.err.println("端口必须在 1~65535 之间");
            System.exit(2);
            return;
        }
        if (drop < 0 || drop > 100) {
            System.err.println("丢包率必须在 0~100 之间");
            System.exit(2);
            return;
        }

        printBanner(host, port, drop, period);
        final GatewayServer server = new GatewayServer(host, port, drop, period);
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("网关启动失败: " + e.getMessage());
            System.err.println("请检查 IP/端口是否被占用，或是否具有绑定该地址的权限。");
            System.exit(1);
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                server.stop();
            }
        }, "gateway-shutdown"));

        if (console) {
            new GatewayConsole(server).start();
        } else {
            GatewayLog.info("SERVER", "控制台已禁用，进程持续运行（Ctrl+C 退出）");
        }

        try {
            server.awaitTermination();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.stop();
        }
        System.out.println("网关进程退出。");
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new IllegalArgumentException(option + " 缺少参数值");
        }
        return args[index];
    }

    private static void printBanner(String host, int port, int drop, int period) {
        System.out.println();
        System.out.println("  ██████████████████████████████████████████████████████████");
        System.out.println("       智慧矿山瓦斯抽采多传感器监控仿真系统 · 边缘网关");
        System.out.println("       TCP Binary Protocol / CRC16-CCITT / Multi-Sensor");
        System.out.println("  ──────────────────────────────────────────────────────────");
        System.out.println("       绑定地址 : " + host + ":" + port);
        System.out.println("       采样周期 : " + period + " ms（4 路同步）");
        System.out.println("       模拟丢包 : " + drop + " %");
        System.out.println("       协议帧长 : " + Protocol.PACKET_LENGTH + " 字节，帧头 0xAA55");
        System.out.println("  ██████████████████████████████████████████████████████████");
        System.out.println();
    }

    private static void printUsage() {
        System.out.println("用法: java com.mine.gateway.GatewayMain [options]");
        System.out.println("  --host <ip>       绑定 IP，默认 " + Protocol.DEFAULT_HOST + "（局域网演示可用 0.0.0.0）");
        System.out.println("  --port <port>     监听端口，默认 " + Protocol.DEFAULT_PORT);
        System.out.println("  --drop <0-100>    模拟网络丢包率，默认 0");
        System.out.println("  --period <ms>     采样周期 200~5000，默认 500");
        System.out.println("  --no-console      不启动交互控制台");
        System.out.println("  --help            显示帮助");
    }
}

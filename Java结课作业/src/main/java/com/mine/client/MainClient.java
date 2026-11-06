package com.mine.client;

import com.mine.client.concurrent.BusinessThreadPool;
import com.mine.client.concurrent.DataQueueManager;
import com.mine.client.gui.MainFrame;
import com.mine.client.gui.UiTheme;
import com.mine.client.network.MineTcpClient;
import com.mine.client.service.AlarmManager;
import com.mine.client.service.DeviceManager;
import com.mine.client.service.SensorDataService;
import com.mine.common.Protocol;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;

/**
 * GUI 客户端入口。
 *
 * <p>启动链路：Swing EDT 构建界面 → 启动告警分发线程 → 启动业务线程池消费者
 * → 用户点击连接后由后台线程建立 TCP 长连接。</p>
 */
public final class MainClient {

    private MainClient() {
    }

    public static void main(String[] args) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "智慧矿山监控系统");
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("[客户端] 未捕获异常，线程=" + thread.getName() + "：" + throwable);
            throwable.printStackTrace();
        });

        final String host = option(args, "--host", Protocol.DEFAULT_HOST);
        final int port = parsePort(option(args, "--port", String.valueOf(Protocol.DEFAULT_PORT)));
        final String localIp = option(args, "--local-ip", "");
        final boolean autoConnect = hasFlag(args, "--auto-connect");

        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("当前环境为 Headless，无法启动 Swing 界面。");
            System.exit(1);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // 系统 LAF 不可用时回退默认
            }
            UiTheme.install();
            startApplication(host, port, localIp, autoConnect);
        });
    }

    public static void startApplication(String host, int port, String localIp, boolean autoConnect) {
        DataQueueManager queue = new DataQueueManager(5000);
        BusinessThreadPool threadPool = new BusinessThreadPool();
        DeviceManager deviceManager = new DeviceManager();
        AlarmManager alarmManager = new AlarmManager();
        MineTcpClient client = new MineTcpClient(queue);

        MainFrame frame = new MainFrame(client, deviceManager, alarmManager, queue, threadPool);
        SensorDataService service = new SensorDataService(queue, threadPool,
                deviceManager, alarmManager, frame);
        frame.setService(service);

        client.setListener(frame);
        alarmManager.start(frame);
        service.start();

        frame.setGatewayAddress(host, port);
        frame.setLocalIp(localIp);
        frame.setVisible(true);
        if (autoConnect) {
            SwingUtilities.invokeLater(frame::connectNow);
        }
        System.out.println("[客户端] GUI 已启动，默认网关 " + host + ":" + port
                + (localIp == null || localIp.trim().isEmpty() ? "，本机 IP 自动" : "，本机工控机 IP " + localIp)
                + (autoConnect ? "，已请求自动连接" : ""));
    }

    private static String option(String[] args, String name, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            return port >= 1 && port <= 65535 ? port : Protocol.DEFAULT_PORT;
        } catch (NumberFormatException e) {
            return Protocol.DEFAULT_PORT;
        }
    }
}

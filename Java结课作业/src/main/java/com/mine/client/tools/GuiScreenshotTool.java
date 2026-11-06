package com.mine.client.tools;

import com.mine.client.concurrent.BusinessThreadPool;
import com.mine.client.concurrent.DataQueueManager;
import com.mine.client.gui.MainFrame;
import com.mine.client.gui.UiTheme;
import com.mine.client.network.MineTcpClient;
import com.mine.client.service.AlarmManager;
import com.mine.client.service.DeviceManager;
import com.mine.client.service.SensorDataService;
import com.mine.gateway.GatewayServer;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 生成课程报告用的运行截图（正常监控 / 瓦斯告警 / 断线重连）。
 * 仅用于开发演示，不参与客户端主流程。
 *
 * <p>用法：{@code java -cp out/classes com.mine.client.tools.GuiScreenshotTool [输出目录]}</p>
 */
public final class GuiScreenshotTool {

    private static final int PORT = 18888;

    private GuiScreenshotTool() {
    }

    public static void main(String[] args) throws Exception {
        Path outputDir = new File(args.length > 0 ? args[0] : "docs/screenshots").toPath();
        Files.createDirectories(outputDir);

        GatewayServer gateway = new GatewayServer("127.0.0.1", PORT, 0, 400);
        gateway.start();

        final AtomicReference<MainFrame> frameRef = new AtomicReference<MainFrame>();
        final AtomicReference<SensorDataService> serviceRef = new AtomicReference<SensorDataService>();
        final AtomicReference<AlarmManager> alarmRef = new AtomicReference<AlarmManager>();
        final AtomicReference<BusinessThreadPool> poolRef = new AtomicReference<BusinessThreadPool>();

        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // ignore
            }
            UiTheme.install();
            DataQueueManager queue = new DataQueueManager(5000);
            BusinessThreadPool pool = new BusinessThreadPool();
            DeviceManager deviceManager = new DeviceManager();
            AlarmManager alarmManager = new AlarmManager();
            MineTcpClient client = new MineTcpClient(queue);
            MainFrame frame = new MainFrame(client, deviceManager, alarmManager, queue, pool);
            SensorDataService service = new SensorDataService(queue, pool, deviceManager,
                    alarmManager, frame);
            frame.setService(service);
            client.setListener(frame);
            alarmManager.start(frame);
            service.start();
            frame.setGatewayAddress("127.0.0.1", PORT);
            frame.setLocalIp("127.0.0.1");
            // 截图期间禁止外部键盘/鼠标干扰，保证自动化结果可复现
            frame.setUndecorated(true);
            frame.setFocusableWindowState(false);
            frame.setAutoRequestFocus(false);
            JPanel blocker = new JPanel();
            blocker.setOpaque(false);
            blocker.addMouseListener(new MouseAdapter() { });
            frame.setGlassPane(blocker);
            blocker.setVisible(true);
            frame.setVisible(true);
            frameRef.set(frame);
            serviceRef.set(service);
            alarmRef.set(alarmManager);
            poolRef.set(pool);
        });

        Thread.sleep(1200L);
        MainFrame frame = frameRef.get();
        SwingUtilities.invokeAndWait(frame::connectNow);
        Thread.sleep(6000L);
        capture(frame, outputDir.resolve("01-dashboard.png"));

        SwingUtilities.invokeAndWait(frame::triggerAccidentNow);
        Thread.sleep(4200L);
        capture(frame, outputDir.resolve("02-gas-alarm.png"));

        gateway.stop();
        Thread.sleep(3200L);
        capture(frame, outputDir.resolve("03-reconnecting.png"));

        SwingUtilities.invokeAndWait(frame::shutdownApplication);
        alarmRef.get().stop();
        poolRef.get().shutdown();
        serviceRef.get().stop();
        gateway.stop();
        System.out.println("截图已生成到 " + outputDir.toAbsolutePath());
        System.exit(0);
    }

    private static void capture(MainFrame frame, Path path) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                java.awt.Container content = frame.getContentPane();
                BufferedImage image = new BufferedImage(content.getWidth(), content.getHeight(),
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = image.createGraphics();
                try {
                    content.paintAll(g2);
                } finally {
                    g2.dispose();
                }
                ImageIO.write(image, "png", path.toFile());
                System.out.println("已截图 " + path + " (" + image.getWidth() + "x" + image.getHeight() + ")");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}

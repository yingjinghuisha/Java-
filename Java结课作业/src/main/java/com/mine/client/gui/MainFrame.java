package com.mine.client.gui;

import com.mine.client.concurrent.BusinessThreadPool;
import com.mine.client.concurrent.DataQueueManager;
import com.mine.client.network.ClientConnectionListener;
import com.mine.client.network.ClientStats;
import com.mine.client.network.ConnectionState;
import com.mine.client.network.MineTcpClient;
import com.mine.client.service.AlarmEvent;
import com.mine.client.service.AlarmLevel;
import com.mine.client.service.AlarmListener;
import com.mine.client.service.AlarmManager;
import com.mine.client.service.CsvExporter;
import com.mine.client.service.DeviceManager;
import com.mine.client.service.DeviceSnapshot;
import com.mine.client.service.SensorDataService;
import com.mine.client.service.SensorUiListener;
import com.mine.common.IpRange;
import com.mine.common.MinePacket;
import com.mine.common.Protocol;
import com.mine.common.SensorType;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 客户端主窗口。
 *
 * <p>所有网络/业务回调都在后台线程触发，进入本类后统一通过
 * SwingUtilities.invokeLater 切换到 EDT 更新组件。</p>
 */
public class MainFrame extends JFrame implements ClientConnectionListener, SensorUiListener,
        AlarmListener, CommandPanel.Actions {

    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final MineTcpClient client;
    private final DeviceManager deviceManager;
    private final AlarmManager alarmManager;
    private final DataQueueManager dataQueue;
    private final BusinessThreadPool threadPool;

    private final SensorTablePanel tablePanel = new SensorTablePanel();
    private final ChartPanel chartPanel = new ChartPanel();
    private final MineMapPanel mapPanel = new MineMapPanel();
    private final AlarmLogPanel alarmLogPanel = new AlarmLogPanel();
    private final CommandPanel commandPanel;

    private final JTextField hostField = new JTextField(Protocol.DEFAULT_HOST);
    private final JTextField portField = new JTextField(String.valueOf(Protocol.DEFAULT_PORT));
    private final JTextField localIpField = new JTextField();
    private final StatusPill statusPill = new StatusPill();
    private final JLabel clockLabel = new JLabel(UiTheme.clockText());
    private final JLabel connectionDetailLabel = new JLabel(
            "允许 172.20.205.0–172.20.224.255，或 127.0.0.1", SwingConstants.RIGHT);
    private final JLabel statsLabel = new JLabel("接收 0 帧 · CRC 0 · 队列 0/--");
    private final JLabel runtimeLabel = new JLabel("线程池 --/5 · 告警待发 0");

    private final StatCard onlineCard = new StatCard("设备在线", "0/4", "等待连接", UiTheme.OK);
    private final StatCard gasCard = new StatCard("瓦斯浓度", "--", "阈值 ≥ 1.00 %", UiTheme.GAS);
    private final StatCard alarmCard = new StatCard("活动告警", "0", "累计 0 条", UiTheme.DANGER);
    private final StatCard packetCard = new StatCard("接收报文", "0", "CRC 错误 0", UiTheme.TEMP);

    private final RoundedButton connectButton = new RoundedButton("连接网关",
            RoundedButton.Variant.PRIMARY, 94);
    private final RoundedButton disconnectButton = new RoundedButton("断开连接",
            RoundedButton.Variant.SECONDARY, 88);
    private final RoundedButton accidentButton = new RoundedButton("模拟瓦斯事故",
            RoundedButton.Variant.DANGER, 116);
    private final RoundedButton clearButton = new RoundedButton("清空告警",
            RoundedButton.Variant.GHOST, 84);
    private final RoundedButton exitButton = new RoundedButton("退出系统",
            RoundedButton.Variant.DANGER, 92);

    private final Timer uiTimer;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private volatile ConnectionState lastState = ConnectionState.DISCONNECTED;
    private volatile ConnectionState loggedState;
    private volatile String lastStateMessage = "尚未连接网关";
    private volatile SensorDataService service;
    private boolean popupEnabled = true;
    private long lastBeepAt;

    public MainFrame(MineTcpClient client, DeviceManager deviceManager,
                     AlarmManager alarmManager, DataQueueManager dataQueue,
                     BusinessThreadPool threadPool) {
        super("智慧矿山瓦斯抽采多传感器监控仿真系统");
        this.client = client;
        this.deviceManager = deviceManager;
        this.alarmManager = alarmManager;
        this.dataQueue = dataQueue;
        this.threadPool = threadPool;
        this.commandPanel = new CommandPanel(this);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1280, 800));
        setBackground(UiTheme.BG);
        setContentPane(buildRoot());
        setIconImage(createAppIcon());
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int width = Math.min(1420, Math.max(1200, screen.width - 60));
        int height = Math.min(940, Math.max(800, screen.height - 40));
        setSize(width, height);
        setLocationRelativeTo(null);

        UiTheme.styleTextField(hostField, 14);
        UiTheme.styleTextField(portField, 5);
        UiTheme.styleTextField(localIpField, 12);
        hostField.setPreferredSize(new Dimension(142, 32));
        hostField.setToolTipText("网关 IP：仅允许连接 " + IpRange.rangeText()
                + "；本机调试可用 " + IpRange.LOCALHOST + " 或 localhost");
        portField.setPreferredSize(new Dimension(72, 32));
        portField.setHorizontalAlignment(SwingConstants.CENTER);
        localIpField.setPreferredSize(new Dimension(132, 32));
        localIpField.setToolTipText("本机工控机源 IP，留空自动使用系统地址；填写后绑定该地址（须为 "
                + IpRange.rangeText() + "，或 127.0.0.1）");

        tablePanel.setSnapshots(deviceManager.snapshots());
        mapPanel.setSnapshots(deviceManager.snapshots());
        applyControls(ConnectionState.DISCONNECTED);
        refreshCards();

        uiTimer = new Timer(1000, e -> tick());
        uiTimer.start();

        bindExitShortcut();
        alarmLogPanel.setOnClear(this::clearAlarms);
        alarmLogPanel.setOnExport(this::exportAlarmsCsv);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdownApplication();
                System.exit(0);
            }
        });
    }

    private JPanel buildRoot() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UiTheme.BG);
        root.add(buildHeader(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));
        body.add(buildSplit(), BorderLayout.CENTER);
        alarmLogPanel.setPreferredSize(new Dimension(0, 145));
        body.add(alarmLogPanel, BorderLayout.SOUTH);
        root.add(body, BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildHeader() {
        HeaderPanel header = new HeaderPanel();
        header.setLayout(new BorderLayout(0, 10));
        header.setBorder(BorderFactory.createEmptyBorder(12, 20, 10, 20));

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 13, 0));
        brand.setOpaque(false);
        brand.add(new LogoMark());

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("智慧矿山瓦斯抽采监控系统");
        title.setForeground(UiTheme.TEXT);
        title.setFont(UiTheme.FONT_TITLE.deriveFont(23f));
        JLabel subtitle = new JLabel("SMART MINE GAS DRAINAGE MONITORING  ·  TCP / CRC16 / CONCURRENCY / SWING");
        subtitle.setForeground(UiTheme.TEXT_FAINT);
        subtitle.setFont(UiTheme.FONT_TINY);
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(subtitle);
        brand.add(titleBox);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        statusRow.setOpaque(false);
        clockLabel.setForeground(UiTheme.TEXT_DIM);
        clockLabel.setFont(UiTheme.FONT_MONO.deriveFont(13f));
        statusRow.add(clockLabel);
        statusRow.add(statusPill);
        statusRow.setAlignmentX(Component.RIGHT_ALIGNMENT);
        right.add(statusRow);
        connectionDetailLabel.setForeground(UiTheme.TEXT_FAINT);
        connectionDetailLabel.setFont(UiTheme.FONT_TINY);
        connectionDetailLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        connectionDetailLabel.setPreferredSize(new Dimension(390, 16));
        connectionDetailLabel.setMaximumSize(new Dimension(390, 16));
        right.add(Box.createVerticalStrut(2));
        right.add(connectionDetailLabel);

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.add(brand, BorderLayout.WEST);
        topRow.add(right, BorderLayout.EAST);

        header.add(topRow, BorderLayout.NORTH);
        header.add(buildControlBar(), BorderLayout.CENTER);
        return header;
    }

    private JPanel buildControlBar() {
        RoundedBar bar = new RoundedBar();
        bar.setLayout(new BorderLayout(12, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(caption("网关 IP"));
        left.add(hostField);
        left.add(caption("端口"));
        left.add(portField);
        left.add(caption("本机IP"));
        left.add(localIpField);
        left.add(separator());
        left.add(connectButton);
        left.add(disconnectButton);
        left.add(accidentButton);
        left.add(clearButton);

        connectButton.setToolTipText("在后台线程建立 TCP 长连接，不阻塞 EDT");
        disconnectButton.setToolTipText("主动断开；断开后不再自动重连");
        accidentButton.setToolTipText("向网关下发 0x81 指令，触发瓦斯浓度突增");
        clearButton.setToolTipText("清空告警日志与计数（不清除设备状态）");
        exitButton.setToolTipText("关闭监控客户端并退出程序（快捷键 Ctrl+Q / ⌘Q）");
        connectButton.addActionListener(e -> doConnect());
        disconnectButton.addActionListener(e -> doDisconnect());
        accidentButton.addActionListener(e -> triggerAccident());
        clearButton.addActionListener(e -> clearAlarms());
        exitButton.addActionListener(e -> confirmExit());

        statsLabel.setForeground(UiTheme.TEXT_DIM);
        statsLabel.setFont(UiTheme.FONT_SMALL);
        statsLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        statsLabel.setToolTipText("网络层实时统计：接收帧数、CRC 错误、阻塞队列占用");

        JPanel rightTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightTools.setOpaque(false);
        rightTools.add(statsLabel);
        rightTools.add(exitButton);

        bar.add(left, BorderLayout.WEST);
        bar.add(rightTools, BorderLayout.EAST);
        return bar;
    }

    private javax.swing.JSplitPane buildSplit() {
        JPanel cards = new JPanel(new GridLayout(1, 4, 10, 0));
        cards.setOpaque(false);
        cards.add(onlineCard);
        cards.add(gasCard);
        cards.add(alarmCard);
        cards.add(packetCard);

        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.setOpaque(false);
        left.add(cards, BorderLayout.NORTH);
        left.add(tablePanel, BorderLayout.CENTER);
        mapPanel.setPreferredSize(new Dimension(0, 175));
        left.add(mapPanel, BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout(0, 6));
        right.setOpaque(false);
        right.add(chartPanel, BorderLayout.CENTER);
        commandPanel.setPreferredSize(new Dimension(0, 180));
        right.add(commandPanel, BorderLayout.SOUTH);

        javax.swing.JSplitPane split = new javax.swing.JSplitPane(
                javax.swing.JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setOpaque(false);
        split.setBorder(null);
        split.setDividerSize(10);
        split.setContinuousLayout(true);
        split.setResizeWeight(0.46);
        split.setDividerLocation(0.46);
        return split;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(14, 0));
        footer.setBackground(UiTheme.BG_ALT);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDER_SOFT),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)));

        JLabel pipeline = new JLabel("数据管线：TCP 接收线程 → 有界阻塞队列 → 业务线程池（3~5）→ ConcurrentHashMap / 告警队列 → SwingUtilities.invokeLater → EDT");
        pipeline.setForeground(UiTheme.TEXT_FAINT);
        pipeline.setFont(UiTheme.FONT_TINY);
        runtimeLabel.setForeground(UiTheme.TEXT_FAINT);
        runtimeLabel.setFont(UiTheme.FONT_TINY);
        runtimeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        footer.add(pipeline, BorderLayout.WEST);
        footer.add(runtimeLabel, BorderLayout.EAST);
        return footer;
    }

    private static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(UiTheme.TEXT_DIM);
        label.setFont(UiTheme.FONT_SMALL);
        return label;
    }

    private static JSeparator separator() {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setForeground(UiTheme.BORDER);
        separator.setPreferredSize(new Dimension(1, 24));
        return separator;
    }

    private void tick() {
        clockLabel.setText(UiTheme.clockText());
        statusPill.pulse();
        ClientStats stats = client.snapshot();
        statsLabel.setText("接收 " + formatCount(stats.getReceivedPackets())
                + " 帧 · CRC " + stats.getCrcErrors()
                + " · 队列 " + stats.getQueueSize() + "/" + stats.getQueueCapacity());
        String localDisplay = stats.getActualLocalIp().isEmpty()
                ? (stats.getRequestedLocalIp().isEmpty() ? "自动" : stats.getRequestedLocalIp())
                : stats.getActualLocalIp();
        statsLabel.setToolTipText("网络层统计：接收帧数、CRC 错误、阻塞队列；本机工控机 IP=" + localDisplay);
        runtimeLabel.setText("业务线程 " + threadPool.getPoolSize() + "/5"
                + " · 池队列 " + threadPool.getQueueSize()
                + " · 完成 " + formatCount(threadPool.getCompletedTaskCount())
                + " · CallerRuns " + threadPool.getCallerRuns()
                + " · 告警待发 " + alarmManager.getPendingCount());
        commandPanel.setConnected(client.isConnected());
        refreshCards();
    }

    private void refreshCards() {
        List<DeviceSnapshot> snapshots = deviceManager.snapshots();
        int online = 0;
        int alarm = 0;
        DeviceSnapshot gas = null;
        for (DeviceSnapshot snapshot : snapshots) {
            if (snapshot.isOnline()) {
                online++;
            }
            if (snapshot.isAlarm()) {
                alarm++;
            }
            if (snapshot.getType() == SensorType.GAS) {
                gas = snapshot;
            }
        }
        onlineCard.setValue(online + "/" + snapshots.size(),
                online > 0 ? UiTheme.OK : UiTheme.TEXT_FAINT);
        onlineCard.setDetail(online == snapshots.size() ? "全部设备在线" : "在线设备 · 离线 "
                + (snapshots.size() - online));

        if (gas != null && gas.hasData()) {
            if (gas.isOnline()) {
                gasCard.setValue(gas.getValueText() + " " + SensorType.GAS.getUnit(),
                        gas.isAlarm() ? UiTheme.DANGER_LIGHT : UiTheme.GAS);
                gasCard.setDetail("瓦斯浓度 · 阈值 " + gas.getThresholdExpression());
            } else {
                gasCard.setValue(gas.getValueText() + " " + SensorType.GAS.getUnit(),
                        UiTheme.TEXT_FAINT);
                gasCard.setDetail("链路离线 · 最后上报 " + gas.getLastReportText());
            }
        } else {
            gasCard.setValue("--", UiTheme.TEXT_FAINT);
            gasCard.setDetail("等待瓦斯测点数据");
        }
        alarmCard.setValue(String.valueOf(alarm), alarm > 0 ? UiTheme.DANGER_LIGHT : UiTheme.OK);
        alarmCard.setDetail("活动告警 · 累计 " + alarmManager.getAlarmCount() + " 条");

        ClientStats stats = client.snapshot();
        packetCard.setValue(formatCount(stats.getReceivedPackets()), UiTheme.TEMP);
        packetCard.setDetail("CRC " + stats.getCrcErrors() + " · 队列丢弃 " + stats.getQueueDropped());
        alarmLogPanel.setCounts(alarmManager.getAlarmCount(), alarmManager.getCriticalCount());
    }

    private void doConnect() {
        if (client.isRunning()) {
            return;
        }
        String host = hostField.getText() == null ? "" : hostField.getText().trim();
        String portText = portField.getText() == null ? "" : portField.getText().trim();
        String hostError = IpRange.validateHost(host);
        if (hostError != null) {
            showInputError(hostError);
            return;
        }
        String localIp = localIpField.getText() == null ? "" : localIpField.getText().trim();
        String localError = IpRange.validateLocalIp(localIp);
        if (localError != null) {
            showInputError(localError);
            localIpField.requestFocusInWindow();
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            showInputError("端口必须是 1~65535 的数字");
            return;
        }
        if (port < 1 || port > 65535) {
            showInputError("端口必须在 1~65535 之间");
            return;
        }
        final int finalPort = port;
        connectButton.setEnabled(false);
        disconnectButton.setEnabled(true);
        hostField.setEnabled(false);
        portField.setEnabled(false);
        localIpField.setEnabled(false);
        commandPanel.setNeutralMessage("正在连接 " + host + ":" + port + " …");
        alarmLogPanel.appendInfo("正在后台线程连接网关 " + host + ":" + port
                + (localIp.isEmpty() ? "（本机 IP 自动）" : "（绑定本机 IP " + localIp + "）") + " …");
        final String finalLocalIp = localIp;
        Thread thread = new Thread(() -> {
            boolean started = client.start(host, finalPort, finalLocalIp);
            if (!started) {
                SwingUtilities.invokeLater(() -> {
                    alarmLogPanel.appendError("连接请求未启动：客户端已在运行或已关闭");
                    applyControls(ConnectionState.DISCONNECTED);
                });
            }
        }, "gui-connect-request");
        thread.setDaemon(true);
        thread.start();
    }

    private void doDisconnect() {
        if (!client.isRunning()) {
            return;
        }
        alarmLogPanel.appendInfo("用户主动断开网关连接。");
        commandPanel.setNeutralMessage("已主动断开连接");
        client.stop();
    }

    private void triggerAccident() {
        if (!client.isConnected()) {
            alarmLogPanel.appendWarning("尚未连接网关，无法下发事故指令。");
            commandPanel.setLastAck("未连接网关，无法下发事故指令", false);
            return;
        }
        final float peak = (float) (SensorType.GAS.getDefaultThreshold() * 2.8);
        sendCommandAsync(Protocol.DEVICE_GAS, Protocol.CMD_TRIGGER_ACCIDENT, peak,
                "已下发瓦斯突增指令：目标峰值 " + SensorType.GAS.format(peak) + " %，等待网关执行",
                "瓦斯突增指令发送失败：链路已断开");
    }

    private void sendCommandAsync(final int target, final int command, final float value,
                                  final String successMessage, final String failureMessage) {
        Thread thread = new Thread(() -> {
            final boolean sent = client.sendCommand(target, command, value);
            SwingUtilities.invokeLater(() -> {
                if (sent) {
                    commandPanel.setLastAck(successMessage, true);
                    alarmLogPanel.appendInfo(successMessage);
                } else {
                    commandPanel.setLastAck(failureMessage, false);
                    alarmLogPanel.appendError(failureMessage);
                }
            });
        }, "gui-command-" + Protocol.typeName(command));
        thread.setDaemon(true);
        thread.start();
    }

    private void clearAlarms() {
        alarmManager.clear();
        alarmLogPanel.clearLog();
        alarmLogPanel.setCounts(0, 0);
        refreshCards();
    }

    private void exportAlarmsCsv() {
        List<AlarmEvent> events = alarmManager.historySnapshot();
        if (events.isEmpty()) {
            JOptionPane.showMessageDialog(this, "当前没有可导出的告警记录。",
                    "导出告警 CSV", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导出告警记录为 CSV");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("CSV 文件 (*.csv)", "csv"));
        chooser.setSelectedFile(new File(System.getProperty("user.dir"),
                "alarm-log-" + FILE_STAMP.format(LocalDateTime.now()) + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final Path path = CsvExporter.ensureCsvSuffix(chooser.getSelectedFile().toPath());
        final List<AlarmEvent> snapshot = alarmManager.historySnapshot();
        alarmLogPanel.appendInfo("正在后台线程导出 " + snapshot.size() + " 条告警记录…");
        Thread thread = new Thread(() -> {
            try {
                final int count = CsvExporter.exportAlarms(path, snapshot);
                SwingUtilities.invokeLater(() -> {
                    alarmLogPanel.appendInfo("已导出 " + count + " 条告警记录：" + path.toAbsolutePath());
                    JOptionPane.showMessageDialog(this,
                            "已导出 " + count + " 条告警记录：\n" + path.toAbsolutePath(),
                            "导出成功", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    alarmLogPanel.appendError("导出告警 CSV 失败：" + e.getMessage());
                    JOptionPane.showMessageDialog(this, "导出失败：" + e.getMessage(),
                            "导出告警 CSV", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "csv-export");
        thread.setDaemon(true);
        thread.start();
    }

    private void showInputError(String message) {
        alarmLogPanel.appendError(message);
        JOptionPane.showMessageDialog(this, message, "输入有误", JOptionPane.WARNING_MESSAGE);
    }

    private void bindExitShortcut() {
        Action exitAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                confirmExit();
            }
        };
        JComponent root = getRootPane();
        root.getActionMap().put("exit-system", exitAction);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK), "exit-system");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.META_DOWN_MASK), "exit-system");
    }

    /**
     * 虚拟化环境可能没有窗口标题栏的关闭按钮，因此提供界面内的退出入口。
     */
    private void confirmExit() {
        int result = JOptionPane.showConfirmDialog(this,
                "确定要关闭监控客户端并退出系统吗？", "退出系统",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (result == JOptionPane.YES_OPTION) {
            shutdownApplication();
            System.exit(0);
        }
    }

    // ------------------- CommandPanel.Actions -------------------

    @Override
    public double currentThreshold(SensorType type) {
        return deviceManager.getThreshold(type.getCode());
    }

    @Override
    public void sendThreshold(SensorType type, double value) {
        if (!client.isConnected()) {
            commandPanel.setLastAck("未连接网关，无法下发阈值", false);
            alarmLogPanel.appendWarning("未连接网关，阈值指令未发送。");
            return;
        }
        double bounded = type.clamp(value);
        deviceManager.setThreshold(type.getCode(), bounded);
        DeviceSnapshot snapshot = deviceManager.getSnapshot(type.getCode());
        if (snapshot != null) {
            tablePanel.updateSnapshot(snapshot);
        }
        chartPanel.setThreshold(type, bounded);
        commandPanel.setThresholdValue(type, bounded);
        commandPanel.setLastAck("阈值已本地生效，正在下发网关…", true);
        sendCommandAsync(type.getCode(), Protocol.CMD_SET_THRESHOLD, (float) bounded,
                type.getDisplayName() + "阈值已下发：" + type.thresholdExpression(bounded),
                "阈值指令发送失败：链路已断开");
        refreshCards();
    }

    @Override
    public void sendPeriod(int periodMs) {
        if (!client.isConnected()) {
            commandPanel.setLastAck("未连接网关，无法下发采样周期", false);
            alarmLogPanel.appendWarning("未连接网关，采样周期指令未发送。");
            return;
        }
        commandPanel.setPeriodValue(periodMs);
        sendCommandAsync(Protocol.DEVICE_BROADCAST, Protocol.CMD_SET_PERIOD, periodMs,
                "采样周期指令已发送：" + periodMs + " ms（4 路同步生效）",
                "采样周期指令发送失败：链路已断开");
    }

    @Override
    public void resetAccident() {
        if (!client.isConnected()) {
            commandPanel.setLastAck("未连接网关，无法解除事故", false);
            return;
        }
        sendCommandAsync(Protocol.DEVICE_BROADCAST, Protocol.CMD_RESET_ACCIDENT, 0F,
                "已下发解除事故指令，传感器将自然回落",
                "解除事故指令发送失败：链路已断开");
    }

    @Override
    public void resetDefaults() {
        if (!client.isConnected()) {
            commandPanel.setLastAck("未连接网关，无法恢复默认阈值", false);
            return;
        }
        for (SensorType type : new SensorType[]{SensorType.GAS, SensorType.VACUUM,
                SensorType.FLOW, SensorType.TEMP}) {
            deviceManager.setThreshold(type.getCode(), type.getDefaultThreshold());
            DeviceSnapshot snapshot = deviceManager.getSnapshot(type.getCode());
            if (snapshot != null) {
                tablePanel.updateSnapshot(snapshot);
            }
            chartPanel.setThreshold(type, type.getDefaultThreshold());
        }
        commandPanel.setLastAck("默认阈值已本地生效，正在下发网关…", true);
        Thread thread = new Thread(() -> {
            int success = 0;
            for (SensorType type : new SensorType[]{SensorType.GAS, SensorType.VACUUM,
                    SensorType.FLOW, SensorType.TEMP}) {
                if (client.sendCommand(type.getCode(), Protocol.CMD_SET_THRESHOLD,
                        (float) type.getDefaultThreshold())) {
                    success++;
                }
            }
            final int count = success;
            SwingUtilities.invokeLater(() -> {
                commandPanel.setLastAck("已下发 " + count + "/4 条默认阈值指令", count == 4);
                alarmLogPanel.appendInfo("已恢复默认阈值，下发 " + count + "/4 条指令。");
            });
        }, "gui-command-reset-thresholds");
        thread.setDaemon(true);
        thread.start();
        refreshCards();
    }

    @Override
    public void setPopupEnabled(boolean enabled) {
        this.popupEnabled = enabled;
        alarmLogPanel.appendInfo(enabled ? "严重告警弹窗与提示音已开启。" : "严重告警弹窗与提示音已关闭。");
    }

    // ------------------- 网络状态回调（后台线程） -------------------

    @Override
    public void onStateChanged(ConnectionState state, String message) {
        final ConnectionState previous = lastState;
        lastState = state;
        lastStateMessage = message;
        final boolean becameOffline = previous == ConnectionState.CONNECTED
                && state != ConnectionState.CONNECTED;
        final List<DeviceSnapshot> offlineSnapshots;
        if (becameOffline) {
            offlineSnapshots = service != null
                    ? service.markAllDevicesOffline()
                    : deviceManager.markAllOffline();
        } else {
            offlineSnapshots = null;
        }
        final boolean shouldLog = state != loggedState;
        loggedState = state;

        SwingUtilities.invokeLater(() -> {
            statusPill.setState(state, message);
            connectionDetailLabel.setText(elide(message, 58));
            connectionDetailLabel.setToolTipText(message);
            applyControls(state);
            if (offlineSnapshots != null) {
                tablePanel.setSnapshots(offlineSnapshots);
                mapPanel.setSnapshots(offlineSnapshots);
                refreshCards();
            }
            if (shouldLog) {
                switch (state) {
                    case CONNECTED:
                        alarmLogPanel.appendInfo("网关连接成功：" + message);
                        commandPanel.setNeutralMessage("网关在线，长连接已建立");
                        break;
                    case CONNECTING:
                        alarmLogPanel.appendInfo(message);
                        break;
                    case RECONNECTING:
                        alarmLogPanel.appendWarning(message);
                        break;
                    case DISCONNECTED:
                    default:
                        alarmLogPanel.appendInfo(message);
                        commandPanel.setNeutralMessage("连接已断开");
                        break;
                }
            }
        });
    }

    private void applyControls(ConnectionState state) {
        boolean disconnected = state == ConnectionState.DISCONNECTED;
        connectButton.setEnabled(disconnected);
        disconnectButton.setEnabled(!disconnected);
        accidentButton.setEnabled(state == ConnectionState.CONNECTED);
        hostField.setEnabled(disconnected);
        portField.setEnabled(disconnected);
        localIpField.setEnabled(disconnected);
        commandPanel.setConnected(state == ConnectionState.CONNECTED);
        chartPanel.setConnected(state == ConnectionState.CONNECTED);
    }

    // ------------------- 业务层回调（线程池线程） -------------------

    @Override
    public void onSensorUpdate(DeviceSnapshot snapshot, MinePacket packet,
                               boolean alarmActivated, boolean alarmCleared) {
        SwingUtilities.invokeLater(() -> {
            tablePanel.updateSnapshot(snapshot);
            chartPanel.addSnapshot(snapshot);
            mapPanel.update(snapshot);
            refreshCards();
        });
    }

    @Override
    public void onGatewayAck(MinePacket ack, boolean success) {
        final String text = "网关应答 · 设备 #" + ack.getDeviceId() + " · "
                + (success ? "指令执行成功" : "指令执行失败");
        SwingUtilities.invokeLater(() -> {
            commandPanel.setLastAck(text, success);
            if (success) {
                alarmLogPanel.appendInfo(text);
            } else {
                alarmLogPanel.appendError(text);
            }
        });
    }

    @Override
    public void onDeviceOffline(DeviceSnapshot snapshot) {
        SwingUtilities.invokeLater(() -> {
            tablePanel.updateSnapshot(snapshot);
            mapPanel.update(snapshot);
            refreshCards();
        });
    }

    @Override
    public void onServiceError(String message) {
        SwingUtilities.invokeLater(() -> alarmLogPanel.appendError(message));
    }

    // ------------------- 告警分发线程回调 -------------------

    @Override
    public void onAlarm(AlarmEvent event) {
        SwingUtilities.invokeLater(() -> {
            alarmLogPanel.appendAlarm(event);
            alarmLogPanel.setCounts(alarmManager.getAlarmCount(), alarmManager.getCriticalCount());
            refreshCards();
            if (event.getLevel() == AlarmLevel.CRITICAL && popupEnabled) {
                AlarmToast.show(this, event);
                long now = System.currentTimeMillis();
                if (now - lastBeepAt > 1500L) {
                    lastBeepAt = now;
                    try {
                        Toolkit.getDefaultToolkit().beep();
                    } catch (Throwable ignored) {
                        // 某些无音频设备环境忽略
                    }
                }
            }
        });
    }

    // ------------------- 对外方法（截图工具/测试） -------------------

    public void setService(SensorDataService service) {
        this.service = service;
    }

    public void setGatewayAddress(String host, int port) {
        Runnable task = () -> {
            hostField.setText(host);
            portField.setText(String.valueOf(port));
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    public void setLocalIp(String localIp) {
        Runnable task = () -> localIpField.setText(localIp == null ? "" : localIp);
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    public void connectNow() {
        if (SwingUtilities.isEventDispatchThread()) {
            doConnect();
        } else {
            SwingUtilities.invokeLater(this::doConnect);
        }
    }

    public void disconnectNow() {
        if (SwingUtilities.isEventDispatchThread()) {
            doDisconnect();
        } else {
            SwingUtilities.invokeLater(this::doDisconnect);
        }
    }

    public void triggerAccidentNow() {
        if (SwingUtilities.isEventDispatchThread()) {
            triggerAccident();
        } else {
            SwingUtilities.invokeLater(this::triggerAccident);
        }
    }

    public void shutdownApplication() {
        if (!shuttingDown.compareAndSet(false, true)) {
            return;
        }
        if (uiTimer != null) {
            uiTimer.stop();
        }
        client.shutdown();
        if (service != null) {
            service.stop();
        }
        alarmManager.stop();
        threadPool.shutdown();
        dispose();
    }

    // ------------------- 工具方法 -------------------

    private static String formatCount(long number) {
        return String.format(Locale.ROOT, "%,d", number);
    }

    private static String elide(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 1) + "…";
    }

    private static Image createAppIcon() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, UiTheme.ACCENT, 64, 64, new Color(0x0EA5E9)));
            g2.fillRoundRect(2, 2, 60, 60, 16, 16);
            g2.setColor(new Color(0x04222A));
            g2.setStroke(new java.awt.BasicStroke(2.2f));
            Path2D hex = new Path2D.Float();
            for (int i = 0; i < 6; i++) {
                double angle = Math.PI / 3 * i - Math.PI / 6;
                int x = 32 + (int) Math.round(18 * Math.cos(angle));
                int y = 32 + (int) Math.round(18 * Math.sin(angle));
                if (i == 0) {
                    hex.moveTo(x, y);
                } else {
                    hex.lineTo(x, y);
                }
            }
            hex.closePath();
            g2.draw(hex);
            g2.setColor(Color.WHITE);
            g2.fillOval(28, 28, 8, 8);
            g2.setColor(UiTheme.ACCENT);
            g2.setStroke(new java.awt.BasicStroke(2f));
            g2.drawLine(20, 44, 27, 37);
            g2.drawLine(37, 37, 45, 43);
        } finally {
            g2.dispose();
        }
        return image;
    }

    /** 顶部渐变背景。 */
    private static final class HeaderPanel extends JPanel {
        HeaderPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, new Color(0x0C2130),
                        getWidth(), getHeight(), new Color(0x08131C)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(0x0F2C3C));
                for (int x = 0; x < getWidth(); x += 56) {
                    g2.drawLine(x, 0, x, getHeight());
                }
                for (int y = 0; y < getHeight(); y += 34) {
                    g2.drawLine(0, y, getWidth(), y);
                }
                g2.setPaint(new GradientPaint(0, 0, UiTheme.ACCENT,
                        320, 0, new Color(0x0EA5E9)));
                g2.fillRect(0, 0, Math.min(520, getWidth()), 2);
                g2.setColor(UiTheme.BORDER_SOFT);
                g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            } finally {
                g2.dispose();
            }
        }
    }

    /** 控制条圆角半透明背景。 */
    private static final class RoundedBar extends JPanel {
        RoundedBar() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x0F2431));
                g2.fillRoundRect(0, 0, getWidth(), getHeight() - 1, 12, 12);
                g2.setColor(UiTheme.BORDER_SOFT);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 2, 12, 12);
            } finally {
                g2.dispose();
            }
        }
    }

    /** 品牌 Logo。 */
    private static final class LogoMark extends JPanel {
        LogoMark() {
            setOpaque(false);
            setPreferredSize(new Dimension(48, 48));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UiTheme.alpha(UiTheme.ACCENT, 34));
                g2.fillRoundRect(1, 3, 46, 46, 15, 15);
                g2.setPaint(new GradientPaint(4, 4, UiTheme.ACCENT, 44, 44,
                        new Color(0x0EA5E9)));
                g2.fillRoundRect(3, 2, 42, 42, 14, 14);
                g2.setColor(new Color(0x04222A));
                g2.setStroke(new java.awt.BasicStroke(2f));
                Path2D hex = new Path2D.Float();
                for (int i = 0; i < 6; i++) {
                    double angle = Math.PI / 3 * i - Math.PI / 6;
                    int x = 24 + (int) Math.round(13 * Math.cos(angle));
                    int y = 23 + (int) Math.round(13 * Math.sin(angle));
                    if (i == 0) {
                        hex.moveTo(x, y);
                    } else {
                        hex.lineTo(x, y);
                    }
                }
                hex.closePath();
                g2.draw(hex);
                g2.setColor(Color.WHITE);
                g2.fillOval(21, 20, 6, 6);
                g2.setColor(new Color(0x04222A));
                g2.setStroke(new java.awt.BasicStroke(1.8f));
                g2.drawLine(15, 32, 20, 27);
                g2.drawLine(28, 27, 34, 32);
            } finally {
                g2.dispose();
            }
        }
    }
}

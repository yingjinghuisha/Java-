package com.mine.client.gui;

import com.mine.client.service.AlarmEvent;
import com.mine.client.service.AlarmLevel;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** 告警/事件日志：JTextPane 富文本着色，严重告警红色高亮。 */
public class AlarmLogPanel extends CardPanel {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int MAX_DOC_LENGTH = 180_000;

    private final JTextPane textPane = new JTextPane();
    private final StyledDocument document = textPane.getStyledDocument();
    private final JScrollPane scrollPane;
    private final JLabel countLabel = new JLabel("告警 0 · 严重 0");
    private final SimpleAttributeSet timeStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet messageStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet criticalStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet warningStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet recoveryStyle = new SimpleAttributeSet();
    private final SimpleAttributeSet infoStyle = new SimpleAttributeSet();

    private Runnable onClear;
    private Runnable onExport;

    public AlarmLogPanel() {
        super("告警与事件日志", "ALARM & EVENT LOG · 独立告警分发线程 → Swing EDT");
        setAccent(UiTheme.DANGER);

        buildStyles();
        textPane.setEditable(false);
        textPane.setFocusable(false);
        UiTheme.styleTextPane(textPane);
        scrollPane = new JScrollPane(textPane);
        UiTheme.styleScrollPane(scrollPane);
        scrollPane.getViewport().setBackground(UiTheme.CARD_ALT);

        countLabel.setForeground(UiTheme.TEXT_DIM);
        countLabel.setFont(UiTheme.FONT_SMALL);
        RoundedButton clearButton = new RoundedButton("清空", RoundedButton.Variant.GHOST, 54);
        clearButton.setToolTipText("清空告警日志（不清除设备当前状态）");
        clearButton.addActionListener(e -> {
            if (onClear != null) {
                onClear.run();
            }
        });
        RoundedButton exportButton = new RoundedButton("导出 CSV", RoundedButton.Variant.SECONDARY, 82);
        exportButton.setToolTipText("在后台线程导出告警记录为 CSV");
        exportButton.addActionListener(e -> {
            if (onExport != null) {
                onExport.run();
            }
        });
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        tools.setOpaque(false);
        tools.add(countLabel);
        tools.add(clearButton);
        tools.add(exportButton);
        setHeaderRight(tools);

        setContent(scrollPane);
        appendInfo("系统就绪。输入网关 IP/端口后点击【连接网关】。");
    }

    public void setOnClear(Runnable onClear) {
        this.onClear = onClear;
    }

    public void setOnExport(Runnable onExport) {
        this.onExport = onExport;
    }

    private void buildStyles() {
        StyleConstants.setFontFamily(timeStyle, UiTheme.MONO_FAMILY);
        StyleConstants.setFontSize(timeStyle, 11);
        StyleConstants.setForeground(timeStyle, UiTheme.TEXT_FAINT);

        StyleConstants.setFontFamily(messageStyle, UiTheme.MONO_FAMILY);
        StyleConstants.setFontSize(messageStyle, 12);
        StyleConstants.setForeground(messageStyle, UiTheme.TEXT);

        configure(criticalStyle, UiTheme.DANGER_LIGHT, true);
        configure(warningStyle, UiTheme.WARN, false);
        configure(recoveryStyle, UiTheme.OK, false);
        configure(infoStyle, UiTheme.INFO, false);
    }

    private void configure(SimpleAttributeSet style, Color color, boolean bold) {
        StyleConstants.setFontFamily(style, UiTheme.MONO_FAMILY);
        StyleConstants.setFontSize(style, 12);
        StyleConstants.setForeground(style, color);
        StyleConstants.setBold(style, bold);
    }

    public void appendAlarm(AlarmEvent event) {
        if (event == null) {
            return;
        }
        Color color;
        String level;
        SimpleAttributeSet style;
        switch (event.getLevel()) {
            case CRITICAL:
                color = UiTheme.DANGER_LIGHT;
                level = "严重";
                style = criticalStyle;
                break;
            case WARNING:
                color = UiTheme.WARN;
                level = "预警";
                style = warningStyle;
                break;
            case RECOVERY:
                color = UiTheme.OK;
                level = "恢复";
                style = recoveryStyle;
                break;
            case INFO:
            default:
                color = UiTheme.INFO;
                level = "信息";
                style = infoStyle;
                break;
        }
        appendLine(event.getShortTimeText(), level, color, event.getMessage(), style);
    }

    public void appendInfo(String message) {
        appendLine(LocalTime.now().format(TIME), "信息", UiTheme.INFO, message, infoStyle);
    }

    public void appendWarning(String message) {
        appendLine(LocalTime.now().format(TIME), "预警", UiTheme.WARN, message, warningStyle);
    }

    public void appendError(String message) {
        appendLine(LocalTime.now().format(TIME), "错误", UiTheme.DANGER_LIGHT, message, criticalStyle);
    }

    private void appendLine(String time, String level, Color color, String message,
                            SimpleAttributeSet style) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> appendLine(time, level, color, message, style));
            return;
        }
        boolean atBottom = isAtBottom();
        try {
            document.insertString(document.getLength(), time + "  ", timeStyle);
            SimpleAttributeSet tagStyle = new SimpleAttributeSet();
            StyleConstants.setFontFamily(tagStyle, UiTheme.MONO_FAMILY);
            StyleConstants.setFontSize(tagStyle, 11);
            StyleConstants.setBold(tagStyle, true);
            StyleConstants.setForeground(tagStyle, color);
            document.insertString(document.getLength(), "[" + level + "]  ", tagStyle);
            document.insertString(document.getLength(), message + "\n", style);
            trimIfNeeded();
        } catch (BadLocationException e) {
            // 日志写入失败不应影响主流程
        }
        if (atBottom) {
            SwingUtilities.invokeLater(this::scrollToBottom);
        }
    }

    private void trimIfNeeded() throws BadLocationException {
        if (document.getLength() <= MAX_DOC_LENGTH) {
            return;
        }
        String content = document.getText(0, document.getLength());
        int cut = content.indexOf('\n', 30_000);
        if (cut > 0) {
            document.remove(0, cut + 1);
        }
    }

    private boolean isAtBottom() {
        if (scrollPane == null) {
            return true;
        }
        javax.swing.JScrollBar bar = scrollPane.getVerticalScrollBar();
        return bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 36;
    }

    private void scrollToBottom() {
        textPane.setCaretPosition(document.getLength());
        scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum());
    }

    public void clearLog() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::clearLog);
            return;
        }
        textPane.setText("");
        setCounts(0, 0);
        appendInfo("告警日志已清空。");
    }

    public void setCounts(int alarms, int critical) {
        countLabel.setText("告警 " + alarms + " · 严重 " + critical);
        countLabel.setForeground(critical > 0 ? UiTheme.DANGER_LIGHT : UiTheme.TEXT_DIM);
    }

    public JTextPane getTextPane() {
        return textPane;
    }
}

package com.mine.client.gui;

import com.mine.client.service.AlarmEvent;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Window;

/** 严重告警浮窗，4.5 秒自动消失。只在 EDT 创建。 */
public final class AlarmToast {

    private static volatile JWindow current;

    private AlarmToast() {
    }

    public static void show(Window owner, AlarmEvent event) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeLater(() -> createAndShow(owner, event));
    }

    private static void createAndShow(Window owner, AlarmEvent event) {
        closeCurrent();
        JWindow window = new JWindow(owner);
        window.setAlwaysOnTop(true);
        window.setBackground(UiTheme.CARD);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(UiTheme.CARD);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.DANGER, 1, true),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));

        JPanel bar = new JPanel();
        bar.setBackground(UiTheme.DANGER);
        bar.setPreferredSize(new Dimension(5, 0));
        root.add(bar, BorderLayout.WEST);

        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 18));

        JLabel title = new JLabel("⚠ 严重告警 · " + event.getType().getDisplayName());
        title.setForeground(UiTheme.DANGER_LIGHT);
        title.setFont(UiTheme.FONT_BOLD.deriveFont(15f));

        JTextArea message = new JTextArea(event.getMessage());
        message.setEditable(false);
        message.setOpaque(false);
        message.setForeground(UiTheme.TEXT);
        message.setFont(UiTheme.FONT_SMALL.deriveFont(12.5f));
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setRows(2);
        message.setBorder(null);

        JLabel hint = new JLabel("请立即查看抽采参数并处置 · 4 秒后自动关闭");
        hint.setForeground(UiTheme.TEXT_FAINT);
        hint.setFont(UiTheme.FONT_TINY);

        content.add(title, BorderLayout.NORTH);
        content.add(message, BorderLayout.CENTER);
        content.add(hint, BorderLayout.SOUTH);
        root.add(content, BorderLayout.CENTER);
        window.setContentPane(root);
        window.setSize(390, 132);

        Point location = calculateLocation(owner, window);
        window.setLocation(location);
        window.setVisible(true);
        current = window;

        Timer timer = new Timer(4500, e -> {
            window.dispose();
            if (current == window) {
                current = null;
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private static Point calculateLocation(Window owner, Window window) {
        if (owner != null && owner.isShowing()) {
            Point ownerLocation = owner.getLocationOnScreen();
            int x = ownerLocation.x + owner.getWidth() - window.getWidth() - 28;
            int y = ownerLocation.y + 86;
            return new Point(Math.max(10, x), Math.max(10, y));
        }
        Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        return new Point(screen.width - window.getWidth() - 30, 80);
    }

    private static void closeCurrent() {
        JWindow window = current;
        if (window != null) {
            window.dispose();
            current = null;
        }
    }
}

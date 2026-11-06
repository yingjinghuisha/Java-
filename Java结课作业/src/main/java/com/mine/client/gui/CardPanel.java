package com.mine.client.gui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** 带圆角、标题栏与阴影的卡片容器。 */
public class CardPanel extends JPanel {

    private final JLabel titleLabel = new JLabel();
    private final JLabel subtitleLabel = new JLabel();
    private final JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    private final JPanel content = new JPanel(new BorderLayout());
    private Color accent = UiTheme.ACCENT;
    private int radius = 16;

    public CardPanel(String title, String subtitle) {
        super(new BorderLayout());
        setOpaque(false);
        titleLabel.setText(title);
        titleLabel.setForeground(UiTheme.TEXT);
        titleLabel.setFont(UiTheme.FONT_CARD_TITLE);
        subtitleLabel.setText(subtitle);
        subtitleLabel.setForeground(UiTheme.TEXT_FAINT);
        subtitleLabel.setFont(UiTheme.FONT_TINY);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(titleLabel);
        titleBox.add(Box.createVerticalStrut(3));
        titleBox.add(subtitleLabel);

        JPanel titleRow = new JPanel();
        titleRow.setOpaque(false);
        titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
        titleRow.add(new AccentBar());
        titleRow.add(Box.createHorizontalStrut(9));
        titleRow.add(titleBox);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(14, 16, 9, 16));
        header.add(titleRow, BorderLayout.WEST);
        headerRight.setOpaque(false);
        header.add(headerRight, BorderLayout.EAST);

        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(0, 14, 14, 14));

        add(header, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }

    public void setHeaderRight(Component component) {
        headerRight.removeAll();
        if (component != null) {
            headerRight.add(component);
        }
        headerRight.revalidate();
        headerRight.repaint();
    }

    public JPanel getHeaderRight() {
        return headerRight;
    }

    public void setContent(Component component) {
        content.removeAll();
        if (component != null) {
            content.add(component, BorderLayout.CENTER);
        }
        content.revalidate();
        content.repaint();
    }

    public JPanel getContentPanel() {
        return content;
    }

    public void setTitleText(String text) {
        titleLabel.setText(text);
    }

    public void setSubtitleText(String text) {
        subtitleLabel.setText(text);
    }

    public void setAccent(Color color) {
        this.accent = color;
        repaint();
    }

    public void setRadius(int radius) {
        this.radius = radius;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            g2.setColor(new Color(0, 0, 0, 55));
            g2.fillRoundRect(2, 4, Math.max(0, w - 4), Math.max(0, h - 5), radius, radius);
            g2.setPaint(new GradientPaint(0, 0, UiTheme.CARD, 0, h, new Color(0x0D1F2A)));
            g2.fillRoundRect(1, 1, Math.max(0, w - 3), Math.max(0, h - 4), radius, radius);
            g2.setColor(UiTheme.BORDER_SOFT);
            g2.drawRoundRect(1, 1, Math.max(0, w - 3), Math.max(0, h - 4), radius, radius);
            g2.setColor(UiTheme.alpha(accent, 38));
            g2.drawLine(radius, 1, Math.max(radius, w - radius), 1);
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    private final class AccentBar extends JComponent {
        AccentBar() {
            setPreferredSize(new Dimension(3, 28));
            setMinimumSize(new Dimension(3, 28));
            setMaximumSize(new Dimension(3, 28));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UiTheme.alpha(accent, 230));
                g2.fillRoundRect(0, 4, getWidth(), Math.max(0, getHeight() - 8), 4, 4);
            } finally {
                g2.dispose();
            }
        }
    }
}

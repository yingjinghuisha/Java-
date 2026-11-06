package com.mine.client.gui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** 顶部指标卡：在线设备、当前瓦斯、活动告警、接收报文。 */
public class StatCard extends JPanel {

    private final JLabel valueLabel = new JLabel("--");
    private final JLabel detailLabel = new JLabel(" ");
    private Color accent;

    public StatCard(String caption, String initialValue, String detail, Color accent) {
        super(new BorderLayout());
        this.accent = accent;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 11));
        setPreferredSize(new Dimension(140, 68));

        JLabel captionLabel = new JLabel(caption);
        captionLabel.setForeground(UiTheme.TEXT_FAINT);
        captionLabel.setFont(UiTheme.FONT_TINY);
        captionLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));

        valueLabel.setText(initialValue);
        valueLabel.setForeground(accent);
        valueLabel.setFont(UiTheme.FONT_MONO_BOLD.deriveFont(19f));

        detailLabel.setText(detail);
        detailLabel.setForeground(UiTheme.TEXT_DIM);
        detailLabel.setFont(UiTheme.FONT_TINY);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new javax.swing.BoxLayout(bottom, javax.swing.BoxLayout.Y_AXIS));
        bottom.add(valueLabel);
        bottom.add(detailLabel);

        add(captionLabel, BorderLayout.NORTH);
        add(bottom, BorderLayout.CENTER);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(UiTheme.CARD_ALT);
            g2.fillRoundRect(0, 0, getWidth(), getHeight() - 1, 12, 12);
            g2.setColor(UiTheme.BORDER_SOFT);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 2, 12, 12);
            g2.setColor(UiTheme.alpha(accent, 220));
            g2.fillRoundRect(0, 14, 3, getHeight() - 30, 3, 3);
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    public void setValue(String value, Color color) {
        valueLabel.setText(value);
        if (color != null) {
            valueLabel.setForeground(color);
        }
        repaint();
    }

    public void setDetail(String detail) {
        detailLabel.setText(detail);
    }

    public void setAccent(Color color) {
        this.accent = color;
        repaint();
    }
}

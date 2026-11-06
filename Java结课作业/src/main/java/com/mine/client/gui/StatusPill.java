package com.mine.client.gui;

import com.mine.client.network.ConnectionState;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** 连接状态胶囊（带呼吸式指示点）。 */
public class StatusPill extends JComponent {

    private ConnectionState state = ConnectionState.DISCONNECTED;
    private String detail = "尚未连接网关";
    private int pulse;

    public StatusPill() {
        setOpaque(false);
        setPreferredSize(new Dimension(204, 34));
    }

    public void setState(ConnectionState state, String detail) {
        this.state = state == null ? ConnectionState.DISCONNECTED : state;
        this.detail = detail == null ? "" : detail;
        setToolTipText(detail);
        repaint();
    }

    public void pulse() {
        pulse = (pulse + 1) % 24;
        if (state == ConnectionState.CONNECTING || state == ConnectionState.RECONNECTING
                || state == ConnectionState.CONNECTED) {
            repaint();
        }
    }

    public ConnectionState getState() {
        return state;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color color = colorFor(state);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(UiTheme.alpha(color, 24));
            g2.fillRoundRect(0, 0, w, h - 1, h, h);
            g2.setColor(UiTheme.alpha(color, 100));
            g2.drawRoundRect(0, 0, w - 1, h - 2, h, h);

            int cx = 18;
            int cy = (h - 1) / 2;
            int glow = 5 + (pulse % 8);
            g2.setColor(UiTheme.alpha(color, state == ConnectionState.DISCONNECTED ? 18 : 28));
            g2.fillOval(cx - glow, cy - glow, glow * 2, glow * 2);
            g2.setColor(color);
            g2.fillOval(cx - 4, cy - 4, 8, 8);
            g2.setColor(UiTheme.alpha(Color.WHITE, 160));
            g2.fillOval(cx - 2, cy - 2, 3, 3);

            g2.setFont(UiTheme.FONT_BOLD.deriveFont(12.5f));
            g2.setColor(UiTheme.TEXT);
            String text = state.getLabel();
            g2.drawString(text, 32, cy + 5);

            String dot = "●";
            g2.setFont(UiTheme.FONT_TINY);
            g2.setColor(UiTheme.TEXT_FAINT);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(dot, w - fm.stringWidth(dot) - 12, cy + 4);
        } finally {
            g2.dispose();
        }
    }

    public static Color colorFor(ConnectionState state) {
        switch (state) {
            case CONNECTED:
                return UiTheme.OK;
            case CONNECTING:
                return UiTheme.INFO;
            case RECONNECTING:
                return UiTheme.WARN;
            case DISCONNECTED:
            default:
                return UiTheme.DANGER_LIGHT;
        }
    }
}

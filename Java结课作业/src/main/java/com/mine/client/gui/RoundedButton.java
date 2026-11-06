package com.mine.client.gui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** 圆角按钮，支持主色、次色、危险、警告、幽灵五种风格。 */
public class RoundedButton extends JButton {

    public enum Variant {
        PRIMARY, SECONDARY, DANGER, WARN, GHOST
    }

    private final Variant variant;
    private final int minWidth;

    public RoundedButton(String text, Variant variant) {
        this(text, variant, 84);
    }

    public RoundedButton(String text, Variant variant, int minWidth) {
        super(text);
        this.variant = variant;
        this.minWidth = minWidth;
        setForeground(foregroundFor(variant));
        setFont(UiTheme.FONT_BOLD.deriveFont(12.5f));
        setHorizontalAlignment(SwingConstants.CENTER);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setRolloverEnabled(true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                repaint();
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(getFont());
        int textWidth = metrics.stringWidth(getText());
        int width = Math.max(minWidth, textWidth + 32);
        return new Dimension(width, 34);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            boolean hover = getModel().isRollover() && isEnabled();
            boolean pressed = getModel().isPressed() && isEnabled();

            Color base = baseColor();
            Color top;
            Color bottom;
            Color border;
            Color fg = foregroundFor(variant);

            if (!isEnabled()) {
                base = UiTheme.blend(base, UiTheme.CARD, 0.65f);
                top = base;
                bottom = base;
                border = UiTheme.BORDER_SOFT;
                fg = UiTheme.TEXT_FAINT;
            } else if (pressed) {
                top = UiTheme.blend(base, Color.BLACK, 0.20f);
                bottom = top;
                border = UiTheme.blend(base, Color.WHITE, 0.08f);
            } else if (hover) {
                top = UiTheme.blend(base, Color.WHITE, 0.12f);
                bottom = UiTheme.blend(base, Color.BLACK, 0.06f);
                border = UiTheme.blend(base, Color.WHITE, 0.25f);
            } else {
                top = UiTheme.blend(base, Color.WHITE, 0.04f);
                bottom = UiTheme.blend(base, Color.BLACK, 0.08f);
                border = borderColor();
            }

            if (variant == Variant.GHOST && isEnabled()) {
                g2.setColor(hover ? UiTheme.alpha(UiTheme.CARD_HI, 170) : new Color(0, 0, 0, 0));
                g2.fillRoundRect(0, 0, w, h - 1, 10, 10);
            } else {
                g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
                g2.fillRoundRect(0, 0, w, h - 1, 10, 10);
            }
            g2.setColor(border);
            g2.drawRoundRect(0, 0, w - 1, h - 2, 10, 10);

            if (isFocusOwner() && isEnabled()) {
                g2.setColor(UiTheme.alpha(UiTheme.ACCENT, 110));
                g2.drawRoundRect(2, 2, w - 5, h - 5, 8, 8);
            }

            g2.setFont(getFont());
            g2.setColor(fg);
            FontMetrics metrics = g2.getFontMetrics();
            int textX = (w - metrics.stringWidth(getText())) / 2;
            int textY = (h - 1 - metrics.getHeight()) / 2 + metrics.getAscent();
            g2.drawString(getText(), Math.max(4, textX), textY);
        } finally {
            g2.dispose();
        }
    }

    private Color baseColor() {
        switch (variant) {
            case PRIMARY:
                return UiTheme.ACCENT;
            case DANGER:
                return new Color(0xC81E3C);
            case WARN:
                return new Color(0xB45309);
            case GHOST:
                return UiTheme.CARD_HI;
            case SECONDARY:
            default:
                return UiTheme.CARD_HI;
        }
    }

    private Color borderColor() {
        switch (variant) {
            case PRIMARY:
                return UiTheme.blend(UiTheme.ACCENT, Color.WHITE, 0.15f);
            case DANGER:
                return UiTheme.alpha(UiTheme.DANGER_LIGHT, 180);
            case WARN:
                return UiTheme.alpha(UiTheme.WARN, 180);
            case GHOST:
                return new Color(0, 0, 0, 0);
            case SECONDARY:
            default:
                return UiTheme.BORDER;
        }
    }

    private static Color foregroundFor(Variant variant) {
        switch (variant) {
            case PRIMARY:
                return new Color(0x04222A);
            case DANGER:
            case WARN:
                return Color.WHITE;
            case GHOST:
                return UiTheme.TEXT_DIM;
            case SECONDARY:
            default:
                return UiTheme.TEXT;
        }
    }
}

package com.mine.client.gui;

import com.mine.client.service.DeviceSnapshot;
import com.mine.common.Protocol;
import com.mine.common.SensorType;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 井下抽采巷道示意：把 4 个测点映射到巷道图，正常/告警/离线用颜色区分。
 * 纯 Java2D 绘制，告警时节点带脉冲光环。
 */
public class MineMapPanel extends CardPanel {

    private final Map<Integer, DeviceSnapshot> snapshots = new LinkedHashMap<Integer, DeviceSnapshot>();
    private final MapCanvas canvas = new MapCanvas();
    private final int[] textureX = new int[90];
    private final int[] textureY = new int[90];
    private final int[] textureS = new int[90];
    private final Timer pulseTimer;
    private int pulse;

    public MineMapPanel() {
        super("井下抽采巷道示意", "SENSOR MAP · 4 路测点状态映射");
        setAccent(UiTheme.VACUUM);

        JLabel legend = new JLabel("<html><font color='#34D399'>●</font> 正常&nbsp;&nbsp;"
                + "<font color='#FB7185'>●</font> 告警&nbsp;&nbsp;"
                + "<font color='#5D7889'>●</font> 离线</html>");
        legend.setFont(UiTheme.FONT_TINY);
        legend.setForeground(UiTheme.TEXT_FAINT);
        setHeaderRight(legend);

        Random random = new Random(20241018L);
        for (int i = 0; i < textureX.length; i++) {
            textureX[i] = random.nextInt(1000);
            textureY[i] = random.nextInt(1000);
            textureS[i] = 1 + random.nextInt(2);
        }

        setContent(canvas);
        pulseTimer = new Timer(110, e -> {
            pulse = (pulse + 1) % 40;
            if (isShowing()) {
                canvas.repaint();
            }
        });
        pulseTimer.start();
    }

    public void setSnapshots(List<DeviceSnapshot> list) {
        snapshots.clear();
        if (list != null) {
            for (DeviceSnapshot snapshot : list) {
                snapshots.put(snapshot.getDeviceId(), snapshot);
            }
        }
        canvas.repaint();
    }

    public void update(DeviceSnapshot snapshot) {
        if (snapshot != null) {
            snapshots.put(snapshot.getDeviceId(), snapshot);
            canvas.repaint();
        }
    }

    private final class MapCanvas extends JPanel {

        MapCanvas() {
            setOpaque(false);
            setPreferredSize(new Dimension(540, 160));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int width = getWidth();
                int height = getHeight();
                if (width < 120 || height < 90) {
                    return;
                }
                g2.setColor(new Color(0x0A1720));
                g2.fillRoundRect(0, 0, width, height, 12, 12);
                g2.setColor(UiTheme.BORDER_SOFT);
                g2.drawRoundRect(0, 0, width - 1, height - 1, 12, 12);

                int tunnelX = 22;
                int tunnelY = Math.max(34, Math.round(height * 0.30f));
                int tunnelW = width - 44;
                int tunnelH = Math.max(36, height - tunnelY - 34);

                drawRock(g2, width, height);
                g2.setPaint(new GradientPaint(0, tunnelY, new Color(0x142B37),
                        0, tunnelY + tunnelH, new Color(0x0B1C26)));
                g2.fillRoundRect(tunnelX, tunnelY, tunnelW, tunnelH, 22, 22);
                g2.setColor(UiTheme.alpha(UiTheme.BORDER, 170));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(tunnelX, tunnelY, tunnelW, tunnelH, 22, 22);
                g2.setColor(new Color(0x0A151D));
                g2.fillRect(tunnelX + 8, tunnelY + tunnelH - 9, tunnelW - 16, 9);

                drawSupports(g2, tunnelX, tunnelY, tunnelW, tunnelH);
                drawPipeline(g2, tunnelX, tunnelY, tunnelW, tunnelH);
                drawAirflow(g2, tunnelX, tunnelY, tunnelW);
                drawSensors(g2, tunnelX, tunnelY, tunnelW, tunnelH, width, height);

                g2.setFont(UiTheme.FONT_TINY);
                g2.setColor(UiTheme.TEXT_FAINT);
                g2.drawString("回风巷", tunnelX + 10, tunnelY + 14);
                g2.drawString("抽采管路", tunnelX + 10, tunnelY + tunnelH - 7);
            } finally {
                g2.dispose();
            }
        }

        private void drawRock(Graphics2D g2, int width, int height) {
            g2.setColor(new Color(0x0C1A23));
            for (int i = 0; i < textureX.length; i++) {
                int x = textureX[i] % Math.max(1, width - 12) + 6;
                int y = textureY[i] % Math.max(1, height - 12) + 6;
                g2.fillOval(x, y, textureS[i], textureS[i]);
            }
            g2.setColor(UiTheme.alpha(UiTheme.BORDER_SOFT, 80));
            for (int x = 70; x < width; x += 120) {
                g2.drawLine(x, 8, x - 18, 40);
            }
        }

        private void drawSupports(Graphics2D g2, int x, int y, int w, int h) {
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(UiTheme.alpha(UiTheme.TEXT_FAINT, 65));
            int count = Math.max(3, w / 120);
            for (int i = 0; i <= count; i++) {
                int supportX = x + 32 + (w - 64) * i / count;
                g2.drawLine(supportX, y + 3, supportX, y + h - 4);
                g2.drawLine(supportX - 7, y + 3, supportX + 7, y + 3);
            }
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(UiTheme.alpha(UiTheme.VACUUM, 75));
            g2.drawLine(x + 14, y + 20, x + w - 14, y + 20);
        }

        private void drawPipeline(Graphics2D g2, int x, int y, int w, int h) {
            int pipeY = y + h - Math.max(8, h / 6);
            g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(0x155E63));
            g2.drawLine(x + 18, pipeY, x + w - 18, pipeY);
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(UiTheme.alpha(UiTheme.VACUUM, 180));
            g2.drawLine(x + 18, pipeY, x + w - 18, pipeY);
            for (int px = x + 40; px < x + w - 30; px += 60) {
                g2.setColor(UiTheme.alpha(UiTheme.VACUUM, 120));
                g2.drawLine(px, pipeY - 5, px, pipeY + 5);
            }
        }

        private void drawAirflow(Graphics2D g2, int x, int y, int w) {
            g2.setColor(UiTheme.alpha(UiTheme.CYAN, 70));
            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    10f, new float[]{5f, 7f}, (pulse % 20) * 0.8f));
            Path2D arrow = new Path2D.Float();
            arrow.moveTo(x + 34, y + 35);
            arrow.lineTo(x + 78, y + 35);
            arrow.moveTo(x + 70, y + 30);
            arrow.lineTo(x + 78, y + 35);
            arrow.lineTo(x + 70, y + 40);
            g2.draw(arrow);
        }

        private void drawSensors(Graphics2D g2, int x, int y, int w, int h, int width, int height) {
            int[] ids = {Protocol.DEVICE_GAS, Protocol.DEVICE_VACUUM,
                    Protocol.DEVICE_FLOW, Protocol.DEVICE_TEMP};
            double[] ratios = {0.14, 0.37, 0.60, 0.83};
            for (int i = 0; i < ids.length; i++) {
                DeviceSnapshot snapshot = snapshots.get(ids[i]);
                SensorType type = snapshot == null ? SensorType.fromDeviceId(ids[i]) : snapshot.getType();
                boolean alarm = snapshot != null && snapshot.isAlarm();
                boolean online = snapshot != null && snapshot.isOnline();
                Color color = alarm ? UiTheme.DANGER_LIGHT
                        : (online ? UiTheme.OK : UiTheme.TEXT_FAINT);
                int nodeX = x + (int) Math.round(w * ratios[i]);
                int offset = Math.max(9, h / 3);
                int nodeY = i % 2 == 0 ? y + offset : y + h - offset;
                int labelW = 88;
                int labelH = 28;
                int labelX = Math.max(x + 4, Math.min(width - labelW - 6, nodeX - labelW / 2));
                int labelY = i % 2 == 0 ? Math.max(2, y - labelH - 2)
                        : Math.min(height - labelH - 2, y + h + 3);

                g2.setColor(UiTheme.alpha(UiTheme.BORDER, 190));
                g2.setStroke(new BasicStroke(1f));
                g2.drawLine(nodeX, nodeY, nodeX, i % 2 == 0 ? labelY + labelH : labelY);

                if (alarm) {
                    int halo = 12 + (int) (Math.sin(pulse / 40.0 * Math.PI * 2) * 3);
                    g2.setColor(UiTheme.alpha(UiTheme.DANGER, 42));
                    g2.fillOval(nodeX - halo, nodeY - halo, halo * 2, halo * 2);
                    g2.setColor(UiTheme.alpha(UiTheme.DANGER, 90));
                    g2.drawOval(nodeX - halo + 4, nodeY - halo + 4, halo * 2 - 8, halo * 2 - 8);
                } else if (online) {
                    g2.setColor(UiTheme.alpha(UiTheme.OK, 26));
                    g2.fillOval(nodeX - 9, nodeY - 9, 18, 18);
                }
                g2.setColor(color);
                g2.fillOval(nodeX - 5, nodeY - 5, 10, 10);
                g2.setColor(new Color(0x0A1720));
                g2.fillOval(nodeX - 2, nodeY - 2, 4, 4);

                g2.setColor(UiTheme.alpha(UiTheme.CARD_HI, 245));
                g2.fillRoundRect(labelX, labelY, labelW, labelH, 9, 9);
                g2.setColor(UiTheme.alpha(color, 130));
                g2.drawRoundRect(labelX, labelY, labelW, labelH, 9, 9);
                g2.setFont(UiTheme.FONT_TINY.deriveFont(Font.BOLD, 9f));
                g2.setColor(UiTheme.TEXT_DIM);
                g2.drawString(type.getTag(), labelX + 7, labelY + 11);
                g2.setFont(UiTheme.FONT_MONO_BOLD.deriveFont(10f));
                g2.setColor(alarm ? UiTheme.DANGER_LIGHT : (online ? color : UiTheme.TEXT_FAINT));
                String value = snapshot == null || !snapshot.hasData()
                        ? "--" : snapshot.getValueText() + " " + type.getUnit();
                g2.drawString(value, labelX + 7, labelY + 23);
            }
        }
    }
}

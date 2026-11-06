package com.mine.client.gui;

import com.mine.client.service.DeviceSnapshot;
import com.mine.common.SensorType;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;

/**
 * 实时曲线面板：纯 Swing/Java2D 绘制，无第三方绘图库。
 * 默认显示瓦斯浓度，可切换 4 类传感器；显示最近 60 秒数据、阈值线、危险区。
 */
public class ChartPanel extends CardPanel {

    public static final long WINDOW_MS = 60_000L;
    private static final int MAX_SAMPLES = 900;

    private static final DateTimeFormatter AXIS_TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final EnumMap<SensorType, Deque<Sample>> series =
            new EnumMap<SensorType, Deque<Sample>>(SensorType.class);
    private final EnumMap<SensorType, Double> thresholds =
            new EnumMap<SensorType, Double>(SensorType.class);
    private final ChartCanvas canvas = new ChartCanvas();
    private final JComboBox<SensorType> typeCombo = new JComboBox<SensorType>(
            new SensorType[]{SensorType.GAS, SensorType.VACUUM, SensorType.FLOW, SensorType.TEMP});
    private final JCheckBox pauseBox = new JCheckBox("冻结");
    private final JLabel latestValue = new JLabel("--");
    private final JLabel peakValue = new JLabel("--");
    private final JLabel averageValue = new JLabel("--");
    private final JLabel sampleCount = new JLabel("0");
    private final Timer liveTimer;

    private SensorType selectedType = SensorType.GAS;
    private boolean paused;
    private volatile boolean connected;

    public ChartPanel() {
        super("瓦斯浓度趋势", "REALTIME TREND · 最近 60 秒 · Java2D 绘制");
        setAccent(UiTheme.GAS);
        for (SensorType type : new SensorType[]{SensorType.GAS, SensorType.VACUUM,
                SensorType.FLOW, SensorType.TEMP}) {
            series.put(type, new ArrayDeque<Sample>());
            thresholds.put(type, type.getDefaultThreshold());
        }

        typeCombo.setRenderer(new TypeRenderer());
        typeCombo.setSelectedItem(selectedType);
        typeCombo.setPreferredSize(new Dimension(152, 30));
        UiTheme.styleCombo(typeCombo);
        typeCombo.addActionListener(e -> onTypeChanged());

        UiTheme.styleCheckBox(pauseBox);
        pauseBox.addActionListener(e -> {
            paused = pauseBox.isSelected();
            canvas.repaint();
            if (!paused) {
                refreshMetrics();
            }
        });

        JPanel headerTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerTools.setOpaque(false);
        headerTools.add(typeCombo);
        headerTools.add(pauseBox);
        setHeaderRight(headerTools);

        JPanel footer = new JPanel(new GridLayout(1, 4, 8, 0));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDER_SOFT),
                BorderFactory.createEmptyBorder(8, 6, 0, 6)));
        footer.add(new MetricView("最新值", latestValue, UiTheme.GAS));
        footer.add(new MetricView("窗口峰值", peakValue, UiTheme.TEMP));
        footer.add(new MetricView("窗口均值", averageValue, UiTheme.VACUUM));
        footer.add(new MetricView("样本帧数", sampleCount, UiTheme.TEXT_DIM));

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(canvas, BorderLayout.CENTER);
        body.add(footer, BorderLayout.SOUTH);
        setContent(body);
        refreshMetrics();

        liveTimer = new Timer(1000, e -> {
            if (!paused) {
                canvas.repaint();
            }
        });
        liveTimer.start();
    }

    private void onTypeChanged() {
        Object selected = typeCombo.getSelectedItem();
        if (selected instanceof SensorType) {
            selectedType = (SensorType) selected;
            setAccent(UiTheme.colorForSensor(selectedType));
            setTitleText(selectedType.getDisplayName() + "趋势");
            setSubtitleText("REALTIME TREND · 最近 60 秒 · " + selectedType.getUnit()
                    + " / 阈值 " + selectedType.thresholdExpression(thresholds.get(selectedType)));
            refreshMetrics();
            canvas.repaint();
        }
    }

    /** 业务线程 → EDT 之后调用：追加一帧采样。 */
    public void addSnapshot(DeviceSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasData()) {
            return;
        }
        SensorType type = snapshot.getType();
        if (!type.isKnown()) {
            return;
        }
        Deque<Sample> deque = series.get(type);
        if (deque == null) {
            return;
        }
        if (!deque.isEmpty() && snapshot.getTimestamp() < deque.peekLast().time) {
            return;
        }
        deque.addLast(new Sample(snapshot.getTimestamp(), snapshot.getValue(), snapshot.isAlarm()));
        while (deque.size() > MAX_SAMPLES) {
            deque.removeFirst();
        }
        thresholds.put(type, snapshot.getThreshold());
        if (type == selectedType) {
            if (typeCombo.getSelectedItem() == type) {
                setSubtitleText("REALTIME TREND · 最近 60 秒 · " + type.getUnit()
                        + " / 阈值 " + type.thresholdExpression(snapshot.getThreshold()));
            }
            if (!paused) {
                refreshMetrics();
                canvas.repaint();
            }
        }
    }

    public void setThreshold(SensorType type, double threshold) {
        if (type == null || !type.isKnown()) {
            return;
        }
        thresholds.put(type, threshold);
        if (type == selectedType) {
            setSubtitleText("REALTIME TREND · 最近 60 秒 · " + type.getUnit()
                    + " / 阈值 " + type.thresholdExpression(threshold));
            canvas.repaint();
        }
    }

    public void clearData() {
        for (Deque<Sample> deque : series.values()) {
            deque.clear();
        }
        refreshMetrics();
        canvas.repaint();
    }

    public SensorType getSelectedType() {
        return selectedType;
    }

    /** 由 EDT 调用：链路状态变化时更新 LIVE/OFFLINE 标识。 */
    public void setConnected(boolean connected) {
        this.connected = connected;
        canvas.repaint();
    }

    private List<Sample> visibleSamples(long now) {
        List<Sample> visible = new ArrayList<Sample>();
        long from = now - WINDOW_MS;
        for (Sample sample : series.get(selectedType)) {
            if (sample.time >= from) {
                visible.add(sample);
            }
        }
        return visible;
    }

    private void refreshMetrics() {
        Deque<Sample> deque = series.get(selectedType);
        Color color = UiTheme.colorForSensor(selectedType);
        if (deque.isEmpty()) {
            latestValue.setText("--");
            peakValue.setText("--");
            averageValue.setText("--");
            sampleCount.setText("0");
            latestValue.setForeground(color);
            return;
        }
        long now = System.currentTimeMillis();
        long from = now - WINDOW_MS;
        double last = 0;
        double max = -Double.MAX_VALUE;
        double sum = 0;
        int count = 0;
        for (Sample sample : deque) {
            if (sample.time < from) {
                continue;
            }
            last = sample.value;
            max = Math.max(max, sample.value);
            sum += sample.value;
            count++;
        }
        if (count == 0) {
            Sample latest = deque.peekLast();
            last = latest.value;
            max = latest.value;
            sum = latest.value;
            count = 1;
        }
        latestValue.setText(selectedType.format(last) + " " + selectedType.getUnit());
        peakValue.setText(selectedType.format(max) + " " + selectedType.getUnit());
        averageValue.setText(selectedType.format(sum / count) + " " + selectedType.getUnit());
        sampleCount.setText(String.valueOf(count));
        latestValue.setForeground(selectedType.isAlarm(last, thresholds.get(selectedType))
                ? UiTheme.DANGER_LIGHT : color);
    }

    /** 曲线实际绘制画布。 */
    private final class ChartCanvas extends JPanel {

        ChartCanvas() {
            setOpaque(false);
            setPreferredSize(new Dimension(560, 310));
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
                if (width < 80 || height < 80) {
                    return;
                }
                int left = 56;
                int right = 18;
                int top = 22;
                int bottom = 36;
                int plotW = width - left - right;
                int plotH = height - top - bottom;
                if (plotW <= 20 || plotH <= 20) {
                    return;
                }

                g2.setColor(UiTheme.CARD_ALT);
                g2.fillRoundRect(0, 0, width, height, 12, 12);
                g2.setColor(UiTheme.BORDER_SOFT);
                g2.drawRoundRect(0, 0, width - 1, height - 1, 12, 12);
                g2.setClip(new RoundRectangle2D.Float(0, 0, width, height, 12, 12));

                long now = System.currentTimeMillis();
                List<Sample> visible = visibleSamples(now);
                double threshold = thresholds.get(selectedType);
                double[] range = calculateRange(visible, threshold);
                double yMin = range[0];
                double yMax = range[1];
                Color accent = UiTheme.colorForSensor(selectedType);

                drawGrid(g2, left, top, plotW, plotH, yMin, yMax, now);
                drawDangerZone(g2, left, top, plotW, plotH, yMin, yMax, threshold);

                if (visible.isEmpty()) {
                    drawEmptyState(g2, left, top, plotW, plotH);
                } else {
                    drawSeries(g2, visible, left, top, plotW, plotH, yMin, yMax, now, accent);
                }
                drawThreshold(g2, left, top, plotW, plotH, yMin, yMax, threshold);

                g2.setFont(UiTheme.FONT_TINY.deriveFont(Font.BOLD));
                if (paused) {
                    g2.setColor(UiTheme.WARN);
                    g2.drawString("● 已冻结", left + 8, top + 15);
                } else if (connected) {
                    g2.setColor(UiTheme.OK);
                    g2.drawString("● LIVE  60s", left + 8, top + 15);
                } else {
                    g2.setColor(UiTheme.DANGER_LIGHT);
                    g2.drawString("● 链路离线 · 等待重连", left + 8, top + 15);
                }

            } finally {
                g2.dispose();
            }
        }

        private void drawGrid(Graphics2D g2, int left, int top, int plotW, int plotH,
                              double yMin, double yMax, long now) {
            g2.setFont(UiTheme.FONT_TINY);
            g2.setStroke(new BasicStroke(1f));
            int yTicks = 5;
            for (int i = 0; i <= yTicks; i++) {
                int y = top + plotH - (int) Math.round(plotH * i / (double) yTicks);
                g2.setColor(i == 0 ? UiTheme.BORDER : UiTheme.alpha(UiTheme.BORDER_SOFT, 150));
                g2.drawLine(left, y, left + plotW, y);
                double value = yMin + (yMax - yMin) * i / yTicks;
                g2.setColor(UiTheme.TEXT_FAINT);
                String label = selectedType.format(value);
                int textWidth = g2.getFontMetrics().stringWidth(label);
                g2.drawString(label, left - textWidth - 8, y + 3);
            }
            int xTicks = 5;
            for (int i = 0; i <= xTicks; i++) {
                int x = left + (int) Math.round(plotW * i / (double) xTicks);
                g2.setColor(UiTheme.alpha(UiTheme.BORDER_SOFT, 120));
                g2.drawLine(x, top, x, top + plotH);
                if (i == xTicks) {
                    continue;
                }
                long time = now - WINDOW_MS + (long) (WINDOW_MS * i / (double) xTicks);
                String label = AXIS_TIME.format(Instant.ofEpochMilli(time));
                g2.setColor(UiTheme.TEXT_FAINT);
                g2.drawString(label, x + 2, top + plotH + 18);
            }
            g2.setColor(UiTheme.TEXT_FAINT);
            String unit = selectedType.getUnit();
            g2.drawString(unit, 8, top + 2);
        }

        private void drawDangerZone(Graphics2D g2, int left, int top, int plotW, int plotH,
                                    double yMin, double yMax, double threshold) {
            if (threshold < yMin || threshold > yMax) {
                return;
            }
            int thresholdY = valueToY(threshold, top, plotH, yMin, yMax);
            boolean high = selectedType.getDirection() == SensorType.AlarmDirection.HIGH;
            int zoneTop = high ? top : thresholdY;
            int zoneBottom = high ? thresholdY : top + plotH;
            if (zoneBottom > zoneTop) {
                g2.setColor(UiTheme.alpha(UiTheme.DANGER, 16));
                g2.fillRect(left, zoneTop, plotW, zoneBottom - zoneTop);
                g2.setFont(UiTheme.FONT_TINY);
                g2.setColor(UiTheme.alpha(UiTheme.DANGER_LIGHT, 130));
                g2.drawString("危险区", left + 8, high ? zoneTop + 15 : zoneBottom - 7);
            }
        }

        private void drawSeries(Graphics2D g2, List<Sample> samples, int left, int top,
                                int plotW, int plotH, double yMin, double yMax,
                                long now, Color accent) {
            List<int[]> points = new ArrayList<int[]>(samples.size());
            List<Boolean> alarms = new ArrayList<Boolean>(samples.size());
            for (Sample sample : samples) {
                double ratio = (sample.time - (now - WINDOW_MS)) / (double) WINDOW_MS;
                if (ratio < 0 || ratio > 1.02) {
                    continue;
                }
                int x = left + (int) Math.round(plotW * Math.min(1.0, ratio));
                int y = valueToY(sample.value, top, plotH, yMin, yMax);
                points.add(new int[]{x, y});
                alarms.add(sample.alarm);
            }
            if (points.isEmpty()) {
                drawEmptyState(g2, left, top, plotW, plotH);
                return;
            }

            Path2D line = new Path2D.Float();
            Path2D area = new Path2D.Float();
            line.moveTo(points.get(0)[0], points.get(0)[1]);
            area.moveTo(points.get(0)[0], top + plotH);
            area.lineTo(points.get(0)[0], points.get(0)[1]);
            for (int i = 1; i < points.size(); i++) {
                int[] previous = points.get(i - 1);
                int[] current = points.get(i);
                int midX = (previous[0] + current[0]) / 2;
                int midY = (previous[1] + current[1]) / 2;
                line.quadTo(previous[0], previous[1], midX, midY);
                area.quadTo(previous[0], previous[1], midX, midY);
                if (i == points.size() - 1) {
                    line.lineTo(current[0], current[1]);
                    area.lineTo(current[0], current[1]);
                }
            }
            area.lineTo(points.get(points.size() - 1)[0], top + plotH);
            area.closePath();
            g2.setPaint(new GradientPaint(0, top, UiTheme.alpha(accent, 95),
                    0, top + plotH, UiTheme.alpha(accent, 0)));
            g2.fill(area);

            g2.setColor(accent);
            g2.setStroke(new BasicStroke(2.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(line);

            for (int i = 0; i < points.size(); i++) {
                if (alarms.get(i)) {
                    int[] point = points.get(i);
                    g2.setColor(UiTheme.DANGER);
                    g2.fillOval(point[0] - 2, point[1] - 2, 5, 5);
                }
            }

            int[] last = points.get(points.size() - 1);
            boolean lastAlarm = alarms.get(alarms.size() - 1);
            Color pointColor = lastAlarm ? UiTheme.DANGER_LIGHT : accent;
            g2.setColor(UiTheme.alpha(pointColor, 36));
            g2.fillOval(last[0] - 10, last[1] - 10, 20, 20);
            g2.setColor(pointColor);
            g2.fillOval(last[0] - 4, last[1] - 4, 8, 8);
            g2.setColor(Color.WHITE);
            g2.fillOval(last[0] - 1, last[1] - 1, 2, 2);

            drawLatestBubble(g2, last[0], last[1], samples.get(samples.size() - 1), pointColor);
        }

        private void drawLatestBubble(Graphics2D g2, int cx, int cy, Sample sample, Color color) {
            String text = selectedType.format(sample.value) + " " + selectedType.getUnit();
            g2.setFont(UiTheme.FONT_MONO_BOLD);
            int textWidth = g2.getFontMetrics().stringWidth(text);
            int bubbleW = textWidth + 18;
            int bubbleH = 24;
            int x = cx + 12;
            if (x + bubbleW > getWidth() - 12) {
                x = cx - bubbleW - 12;
            }
            int y = cy - bubbleH - 8;
            if (y < 8) {
                y = cy + 12;
            }
            g2.setColor(UiTheme.alpha(UiTheme.CARD_HI, 240));
            g2.fillRoundRect(x, y, bubbleW, bubbleH, 9, 9);
            g2.setColor(UiTheme.alpha(color, 150));
            g2.drawRoundRect(x, y, bubbleW, bubbleH, 9, 9);
            g2.setColor(color);
            g2.drawString(text, x + 9, y + 16);
        }

        private void drawThreshold(Graphics2D g2, int left, int top, int plotW, int plotH,
                                   double yMin, double yMax, double threshold) {
            if (threshold < yMin || threshold > yMax) {
                return;
            }
            int y = valueToY(threshold, top, plotH, yMin, yMax);
            g2.setColor(UiTheme.alpha(UiTheme.WARN, 220));
            g2.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[]{6f, 5f}, 0f));
            g2.drawLine(left, y, left + plotW, y);
            g2.setStroke(new BasicStroke(1f));
            g2.setFont(UiTheme.FONT_TINY.deriveFont(Font.BOLD));
            String text = "阈值 " + selectedType.format(threshold);
            int textWidth = g2.getFontMetrics().stringWidth(text);
            g2.setColor(UiTheme.alpha(UiTheme.CARD, 230));
            g2.fillRoundRect(left + plotW - textWidth - 16, y - 16, textWidth + 10, 16, 6, 6);
            g2.setColor(UiTheme.WARN);
            g2.drawString(text, left + plotW - textWidth - 11, y - 4);
        }

        private void drawEmptyState(Graphics2D g2, int left, int top, int plotW, int plotH) {
            int centerX = left + plotW / 2;
            int centerY = top + plotH / 2;
            g2.setColor(UiTheme.alpha(UiTheme.TEXT_FAINT, 90));
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D wave = new Path2D.Float();
            int width = Math.min(260, plotW - 40);
            int startX = centerX - width / 2;
            wave.moveTo(startX, centerY);
            for (int i = 0; i < 8; i++) {
                int x1 = startX + width * i / 8;
                int x2 = startX + width * (i + 1) / 8;
                double amp = Math.sin(i * 1.2) * 12;
                wave.quadTo((x1 + x2) / 2.0, centerY + amp, x2, centerY);
            }
            g2.draw(wave);
            g2.setFont(UiTheme.FONT_BASE);
            g2.setColor(UiTheme.TEXT_DIM);
            String text = "等待网关实时数据…";
            int textWidth = g2.getFontMetrics().stringWidth(text);
            g2.drawString(text, centerX - textWidth / 2, centerY + 42);
            g2.setFont(UiTheme.FONT_TINY);
            g2.setColor(UiTheme.TEXT_FAINT);
            String hint = "连接 " + selectedType.getDisplayName() + " 测点后自动绘制";
            int hintWidth = g2.getFontMetrics().stringWidth(hint);
            g2.drawString(hint, centerX - hintWidth / 2, centerY + 60);
        }

        private double[] calculateRange(List<Sample> visible, double threshold) {
            if (selectedType == SensorType.GAS) {
                double max = Math.max(0.5, threshold * 1.3);
                for (Sample sample : visible) {
                    max = Math.max(max, sample.value * 1.15);
                }
                return new double[]{0.0, Math.ceil(max * 10) / 10.0};
            }
            double min = threshold;
            double max = threshold;
            for (Sample sample : visible) {
                min = Math.min(min, sample.value);
                max = Math.max(max, sample.value);
            }
            double span = max - min;
            double minimumSpan = selectedType == SensorType.VACUUM ? 6.0
                    : selectedType == SensorType.FLOW ? 10.0 : 5.0;
            if (span < minimumSpan) {
                double center = (min + max) / 2.0;
                min = center - minimumSpan / 2.0;
                max = center + minimumSpan / 2.0;
            } else {
                double pad = span * 0.12;
                min -= pad;
                max += pad;
            }
            if (selectedType == SensorType.FLOW || selectedType == SensorType.TEMP) {
                min = Math.max(0, min);
            }
            return new double[]{min, max};
        }

        private int valueToY(double value, int top, int plotH, double yMin, double yMax) {
            double ratio = (value - yMin) / (yMax - yMin);
            ratio = Math.max(0, Math.min(1, ratio));
            return top + plotH - (int) Math.round(plotH * ratio);
        }
    }

    /** 指标小面板。 */
    private static final class MetricView extends JPanel {

        MetricView(String caption, JLabel valueLabel, Color color) {
            super();
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            JLabel captionLabel = new JLabel(caption);
            captionLabel.setForeground(UiTheme.TEXT_FAINT);
            captionLabel.setFont(UiTheme.FONT_TINY);
            valueLabel.setForeground(color);
            valueLabel.setFont(UiTheme.FONT_MONO_BOLD.deriveFont(14f));
            add(captionLabel);
            add(valueLabel);
        }
    }

    /** 下拉框渲染器。 */
    private static final class TypeRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index,
                    isSelected, cellHasFocus);
            label.setFont(UiTheme.FONT_SMALL);
            if (value instanceof SensorType) {
                SensorType type = (SensorType) value;
                label.setText(type.getDisplayName());
                label.setForeground(UiTheme.colorForSensor(type));
            }
            label.setBackground(isSelected ? UiTheme.CARD_HI : UiTheme.CARD);
            label.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            return label;
        }
    }

    private static final class Sample {
        final long time;
        final double value;
        final boolean alarm;

        Sample(long time, double value, boolean alarm) {
            this.time = time;
            this.value = value;
            this.alarm = alarm;
        }
    }
}

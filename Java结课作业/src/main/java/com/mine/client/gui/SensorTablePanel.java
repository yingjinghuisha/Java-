package com.mine.client.gui;

import com.mine.client.service.DeviceSnapshot;
import com.mine.common.SensorType;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 传感器实时数据表（JTable + 自定义单元格渲染）。 */
public class SensorTablePanel extends CardPanel {

    private static final String[] COLUMNS = {
            "设备ID", "传感器类型", "当前数值", "单位", "告警阈值", "状态", "最后上报"
    };

    private final TelemetryTableModel model = new TelemetryTableModel();
    private final JLabel summaryLabel = new JLabel();
    private final JTable table;

    public SensorTablePanel() {
        super("传感器实时数据", "SENSOR TELEMETRY · ConcurrentHashMap + 读写锁 · 8 秒离线判定");
        setAccent(UiTheme.GAS);

        summaryLabel.setForeground(UiTheme.TEXT_DIM);
        summaryLabel.setFont(UiTheme.FONT_SMALL);
        summaryLabel.setText("在线 --/--");
        setHeaderRight(summaryLabel);

        table = new TelemetryTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setRowHeight(37);
        table.getColumnModel().getColumn(0).setPreferredWidth(62);
        table.getColumnModel().getColumn(1).setPreferredWidth(104);
        table.getColumnModel().getColumn(2).setPreferredWidth(92);
        table.getColumnModel().getColumn(3).setPreferredWidth(64);
        table.getColumnModel().getColumn(4).setPreferredWidth(104);
        table.getColumnModel().getColumn(5).setPreferredWidth(78);
        table.getColumnModel().getColumn(6).setPreferredWidth(92);
        for (int i = 0; i < COLUMNS.length; i++) {
            if (i == 5) {
                table.getColumnModel().getColumn(i).setCellRenderer(new StatusBadgeRenderer());
            } else {
                table.getColumnModel().getColumn(i).setCellRenderer(new TelemetryRenderer(i));
            }
        }

        JScrollPane scrollPane = new JScrollPane(table);
        UiTheme.styleScrollPane(scrollPane);
        table.setOpaque(true);
        scrollPane.getViewport().setBackground(UiTheme.CARD);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(scrollPane, BorderLayout.CENTER);
        setContent(body);
    }

    public void setSnapshots(List<DeviceSnapshot> snapshots) {
        model.setRows(snapshots);
        updateSummary();
    }

    public void updateSnapshot(DeviceSnapshot snapshot) {
        model.update(snapshot);
        updateSummary();
    }

    public void clearRows() {
        model.clear();
        updateSummary();
    }

    private void updateSummary() {
        int total = model.getRowCount();
        int online = 0;
        int alarms = 0;
        for (int i = 0; i < total; i++) {
            DeviceSnapshot snapshot = model.getSnapshotAt(i);
            if (snapshot.isOnline()) {
                online++;
            }
            if (snapshot.isAlarm()) {
                alarms++;
            }
        }
        summaryLabel.setText("在线 " + online + "/" + total + " · 告警 " + alarms);
        summaryLabel.setForeground(alarms > 0 ? UiTheme.DANGER_LIGHT : UiTheme.TEXT_DIM);
    }

    public JTable getTable() {
        return table;
    }

    private static Color rowBackground(int row, boolean selected) {
        if (selected) {
            return UiTheme.CARD_HI;
        }
        return row % 2 == 0 ? UiTheme.CARD : UiTheme.CARD_ALT;
    }

    private static String thresholdText(DeviceSnapshot snapshot) {
        String op = snapshot.getType().getDirection() == SensorType.AlarmDirection.HIGH ? "≥ " : "≤ ";
        return op + snapshot.getThresholdText();
    }

    /** 表格模型。 */
    private static final class TelemetryTableModel extends AbstractTableModel {

        private final List<DeviceSnapshot> rows = new ArrayList<DeviceSnapshot>();
        private final Map<Integer, Integer> rowIndex = new HashMap<Integer, Integer>();

        void setRows(List<DeviceSnapshot> snapshots) {
            rows.clear();
            rowIndex.clear();
            if (snapshots != null) {
                for (DeviceSnapshot snapshot : snapshots) {
                    rowIndex.put(snapshot.getDeviceId(), rows.size());
                    rows.add(snapshot);
                }
            }
            Collections.sort(rows, new Comparator<DeviceSnapshot>() {
                @Override
                public int compare(DeviceSnapshot a, DeviceSnapshot b) {
                    return Integer.compare(a.getDeviceId(), b.getDeviceId());
                }
            });
            reindex();
            fireTableDataChanged();
        }

        void update(DeviceSnapshot snapshot) {
            Integer index = rowIndex.get(snapshot.getDeviceId());
            if (index == null) {
                rowIndex.put(snapshot.getDeviceId(), rows.size());
                rows.add(snapshot);
                fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
            } else {
                rows.set(index, snapshot);
                fireTableRowsUpdated(index, index);
            }
        }

        void clear() {
            rows.clear();
            rowIndex.clear();
            fireTableDataChanged();
        }

        DeviceSnapshot getSnapshotAt(int row) {
            return rows.get(row);
        }

        private void reindex() {
            rowIndex.clear();
            for (int i = 0; i < rows.size(); i++) {
                rowIndex.put(rows.get(i).getDeviceId(), i);
            }
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Integer.class : String.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            DeviceSnapshot snapshot = rows.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return snapshot.getDeviceId();
                case 1:
                    return snapshot.getType().getDisplayName();
                case 2:
                    return snapshot.getValueText();
                case 3:
                    return snapshot.getType().getUnit();
                case 4:
                    return thresholdText(snapshot);
                case 5:
                    return snapshot.getStatusText();
                case 6:
                    return snapshot.getLastReportText();
                default:
                    return "";
            }
        }
    }

    /** 文本单元格渲染器。 */
    private static final class TelemetryRenderer extends DefaultTableCellRenderer {

        private final int column;

        TelemetryRenderer(int column) {
            this.column = column;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected,
                    false, row, column);
            label.setOpaque(true);
            label.setBackground(rowBackground(row, isSelected));
            label.setForeground(UiTheme.TEXT);
            label.setFont(UiTheme.FONT_BASE);
            label.setHorizontalAlignment(SwingConstants.LEFT);
            label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 8));
            DeviceSnapshot snapshot = ((TelemetryTable) table).getSnapshotAt(row);
            SensorType type = snapshot.getType();
            Color sensorColor = UiTheme.colorForSensor(type);

            switch (this.column) {
                case 0:
                    label.setText("#" + value);
                    label.setForeground(snapshot.isOnline() ? UiTheme.TEXT_DIM : UiTheme.TEXT_FAINT);
                    label.setFont(UiTheme.FONT_MONO);
                    break;
                case 1:
                    label.setForeground(sensorColor);
                    label.setFont(UiTheme.FONT_BOLD);
                    break;
                case 2:
                    label.setHorizontalAlignment(SwingConstants.RIGHT);
                    label.setFont(UiTheme.FONT_MONO_BOLD.deriveFont(14f));
                    if (snapshot.isAlarm()) {
                        label.setForeground(UiTheme.DANGER_LIGHT);
                    } else if (!snapshot.isOnline()) {
                        label.setForeground(UiTheme.TEXT_FAINT);
                    } else {
                        label.setForeground(UiTheme.TEXT);
                    }
                    break;
                case 3:
                    label.setForeground(UiTheme.TEXT_FAINT);
                    label.setFont(UiTheme.FONT_SMALL);
                    break;
                case 4:
                    label.setForeground(snapshot.isAlarm() ? UiTheme.DANGER_LIGHT : UiTheme.TEXT_DIM);
                    label.setFont(UiTheme.FONT_MONO);
                    break;
                case 6:
                    label.setForeground(snapshot.isOnline() ? UiTheme.TEXT_DIM : UiTheme.TEXT_FAINT);
                    label.setFont(UiTheme.FONT_MONO);
                    break;
                default:
                    break;
            }
            label.setToolTipText(snapshot.describe() + " · 阈值 " + snapshot.getThresholdExpression());
            return label;
        }
    }

    /** 状态徽章渲染器。 */
    private static final class StatusBadgeRenderer extends JComponent implements TableCellRenderer {

        private String text = "";
        private int row;
        private boolean selected;
        private boolean alarm;
        private boolean online;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            this.text = value == null ? "" : value.toString();
            this.row = row;
            this.selected = isSelected;
            DeviceSnapshot snapshot = ((TelemetryTable) table).getSnapshotAt(row);
            this.alarm = snapshot.isAlarm();
            this.online = snapshot.isOnline();
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(rowBackground(row, selected));
                g2.fillRect(0, 0, getWidth(), getHeight());

                Color color = alarm ? UiTheme.DANGER_LIGHT
                        : (online ? UiTheme.OK : UiTheme.TEXT_FAINT);
                int badgeWidth = 54;
                int badgeHeight = 22;
                int x = 7;
                int y = (getHeight() - badgeHeight) / 2;
                g2.setColor(UiTheme.alpha(color, 26));
                g2.fillRoundRect(x, y, badgeWidth, badgeHeight, badgeHeight, badgeHeight);
                g2.setColor(UiTheme.alpha(color, 95));
                g2.drawRoundRect(x, y, badgeWidth, badgeHeight, badgeHeight, badgeHeight);
                g2.setColor(color);
                g2.fillOval(x + 9, y + badgeHeight / 2 - 3, 6, 6);

                g2.setFont(UiTheme.FONT_TINY.deriveFont(Font.BOLD));
                g2.setColor(color);
                g2.drawString(text, x + 21, y + 15);
            } finally {
                g2.dispose();
            }
        }
    }

    /** 带行上下文的表格。 */
    private static final class TelemetryTable extends JTable {

        TelemetryTable(TelemetryTableModel model) {
            super(model);
            UiTheme.styleTable(this);
        }

        DeviceSnapshot getSnapshotAt(int row) {
            int modelRow = convertRowIndexToModel(row);
            return ((TelemetryTableModel) getModel()).getSnapshotAt(modelRow);
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int row = rowAtPoint(event.getPoint());
            int column = columnAtPoint(event.getPoint());
            if (row >= 0 && column >= 0) {
                DeviceSnapshot snapshot = getSnapshotAt(row);
                return "<html><b>" + snapshot.getType().getDisplayName() + " #"
                        + snapshot.getDeviceId() + "</b><br/>数值 " + snapshot.getValueWithUnit()
                        + "<br/>阈值 " + snapshot.getThresholdExpression()
                        + "<br/>状态 " + snapshot.getStatusText()
                        + " · 样本 " + snapshot.getSampleCount() + " 帧</html>";
            }
            return null;
        }
    }
}

package com.mine.client.gui;

import com.mine.common.SensorType;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

/** 参数下发面板：阈值、采样周期、解除事故，演示双向 TCP 指令。 */
public class CommandPanel extends CardPanel {

    public interface Actions {
        double currentThreshold(SensorType type);

        void sendThreshold(SensorType type, double value);

        void sendPeriod(int periodMs);

        void resetAccident();

        void resetDefaults();

        void setPopupEnabled(boolean enabled);
    }

    private static final SensorType[] TYPES = {
            SensorType.GAS, SensorType.VACUUM, SensorType.FLOW, SensorType.TEMP
    };

    private final Actions actions;
    private final JComboBox<SensorType> typeCombo = new JComboBox<SensorType>(TYPES);
    private final JSpinner thresholdSpinner = new JSpinner();
    private final JSpinner periodSpinner = new JSpinner(
            new SpinnerNumberModel(500, 200, 5000, 100));
    private final JLabel ackLabel = new JLabel("等待指令下发…");
    private final RoundedButton thresholdButton = new RoundedButton("下发阈值",
            RoundedButton.Variant.PRIMARY, 82);
    private final RoundedButton periodButton = new RoundedButton("下发周期",
            RoundedButton.Variant.SECONDARY, 82);
    private final RoundedButton resetButton = new RoundedButton("解除事故",
            RoundedButton.Variant.WARN, 82);
    private final RoundedButton defaultsButton = new RoundedButton("恢复默认阈值",
            RoundedButton.Variant.GHOST, 104);

    public CommandPanel(Actions actions) {
        super("参数下发与事故演练", "TCP COMMAND · 双向通信 · 阈值 / 采样周期 / 事故");
        this.actions = actions;
        setAccent(UiTheme.TEMP);

        typeCombo.setRenderer(new TypeRenderer());
        UiTheme.styleCombo(typeCombo);
        typeCombo.setPreferredSize(new Dimension(144, 32));
        typeCombo.addActionListener(e -> refreshThresholdForType());

        UiTheme.styleSpinner(thresholdSpinner);
        thresholdSpinner.setPreferredSize(new Dimension(90, 32));
        UiTheme.styleSpinner(periodSpinner);
        periodSpinner.setPreferredSize(new Dimension(86, 32));
        thresholdButton.addActionListener(e -> actions.sendThreshold(selectedType(),
                ((Number) thresholdSpinner.getValue()).doubleValue()));
        periodButton.addActionListener(e -> actions.sendPeriod(
                ((Number) periodSpinner.getValue()).intValue()));
        resetButton.addActionListener(e -> actions.resetAccident());
        defaultsButton.addActionListener(e -> actions.resetDefaults());

        ackLabel.setFont(UiTheme.FONT_SMALL);
        ackLabel.setForeground(UiTheme.TEXT_FAINT);

        JCheckBox popupBox = new JCheckBox("严重告警弹窗/提示音", true);
        UiTheme.styleCheckBox(popupBox);
        popupBox.addActionListener(e -> actions.setPopupEnabled(popupBox.isSelected()));

        JPanel row1 = row();
        row1.add(caption("目标设备"));
        row1.add(typeCombo);
        row1.add(Box.createHorizontalStrut(6));
        row1.add(caption("告警阈值"));
        row1.add(thresholdSpinner);
        row1.add(thresholdButton);
        row1.add(Box.createHorizontalStrut(4));
        row1.add(resetButton);

        JPanel row2 = row();
        row2.add(caption("全局采样周期"));
        row2.add(periodSpinner);
        row2.add(caption("ms（200~5000）"));
        row2.add(periodButton);
        row2.add(Box.createHorizontalStrut(4));
        row2.add(defaultsButton);

        JPanel row3 = new JPanel(new BorderLayout(8, 0));
        row3.setOpaque(false);
        row3.add(ackLabel, BorderLayout.CENTER);
        row3.add(popupBox, BorderLayout.EAST);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(row1);
        content.add(Box.createVerticalStrut(6));
        content.add(row2);
        content.add(Box.createVerticalStrut(4));
        content.add(row3);
        content.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        setContent(content);

        refreshThresholdForType();
        refreshThresholdEditor();
        applyThresholdLimits(SensorType.GAS);
    }

    private static JPanel row() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        return row;
    }

    private static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(UiTheme.TEXT_DIM);
        label.setFont(UiTheme.FONT_SMALL);
        return label;
    }

    private SensorType selectedType() {
        Object selected = typeCombo.getSelectedItem();
        return selected instanceof SensorType ? (SensorType) selected : SensorType.GAS;
    }

    private void refreshThresholdForType() {
        if (actions == null) {
            return;
        }
        SensorType type = selectedType();
        applyThresholdLimits(type);
        thresholdSpinner.setValue(actions.currentThreshold(type));
        refreshThresholdEditor();
    }

    private void applyThresholdLimits(SensorType type) {
        double value = actions == null ? type.getDefaultThreshold() : actions.currentThreshold(type);
        SpinnerNumberModel model = new SpinnerNumberModel(
                value, type.getSafeMin(), type.getSafeMax(), type.getThresholdStep());
        thresholdSpinner.setModel(model);
        thresholdSpinner.setValue(Math.max(type.getSafeMin(),
                Math.min(type.getSafeMax(), value)));
        UiTheme.styleSpinner(thresholdSpinner);
    }

    private void refreshThresholdEditor() {
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(thresholdSpinner,
                "0." + repeat('0', selectedType().getDecimals()));
        thresholdSpinner.setEditor(editor);
        editor.getTextField().setBackground(UiTheme.CARD_HI);
        editor.getTextField().setForeground(UiTheme.TEXT);
        editor.getTextField().setCaretColor(UiTheme.ACCENT);
        editor.getTextField().setFont(UiTheme.FONT_MONO_BOLD);
        editor.getTextField().setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
    }

    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }

    public void setConnected(boolean connected) {
        thresholdButton.setEnabled(connected);
        periodButton.setEnabled(connected);
        resetButton.setEnabled(connected);
        defaultsButton.setEnabled(connected);
    }

    public void setLastAck(String message, boolean success) {
        ackLabel.setText(message);
        ackLabel.setForeground(success ? UiTheme.OK
                : (message.contains("等待") ? UiTheme.TEXT_FAINT : UiTheme.DANGER_LIGHT));
        ackLabel.setToolTipText(message);
    }

    public void setNeutralMessage(String message) {
        ackLabel.setText(message);
        ackLabel.setForeground(UiTheme.TEXT_FAINT);
    }

    public void setPeriodValue(int periodMs) {
        periodSpinner.setValue(periodMs);
    }

    public void setThresholdValue(SensorType type, double value) {
        if (type == selectedType()) {
            thresholdSpinner.setValue(value);
        }
    }

    private static final class TypeRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index,
                    isSelected, cellHasFocus);
            if (value instanceof SensorType) {
                SensorType type = (SensorType) value;
                label.setText(type.getDisplayName());
                label.setForeground(UiTheme.colorForSensor(type));
                label.setFont(UiTheme.FONT_SMALL.deriveFont(Font.BOLD));
            }
            label.setBackground(isSelected ? UiTheme.CARD_HI : UiTheme.CARD);
            label.setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));
            return label;
        }
    }
}

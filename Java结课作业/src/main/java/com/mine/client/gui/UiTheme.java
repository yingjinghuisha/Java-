package com.mine.client.gui;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** 暗色工业风 UI 主题：统一颜色、字体、控件样式。 */
public final class UiTheme {

    private UiTheme() {
    }

    public static final Color BG = new Color(0x08131C);
    public static final Color BG_ALT = new Color(0x0B1A24);
    public static final Color CARD = new Color(0x102330);
    public static final Color CARD_ALT = new Color(0x132A38);
    public static final Color CARD_HI = new Color(0x173243);
    public static final Color BORDER = new Color(0x244255);
    public static final Color BORDER_SOFT = new Color(0x1A3342);
    public static final Color TEXT = new Color(0xE8F2F8);
    public static final Color TEXT_DIM = new Color(0x8FA9B9);
    public static final Color TEXT_FAINT = new Color(0x5D7889);
    public static final Color ACCENT = new Color(0x2DD4BF);
    public static final Color ACCENT_DARK = new Color(0x0F766E);
    public static final Color CYAN = new Color(0x38BDF8);
    public static final Color GAS = new Color(0x38BDF8);
    public static final Color VACUUM = new Color(0x2DD4BF);
    public static final Color FLOW = new Color(0xA3E635);
    public static final Color TEMP = new Color(0xFBBF24);
    public static final Color DANGER = new Color(0xF43F5E);
    public static final Color DANGER_LIGHT = new Color(0xFB7185);
    public static final Color WARN = new Color(0xF59E0B);
    public static final Color OK = new Color(0x34D399);
    public static final Color INFO = new Color(0x60A5FA);

    public static final String FONT_FAMILY = findFont("PingFang SC", "Microsoft YaHei",
            "Noto Sans CJK SC", "Source Han Sans CN", "Hiragino Sans GB", "SansSerif");
    public static final String MONO_FAMILY = findFont("JetBrains Mono", "SF Mono", "Menlo",
            "Consolas", "Monospaced");

    public static final Font FONT_TINY = new Font(FONT_FAMILY, Font.PLAIN, 10);
    public static final Font FONT_SMALL = new Font(FONT_FAMILY, Font.PLAIN, 11);
    public static final Font FONT_BASE = new Font(FONT_FAMILY, Font.PLAIN, 13);
    public static final Font FONT_BOLD = new Font(FONT_FAMILY, Font.BOLD, 13);
    public static final Font FONT_TITLE = new Font(FONT_FAMILY, Font.BOLD, 24);
    public static final Font FONT_CARD_TITLE = new Font(FONT_FAMILY, Font.BOLD, 14);
    public static final Font FONT_MONO = new Font(MONO_FAMILY, Font.PLAIN, 12);
    public static final Font FONT_MONO_BOLD = new Font(MONO_FAMILY, Font.BOLD, 12);

    private static final DateTimeFormatter CLOCK_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter SHORT_TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public static void install() {
        UIManager.put("Panel.background", BG);
        UIManager.put("Viewport.background", CARD);
        UIManager.put("ScrollPane.background", CARD);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.font", FONT_BASE);
        UIManager.put("OptionPane.background", CARD);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("OptionPane.messageFont", FONT_BASE);
        UIManager.put("OptionPane.buttonFont", FONT_BOLD);
        UIManager.put("TextField.background", CARD_HI);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", ACCENT);
        UIManager.put("TextField.font", FONT_BASE);
        UIManager.put("FormattedTextField.background", CARD_HI);
        UIManager.put("FormattedTextField.foreground", TEXT);
        UIManager.put("Spinner.background", CARD_HI);
        UIManager.put("Spinner.foreground", TEXT);
        UIManager.put("ComboBox.background", CARD_HI);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", ACCENT_DARK);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("ComboBox.font", FONT_BASE);
        UIManager.put("CheckBox.background", BG);
        UIManager.put("CheckBox.foreground", TEXT_DIM);
        UIManager.put("CheckBox.font", FONT_SMALL);
        UIManager.put("Table.background", CARD);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.gridColor", BORDER_SOFT);
        UIManager.put("Table.selectionBackground", CARD_HI);
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Table.font", FONT_BASE);
        UIManager.put("Table.rowHeight", 38);
        UIManager.put("TableHeader.background", CARD);
        UIManager.put("TableHeader.foreground", TEXT_DIM);
        UIManager.put("TableHeader.font", FONT_SMALL);
        UIManager.put("SplitPane.background", BG);
        UIManager.put("SplitPane.dividerSize", 10);
        UIManager.put("SplitPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("ToolTip.background", CARD_HI);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.font", FONT_SMALL);
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        UIManager.put("FileChooser.background", CARD);
        UIManager.put("FileChooser.foreground", TEXT);
        UIManager.put("FileChooser.listFont", FONT_BASE);
        UIManager.put("FileChooser.listBackground", CARD_ALT);
        UIManager.put("FileChooser.listForeground", TEXT);
        UIManager.put("FileChooser.fileNameLabelText", "文件名");
        UIManager.put("FileChooser.filesOfTypeLabelText", "文件类型");
        UIManager.put("FileChooser.saveButtonText", "保存");
        UIManager.put("FileChooser.cancelButtonText", "取消");
        UIManager.put("FileChooser.lookInLabelText", "位置");
    }

    public static void styleTextField(JTextField field, int columns) {
        field.setColumns(columns);
        field.setBackground(CARD_HI);
        field.setForeground(TEXT);
        field.setCaretColor(ACCENT);
        field.setFont(FONT_MONO_BOLD.deriveFont(13f));
        field.setBorder(fieldBorder(BORDER));
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.setBorder(fieldBorder(ACCENT));
            }

            @Override
            public void focusLost(FocusEvent e) {
                field.setBorder(fieldBorder(BORDER));
            }
        });
    }

    public static void styleSpinner(JSpinner spinner) {
        spinner.setBackground(CARD_HI);
        spinner.setForeground(TEXT);
        spinner.setFont(FONT_MONO_BOLD);
        spinner.setBorder(fieldBorder(BORDER));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField field = ((JSpinner.DefaultEditor) editor).getTextField();
            field.setBackground(CARD_HI);
            field.setForeground(TEXT);
            field.setCaretColor(ACCENT);
            field.setFont(FONT_MONO_BOLD);
            field.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
            field.setHorizontalAlignment(JTextField.LEFT);
        }
    }

    public static void styleCombo(JComboBox<?> combo) {
        combo.setBackground(CARD_HI);
        combo.setForeground(TEXT);
        combo.setFont(FONT_BASE);
        combo.setBorder(fieldBorder(BORDER));
        combo.setFocusable(false);
    }

    public static void styleCheckBox(JCheckBox checkBox) {
        checkBox.setOpaque(false);
        checkBox.setForeground(TEXT_DIM);
        checkBox.setFont(FONT_SMALL);
        checkBox.setFocusPainted(false);
        checkBox.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    }

    public static void styleScrollPane(JScrollPane scrollPane) {
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBackground(CARD);
        scrollPane.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new ModernScrollBarUI());
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        scrollPane.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 8));
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.getViewport().setScrollMode(javax.swing.JViewport.BLIT_SCROLL_MODE);
    }

    public static void styleTextPane(JTextPane pane) {
        pane.setBackground(CARD_ALT);
        pane.setForeground(TEXT);
        pane.setCaretColor(ACCENT);
        pane.setFont(FONT_MONO);
        pane.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        pane.setSelectionColor(ACCENT_DARK);
        pane.setSelectedTextColor(TEXT);
    }

    public static void styleTable(JTable table) {
        table.setBackground(CARD);
        table.setForeground(TEXT);
        table.setFont(FONT_BASE);
        table.setRowHeight(37);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setSelectionBackground(CARD_HI);
        table.setSelectionForeground(TEXT);
        table.setFocusable(false);
        table.setRowSelectionAllowed(true);
        table.setAutoCreateRowSorter(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0, 34));
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        table.getTableHeader().setDefaultRenderer(new HeaderRenderer());
    }

    public static Color colorForSensor(com.mine.common.SensorType type) {
        switch (type) {
            case GAS:
                return GAS;
            case VACUUM:
                return VACUUM;
            case FLOW:
                return FLOW;
            case TEMP:
                return TEMP;
            default:
                return TEXT_DIM;
        }
    }

    public static String clockText() {
        return CLOCK_FORMAT.format(Instant.now());
    }

    public static String timeText(long millis) {
        return millis <= 0 ? "--" : SHORT_TIME.format(Instant.ofEpochMilli(millis));
    }

    public static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public static Color blend(Color a, Color b, float ratio) {
        float r = 1f - ratio;
        return new Color(
                Math.min(255, Math.round(a.getRed() * r + b.getRed() * ratio)),
                Math.min(255, Math.round(a.getGreen() * r + b.getGreen() * ratio)),
                Math.min(255, Math.round(a.getBlue() * r + b.getBlue() * ratio)),
                Math.min(255, Math.round(a.getAlpha() * r + b.getAlpha() * ratio)));
    }

    private static Border fieldBorder(Color color) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 1, true),
                BorderFactory.createEmptyBorder(5, 8, 5, 8));
    }

    private static String findFont(String... names) {
        try {
            Set<String> available = new HashSet<String>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String name : names) {
                if (available.contains(name)) {
                    return name;
                }
            }
        } catch (Throwable ignored) {
            // Headless / 字体环境异常时回退
        }
        return "SansSerif";
    }

    /** 细窄暗色滚动条。 */
    public static final class ModernScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            this.thumbColor = BORDER;
            this.trackColor = new Color(0, 0, 0, 0);
        }

        @Override
        protected javax.swing.JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected javax.swing.JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private javax.swing.JButton zeroButton() {
            javax.swing.JButton button = new javax.swing.JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            g.setColor(new Color(0, 0, 0, 0));
            g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isDragging ? ACCENT : BORDER);
            g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y + 2,
                    Math.max(3, thumbBounds.width - 4), Math.max(3, thumbBounds.height - 4), 6, 6);
            g2.dispose();
        }
    }

    /** 表头渲染器。 */
    public static final class HeaderRenderer extends javax.swing.table.DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            javax.swing.JLabel label = (javax.swing.JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            label.setBackground(CARD);
            label.setForeground(TEXT_FAINT);
            label.setFont(FONT_SMALL.deriveFont(Font.BOLD));
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                    BorderFactory.createEmptyBorder(0, 10, 0, 8)));
            label.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
            return label;
        }
    }
}

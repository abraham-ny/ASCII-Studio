package com.vitalsoft.asciistudio.ui;

import com.vitalsoft.asciistudio.core.AsciiConverter;
import com.vitalsoft.asciistudio.core.AsciiConverter.*;
import com.vitalsoft.asciistudio.core.DimensionCalculator;
import com.vitalsoft.asciistudio.core.DimensionCalculator.Params;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;

public class ImageTab extends JPanel {

    private JLabel statusLabel;
    private JLabel dimsLabel;
    private JSpinner colsSpinner;
    private JSpinner rowsSpinner;
    private JSpinner fontSizeSpinner;
    private JCheckBox invertCheck;
    private JComboBox<String> densityPresetCombo;
    private JTextField densityField;
    private JComboBox<String> modeCombo;
    private JCheckBox darkBgCheck;

    private JTextArea plainArea;
    private ColorAsciiPanel colorPanel;
    private JScrollPane plainScroll;
    private JScrollPane colorScroll;
    private CardLayout cardLayout;
    private JPanel displayCard;

    private AsciiConverter converter;
    private BufferedImage currentImage;
    private boolean autoFilling = false;

    // Density presets parallel to densityPresetCombo entries
    private static final String[] PRESET_LABELS = {
        "Detailed (70 chars)", "Simple (12 chars)", "Blocks", "Hex", "Binary", "Custom"
    };
    private static final String[] PRESET_VALUES = {
        AsciiConverter.PRESET_DETAILED,
        AsciiConverter.PRESET_SIMPLE,
        AsciiConverter.PRESET_BLOCKS,
        AsciiConverter.PRESET_HEX,
        AsciiConverter.PRESET_BINARY,
        null
    };

    public ImageTab() {
        setLayout(new BorderLayout(0, 0));
        converter = new AsciiConverter(120, 0, false, true);
        buildUI();
    }

    private void buildUI() {
        // --- Top control strip (two rows) ---
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBackground(Theme.CONTROL_BG);
        controls.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        // Row 1: file + dimensions
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row1.setBackground(Theme.CONTROL_BG);

        JButton openBtn = Theme.iconBtn("Open Image");
        row1.add(openBtn);
        row1.add(vSep());

        row1.add(Theme.label("Cols:"));
        colsSpinner = intSpinner(120, 10, 600, 10);
        row1.add(colsSpinner);

        row1.add(Theme.label("Rows:"));
        rowsSpinner = intSpinner(0, 0, 400, 5);
        row1.add(rowsSpinner);

        row1.add(Theme.label("Font pt:"));
        fontSizeSpinner = intSpinner(8, 4, 24, 1);
        row1.add(fontSizeSpinner);

        dimsLabel = new JLabel("  —");
        dimsLabel.setForeground(Theme.DIM);
        dimsLabel.setFont(dimsLabel.getFont().deriveFont(11f));
        row1.add(dimsLabel);

        controls.add(row1);

        // Row 2: mode + density + options + actions
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row2.setBackground(Theme.CONTROL_BG);

        row2.add(Theme.label("Mode:"));
        modeCombo = new JComboBox<>(new String[]{"Plain", "Color (render)", "Color (HTML)"});
        modeCombo.setBackground(Theme.FIELD_BG);
        modeCombo.setForeground(Theme.FG);
        row2.add(modeCombo);
        row2.add(vSep());

        row2.add(Theme.label("Density:"));
        densityPresetCombo = new JComboBox<>(PRESET_LABELS);
        densityPresetCombo.setBackground(Theme.FIELD_BG);
        densityPresetCombo.setForeground(Theme.FG);
        row2.add(densityPresetCombo);

        densityField = new JTextField(AsciiConverter.PRESET_DETAILED, 22);
        densityField.setBackground(Theme.FIELD_BG);
        densityField.setForeground(Theme.FG);
        densityField.setCaretColor(Theme.FG);
        densityField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        densityField.setToolTipText("Characters ordered dark→light. Edit freely or choose a preset.");
        row2.add(densityField);
        row2.add(vSep());

        invertCheck = new JCheckBox("Invert");
        invertCheck.setBackground(Theme.CONTROL_BG);
        invertCheck.setForeground(Theme.FG);
        row2.add(invertCheck);

        darkBgCheck = new JCheckBox("Dark BG");
        darkBgCheck.setSelected(true);
        darkBgCheck.setBackground(Theme.CONTROL_BG);
        darkBgCheck.setForeground(Theme.FG);
        row2.add(darkBgCheck);
        row2.add(vSep());

        JButton convertBtn = Theme.accentBtn("Convert");
        row2.add(convertBtn);
        JButton copyBtn = Theme.iconBtn("Copy");
        row2.add(copyBtn);
        JButton saveBtn = Theme.iconBtn("Save...");
        row2.add(saveBtn);

        controls.add(row2);
        add(controls, BorderLayout.NORTH);

        // --- Display area ---
        cardLayout = new CardLayout();
        displayCard = new JPanel(cardLayout);

        plainArea = new JTextArea();
        plainArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 8));
        plainArea.setBackground(Color.BLACK);
        plainArea.setForeground(new Color(0x33FF66));
        plainArea.setEditable(false);
        plainArea.setLineWrap(false);
        plainScroll = new JScrollPane(plainArea);
        plainScroll.setBorder(null);

        colorPanel = new ColorAsciiPanel();
        colorScroll = new JScrollPane(colorPanel);
        colorScroll.setBorder(null);

        displayCard.add(plainScroll, "PLAIN");
        displayCard.add(colorScroll, "COLOR");
        add(displayCard, BorderLayout.CENTER);

        statusLabel = new JLabel("  Open an image to begin");
        statusLabel.setForeground(Theme.DIM);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        add(statusLabel, BorderLayout.SOUTH);

        // --- Wiring ---
        openBtn.addActionListener(e -> openImage());
        convertBtn.addActionListener(e -> convert());
        copyBtn.addActionListener(e -> copyToClipboard());
        saveBtn.addActionListener(e -> saveOutput());

        modeCombo.addActionListener(e -> {
            if (modeCombo.getSelectedIndex() > 0) cardLayout.show(displayCard, "COLOR");
            else cardLayout.show(displayCard, "PLAIN");
        });

        darkBgCheck.addActionListener(e -> colorPanel.setDarkBg(darkBgCheck.isSelected()));

        densityPresetCombo.addActionListener(e -> {
            int idx = densityPresetCombo.getSelectedIndex();
            if (PRESET_VALUES[idx] != null) {
                densityField.setText(PRESET_VALUES[idx]);
                densityField.setEditable(false);
            } else {
                densityField.setEditable(true);
                densityField.requestFocus();
            }
        });

        // When font size changes, recalculate rows to keep aspect
        fontSizeSpinner.addChangeListener(e -> {
            if (autoFilling || currentImage == null) return;
            int fs = (int) fontSizeSpinner.getValue();
            int cols = (int) colsSpinner.getValue();
            int newRows = DimensionCalculator.autoRows(cols, currentImage.getWidth(), currentImage.getHeight(), fs);
            autoFilling = true;
            rowsSpinner.setValue(newRows);
            autoFilling = false;
            updateDimsLabel(cols, newRows, fs);
        });

        // When cols change, recalculate rows
        colsSpinner.addChangeListener(e -> {
            if (autoFilling || currentImage == null) return;
            int cols = (int) colsSpinner.getValue();
            int fs   = (int) fontSizeSpinner.getValue();
            int newRows = DimensionCalculator.autoRows(cols, currentImage.getWidth(), currentImage.getHeight(), fs);
            autoFilling = true;
            rowsSpinner.setValue(newRows);
            autoFilling = false;
            updateDimsLabel(cols, newRows, fs);
        });
    }

    private void openImage() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "gif", "bmp", "webp"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        try {
            currentImage = ImageIO.read(f);
            if (currentImage == null) { status("Error: cannot read image"); return; }
            autofillFromDimensions(currentImage.getWidth(), currentImage.getHeight(), false);
            status("Loaded: " + f.getName() + "  (" + currentImage.getWidth() + "×" + currentImage.getHeight() + ")");
            convert();
        } catch (IOException ex) {
            status("Error: " + ex.getMessage());
        }
    }

    /** Compute and fill cols/rows/fontSize from pixel dimensions. */
    public void autofillFromDimensions(int w, int h, boolean forVideo) {
        Params p = forVideo
            ? DimensionCalculator.forVideo(w, h, 200)
            : DimensionCalculator.forImage(w, h, 320);
        autoFilling = true;
        colsSpinner.setValue(p.cols());
        rowsSpinner.setValue(p.rows());
        fontSizeSpinner.setValue(p.fontSize());
        autoFilling = false;
        updateDimsLabel(p.cols(), p.rows(), p.fontSize());
        // Also update the display font size
        plainArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, p.fontSize()));
        colorPanel.setFontSize(p.fontSize());
    }

    private void updateDimsLabel(int cols, int rows, int fs) {
        if (currentImage != null) {
            int rw = cols * DimensionCalculator.charWidth(fs);
            int rh = rows * DimensionCalculator.charHeight(fs);
            dimsLabel.setText(String.format("  %d×%d chars → renders ~%d×%dpx  (font %dpt)",
                cols, rows, rw, rh, fs));
        }
    }

    private void convert() {
        if (currentImage == null) { status("No image loaded"); return; }
        int cols = (int) colsSpinner.getValue();
        int rows = (int) rowsSpinner.getValue();
        int fs   = (int) fontSizeSpinner.getValue();

        converter.setCols(cols);
        converter.setRows(rows);
        converter.setFontAspect(DimensionCalculator.fontAspect(fs));
        converter.setInvert(invertCheck.isSelected());

        // Apply density
        String density = densityField.getText();
        if (density != null && !density.trim().isEmpty()) {
            converter.setCustomDensity(density);
        } else {
            converter.setCustomDensity(null);
            converter.setDetailed(true);
        }

        // Update display font
        plainArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, fs));
        colorPanel.setFontSize(fs);

        int modeIdx = modeCombo.getSelectedIndex();
        if (modeIdx == 0) {
            String ascii = converter.toPlainAscii(currentImage);
            plainArea.setText(ascii);
            cardLayout.show(displayCard, "PLAIN");
            status("Plain ASCII — " + cols + "×" + rows + "  font " + fs + "pt");
        } else if (modeIdx == 1) {
            PixelChar[][] grid = converter.toPixelChars(currentImage);
            colorPanel.setGrid(grid);
            colorPanel.setDarkBg(darkBgCheck.isSelected());
            cardLayout.show(displayCard, "COLOR");
            status("Color ASCII — " + cols + "×" + rows + "  font " + fs + "pt");
        } else {
            String html = converter.toColorHtml(currentImage);
            plainArea.setText(html);
            cardLayout.show(displayCard, "PLAIN");
            status("Color HTML — " + cols + "×" + rows + "  font " + fs + "pt");
        }
    }

    private void copyToClipboard() {
        int idx = modeCombo.getSelectedIndex();
        if (idx == 1) { status("Color render — use Save to export as PNG"); return; }
        Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(plainArea.getText()), null);
        status("Copied to clipboard");
    }

    private void saveOutput() {
        JFileChooser fc = new JFileChooser();
        int idx = modeCombo.getSelectedIndex();
        if (idx == 1) {
            fc.setFileFilter(new FileNameExtensionFilter("PNG Image", "png"));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File f = fc.getSelectedFile();
            if (!f.getName().endsWith(".png")) f = new File(f.getAbsolutePath() + ".png");
            try {
                BufferedImage img = colorPanel.toImage();
                if (img != null) ImageIO.write(img, "png", f);
                status("Saved: " + f.getName());
            } catch (IOException ex) { status("Save error: " + ex.getMessage()); }
        } else {
            String ext = idx == 2 ? ".html" : ".txt";
            fc.setFileFilter(new FileNameExtensionFilter(idx == 2 ? "HTML" : "Text",
                idx == 2 ? "html" : "txt"));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File f = fc.getSelectedFile();
            if (!f.getName().contains(".")) f = new File(f.getAbsolutePath() + ext);
            try {
                Files.writeString(f.toPath(), plainArea.getText());
                status("Saved: " + f.getName());
            } catch (IOException ex) { status("Save error: " + ex.getMessage()); }
        }
    }

    // ---- helpers ----

    public int getFontSize()  { return (int) fontSizeSpinner.getValue(); }
    public int getCols()      { return (int) colsSpinner.getValue(); }
    public int getRows()      { return (int) rowsSpinner.getValue(); }
    public String getDensity(){ return densityField.getText(); }

    private void status(String msg) { statusLabel.setText("  " + msg); }

    private JSpinner intSpinner(int val, int min, int max, int step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(val, min, max, step));
        s.setPreferredSize(new Dimension(70, 24));
        return s;
    }

    private Component vSep() {
        JSeparator sep = new JSeparator(JSeparator.VERTICAL);
        sep.setPreferredSize(new Dimension(2, 20));
        sep.setForeground(new Color(0x444444));
        return sep;
    }
}

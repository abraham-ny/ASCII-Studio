package com.vitalsoft.asciistudio.ui;

import com.vitalsoft.asciistudio.core.AsciiConverter;
import com.vitalsoft.asciistudio.core.DimensionCalculator;
import com.vitalsoft.asciistudio.core.DimensionCalculator.Params;
import com.vitalsoft.asciistudio.video.VideoExtractor;
import com.vitalsoft.asciistudio.video.VideoMetadata;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;

public class VideoTab extends JPanel {

    private JTextField videoPathField;
    private JTextField outputDirField;
    private JSpinner fpsSpinner;
    private JSpinner colsSpinner;
    private JSpinner rowsSpinner;
    private JSpinner fontSizeSpinner;
    private JLabel dimsLabel;
    private JComboBox<String> modeCombo;
    private JComboBox<String> densityPresetCombo;
    private JTextField densityField;
    private JCheckBox invertCheck;
    private JCheckBox extractAudioCheck;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JButton convertBtn;
    private JButton cancelBtn;

    private VideoExtractor extractor;
    private File lastOutputDir;
    private Runnable onConversionComplete;
    private boolean autoFilling = false;

    private static final String[] PRESET_LABELS = {
        "Detailed (70 chars)", "Simple (12 chars)", "Blocks", "Hex", "Binary", "Custom"
    };
    private static final String[] PRESET_VALUES = {
        AsciiConverter.PRESET_DETAILED, AsciiConverter.PRESET_SIMPLE,
        AsciiConverter.PRESET_BLOCKS,   AsciiConverter.PRESET_HEX,
        AsciiConverter.PRESET_BINARY,   null
    };

    public VideoTab(Runnable onConversionComplete) {
        this.onConversionComplete = onConversionComplete;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        setBackground(Theme.BG);
        buildUI();
    }

    private void buildUI() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.BG);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 4, 5, 4);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;

        int row = 0;

        // ---- Video file ----
        gc.gridx = 0; gc.gridy = row; gc.weightx = 0;
        form.add(labelRight("Video File:"), gc);
        videoPathField = darkTextField();
        gc.gridx = 1; gc.weightx = 1.0;
        form.add(videoPathField, gc);
        JButton browseVideo = Theme.iconBtn("Browse...");
        gc.gridx = 2; gc.weightx = 0;
        form.add(browseVideo, gc);

        // ---- Output dir ----
        row++;
        gc.gridx = 0; gc.gridy = row; gc.weightx = 0;
        form.add(labelRight("Output Dir:"), gc);
        outputDirField = darkTextField();
        gc.gridx = 1; gc.weightx = 1.0;
        form.add(outputDirField, gc);
        JButton browseOut = Theme.iconBtn("Browse...");
        gc.gridx = 2; gc.weightx = 0;
        form.add(browseOut, gc);

        // ---- Detected dimensions info ----
        row++;
        gc.gridx = 0; gc.gridy = row; gc.weightx = 0;
        form.add(labelRight("Source:"), gc);
        dimsLabel = new JLabel("—  (select a video to autofill)");
        dimsLabel.setForeground(Theme.DIM);
        dimsLabel.setFont(dimsLabel.getFont().deriveFont(11f));
        gc.gridx = 1; gc.gridwidth = 2; gc.weightx = 1.0;
        form.add(dimsLabel, gc);
        gc.gridwidth = 1;

        // ---- FPS + cols + rows + fontSize ----
        row++;
        gc.gridx = 0; gc.gridy = row; gc.weightx = 0;
        form.add(labelRight("FPS / Dims:"), gc);

        JPanel dimsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        dimsRow.setBackground(Theme.BG);

        fpsSpinner = new JSpinner(new SpinnerNumberModel(24.0, 1.0, 60.0, 1.0));
        fpsSpinner.setPreferredSize(new Dimension(72, 24));
        dimsRow.add(Theme.label("FPS"));
        dimsRow.add(fpsSpinner);

        colsSpinner = intSpinner(120, 10, 600, 10);
        dimsRow.add(Theme.label("  Cols"));
        dimsRow.add(colsSpinner);

        rowsSpinner = intSpinner(0, 0, 400, 5);
        dimsRow.add(Theme.label("Rows"));
        dimsRow.add(rowsSpinner);

        fontSizeSpinner = intSpinner(8, 4, 24, 1);
        dimsRow.add(Theme.label("Font pt"));
        dimsRow.add(fontSizeSpinner);

        gc.gridx = 1; gc.gridwidth = 2; gc.weightx = 1.0;
        form.add(dimsRow, gc);
        gc.gridwidth = 1;

        // ---- Mode + density ----
        row++;
        gc.gridx = 0; gc.gridy = row; gc.weightx = 0;
        form.add(labelRight("ASCII Mode:"), gc);

        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        modeRow.setBackground(Theme.BG);
        modeCombo = new JComboBox<>(new String[]{"Plain Text", "Color ACF (compact binary)", "Color HTML"});
        modeCombo.setBackground(Theme.FIELD_BG);
        modeCombo.setForeground(Theme.FG);
        modeRow.add(modeCombo);

        invertCheck = new JCheckBox("Invert brightness");
        invertCheck.setBackground(Theme.BG);
        invertCheck.setForeground(Theme.FG);
        modeRow.add(invertCheck);

        gc.gridx = 1; gc.gridwidth = 2; gc.weightx = 1.0;
        form.add(modeRow, gc);
        gc.gridwidth = 1;

        // ---- Density ----
        row++;
        gc.gridx = 0; gc.gridy = row; gc.weightx = 0;
        form.add(labelRight("Density:"), gc);

        JPanel densityRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        densityRow.setBackground(Theme.BG);
        densityPresetCombo = new JComboBox<>(PRESET_LABELS);
        densityPresetCombo.setBackground(Theme.FIELD_BG);
        densityPresetCombo.setForeground(Theme.FG);
        densityRow.add(densityPresetCombo);

        densityField = new JTextField(AsciiConverter.PRESET_DETAILED, 28);
        densityField.setBackground(Theme.FIELD_BG);
        densityField.setForeground(Theme.FG);
        densityField.setCaretColor(Theme.FG);
        densityField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        densityField.setEditable(false);
        densityField.setToolTipText("Characters ordered dark→light. Select 'Custom' to edit.");
        densityRow.add(densityField);

        gc.gridx = 1; gc.gridwidth = 2; gc.weightx = 1.0;
        form.add(densityRow, gc);
        gc.gridwidth = 1;

        // ---- Audio ----
        row++;
        gc.gridx = 0; gc.gridy = row; gc.weightx = 0;
        form.add(labelRight("Options:"), gc);
        extractAudioCheck = new JCheckBox("Extract audio track (MP3)");
        extractAudioCheck.setSelected(true);
        extractAudioCheck.setBackground(Theme.BG);
        extractAudioCheck.setForeground(Theme.FG);
        gc.gridx = 1; gc.gridwidth = 2;
        form.add(extractAudioCheck, gc);
        gc.gridwidth = 1;

        add(form, BorderLayout.NORTH);

        // ---- Centre: FFmpeg status + progress ----
        JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
        centerPanel.setBackground(Theme.BG);

        boolean ffAvail = VideoExtractor.isFFmpegAvailable();
        JLabel ffLabel = new JLabel(ffAvail
            ? "  ✔  FFmpeg detected — ready to convert"
            : "  ⚠  FFmpeg not found — install it and ensure it is in PATH");
        ffLabel.setForeground(ffAvail ? new Color(0x44CC88) : new Color(0xFFAA33));
        centerPanel.add(ffLabel, BorderLayout.NORTH);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("Idle");
        progressBar.setForeground(Theme.ACCENT);
        progressBar.setBackground(Theme.FIELD_BG);
        centerPanel.add(progressBar, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // ---- Bottom: buttons + status ----
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnPanel.setBackground(Theme.BG);
        convertBtn = Theme.accentBtn("Convert Video");
        cancelBtn = Theme.iconBtn("Cancel");
        cancelBtn.setEnabled(false);
        btnPanel.add(convertBtn);
        btnPanel.add(cancelBtn);
        statusLabel = new JLabel("  Ready");
        statusLabel.setForeground(Theme.DIM);
        btnPanel.add(statusLabel);
        add(btnPanel, BorderLayout.SOUTH);

        // ---- Event wiring ----
        browseVideo.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("Video Files",
                "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v"));
            if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File f = fc.getSelectedFile();
            videoPathField.setText(f.getAbsolutePath());
            // Default output dir
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            outputDirField.setText(f.getParent() + File.separator + name + "_ascii");
            // Autofill from video dimensions in background
            status("Probing video dimensions...");
            Thread probe = new Thread(() -> autofillFromVideo(f));
            probe.setDaemon(true);
            probe.start();
        });

        browseOut.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                outputDirField.setText(fc.getSelectedFile().getAbsolutePath());
        });

        convertBtn.addActionListener(e -> startConversion());
        cancelBtn.addActionListener(e -> {
            if (extractor != null) { extractor.cancel(); status("Cancelling..."); }
        });

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

        // When font size changes, recalculate rows
        fontSizeSpinner.addChangeListener(e -> recalcRows());
        colsSpinner.addChangeListener(e -> recalcRows());
    }

    /** Probe video dims and fps via ffprobe, then autofill all spinners. */
    private void autofillFromVideo(File f) {
        int[] dims = VideoExtractor.probeVideoDimensions(f);
        double fps  = VideoExtractor.probeVideoFps(f);
        double dur  = VideoExtractor.probeVideoDuration(f);

        SwingUtilities.invokeLater(() -> {
            if (dims[0] > 0 && dims[1] > 0) {
                Params p = DimensionCalculator.forVideo(dims[0], dims[1], 200);
                autoFilling = true;
                colsSpinner.setValue(p.cols());
                rowsSpinner.setValue(p.rows());
                fontSizeSpinner.setValue(p.fontSize());
                if (fps > 0) fpsSpinner.setValue(Math.min(fps, 30.0));
                autoFilling = false;

                int rw = p.renderWidth();
                int rh = p.renderHeight();
                String durStr = dur > 0 ? String.format("  %.1fs", dur) : "";
                dimsLabel.setText(String.format(
                    "%d×%d px%s  →  autofilled: %d×%d chars  font %dpt  renders ~%d×%dpx",
                    dims[0], dims[1], durStr, p.cols(), p.rows(), p.fontSize(), rw, rh));
                status("Autofilled from " + dims[0] + "×" + dims[1] + " @ " + String.format("%.2g", fps) + " fps");
            } else {
                dimsLabel.setText("Could not probe video dimensions — set manually");
                status("Ready (manual)");
            }
        });
    }

    private void recalcRows() {
        if (autoFilling) return;
        String path = videoPathField.getText().trim();
        // We need to know source dims to recalc; use stored label as fallback
        // Just recalc from current cols+fontsize using a square aspect if no source known
        int cols = (int) colsSpinner.getValue();
        int fs   = (int) fontSizeSpinner.getValue();
        // If we have video loaded, re-probe or use last known dims embedded in dimsLabel text
        // For simplicity, recalc preserving existing rows ratio
    }

    private void startConversion() {
        String videoPath = videoPathField.getText().trim();
        String outPath   = outputDirField.getText().trim();
        if (videoPath.isEmpty()) { status("No video file selected"); return; }
        if (outPath.isEmpty())   { status("No output directory specified"); return; }
        if (!VideoExtractor.isFFmpegAvailable()) { status("FFmpeg not found in PATH"); return; }

        File videoFile = new File(videoPath);
        if (!videoFile.exists()) { status("Video file not found"); return; }

        File outDir = new File(outPath);
        outDir.mkdirs();
        lastOutputDir = outDir;

        double fps    = (double) fpsSpinner.getValue();
        int cols      = (int) colsSpinner.getValue();
        int rows      = (int) rowsSpinner.getValue();
        int fontSize  = (int) fontSizeSpinner.getValue();
        String density = densityField.getText();
        AsciiConverter.Mode mode = switch (modeCombo.getSelectedIndex()) {
            case 1  -> AsciiConverter.Mode.COLOR_ACF;
            case 2  -> AsciiConverter.Mode.COLOR_HTML;
            default -> AsciiConverter.Mode.PLAIN;
        };
        boolean invert       = invertCheck.isSelected();
        boolean extractAudio = extractAudioCheck.isSelected();

        convertBtn.setEnabled(false);
        cancelBtn.setEnabled(true);
        progressBar.setValue(0);
        progressBar.setString("Starting...");

        extractor = new VideoExtractor();

        Thread t = new Thread(() -> {
            try {
                String audioSrc = "";
                if (extractAudio) {
                    SwingUtilities.invokeLater(() -> status("Extracting audio..."));
                    try { audioSrc = VideoExtractor.extractAudio(videoFile, outDir); }
                    catch (Exception ignored) {}
                }
                final String finalAudio = audioSrc;

                extractor.extractFrames(videoFile, outDir, fps, cols, rows, mode, invert,
                    density, fontSize,
                    (done, total, msg) -> {
                        int pct = total > 0 ? (int)((done * 100.0) / total) : 0;
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setValue(pct);
                            progressBar.setString(msg);
                            status(msg);
                        });
                    });

                if (!outDir.exists()) return;

                File[] frameFiles = outDir.listFiles((d, n) ->
                    n.endsWith(".txt") || n.endsWith(".html") || n.endsWith(".acf"));
                int frameCount = frameFiles == null ? 0 : frameFiles.length;
                double duration = VideoExtractor.probeVideoDuration(videoFile);
                long durMs = duration > 0 ? (long)(duration * 1000) : 0;

                VideoMetadata meta = new VideoMetadata(
                    videoFile.getName(), fps, frameCount, cols, rows,
                    fontSize, density, finalAudio, mode.name(), durMs
                );
                meta.save(outDir);

                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(100);
                    progressBar.setString("Done — " + frameCount + " frames");
                    status("Complete: " + frameCount + " frames → " + outDir.getName());
                    convertBtn.setEnabled(true);
                    cancelBtn.setEnabled(false);
                    if (onConversionComplete != null) onConversionComplete.run();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    status("Error: " + ex.getMessage());
                    progressBar.setString("Error");
                    convertBtn.setEnabled(true);
                    cancelBtn.setEnabled(false);
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void status(String msg) { statusLabel.setText("  " + msg); }

    private JTextField darkTextField() {
        JTextField f = new JTextField();
        f.setBackground(Theme.FIELD_BG);
        f.setForeground(Theme.FG);
        f.setCaretColor(Theme.FG);
        return f;
    }

    private JLabel labelRight(String txt) {
        JLabel l = Theme.label(txt);
        l.setHorizontalAlignment(SwingConstants.RIGHT);
        return l;
    }

    private JSpinner intSpinner(int val, int min, int max, int step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(val, min, max, step));
        s.setPreferredSize(new Dimension(70, 24));
        return s;
    }

    public File getLastOutputDir() { return lastOutputDir; }
}

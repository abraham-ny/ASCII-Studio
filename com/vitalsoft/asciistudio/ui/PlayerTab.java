package com.vitalsoft.asciistudio.ui;

import com.vitalsoft.asciistudio.core.AsciiConverter;
import com.vitalsoft.asciistudio.player.AsciiPlayer;
import com.vitalsoft.asciistudio.player.AsciiPlayer.*;
import com.vitalsoft.asciistudio.video.AsciiVideoRenderer;
import com.vitalsoft.asciistudio.video.VideoMetadata;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;

public class PlayerTab extends JPanel {

    // Display area
    private JTextArea   asciiDisplay;
    private JEditorPane htmlDisplay;
    private ColorAsciiPanel colorAcfPanel;
    private CardLayout  displayCard;
    private JPanel      displayPanel;

    // Controls
    private JLabel     frameLabel;
    private JLabel     timeLabel;
    private JLabel     metaLabel;
    private JSlider    seekBar;
    private JButton    playBtn;
    private JButton    stopBtn;
    private JButton    stepBackBtn;
    private JButton    stepFwdBtn;
    private JButton    openBtn;
    private JButton    exportBtn;
    private JCheckBox  darkBgCheck;
    private JComboBox<String> fontSizeCombo;
    private JLabel     statusLabel;

    // Export progress
    private JProgressBar exportBar;
    private JLabel       exportLabel;

    private AsciiPlayer player;
    private boolean seekBarUpdating = false;
    private File loadedDir;
    private AsciiVideoRenderer currentRenderer;

    public PlayerTab() {
        setLayout(new BorderLayout(0, 0));
        setBackground(Theme.BG);
        player = new AsciiPlayer(new FrameListener() {
            public void onFrame(FrameData fd) {
                SwingUtilities.invokeLater(() -> showFrame(fd));
            }
            public void onStateChange(State state) {
                SwingUtilities.invokeLater(() -> updateControls(state));
            }
            public void onEnd() {
                SwingUtilities.invokeLater(() -> statusLabel.setText("  Playback finished"));
            }
        });
        buildUI();
    }

    private void buildUI() {
        // ---- Top bar ----
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        topBar.setBackground(Theme.CONTROL_BG);
        openBtn = Theme.iconBtn("Open Dir");
        topBar.add(openBtn);
        topBar.add(vSep());

        topBar.add(Theme.label("Font:"));
        fontSizeCombo = new JComboBox<>(new String[]{"4","5","6","7","8","9","10","11","12","14","16","18","20","24"});
        fontSizeCombo.setSelectedItem("8");
        fontSizeCombo.setBackground(Theme.FIELD_BG);
        fontSizeCombo.setForeground(Theme.FG);
        topBar.add(fontSizeCombo);

        darkBgCheck = new JCheckBox("Dark BG");
        darkBgCheck.setSelected(true);
        darkBgCheck.setBackground(Theme.CONTROL_BG);
        darkBgCheck.setForeground(Theme.FG);
        topBar.add(darkBgCheck);
        topBar.add(vSep());

        exportBtn = Theme.accentBtn("Export ASCII Video…");
        exportBtn.setEnabled(false);
        topBar.add(exportBtn);

        topBar.add(vSep());
        metaLabel = new JLabel("  No video loaded");
        metaLabel.setForeground(Theme.DIM);
        metaLabel.setFont(metaLabel.getFont().deriveFont(11f));
        topBar.add(metaLabel);
        add(topBar, BorderLayout.NORTH);

        // ---- Display card ----
        displayCard  = new CardLayout();
        displayPanel = new JPanel(displayCard);

        asciiDisplay = new JTextArea();
        asciiDisplay.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 8));
        asciiDisplay.setBackground(Color.BLACK);
        asciiDisplay.setForeground(new Color(0x33FF66));
        asciiDisplay.setEditable(false);
        asciiDisplay.setLineWrap(false);
        displayPanel.add(new JScrollPane(asciiDisplay), "PLAIN");

        htmlDisplay = new JEditorPane("text/html", "");
        htmlDisplay.setEditable(false);
        htmlDisplay.setBackground(Color.BLACK);
        displayPanel.add(new JScrollPane(htmlDisplay), "HTML");

        colorAcfPanel = new ColorAsciiPanel();
        JScrollPane acfScroll = new JScrollPane(colorAcfPanel);
        displayPanel.add(acfScroll, "ACF");

        add(displayPanel, BorderLayout.CENTER);

        // ---- Bottom ----
        JPanel bottom = new JPanel(new BorderLayout(0, 2));
        bottom.setBackground(Theme.CONTROL_BG);
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        seekBar = new JSlider(0, 0, 0);
        seekBar.setBackground(Theme.CONTROL_BG);
        seekBar.setForeground(Theme.ACCENT);
        bottom.add(seekBar, BorderLayout.NORTH);

        JPanel ctrlRow = new JPanel(new BorderLayout());
        ctrlRow.setBackground(Theme.CONTROL_BG);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        btns.setBackground(Theme.CONTROL_BG);
        stepBackBtn = Theme.iconBtn("|◀");
        stopBtn     = Theme.iconBtn("■ Stop");
        playBtn     = Theme.accentBtn("▶  Play");
        stepFwdBtn  = Theme.iconBtn("▶|");
        btns.add(stepBackBtn); btns.add(stopBtn); btns.add(playBtn); btns.add(stepFwdBtn);
        ctrlRow.add(btns, BorderLayout.CENTER);

        JPanel infoRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        infoRow.setBackground(Theme.CONTROL_BG);
        frameLabel = new JLabel("Frame: 0 / 0");
        frameLabel.setForeground(Theme.DIM);
        timeLabel = new JLabel("00:00.000");
        timeLabel.setForeground(Theme.DIM);
        infoRow.add(frameLabel); infoRow.add(timeLabel);
        ctrlRow.add(infoRow, BorderLayout.EAST);
        bottom.add(ctrlRow, BorderLayout.CENTER);

        // Export progress row (hidden until export starts)
        JPanel exportRow = new JPanel(new BorderLayout(6, 0));
        exportRow.setBackground(Theme.CONTROL_BG);
        exportBar = new JProgressBar(0, 100);
        exportBar.setStringPainted(true);
        exportBar.setString("");
        exportBar.setForeground(new Color(0x44AAFF));
        exportBar.setBackground(Theme.FIELD_BG);
        exportLabel = new JLabel("  ");
        exportLabel.setForeground(Theme.DIM);
        exportLabel.setFont(exportLabel.getFont().deriveFont(11f));
        exportRow.add(exportBar, BorderLayout.CENTER);
        exportRow.add(exportLabel, BorderLayout.EAST);
        exportRow.setVisible(false);
        bottom.add(exportRow, BorderLayout.SOUTH);
        // store ref so we can show/hide
        this.exportRow = exportRow;

        statusLabel = new JLabel("  Open an ASCII video directory to begin");
        statusLabel.setForeground(Theme.DIM);
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(Theme.CONTROL_BG);
        statusPanel.add(statusLabel, BorderLayout.WEST);
        bottom.add(statusPanel, BorderLayout.NORTH);

        add(bottom, BorderLayout.SOUTH);

        // ---- Wiring ----
        openBtn.addActionListener(e -> openDir());
        playBtn.addActionListener(e -> {
            if (player.getState() == State.PLAYING) player.pause();
            else player.play();
        });
        stopBtn.addActionListener(e -> player.stop());
        stepBackBtn.addActionListener(e -> { player.pause(); player.stepBack(); });
        stepFwdBtn.addActionListener(e -> { player.pause(); player.stepForward(); });

        fontSizeCombo.addActionListener(e -> {
            int sz = Integer.parseInt((String) fontSizeCombo.getSelectedItem());
            asciiDisplay.setFont(new Font(Font.MONOSPACED, Font.PLAIN, sz));
            colorAcfPanel.setFontSize(sz);
        });

        darkBgCheck.addActionListener(e -> {
            boolean dark = darkBgCheck.isSelected();
            asciiDisplay.setBackground(dark ? Color.BLACK : Color.WHITE);
            asciiDisplay.setForeground(dark ? new Color(0x33FF66) : Color.BLACK);
            colorAcfPanel.setDarkBg(dark);
        });

        seekBar.addChangeListener(e -> {
            if (seekBar.getValueIsAdjusting() && !seekBarUpdating)
                player.seekTo(seekBar.getValue());
        });

        exportBtn.addActionListener(e -> exportVideo());
    }

    // keep ref for show/hide
    private JPanel exportRow;

    public void openDir(File dir) {
        loadedDir = dir;
        boolean ok = player.load(dir);
        if (!ok) { statusLabel.setText("  No ASCII frames found in: " + dir.getName()); return; }
        seekBar.setMaximum(Math.max(0, player.getFrameCount() - 1));
        seekBar.setValue(0);
        VideoMetadata meta = player.getMetadata();
        int fs = meta.fontSize > 0 ? meta.fontSize : 8;
        metaLabel.setText(String.format("  %s  |  %.1f fps  |  %d frames  |  %s  |  font %dpt",
            meta.videoName, meta.fps, meta.frameCount, meta.mode, fs));
        statusLabel.setText("  Loaded: " + dir.getName());
        // Apply stored font size
        asciiDisplay.setFont(new Font(Font.MONOSPACED, Font.PLAIN, fs));
        colorAcfPanel.setFontSize(fs);
        fontSizeCombo.setSelectedItem(String.valueOf(Math.min(fs, 24)));
        // Show correct display pane
        switch (player.getFrameType()) {
            case ACF  -> displayCard.show(displayPanel, "ACF");
            case HTML -> displayCard.show(displayPanel, "HTML");
            default   -> displayCard.show(displayPanel, "PLAIN");
        }
        exportBtn.setEnabled(true);
    }

    private void openDir() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            openDir(fc.getSelectedFile());
    }

    private void showFrame(FrameData fd) {
        switch (fd.type) {
            case ACF -> {
                colorAcfPanel.setGrid(fd.acf.toPixelChars(fd.charset));
                displayCard.show(displayPanel, "ACF");
            }
            case HTML -> {
                htmlDisplay.setText(fd.text);
                htmlDisplay.setCaretPosition(0);
            }
            default -> {
                asciiDisplay.setText(fd.text);
                asciiDisplay.setCaretPosition(0);
            }
        }
        seekBarUpdating = true;
        seekBar.setValue(fd.index);
        seekBarUpdating = false;
        frameLabel.setText("Frame: " + (fd.index + 1) + " / " + fd.total);
        VideoMetadata meta = player.getMetadata();
        long ms = (long)(fd.index * 1000.0 / meta.fps);
        timeLabel.setText(String.format("%02d:%02d.%03d", ms/60000, (ms/1000)%60, ms%1000));
    }

    private void updateControls(State state) {
        playBtn.setText(state == State.PLAYING ? "⏸  Pause" : "▶  Play");
        boolean loaded = player.isLoaded();
        stopBtn.setEnabled(loaded && state != State.STOPPED);
        stepBackBtn.setEnabled(loaded);
        stepFwdBtn.setEnabled(loaded);
    }

    private void exportVideo() {
        if (loadedDir == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save ASCII Video As...");
        fc.setFileFilter(new FileNameExtensionFilter("MP4 Video", "mp4"));
        fc.setSelectedFile(new File(loadedDir.getName() + "_ascii.mp4"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File outFile = fc.getSelectedFile();
        if (!outFile.getName().endsWith(".mp4"))
            outFile = new File(outFile.getAbsolutePath() + ".mp4");
        final File finalOut = outFile;

        boolean dark = darkBgCheck.isSelected();
        exportBtn.setEnabled(false);
        exportRow.setVisible(true);
        exportBar.setValue(0);
        exportBar.setString("Starting...");
        exportLabel.setText("  Initialising");

        currentRenderer = new AsciiVideoRenderer();
        final File capturedDir = loadedDir;

        Thread t = new Thread(() -> {
            try {
                currentRenderer.render(capturedDir, finalOut, dark,
                    (done, total, phase, detail) -> SwingUtilities.invokeLater(() -> {
                        int pct = total > 0 ? (int)(done * 100.0 / total) : 0;
                        exportBar.setValue(pct);
                        exportBar.setString(phase + "  " + pct + "%");
                        exportLabel.setText("  " + detail);
                    }));
                SwingUtilities.invokeLater(() -> {
                    exportBar.setValue(100);
                    exportBar.setString("Done");
                    exportLabel.setText("  Saved: " + finalOut.getName());
                    exportBtn.setEnabled(true);
                    statusLabel.setText("  Export complete: " + finalOut.getAbsolutePath());
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    exportBar.setString("Error");
                    exportLabel.setText("  " + ex.getMessage());
                    exportBtn.setEnabled(true);
                    statusLabel.setText("  Export failed: " + ex.getMessage());
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private Component vSep() {
        JSeparator s = new JSeparator(JSeparator.VERTICAL);
        s.setPreferredSize(new Dimension(2, 20));
        s.setForeground(new Color(0x444444));
        return s;
    }
}

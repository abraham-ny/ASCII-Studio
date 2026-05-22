package com.vitalsoft.asciistudio.ui;

import javax.swing.*;
import java.awt.*;

public class MainWindow extends JFrame {

    private PlayerTab playerTab;
    private VideoTab videoTab;
    private JTabbedPane tabs;

    public MainWindow() {
        super("AsciiStudio — vitalsoft");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 760);
        setMinimumSize(new Dimension(800, 560));
        setLocationRelativeTo(null);
        applyDarkFrame();
        buildContent();
    }

    private void applyDarkFrame() {
        try {
            UIManager.put("TabbedPane.background", Theme.BG);
            UIManager.put("TabbedPane.foreground", Theme.FG);
            UIManager.put("TabbedPane.selected", Theme.CONTROL_BG);
            UIManager.put("TabbedPane.contentAreaColor", Theme.BG);
            UIManager.put("Panel.background", Theme.BG);
            UIManager.put("ScrollPane.background", Theme.BG);
            UIManager.put("ScrollBar.background", Theme.FIELD_BG);
            UIManager.put("ScrollBar.thumb", Theme.ACCENT_DARK);
            UIManager.put("Spinner.background", Theme.FIELD_BG);
            UIManager.put("ComboBox.background", Theme.FIELD_BG);
            UIManager.put("ProgressBar.background", Theme.FIELD_BG);
            UIManager.put("ProgressBar.foreground", Theme.ACCENT);
            UIManager.put("Slider.background", Theme.CONTROL_BG);
            UIManager.put("CheckBox.background", Theme.BG);
        } catch (Exception ignored) {}
        getContentPane().setBackground(Theme.BG);
    }

    private void buildContent() {
        tabs = new JTabbedPane();
        tabs.setBackground(Theme.BG);
        tabs.setForeground(Theme.FG);

        ImageTab imageTab = new ImageTab();
        tabs.addTab("  Image → ASCII  ", imageTab);

        playerTab = new PlayerTab();
        tabs.addTab("  ASCII Player  ", playerTab);

        videoTab = new VideoTab(() -> {
            if (videoTab.getLastOutputDir() != null) {
                playerTab.openDir(videoTab.getLastOutputDir());
                tabs.setSelectedIndex(1);
            }
        });
        tabs.addTab("  Video → ASCII  ", videoTab);

        JPanel about = buildAboutPanel();
        tabs.addTab("  About  ", about);

        add(tabs);
    }

    private JPanel buildAboutPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Theme.BG);
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setBackground(Theme.BG);
        inner.setBorder(BorderFactory.createEmptyBorder(40, 60, 40, 60));

        JLabel title = new JLabel("AsciiStudio");
        title.setFont(new Font(Font.MONOSPACED, Font.BOLD, 28));
        title.setForeground(Theme.ACCENT);
        title.setAlignmentX(0.5f);
        inner.add(title);
        inner.add(Box.createVerticalStrut(4));

        JLabel sub = new JLabel("by vitalsoft");
        sub.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        sub.setForeground(Theme.DIM);
        sub.setAlignmentX(0.5f);
        inner.add(sub);
        inner.add(Box.createVerticalStrut(24));

        String[] features = {
            "Image → ASCII  —  plain text or true-colour render",
            "Colour ASCII Mode  —  per-character RGB colouring via Java2D",
            "HTML colour export  —  inline-styled span per character",
            "Video → ASCII  —  FFmpeg frame extraction at any FPS",
            "ASCII Player  —  frame directory playback with seekbar",
            "Metadata file  —  fps, audio source, mode, frame count",
            "Audio extraction  —  MP3 sidecar alongside frame directory",
            "Detailed / simple charset toggle, brightness invert",
        };
        for (String f : features) {
            JLabel l = new JLabel("◆  " + f);
            l.setForeground(Theme.FG);
            l.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            inner.add(l);
            inner.add(Box.createVerticalStrut(6));
        }
        inner.add(Box.createVerticalStrut(20));
        JLabel req = new JLabel("Requires FFmpeg in PATH for video features");
        req.setForeground(Theme.DIM);
        req.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        inner.add(req);

        p.add(inner);
        return p;
    }
}

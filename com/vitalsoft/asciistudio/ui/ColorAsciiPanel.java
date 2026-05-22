package com.vitalsoft.asciistudio.ui;

import com.vitalsoft.asciistudio.core.AsciiConverter;
import com.vitalsoft.asciistudio.core.AsciiConverter.PixelChar;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class ColorAsciiPanel extends JPanel {

    private PixelChar[][] grid;
    private int charW = 7;
    private int charH = 12;
    private Font monoFont;
    private boolean darkBg = true;

    public ColorAsciiPanel() {
        setBackground(Color.BLACK);
        monoFont = new Font(Font.MONOSPACED, Font.BOLD, 9);
    }

    public void setGrid(PixelChar[][] grid) {
        this.grid = grid;
        if (grid != null && grid.length > 0) {
            int cols = grid[0].length;
            int rows = grid.length;
            FontMetrics fm = getFontMetrics(monoFont);
            if (fm != null) {
                charW = fm.charWidth('@');
                charH = fm.getHeight();
            }
            setPreferredSize(new Dimension(cols * charW, rows * charH));
        }
        repaint();
    }

    public void setFontSize(int size) {
        monoFont = new Font(Font.MONOSPACED, Font.BOLD, size);
        repaint();
    }

    public void setDarkBg(boolean dark) {
        darkBg = dark;
        setBackground(dark ? Color.BLACK : Color.WHITE);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (grid == null) return;
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(monoFont);
        FontMetrics fm = g2.getFontMetrics();
        charW = fm.charWidth('@');
        charH = fm.getHeight();
        int baseline = fm.getAscent();
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                PixelChar pc = grid[row][col];
                g2.setColor(pc.color());
                g2.drawString(String.valueOf(pc.ch()), col * charW, row * charH + baseline);
            }
        }
    }

    public BufferedImage toImage() {
        if (grid == null) return null;
        int w = grid[0].length * charW;
        int h = grid.length * charH;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(darkBg ? Color.BLACK : Color.WHITE);
        g2.fillRect(0, 0, w, h);
        g2.setFont(monoFont);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        FontMetrics fm = g2.getFontMetrics();
        int cw = fm.charWidth('@');
        int ch = fm.getHeight();
        int baseline = fm.getAscent();
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                PixelChar pc = grid[row][col];
                g2.setColor(pc.color());
                g2.drawString(String.valueOf(pc.ch()), col * cw, row * ch + baseline);
            }
        }
        g2.dispose();
        return img;
    }
}

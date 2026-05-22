package com.vitalsoft.asciistudio.core;

import com.vitalsoft.asciistudio.core.AsciiColorFrame;
import java.awt.*;
import java.awt.image.BufferedImage;

public class AsciiConverter {

    public enum Mode { PLAIN, COLOR_HTML, COLOR_ANSI, COLOR_ACF }

    public static final String PRESET_DETAILED =
        "$@B%8&WM#*oahkbdpqwmZO0QLCJUYXzcvunxrjft/\\|()1{}[]?-_+~<>i!lI;:,\"^`'. ";
    public static final String PRESET_SIMPLE =
        "@#S%?*+;:,. ";
    public static final String PRESET_BLOCKS =
        "█▓▒░ ";
    public static final String PRESET_HEX =
        "0123456789ABCDEFabcdef ";
    public static final String PRESET_BINARY =
        "10 ";

    private int cols;
    private int rows;
    private boolean invertBrightness;
    private boolean detailedCharset;
    private float fontAspect = 0.55f;
    private String customDensity = null; // if set, overrides detailed/simple

    public AsciiConverter(int cols, int rows, boolean invertBrightness, boolean detailedCharset) {
        this.cols = cols;
        this.rows = rows;
        this.invertBrightness = invertBrightness;
        this.detailedCharset = detailedCharset;
    }

    public void setCols(int cols) { this.cols = cols; }
    public void setRows(int rows) { this.rows = rows; }
    public void setInvert(boolean v) { this.invertBrightness = v; }
    public void setDetailed(boolean v) { this.detailedCharset = v; }
    public void setFontAspect(float v) { this.fontAspect = v; }
    public void setCustomDensity(String density) {
        this.customDensity = (density != null && !density.trim().isEmpty()) ? density : null;
    }
    public String getCustomDensity() { return customDensity; }
    public int getCols() { return cols; }
    public int getRows() { return rows; }

    private String getCharset() {
        if (customDensity != null) return customDensity;
        return detailedCharset ? PRESET_DETAILED : PRESET_SIMPLE;
    }

    private char brightnessToChar(float brightness) {
        String charset = getCharset();
        if (invertBrightness) brightness = 1f - brightness;
        int idx = Math.min((int)(brightness * charset.length()), charset.length() - 1);
        return charset.charAt(idx);
    }

    private float getBrightness(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f;
    }

    public BufferedImage scaleImage(BufferedImage src) {
        int targetW = cols;
        int targetH = rows;
        if (targetH <= 0) {
            double ratio = (double) src.getHeight() / src.getWidth();
            targetH = (int)(targetW * ratio * fontAspect);
        }
        BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, targetW, targetH, null);
        g.dispose();
        return scaled;
    }

    public String toPlainAscii(BufferedImage img) {
        BufferedImage scaled = scaleImage(img);
        int w = scaled.getWidth();
        int h = scaled.getHeight();
        StringBuilder sb = new StringBuilder((w + 1) * h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                sb.append(brightnessToChar(getBrightness(scaled.getRGB(x, y))));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public String toColorHtml(BufferedImage img) {
        BufferedImage scaled = scaleImage(img);
        int w = scaled.getWidth();
        int h = scaled.getHeight();
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='background:#000;font-family:monospace;font-size:8px;line-height:1;letter-spacing:0'><pre style='margin:0'>");
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = scaled.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                char c = brightnessToChar(getBrightness(rgb));
                sb.append(String.format("<span style='color:rgb(%d,%d,%d)'>%c</span>", r, g, b, c));
            }
            sb.append('\n');
        }
        sb.append("</pre></body></html>");
        return sb.toString();
    }

    public String toAnsiColor(BufferedImage img) {
        BufferedImage scaled = scaleImage(img);
        int w = scaled.getWidth();
        int h = scaled.getHeight();
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = scaled.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                char c = brightnessToChar(getBrightness(rgb));
                sb.append(String.format("\u001B[38;2;%d;%d;%dm%c", r, g, b, c));
            }
            sb.append("\u001B[0m\n");
        }
        return sb.toString();
    }

    public PixelChar[][] toPixelChars(BufferedImage img) {
        BufferedImage scaled = scaleImage(img);
        int w = scaled.getWidth();
        int h = scaled.getHeight();
        PixelChar[][] grid = new PixelChar[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = scaled.getRGB(x, y);
                grid[y][x] = new PixelChar(brightnessToChar(getBrightness(rgb)), new Color(rgb));
            }
        }
        return grid;
    }

    /** Encode this image as a compact binary AsciiColorFrame (.acf). */
    public AsciiColorFrame toAcfFrame(BufferedImage img, int fontSize) {
        return AsciiColorFrame.fromPixelChars(toPixelChars(img), getCharset(), fontSize);
    }

    public record PixelChar(char ch, Color color) {}
}

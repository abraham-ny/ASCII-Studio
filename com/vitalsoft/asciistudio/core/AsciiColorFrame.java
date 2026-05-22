package com.vitalsoft.asciistudio.core;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

/**
 * Compact binary color ASCII frame format (.acf)
 *
 * File layout:
 *   Header (16 bytes):
 *     [0..1]  magic  0x4143  ('AC')
 *     [2]     version  0x01
 *     [3]     flags    0x00 (reserved)
 *     [4..5]  cols  (uint16 BE)
 *     [6..7]  rows  (uint16 BE)
 *     [8..9]  fontSize (uint16 BE)
 *     [10..15] reserved zeros
 *
 *   Cell data: cols * rows cells, each 4 bytes:
 *     [0]    char index into charset (uint8, 0-255)
 *     [1]    R  (uint8)
 *     [2]    G  (uint8)
 *     [3]    B  (uint8)
 *
 * Total size: 16 + cols*rows*4 bytes
 * e.g. 200×112 frame = 16 + 89600 = ~87 KB  vs ~3.5 MB for equivalent HTML
 *
 * The charset used during conversion is NOT embedded — the player uses
 * whatever density the metadata specifies (or the default detailed preset).
 * The char index is the raw charset index, so display only needs the same
 * charset string to reconstruct characters exactly.
 */
public class AsciiColorFrame {

    public static final String EXT     = ".acf";
    public static final short  MAGIC   = 0x4143;
    public static final byte   VERSION = 0x01;

    public final int   cols;
    public final int   rows;
    public final int   fontSize;
    public final byte[] charIndex; // cols*rows bytes
    public final byte[] r;         // cols*rows bytes
    public final byte[] g;
    public final byte[] b;

    public AsciiColorFrame(int cols, int rows, int fontSize,
                           byte[] charIndex, byte[] r, byte[] g, byte[] b) {
        this.cols      = cols;
        this.rows      = rows;
        this.fontSize  = fontSize;
        this.charIndex = charIndex;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    /** Encode a pixel grid directly from converter output. */
    public static AsciiColorFrame fromPixelChars(AsciiConverter.PixelChar[][] grid,
                                                  String charset, int fontSize) {
        int rows = grid.length;
        int cols = rows > 0 ? grid[0].length : 0;
        int n    = cols * rows;
        byte[] ci = new byte[n];
        byte[] ra = new byte[n];
        byte[] ga = new byte[n];
        byte[] ba = new byte[n];
        int idx = 0;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                AsciiConverter.PixelChar pc = grid[y][x];
                int charPos = charset.indexOf(pc.ch());
                if (charPos < 0) charPos = charset.length() - 1;
                ci[idx] = (byte)(charPos & 0xFF);
                ra[idx] = (byte)(pc.color().getRed());
                ga[idx] = (byte)(pc.color().getGreen());
                ba[idx] = (byte)(pc.color().getBlue());
                idx++;
            }
        }
        return new AsciiColorFrame(cols, rows, fontSize, ci, ra, ga, ba);
    }

    /** Write to a file. */
    public void write(File f) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(f), 65536))) {
            dos.writeShort(MAGIC);
            dos.writeByte(VERSION);
            dos.writeByte(0);
            dos.writeShort(cols);
            dos.writeShort(rows);
            dos.writeShort(fontSize);
            dos.writeLong(0); // 8 reserved bytes
            int n = cols * rows;
            for (int i = 0; i < n; i++) {
                dos.writeByte(charIndex[i]);
                dos.writeByte(r[i]);
                dos.writeByte(g[i]);
                dos.writeByte(b[i]);
            }
        }
    }

    /** Read from a file. */
    public static AsciiColorFrame read(File f) throws IOException {
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(f), 65536))) {
            short magic = dis.readShort();
            if (magic != MAGIC) throw new IOException("Not an ACF file: " + f.getName());
            dis.readByte(); // version
            dis.readByte(); // flags
            int cols     = dis.readUnsignedShort();
            int rows     = dis.readUnsignedShort();
            int fontSize = dis.readUnsignedShort();
            dis.readLong(); // reserved
            int n = cols * rows;
            byte[] ci = new byte[n];
            byte[] ra = new byte[n];
            byte[] ga = new byte[n];
            byte[] ba = new byte[n];
            for (int i = 0; i < n; i++) {
                ci[i] = dis.readByte();
                ra[i] = dis.readByte();
                ga[i] = dis.readByte();
                ba[i] = dis.readByte();
            }
            return new AsciiColorFrame(cols, rows, fontSize, ci, ra, ga, ba);
        }
    }

    /** Reconstruct PixelChar grid for rendering. */
    public AsciiConverter.PixelChar[][] toPixelChars(String charset) {
        AsciiConverter.PixelChar[][] grid = new AsciiConverter.PixelChar[rows][cols];
        int idx = 0;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int ci = charIndex[idx] & 0xFF;
                if (ci >= charset.length()) ci = charset.length() - 1;
                char ch = charset.charAt(ci);
                Color color = new Color(r[idx] & 0xFF, g[idx] & 0xFF, b[idx] & 0xFF);
                grid[y][x] = new AsciiConverter.PixelChar(ch, color);
                idx++;
            }
        }
        return grid;
    }

    /**
     * Render this frame to a BufferedImage using Java2D.
     * The image dimensions are cols*charW x rows*charH at the stored font size.
     */
    public BufferedImage render(String charset, Font font, boolean darkBg) {
        int cw, ch, baseline;
        // Measure using a scratch canvas
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D pg = probe.createGraphics();
        pg.setFont(font);
        FontMetrics fm = pg.getFontMetrics();
        cw = fm.charWidth('W');
        ch = fm.getHeight();
        baseline = fm.getAscent();
        pg.dispose();

        int imgW = cols * cw;
        int imgH = rows * ch;
        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(darkBg ? Color.BLACK : Color.WHITE);
        g2.fillRect(0, 0, imgW, imgH);
        g2.setFont(font);

        int idx = 0;
        char[] buf = new char[1];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int ci = charIndex[idx] & 0xFF;
                if (ci >= charset.length()) ci = charset.length() - 1;
                buf[0] = charset.charAt(ci);
                g2.setColor(new Color(r[idx] & 0xFF, g[idx] & 0xFF, b[idx] & 0xFF));
                g2.drawChars(buf, 0, 1, col * cw, row * ch + baseline);
                idx++;
            }
        }
        g2.dispose();
        return img;
    }
}

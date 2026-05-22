package com.vitalsoft.asciistudio.core;

/**
 * Calculates optimal ASCII conversion parameters from pixel dimensions.
 *
 * Strategy:
 *  - A monospaced char at a given font size has a known pixel footprint (charW x charH).
 *  - We target a render area ≈ the source dimensions so the output "fills" a display
 *    without becoming unreadably tiny or absurdly coarse.
 *  - Smaller font → more cols/rows → higher fidelity, at the cost of needing a larger canvas.
 *  - We pick the smallest font that keeps cols within a sane maximum (≤320 for images, ≤240 for
 *    video where per-frame file size matters), then derive rows from the aspect ratio.
 */
public class DimensionCalculator {

    public record Params(int cols, int rows, int fontSize, float fontAspect) {
        /** Rendered pixel width of the ASCII art at this font size */
        public int renderWidth()  { return cols * charWidth(fontSize); }
        /** Rendered pixel height of the ASCII art at this font size */
        public int renderHeight() { return rows * charHeight(fontSize); }
    }

    // Empirical monospaced char dimensions for Font.MONOSPACED PLAIN at various sizes.
    // charWidth  ≈ fontSize * 0.60
    // charHeight ≈ fontSize * 1.20  (includes leading)
    public static int charWidth(int fontSize)  { return Math.max(1, Math.round(fontSize * 0.60f)); }
    public static int charHeight(int fontSize) { return Math.max(1, Math.round(fontSize * 1.20f)); }

    /** The aspect correction needed because chars are taller than they are wide. */
    public static float fontAspect(int fontSize) {
        return (float) charWidth(fontSize) / charHeight(fontSize);
    }

    /**
     * For images: pick the smallest font that keeps cols ≤ maxCols,
     * while ensuring the rendered output is at least minRenderPx wide.
     *
     * @param srcW      source pixel width
     * @param srcH      source pixel height
     * @param maxCols   hard ceiling on columns (quality / performance cap)
     */
    public static Params forImage(int srcW, int srcH, int maxCols) {
        // Try each font size from smallest (best quality) upward until cols ≤ maxCols
        int[] candidates = {5, 6, 7, 8, 9, 10, 11, 12, 14, 16};
        for (int fs : candidates) {
            int cw = charWidth(fs);
            int ch = charHeight(fs);
            // How many cols fit if we map 1 source pixel → 1 char?
            // We want cols such that cols*cw ≈ srcW  →  cols = srcW / cw
            int cols = srcW / cw;
            if (cols <= maxCols) {
                float aspect = (float) cw / ch;
                int rows = Math.round(cols * ((float) srcH / srcW) * aspect);
                rows = Math.max(1, rows);
                return new Params(Math.max(1, cols), rows, fs, aspect);
            }
        }
        // Fallback: clamp to maxCols at font 16
        int fs = 16;
        float aspect = fontAspect(fs);
        int rows = Math.round(maxCols * ((float) srcH / srcW) * aspect);
        return new Params(maxCols, Math.max(1, rows), fs, aspect);
    }

    /**
     * For video: prefer slightly coarser output (fewer cols) to keep per-frame
     * file sizes small. Also takes target display width into account.
     *
     * @param srcW       source video width in pixels
     * @param srcH       source video height in pixels
     * @param maxCols    hard ceiling (typically 200 for video)
     */
    public static Params forVideo(int srcW, int srcH, int maxCols) {
        // Video frames should be high quality but file size matters.
        // Target: cols where 1 char covers ~3-4 source pixels wide.
        int[] candidates = {6, 7, 8, 9, 10, 11, 12, 14};
        for (int fs : candidates) {
            int cw = charWidth(fs);
            int ch = charHeight(fs);
            int cols = srcW / cw;
            if (cols <= maxCols) {
                float aspect = (float) cw / ch;
                int rows = Math.round(cols * ((float) srcH / srcW) * aspect);
                return new Params(Math.max(1, cols), Math.max(1, rows), fs, aspect);
            }
        }
        int fs = 14;
        float aspect = fontAspect(fs);
        int rows = Math.round(maxCols * ((float) srcH / srcW) * aspect);
        return new Params(maxCols, Math.max(1, rows), fs, aspect);
    }

    /**
     * Given explicit cols and a known source aspect ratio, compute the correct rows
     * so the output isn't stretched.
     */
    public static int autoRows(int cols, int srcW, int srcH, int fontSize) {
        float aspect = fontAspect(fontSize);
        return Math.max(1, Math.round(cols * ((float) srcH / srcW) * aspect));
    }

    /** Describe what the params mean in human-readable form. */
    public static String describe(Params p, int srcW, int srcH) {
        return String.format(
            "%dx%d chars  |  font %dpt  |  renders ~%dx%dpx  (source %dx%d)",
            p.cols(), p.rows(), p.fontSize(),
            p.renderWidth(), p.renderHeight(), srcW, srcH
        );
    }
}

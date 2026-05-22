package com.vitalsoft.asciistudio.video;

import com.vitalsoft.asciistudio.core.AsciiColorFrame;
import com.vitalsoft.asciistudio.core.AsciiConverter;
import com.vitalsoft.asciistudio.core.DimensionCalculator;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Renders ASCII frames (plain .txt, color .acf, or color .html) into a real
 * MP4 video by piping raw BGR24 frames directly to FFmpeg's stdin.
 *
 * Pipeline:
 *   Java renders each ASCII frame → BufferedImage
 *       → writes raw BGR24 bytes to FFmpeg stdin pipe
 *   FFmpeg encodes rawvideo → libx264 @ output fps
 *   If audio.mp3 exists in frame dir, a second FFmpeg pass muxes it in.
 */
public class AsciiVideoRenderer {

    public interface ProgressCallback {
        void onProgress(int done, int total, String phase, String detail);
    }

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public void cancel() { cancelled.set(true); }

    /**
     * @param frameDir   directory containing ASCII frames + meta.properties
     * @param outputFile destination .mp4
     * @param darkBg     black background (true) or white (false)
     * @param callback   progress updates
     */
    public void render(File frameDir, File outputFile, boolean darkBg,
                       ProgressCallback callback) throws Exception {
        cancelled.set(false);
        VideoMetadata meta;
        try { meta = VideoMetadata.load(frameDir); }
        catch (IOException e) {
            throw new IOException("No metadata in frame directory: " + e.getMessage());
        }

        boolean isAcf   = "COLOR_ACF".equals(meta.mode);
        boolean isHtml  = "COLOR_HTML".equals(meta.mode);
        boolean isPlain = !isAcf && !isHtml;

        String ext = isAcf ? ".acf" : (isHtml ? ".html" : ".txt");
        File[] frameFiles = frameDir.listFiles((d, n) -> n.endsWith(ext));
        if (frameFiles == null || frameFiles.length == 0)
            throw new IOException("No " + ext + " frames found in " + frameDir);
        Arrays.sort(frameFiles, Comparator.comparing(File::getName));

        int total = frameFiles.length;
        double fps = meta.fps > 0 ? meta.fps : 24.0;
        int fontSize = meta.fontSize > 0 ? meta.fontSize : 8;

        // --- Measure output image size from first frame ---
        int[] imgSize = measureFrameSize(frameFiles[0], meta, fontSize, isAcf, isPlain);
        int imgW = imgSize[0];
        int imgH = imgSize[1];

        // FFmpeg requires even dimensions for h264
        if (imgW % 2 != 0) imgW++;
        if (imgH % 2 != 0) imgH++;
        final int finalW = imgW;
        final int finalH = imgH;

        File tmpVideoNoAudio = File.createTempFile("ascii_render_", ".mp4");
        tmpVideoNoAudio.deleteOnExit();

        // Build charset and font for rendering
        String charset = (meta.density != null && !meta.density.isEmpty())
            ? meta.density : AsciiConverter.PRESET_DETAILED;
        Font font = new Font(Font.MONOSPACED, Font.BOLD, fontSize);

        // --- Phase 1: pipe raw frames to FFmpeg ---
        if (callback != null) callback.onProgress(0, total, "Rendering", "Starting FFmpeg encoder...");

        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-y",
            "-f", "rawvideo",
            "-vcodec", "rawvideo",
            "-s", finalW + "x" + finalH,
            "-pix_fmt", "bgr24",
            "-r", String.valueOf(fps),
            "-i", "pipe:0",
            "-c:v", "libx264",
            "-preset", "fast",
            "-crf", "18",
            "-pix_fmt", "yuv420p",
            tmpVideoNoAudio.getAbsolutePath()
        );
        pb.redirectErrorStream(false);
        Process ffmpeg = pb.start();

        // Drain stderr on a separate thread to avoid blocking
        StringBuilder ffmpegErr = new StringBuilder();
        Thread stderrDrain = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(ffmpeg.getErrorStream()))) {
                String line;
                while ((line = br.readLine()) != null) ffmpegErr.append(line).append('\n');
            } catch (IOException ignored) {}
        });
        stderrDrain.setDaemon(true);
        stderrDrain.start();

        OutputStream ffmpegIn = new BufferedOutputStream(ffmpeg.getOutputStream(), 1 << 20);

        try {
            for (int i = 0; i < frameFiles.length; i++) {
                if (cancelled.get()) { ffmpegIn.close(); ffmpeg.destroyForcibly(); return; }

                BufferedImage frame = renderFrame(frameFiles[i], meta, charset, font, darkBg,
                                                   fontSize, isAcf, isPlain, finalW, finalH);
                writeBgr24(frame, ffmpegIn);

                if (callback != null) {
                    int pct = (int)((i + 1) * 100.0 / total);
                    callback.onProgress(i + 1, total, "Rendering",
                        "Frame " + (i + 1) + " / " + total + "  (" + pct + "%)");
                }
            }
        } finally {
            ffmpegIn.close();
        }

        int exitCode = ffmpeg.waitFor();
        if (exitCode != 0 && !cancelled.get()) {
            throw new IOException("FFmpeg encode failed (exit " + exitCode + "):\n" + ffmpegErr);
        }

        if (cancelled.get()) { tmpVideoNoAudio.delete(); return; }

        // --- Phase 2: mux audio if available ---
        String audioSrc = (meta.audioSource != null && !meta.audioSource.isEmpty())
            ? meta.audioSource : "";
        // Also check for audio.mp3 in frameDir
        if (audioSrc.isEmpty()) {
            File localAudio = new File(frameDir, "audio.mp3");
            if (localAudio.exists()) audioSrc = localAudio.getAbsolutePath();
        }

        if (!audioSrc.isEmpty() && new File(audioSrc).exists()) {
            if (callback != null) callback.onProgress(total, total, "Muxing", "Adding audio track...");
            ProcessBuilder mux = new ProcessBuilder(
                "ffmpeg", "-y",
                "-i", tmpVideoNoAudio.getAbsolutePath(),
                "-i", audioSrc,
                "-c:v", "copy",
                "-c:a", "aac",
                "-shortest",
                outputFile.getAbsolutePath()
            );
            mux.redirectErrorStream(true);
            Process muxProc = mux.start();
            muxProc.getInputStream().transferTo(OutputStream.nullOutputStream());
            int muxExit = muxProc.waitFor();
            tmpVideoNoAudio.delete();
            if (muxExit != 0) throw new IOException("FFmpeg mux failed (exit " + muxExit + ")");
        } else {
            Files.move(tmpVideoNoAudio.toPath(), outputFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        if (callback != null)
            callback.onProgress(total, total, "Done", "Written: " + outputFile.getName());
    }

    private int[] measureFrameSize(File firstFrame, VideoMetadata meta, int fontSize,
                                    boolean isAcf, boolean isPlain) throws IOException {
        if (isAcf) {
            AsciiColorFrame f = AsciiColorFrame.read(firstFrame);
            // Measure char dims
            BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            Graphics2D pg = probe.createGraphics();
            pg.setFont(new Font(Font.MONOSPACED, Font.BOLD, fontSize));
            FontMetrics fm = pg.getFontMetrics();
            int cw = fm.charWidth('W');
            int ch = fm.getHeight();
            pg.dispose();
            return new int[]{f.cols * cw, f.rows * ch};
        } else {
            // Plain text: count cols/rows from metadata
            int cols = meta.cols > 0 ? meta.cols : 120;
            int rows = meta.rows > 0 ? meta.rows : 60;
            BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            Graphics2D pg = probe.createGraphics();
            pg.setFont(new Font(Font.MONOSPACED, Font.PLAIN, fontSize));
            FontMetrics fm = pg.getFontMetrics();
            int cw = fm.charWidth('W');
            int ch = fm.getHeight();
            pg.dispose();
            return new int[]{cols * cw, rows * ch};
        }
    }

    private BufferedImage renderFrame(File frameFile, VideoMetadata meta,
                                       String charset, Font font, boolean darkBg,
                                       int fontSize, boolean isAcf, boolean isPlain,
                                       int finalW, int finalH) throws IOException {
        BufferedImage img;
        if (isAcf) {
            AsciiColorFrame frame = AsciiColorFrame.read(frameFile);
            img = frame.render(charset, font, darkBg);
        } else {
            // Plain text: render green-on-black monochrome
            String text = Files.readString(frameFile.toPath());
            String[] lines = text.split("\n");
            img = new BufferedImage(finalW, finalH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(darkBg ? Color.BLACK : Color.WHITE);
            g2.fillRect(0, 0, finalW, finalH);
            g2.setFont(font.deriveFont(Font.PLAIN, fontSize));
            FontMetrics fm = g2.getFontMetrics();
            int ch = fm.getHeight();
            int baseline = fm.getAscent();
            Color fg = darkBg ? new Color(0x33FF66) : Color.BLACK;
            g2.setColor(fg);
            for (int row = 0; row < lines.length && row * ch < finalH; row++) {
                g2.drawString(lines[row], 0, row * ch + baseline);
            }
            g2.dispose();
        }

        // Ensure output matches FFmpeg's expected dimensions
        if (img.getWidth() != finalW || img.getHeight() != finalH) {
            BufferedImage resized = new BufferedImage(finalW, finalH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.drawImage(img, 0, 0, finalW, finalH, null);
            g.dispose();
            img = resized;
        }
        return img;
    }

    /** Write a BufferedImage as raw BGR24 bytes to a stream. */
    private void writeBgr24(BufferedImage img, OutputStream out) throws IOException {
        int w = img.getWidth();
        int h = img.getHeight();
        byte[] row = new byte[w * 3];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                row[x * 3]     = (byte)(rgb & 0xFF);         // B
                row[x * 3 + 1] = (byte)((rgb >> 8) & 0xFF);  // G
                row[x * 3 + 2] = (byte)((rgb >> 16) & 0xFF); // R
            }
            out.write(row);
        }
    }
}

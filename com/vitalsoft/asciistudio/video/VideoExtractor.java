package com.vitalsoft.asciistudio.video;

import com.vitalsoft.asciistudio.core.AsciiConverter;
import com.vitalsoft.asciistudio.core.AsciiColorFrame;
import com.vitalsoft.asciistudio.core.DimensionCalculator;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoExtractor {

    public interface ProgressCallback {
        void onProgress(int framesDone, int totalEstimate, String status);
    }

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public void cancel() { cancelled.set(true); }

    public static boolean isFFmpegAvailable() {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version").start();
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns [width, height] or [-1, -1] on failure. */
    public static int[] probeVideoDimensions(File videoFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "ffprobe", "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height",
                "-of", "csv=s=x:p=0",
                videoFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            String[] parts = out.split("x");
            if (parts.length == 2) {
                return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
            }
        } catch (Exception ignored) {}
        return new int[]{-1, -1};
    }

    /** Returns native fps or -1 on failure. */
    public static double probeVideoFps(File videoFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "ffprobe", "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=r_frame_rate",
                "-of", "default=noprint_wrappers=1:nokey=1",
                videoFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            // r_frame_rate is often "25/1" or "30000/1001"
            if (out.contains("/")) {
                String[] parts = out.split("/");
                double num = Double.parseDouble(parts[0].trim());
                double den = Double.parseDouble(parts[1].trim());
                if (den != 0) return num / den;
            } else {
                return Double.parseDouble(out);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    public static double probeVideoDuration(File videoFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "ffprobe", "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", videoFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            return Double.parseDouble(out);
        } catch (Exception e) {
            return -1;
        }
    }

    public static String extractAudio(File videoFile, File outputDir) throws IOException, InterruptedException {
        String audioOut = new File(outputDir, "audio.mp3").getAbsolutePath();
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-y", "-i", videoFile.getAbsolutePath(),
            "-q:a", "0", "-map", "a", audioOut
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getInputStream().transferTo(OutputStream.nullOutputStream());
        p.waitFor();
        File f = new File(audioOut);
        return f.exists() ? audioOut : "";
    }

    public void extractFrames(
        File videoFile,
        File outputDir,
        double fps,
        int cols,
        int rows,
        AsciiConverter.Mode mode,
        boolean invert,
        String density,
        int fontSize,
        ProgressCallback progress
    ) throws Exception {
        cancelled.set(false);
        outputDir.mkdirs();

        double duration = probeVideoDuration(videoFile);
        int totalEstimate = duration > 0 ? (int)(duration * fps) : -1;

        File tempFrameDir = new File(outputDir, ".tmp_frames");
        tempFrameDir.mkdirs();

        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-y", "-i", videoFile.getAbsolutePath(),
            "-vf", "fps=" + fps,
            new File(tempFrameDir, "%06d.png").getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process ffmpeg = pb.start();
        ffmpeg.getInputStream().transferTo(OutputStream.nullOutputStream());
        ffmpeg.waitFor();

        if (cancelled.get()) { deleteDir(tempFrameDir); return; }

        File[] pngFiles = tempFrameDir.listFiles((d, name) -> name.endsWith(".png"));
        if (pngFiles == null || pngFiles.length == 0) {
            deleteDir(tempFrameDir);
            throw new IOException("FFmpeg produced no frames. Is the video file valid?");
        }
        Arrays.sort(pngFiles, java.util.Comparator.comparing(File::getName));

        // Build converter — use actual image dims for fontAspect
        AsciiConverter converter = new AsciiConverter(cols, rows, invert, true);
        converter.setFontAspect(DimensionCalculator.fontAspect(fontSize));
        if (density != null && !density.trim().isEmpty()) {
            converter.setCustomDensity(density);
        }

        for (int i = 0; i < pngFiles.length; i++) {
            if (cancelled.get()) break;

            BufferedImage img = ImageIO.read(pngFiles[i]);
            if (img == null) continue;

            if (mode == AsciiConverter.Mode.COLOR_ACF) {
                AsciiColorFrame frame = converter.toAcfFrame(img, fontSize);
                File outFile = new File(outputDir, String.format("%06d%s", i + 1, AsciiColorFrame.EXT));
                frame.write(outFile);
            } else if (mode == AsciiConverter.Mode.COLOR_HTML) {
                String content = converter.toColorHtml(img);
                File outFile = new File(outputDir, String.format("%06d.html", i + 1));
                try (FileWriter fw = new FileWriter(outFile)) { fw.write(content); }
            } else {
                String content = converter.toPlainAscii(img);
                File outFile = new File(outputDir, String.format("%06d.txt", i + 1));
                try (FileWriter fw = new FileWriter(outFile)) { fw.write(content); }
            }

            if (progress != null) {
                progress.onProgress(i + 1,
                    totalEstimate > 0 ? totalEstimate : pngFiles.length,
                    "Converting frame " + (i + 1) + " / " + pngFiles.length);
            }
        }
        deleteDir(tempFrameDir);
    }

    private void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
        dir.delete();
    }
}

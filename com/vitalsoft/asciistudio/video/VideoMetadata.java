package com.vitalsoft.asciistudio.video;

import java.io.*;
import java.util.Properties;

public class VideoMetadata {
    public static final String FILENAME = "meta.properties";

    public String videoName;
    public double fps;
    public int frameCount;
    public int cols;
    public int rows;
    public int fontSize;
    public String density;
    public String audioSource;
    public String mode;
    public long createdAt;
    public long durationMs;

    public VideoMetadata() {}

    public VideoMetadata(String videoName, double fps, int frameCount,
                         int cols, int rows, int fontSize, String density,
                         String audioSource, String mode, long durationMs) {
        this.videoName = videoName;
        this.fps = fps;
        this.frameCount = frameCount;
        this.cols = cols;
        this.rows = rows;
        this.fontSize = fontSize;
        this.density = density == null ? "" : density;
        this.audioSource = audioSource == null ? "" : audioSource;
        this.mode = mode == null ? "PLAIN" : mode;
        this.createdAt = System.currentTimeMillis();
        this.durationMs = durationMs;
    }

    public void save(File dir) throws IOException {
        Properties p = new Properties();
        p.setProperty("videoName", videoName == null ? "" : videoName);
        p.setProperty("fps", String.valueOf(fps));
        p.setProperty("frameCount", String.valueOf(frameCount));
        p.setProperty("cols", String.valueOf(cols));
        p.setProperty("rows", String.valueOf(rows));
        p.setProperty("fontSize", String.valueOf(fontSize));
        p.setProperty("density", density == null ? "" : density);
        p.setProperty("audioSource", audioSource == null ? "" : audioSource);
        p.setProperty("mode", mode == null ? "PLAIN" : mode);
        p.setProperty("createdAt", String.valueOf(createdAt));
        p.setProperty("durationMs", String.valueOf(durationMs));
        try (FileWriter fw = new FileWriter(new File(dir, FILENAME))) {
            p.store(fw, "AsciiStudio Video Metadata");
        }
    }

    public static VideoMetadata load(File dir) throws IOException {
        File f = new File(dir, FILENAME);
        if (!f.exists()) throw new IOException("No metadata file in: " + dir.getAbsolutePath());
        Properties p = new Properties();
        try (FileReader fr = new FileReader(f)) {
            p.load(fr);
        }
        VideoMetadata m = new VideoMetadata();
        m.videoName    = p.getProperty("videoName", "");
        m.fps          = Double.parseDouble(p.getProperty("fps", "24"));
        m.frameCount   = Integer.parseInt(p.getProperty("frameCount", "0"));
        m.cols         = Integer.parseInt(p.getProperty("cols", "120"));
        m.rows         = Integer.parseInt(p.getProperty("rows", "0"));
        m.fontSize     = Integer.parseInt(p.getProperty("fontSize", "8"));
        m.density      = p.getProperty("density", "");
        m.audioSource  = p.getProperty("audioSource", "");
        m.mode         = p.getProperty("mode", "PLAIN");
        m.createdAt    = Long.parseLong(p.getProperty("createdAt", "0"));
        m.durationMs   = Long.parseLong(p.getProperty("durationMs", "0"));
        return m;
    }

    public long frameDelay() {
        return (long)(1000.0 / fps);
    }
}

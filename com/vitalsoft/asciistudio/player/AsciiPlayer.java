package com.vitalsoft.asciistudio.player;

import com.vitalsoft.asciistudio.core.AsciiColorFrame;
import com.vitalsoft.asciistudio.core.AsciiConverter;
import com.vitalsoft.asciistudio.video.VideoMetadata;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class AsciiPlayer {

    public enum State { STOPPED, PLAYING, PAUSED }
    public enum FrameType { PLAIN, HTML, ACF }

    /** What the player delivers per frame. */
    public static class FrameData {
        public final int index;
        public final int total;
        public final FrameType type;
        // plain / html
        public final String text;
        // acf
        public final AsciiColorFrame acf;
        public final String charset;

        public FrameData(int idx, int total, String text, boolean isHtml) {
            this.index = idx; this.total = total;
            this.type  = isHtml ? FrameType.HTML : FrameType.PLAIN;
            this.text  = text; this.acf = null; this.charset = null;
        }
        public FrameData(int idx, int total, AsciiColorFrame acf, String charset) {
            this.index = idx; this.total = total;
            this.type  = FrameType.ACF;
            this.text  = null; this.acf = acf; this.charset = charset;
        }
    }

    public interface FrameListener {
        void onFrame(FrameData frame);
        void onStateChange(State state);
        void onEnd();
    }

    private File directory;
    private VideoMetadata metadata;
    private List<File> frames = new ArrayList<>();
    private int currentFrame = 0;
    private State state = State.STOPPED;
    private Thread playThread;
    private FrameListener listener;
    private FrameType frameType;
    private String charset;

    public AsciiPlayer(FrameListener listener) {
        this.listener = listener;
    }

    public boolean load(File dir) {
        directory = dir;
        frames.clear();
        currentFrame = 0;
        state = State.STOPPED;
        try {
            metadata = VideoMetadata.load(dir);
        } catch (IOException e) {
            metadata = new VideoMetadata("Unknown", 24, 0, 120, 0, 8, "", "", "PLAIN", 0);
        }

        charset = (metadata.density != null && !metadata.density.isEmpty())
            ? metadata.density : AsciiConverter.PRESET_DETAILED;

        // Determine frame type from mode
        if ("COLOR_ACF".equals(metadata.mode)) {
            frameType = FrameType.ACF;
        } else if ("COLOR_HTML".equals(metadata.mode)) {
            frameType = FrameType.HTML;
        } else {
            frameType = FrameType.PLAIN;
        }

        String ext = switch (frameType) {
            case ACF  -> ".acf";
            case HTML -> ".html";
            default   -> ".txt";
        };

        File[] files = dir.listFiles((d, name) -> name.endsWith(ext));
        // Fallback: detect from any frame files present
        if (files == null || files.length == 0) {
            files = dir.listFiles((d, name) ->
                name.endsWith(".acf") || name.endsWith(".txt") || name.endsWith(".html"));
            if (files != null && files.length > 0) {
                String firstName = files[0].getName();
                if (firstName.endsWith(".acf"))  frameType = FrameType.ACF;
                else if (firstName.endsWith(".html")) frameType = FrameType.HTML;
                else frameType = FrameType.PLAIN;
            }
        }
        if (files == null || files.length == 0) return false;

        Arrays.sort(files, Comparator.comparing(File::getName));
        frames.addAll(Arrays.asList(files));
        metadata.frameCount = frames.size();
        if (listener != null) listener.onStateChange(State.STOPPED);
        showFrame(0);
        return true;
    }

    private void showFrame(int idx) {
        if (idx < 0 || idx >= frames.size()) return;
        currentFrame = idx;
        try {
            FrameData fd = loadFrame(frames.get(idx), idx);
            if (listener != null) listener.onFrame(fd);
        } catch (IOException ignored) {}
    }

    private FrameData loadFrame(File f, int idx) throws IOException {
        return switch (frameType) {
            case ACF  -> new FrameData(idx, frames.size(),
                             AsciiColorFrame.read(f), charset);
            case HTML -> new FrameData(idx, frames.size(),
                             Files.readString(f.toPath()), true);
            default   -> new FrameData(idx, frames.size(),
                             Files.readString(f.toPath()), false);
        };
    }

    public void play() {
        if (state == State.PLAYING) return;
        if (state == State.STOPPED && currentFrame >= frames.size() - 1) currentFrame = 0;
        state = State.PLAYING;
        if (listener != null) listener.onStateChange(state);
        playThread = new Thread(() -> {
            long delay = metadata.frameDelay();
            while (state == State.PLAYING && currentFrame < frames.size()) {
                showFrame(currentFrame);
                currentFrame++;
                try { Thread.sleep(delay); } catch (InterruptedException e) { break; }
            }
            if (state == State.PLAYING) {
                state = State.STOPPED;
                if (listener != null) { listener.onStateChange(state); listener.onEnd(); }
            }
        });
        playThread.setDaemon(true);
        playThread.start();
    }

    public void pause() {
        if (state != State.PLAYING) return;
        state = State.PAUSED;
        if (playThread != null) playThread.interrupt();
        if (listener != null) listener.onStateChange(state);
    }

    public void stop() {
        state = State.STOPPED;
        if (playThread != null) playThread.interrupt();
        currentFrame = 0;
        if (listener != null) listener.onStateChange(state);
        if (!frames.isEmpty()) showFrame(0);
    }

    public void stepForward() { if (currentFrame < frames.size()-1) showFrame(++currentFrame); }
    public void stepBack()    { if (currentFrame > 0) showFrame(--currentFrame); }
    public void seekTo(int f) { if (f >= 0 && f < frames.size()) { currentFrame = f; showFrame(f); } }

    public State getState()      { return state; }
    public int getCurrentFrame() { return currentFrame; }
    public int getFrameCount()   { return frames.size(); }
    public VideoMetadata getMetadata() { return metadata; }
    public FrameType getFrameType()    { return frameType; }
    public boolean isLoaded()    { return !frames.isEmpty(); }
}

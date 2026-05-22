# AsciiStudio

**by [vitalsoft](https://github.com/abraham-ny)**

A desktop Java application for converting images and videos to ASCII art — with plain text, colour-rendered, and compact binary colour modes.

## Features

- **Image → ASCII** — convert any image to plain or colour ASCII art
  - Three output modes: plain text, colour render (Java2D per-char RGB), colour HTML
  - Density presets: Detailed (70 chars), Simple, Blocks, Hex, Binary, or fully custom
  - Smart autofill of cols/rows/font size from image dimensions
  - Export as `.txt`, `.html`, or **rendered PNG/JPG** image
  - Copy plain/HTML text to clipboard

- **Video → ASCII** — extract and convert video frames via FFmpeg
  - Three frame storage modes: plain `.txt`, colour `.acf` (compact binary, ~40× smaller than HTML), colour `.html`
  - Per-directory `meta.properties` storing fps, cols, rows, font size, density, audio source
  - Optional audio extraction (MP3 sidecar)
  - Autofill of all conversion parameters from probed video dimensions

- **ASCII Player** — play back any ASCII video frame directory
  - Seekbar, play/pause/stop, frame step, timestamp display
  - Loads stored font size from metadata automatically
  - Supports all three frame modes (plain, HTML, ACF)

- **Export ASCII Video** — renders ASCII frames to a real MP4
  - Pipes rendered frames directly to FFmpeg stdin as raw BGR24 — no intermediate temp files
  - Muxes in extracted audio if present
  - Full progress with phase labels (Rendering / Muxing / Done)

## Requirements

- Java 21+
- [FFmpeg](https://ffmpeg.org/) in `PATH` (for video features)

## Running

```bash
java -jar AsciiStudio.jar
```

## Building from source

```bash
bash build.sh
java -jar out/AsciiStudio.jar
```

## ACF Format

The `.acf` (ASCII Color Frame) format is a compact binary format for colour ASCII frames:

```
Header (16 bytes):
  [0..1]  magic 0x4143 ('AC')
  [2]     version 0x01
  [3]     flags (reserved)
  [4..5]  cols (uint16 BE)
  [6..7]  rows (uint16 BE)
  [8..9]  fontSize (uint16 BE)
  [10..15] reserved

Cell data: cols × rows × 4 bytes
  [0] char index into density string (uint8)
  [1] R (uint8)
  [2] G (uint8)
  [3] B (uint8)
```

A 200×112 colour frame is ~87 KB in ACF vs ~3.5 MB as HTML spans.

## License

[MIT](LICENSE)

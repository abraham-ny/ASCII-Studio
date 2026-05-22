#!/usr/bin/env bash
set -e

SRC_ROOT="src"
OUT_DIR="out"
JAR_NAME="AsciiStudio.jar"
MAIN_CLASS="com.vitalsoft.asciistudio.AsciiStudio"

echo "=== AsciiStudio Build ==="

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/classes"

find "$SRC_ROOT" -name "*.java" > /tmp/sources.txt
echo "Compiling $(wc -l < /tmp/sources.txt) source files..."
javac -d "$OUT_DIR/classes" @/tmp/sources.txt

cat > /tmp/MANIFEST.MF <<EOF
Manifest-Version: 1.0
Main-Class: $MAIN_CLASS
EOF

echo "Packaging JAR..."
jar cfm "$OUT_DIR/$JAR_NAME" /tmp/MANIFEST.MF -C "$OUT_DIR/classes" .

echo ""
echo "✔  Build complete: $OUT_DIR/$JAR_NAME"
echo "   Run with: java -jar $OUT_DIR/$JAR_NAME"

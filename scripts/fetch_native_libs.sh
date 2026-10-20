#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIBS_DIR="$SCRIPT_DIR/../libs"
mkdir -p "$LIBS_DIR"

echo "=== Fetching native libraries for Miyamizu Android ==="

# ---- soxr ----
if [ ! -d "$LIBS_DIR/soxr" ]; then
    echo "Fetching soxr..."
    TMP_DIR=$(mktemp -d)
    if ! git clone --depth 1 --branch 0.1.3 https://github.com/chirlu/soxr.git "$TMP_DIR/soxr" 2>/dev/null; then
        echo "  git clone failed, trying curl fallback..."
        curl -L --retry 3 https://sourceforge.net/projects/soxr/files/soxr-0.1.3-Source.tar.xz -o "$TMP_DIR/soxr.tar.xz"
        tar -xJf "$TMP_DIR/soxr.tar.xz" -C "$TMP_DIR"
        mv "$TMP_DIR"/soxr-0.1.3-Source "$LIBS_DIR/soxr"
    else
        mv "$TMP_DIR/soxr" "$LIBS_DIR/soxr"
    fi
    rm -rf "$TMP_DIR"
fi

# ---- r8brain-free-src ----
if [ ! -d "$LIBS_DIR/r8brain-free-src" ]; then
    echo "Fetching r8brain-free-src..."
    git clone --depth 1 https://github.com/avaneev/r8brain-free-src.git "$LIBS_DIR/r8brain-free-src"
    echo "  r8brain-free-src fetched."
fi

# ---- libflac ----
if [ ! -d "$LIBS_DIR/flac" ]; then
    echo "Fetching libflac..."
    git clone --depth 1 --branch 1.4.3 https://github.com/xiph/flac.git "$LIBS_DIR/flac"
fi

# ---- libogg (FLAC dependency) ----
if [ ! -d "$LIBS_DIR/ogg" ]; then
    echo "Fetching libogg..."
    git clone --depth 1 --branch v1.3.5 https://github.com/xiph/ogg.git "$LIBS_DIR/ogg"
fi

echo "=== All libraries fetched ==="
echo "Next: Open in Android Studio and build."

#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
LIBS_DIR="$ROOT_DIR/libs"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
mkdir -p "$LIBS_DIR"

fetch_tree() {
    local repo="$1" ref="$2" dest="$3" marker="$4" mode="$5"
    if [[ -f "$dest/$marker" ]]; then
        echo "[ok] $dest already contains $marker"
        return
    fi
    echo "Fetching $repo ($ref)..."
    git clone --depth 1 --branch "$ref" "$repo" "$TMP_DIR/checkout"
    mkdir -p "$dest"
    case "$mode" in
        c-libraries)
            [[ ! -d "$TMP_DIR/checkout/include" ]] || cp -a "$TMP_DIR/checkout/include" "$dest/"
            [[ ! -d "$TMP_DIR/checkout/src" ]] || cp -a "$TMP_DIR/checkout/src" "$dest/"
            ;;
        root-sources)
            find "$TMP_DIR/checkout" -maxdepth 1 -type f ! -name 'CMakeLists.txt' ! -name 'CMakeLists.txt.*' -exec cp -a {} "$dest/" \\;
            ;;
    esac
    rm -rf "$TMP_DIR/checkout"
}

# Existing project directories contain Android-specific CMake files but not the upstream sources.
fetch_tree https://github.com/xiph/ogg.git v1.3.5 "$LIBS_DIR/ogg" src/bitwise.c c-libraries
fetch_tree https://github.com/xiph/flac.git 1.4.3 "$LIBS_DIR/flac" src/libFLAC/stream_decoder.c c-libraries
fetch_tree https://github.com/avaneev/r8brain-free-src.git master "$LIBS_DIR/r8brain-free-src" CDSPResampler.h root-sources

# soxr upstream tags differ between mirrors; fetch the canonical repository first.
if [[ ! -f "$LIBS_DIR/soxr/src/soxr.h" ]]; then
    echo "Fetching soxr source..."
    git clone --depth 1 --branch 0.1.3 https://github.com/chirlu/soxr.git "$TMP_DIR/soxr"
    mkdir -p "$LIBS_DIR/soxr"
    cp -a "$TMP_DIR/soxr/." "$LIBS_DIR/soxr/"
    # The project's Android CMakeLists is written for the upstream src/ layout.
    cp "$LIBS_DIR/soxr/CMakeLists.txt.android" "$LIBS_DIR/soxr/src/CMakeLists.txt"
fi

echo "Native library source fetch completed. This does not imply that the NDK build succeeds."
echo "Next: ./gradlew :app:assembleDebug (after Gradle wrapper and Android SDK/NDK are available)."

#!/usr/bin/env bash
set -u
ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
echo "== Miyamizu Android build environment =="
for tool in java git cmake ninja adb; do
  if command -v "$tool" >/dev/null 2>&1; then printf '[OK] %-8s %s\n' "$tool" "$(command -v "$tool")"; else printf '[MISSING] %s\n' "$tool"; fi
done
java -version 2>&1 | head -n 3 || true
if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then echo "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"; elif [[ -n "${ANDROID_HOME:-}" ]]; then echo "ANDROID_HOME=$ANDROID_HOME"; else echo '[MISSING] ANDROID_SDK_ROOT / ANDROID_HOME'; fi
[[ -x "$ROOT_DIR/gradlew" ]] && echo '[OK] gradlew script exists' || echo '[MISSING] gradlew script and wrapper JAR (properties alone are not enough)'
[[ -f "$ROOT_DIR/libs/ogg/src/bitwise.c" ]] && echo '[OK] libogg sources present' || echo '[MISSING] libogg sources (run scripts/fetch_native_libs.sh with network access)'
[[ -f "$ROOT_DIR/libs/flac/src/libFLAC/stream_decoder.c" ]] && echo '[OK] FLAC sources present' || echo '[MISSING] FLAC sources (run scripts/fetch_native_libs.sh with network access)'
[[ -f "$ROOT_DIR/libs/soxr/src/soxr.h" ]] && echo '[OK] soxr sources present' || echo '[MISSING] soxr sources (run scripts/fetch_native_libs.sh with network access)'
[[ -f "$ROOT_DIR/libs/r8brain-free-src/CDSPResampler.h" ]] && echo '[OK] r8brain sources present' || echo '[MISSING] r8brain sources (run scripts/fetch_native_libs.sh with network access)'
echo 'Pinned targets: compileSdk 35, CMake 3.22.1, AGP 8.5.0, Gradle 8.7.'

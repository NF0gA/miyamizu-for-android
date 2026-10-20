# Miyamizu Android build status (2026-10-20)

## Changes made in this pass

- Fixed the native CMake integration for soxr so the Android-specific `libs/soxr/src/CMakeLists.txt` is used when present, with an explicit configuration error when the sources are missing.
- Removed `-mfpu=neon` from the `arm64-v8a` native target. Android AArch64 already enables Advanced SIMD and this ARM32-only option can be rejected by Clang.
- Added direct standard-library includes (`<algorithm>`, `<cstring>`, `<memory>`, `<vector>`) to `r8brain_resampler.cpp` for symbols used in that translation unit.
- Corrected the libogg include path in the custom libFLAC CMake file to be relative to that file rather than `CMAKE_SOURCE_DIR`.
- Reworked `scripts/fetch_native_libs.sh` to check for source markers rather than treating the pre-created placeholder directories as complete downloads, and to preserve project-specific CMake files when fetching FLAC, Ogg, and r8brain sources.

## Validation performed

- `bash -n scripts/fetch_native_libs.sh`: passed.
- Confirmed Java 21 is present.
- Confirmed Gradle, `gradlew`, Android SDK tools, and NDK are not present in this environment.
- Attempted to reach the Gradle distribution host and GitHub; DNS resolution failed (`Could not resolve host`). As a result, dependencies could not be fetched and no Gradle/CMake build was run.

## Important remaining blockers

1. No Gradle wrapper is included in this project snapshot.
2. Native upstream sources are not present in the ZIP; `libs/*` contains custom CMake files/placeholders rather than complete upstream source trees.
3. The fetch script has not been run successfully because external DNS/network access is unavailable. It must be tested in a network-enabled environment before relying on it.
4. Android compilation, APK packaging, emulator launch, USB DAC output, and bit-perfect behavior remain unverified.

Do not treat this source snapshot as a build-verified release.

## Build preparation follow-up (2026-10-20)

- Added `gradle/wrapper/gradle-wrapper.properties` targeting Gradle 8.7, matching the project's Android Gradle Plugin 8.5.0 requirement.
- Added `scripts/check_android_build_env.sh` to report local prerequisites and missing native sources.
- Both shell scripts pass `bash -n`.
- Re-ran `scripts/fetch_native_libs.sh`; it still fails before fetching sources because this environment cannot resolve `github.com` (`Could not resolve host`).
- The Wrapper properties file alone is not a complete Wrapper: `gradlew`, `gradlew.bat`, and `gradle-wrapper.jar` still need to be generated/provided. Android SDK/NDK and native library sources are still absent. No APK was built.

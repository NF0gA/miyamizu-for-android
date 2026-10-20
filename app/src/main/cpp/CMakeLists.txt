cmake_minimum_required(VERSION 3.22.1)
project("miyamizu")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---- External libraries ----
set(LIBS_ROOT ${CMAKE_SOURCE_DIR}/../../../../libs)

# libogg (FLAC dependency)
add_subdirectory(${LIBS_ROOT}/ogg ${CMAKE_BINARY_DIR}/ogg)

# libflac
add_subdirectory(${LIBS_ROOT}/flac ${CMAKE_BINARY_DIR}/flac)

# soxr
# soxr's Android-specific build file lives beside the upstream source tree.
# The fetch script installs it as libs/soxr/src/CMakeLists.txt.
if(EXISTS "${LIBS_ROOT}/soxr/src/CMakeLists.txt")
    add_subdirectory(${LIBS_ROOT}/soxr/src ${CMAKE_BINARY_DIR}/soxr)
elseif(EXISTS "${LIBS_ROOT}/soxr/CMakeLists.txt")
    add_subdirectory(${LIBS_ROOT}/soxr ${CMAKE_BINARY_DIR}/soxr)
else()
    message(FATAL_ERROR "soxr sources not found. Run scripts/fetch_native_libs.sh first.")
endif()

# r8brain-free-src
add_subdirectory(${LIBS_ROOT}/r8brain-free-src ${CMAKE_BINARY_DIR}/r8brain)

# ---- Miyamizu native engine ----
add_library(miyamizu SHARED
    miyamizu_jni_bridge.cpp
    audio_output/aaudio_output.cpp
    audio_output/usb_audio_output.cpp
    audio_output/ring_buffer.cpp
    decode/flac_decoder.cpp
    resample/soxr_resampler.cpp
    resample/r8brain_resampler.cpp
    dither/tpdf_dither.cpp
    dither/noise_shaper.cpp
    usb/uac2_descriptor_parser.cpp
    usb/usb_device_manager.cpp
)

target_include_directories(miyamizu PRIVATE
    ${CMAKE_SOURCE_DIR}
    ${LIBS_ROOT}/flac/include
    ${LIBS_ROOT}/ogg/include
    ${LIBS_ROOT}/soxr/src
    ${LIBS_ROOT}/r8brain-free-src
)

find_library(LOG_LIB log)
find_library(ANDROID_LIB android)
find_library(AAUDIO_LIB aaudio)

# USB / pthread
find_library(PTHREAD_LIB pthread)

target_link_libraries(miyamizu
    flac
    ogg
    soxr
    r8brain
    ${LOG_LIB}
    ${ANDROID_LIB}
    ${AAUDIO_LIB}
    ${PTHREAD_LIB}
)

target_compile_options(miyamizu PRIVATE
    -O3
    -ffast-math
    -fPIC
    -Wall
    -Wextra
    -Werror=return-type
)

# AArch64 Android targets have Advanced SIMD (NEON) enabled by default.
# Do not pass -mfpu=neon: Clang rejects this ARM32-only flag for arm64-v8a.

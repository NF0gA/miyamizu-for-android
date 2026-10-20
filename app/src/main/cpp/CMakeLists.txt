cmake_minimum_required(VERSION 3.22.1)
project("miyamizu")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# This CMakeLists.txt is under app/src/main/cpp; four levels up is the repo root.
set(LIBS_ROOT ${CMAKE_SOURCE_DIR}/../../../../libs)

# The soxr defaults build tests, examples, SIMD variants and optional bindings.
# Pin a compact, portable Android configuration before adding its CMake project.
set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WITH_OPENMP OFF CACHE BOOL "" FORCE)
set(WITH_LSR_BINDINGS OFF CACHE BOOL "" FORCE)
set(WITH_CR32 ON CACHE BOOL "" FORCE)
set(WITH_VR32 ON CACHE BOOL "" FORCE)
set(WITH_CR32S OFF CACHE BOOL "" FORCE)
set(WITH_CR64 OFF CACHE BOOL "" FORCE)
set(WITH_CR64S OFF CACHE BOOL "" FORCE)
set(WITH_PFFFT OFF CACHE BOOL "" FORCE)

add_subdirectory(${LIBS_ROOT}/ogg ${CMAKE_CURRENT_BINARY_DIR}/ogg)
add_subdirectory(${LIBS_ROOT}/flac ${CMAKE_CURRENT_BINARY_DIR}/flac)
add_subdirectory(${LIBS_ROOT}/soxr ${CMAKE_CURRENT_BINARY_DIR}/soxr)
add_subdirectory(${LIBS_ROOT}/r8brain-free-src ${CMAKE_CURRENT_BINARY_DIR}/r8brain)

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
find_library(M_LIB m)

target_link_libraries(miyamizu
    flac
    ogg
    soxr
    r8brain
    ${LOG_LIB}
    ${ANDROID_LIB}
    ${AAUDIO_LIB}
    ${M_LIB}
)

target_compile_options(miyamizu PRIVATE
    -O3
    -ffast-math
    -fPIC
    -Wall
    -Wextra
    -Werror=return-type
)

# arm64-v8a NEON は ABI の必須機能。-mfpu=neon は32-bit ARM向けなので付けない。

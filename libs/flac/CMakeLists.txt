cmake_minimum_required(VERSION 3.22.1)
project(flac C)

set(CMAKE_C_STANDARD 11)

set(FLAC_SOURCES
    src/libFLAC/bitmath.c
    src/libFLAC/bitreader.c
    src/libFLAC/bitwriter.c
    src/libFLAC/cpu.c
    src/libFLAC/crc.c
    src/libFLAC/fixed.c
    src/libFLAC/fixed_intrin_sse2.c
    src/libFLAC/fixed_intrin_ssse3.c
    src/libFLAC/float.c
    src/libFLAC/format.c
    src/libFLAC/lpc.c
    src/libFLAC/lpc_intrin_avx2.c
    src/libFLAC/lpc_intrin_sse2.c
    src/libFLAC/lpc_intrin_sse41.c
    src/libFLAC/lpc_intrin_vsx.c
    src/libFLAC/md5.c
    src/libFLAC/memory.c
    src/libFLAC/metadata_iterators.c
    src/libFLAC/metadata_object.c
    src/libFLAC/ogg_decoder_aspect.c
    src/libFLAC/ogg_encoder_aspect.c
    src/libFLAC/ogg_helper.c
    src/libFLAC/ogg_mapping.c
    src/libFLAC/stream_decoder.c
    src/libFLAC/stream_encoder.c
    src/libFLAC/stream_encoder_framing.c
    src/libFLAC/stream_encoder_intrin_avx2.c
    src/libFLAC/stream_encoder_intrin_sse2.c
    src/libFLAC/stream_encoder_intrin_ssse3.c
    src/libFLAC/window.c
)

# Filter out architecture-specific files we don't need on Android
if(NOT ANDROID_ABI STREQUAL "x86_64")
    list(REMOVE_ITEM FLAC_SOURCES
        src/libFLAC/lpc_intrin_avx2.c
        src/libFLAC/lpc_intrin_sse2.c
        src/libFLAC/lpc_intrin_sse41.c
        src/libFLAC/stream_encoder_intrin_avx2.c
        src/libFLAC/stream_encoder_intrin_sse2.c
        src/libFLAC/stream_encoder_intrin_ssse3.c
        src/libFLAC/fixed_intrin_sse2.c
        src/libFLAC/fixed_intrin_ssse3.c
    )
endif()

if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
    list(REMOVE_ITEM FLAC_SOURCES src/libFLAC/lpc_intrin_vsx.c)
endif()

add_library(flac STATIC ${FLAC_SOURCES})

target_compile_definitions(flac PRIVATE
    FLAC__NO_DLL
    HAVE_STDINT_H
    HAVE_INTTYPES_H
    HAVE_LROUND=1
    HAVE_SYS_PARAM_H=1
    PACKAGE_VERSION="1.4.3"
    OGG_FOUND
)

target_include_directories(flac PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/src/libFLAC/include
    ${CMAKE_CURRENT_SOURCE_DIR}/../ogg/include
)

target_link_libraries(flac PUBLIC ogg)

target_compile_options(flac PRIVATE -O3 -fPIC -Wall -funroll-loops)

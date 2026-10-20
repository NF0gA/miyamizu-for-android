cmake_minimum_required(VERSION 3.22.1)
project(flac C)
set(CMAKE_C_STANDARD 11)

# Portable FLAC core. Android does not need the upstream x86/VSX intrinsic files
# for this application; leaving them out avoids selecting an ISA unavailable on
# one of the configured ABIs. Ogg encapsulation is not used by the player.
set(FLAC_SOURCES
    src/libFLAC/bitmath.c
    src/libFLAC/bitreader.c
    src/libFLAC/bitwriter.c
    src/libFLAC/cpu.c
    src/libFLAC/crc.c
    src/libFLAC/fixed.c
    src/libFLAC/float.c
    src/libFLAC/format.c
    src/libFLAC/lpc.c
    src/libFLAC/md5.c
    src/libFLAC/memory.c
    src/libFLAC/metadata_iterators.c
    src/libFLAC/metadata_object.c
    src/libFLAC/stream_decoder.c
    src/libFLAC/stream_encoder.c
    src/libFLAC/stream_encoder_framing.c
    src/libFLAC/window.c
)

add_library(flac STATIC ${FLAC_SOURCES})
target_compile_definitions(flac PRIVATE
    FLAC__NO_DLL
    FLAC__NO_ASM
    HAVE_STDINT_H=1
    HAVE_INTTYPES_H=1
    HAVE_LROUND=1
    HAVE_SYS_PARAM_H=1
    PACKAGE_VERSION="1.4.3"
)
target_include_directories(flac PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/src/libFLAC/include
)
target_link_libraries(flac PUBLIC ogg)
target_compile_options(flac PRIVATE -O3 -fPIC -Wall)

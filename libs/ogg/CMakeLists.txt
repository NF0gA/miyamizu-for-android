cmake_minimum_required(VERSION 3.22.1)
project(ogg C)

set(CMAKE_C_STANDARD 11)

set(OGG_SOURCES
    src/bitwise.c
    src/framing.c
)

add_library(ogg STATIC ${OGG_SOURCES})

target_include_directories(ogg PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_BINARY_DIR}/include
)

# Generate config_types.h for Android
file(MAKE_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}/include/ogg)
file(WRITE ${CMAKE_CURRENT_BINARY_DIR}/include/ogg/config_types.h "
#ifndef __CONFIG_TYPES_H__
#define __CONFIG_TYPES_H__

#include <stdint.h>

typedef int16_t ogg_int16_t;
typedef uint16_t ogg_uint16_t;
typedef int32_t ogg_int32_t;
typedef uint32_t ogg_uint32_t;
typedef int64_t ogg_int64_t;

#endif
")

target_compile_options(ogg PRIVATE -O3 -fPIC -Wall)

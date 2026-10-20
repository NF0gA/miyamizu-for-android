# r8brain-free-src is header-only. The old file listed several .cpp files
# that are not present in the upstream repository, causing CMake generation to fail.
add_library(r8brain INTERFACE)
target_include_directories(r8brain INTERFACE ${CMAKE_CURRENT_SOURCE_DIR})

cmake_minimum_required(VERSION 3.22.1)
project(r8brain CXX)

set(CMAKE_CXX_STANDARD 17)

# r8brain is header-only + a few .cpp files
set(R8BRAIN_SOURCES
    CDSPBlockConvolver.cpp
    CDSPFIRFilter.cpp
    CDSPFracInterpolator.cpp
    CDSPRealFFTKL.cpp
    CDSPResampler.cpp
)

add_library(r8brain STATIC ${R8BRAIN_SOURCES})

target_include_directories(r8brain PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

target_compile_options(r8brain PRIVATE -O3 -fPIC -ffast-math -Wall)

# For Android ARM64, r8brain uses KL FFT which needs no extra flags
# For x86_64, SSE2 is auto-detected by the source

#pragma once

#include <cstddef>
#include <cstdint>
#include <random>

// TPDF dither: two independent uniform random values, subtracted
class TpdfDither {
public:
    TpdfDither(int targetBits = 16);
    void process(const float* in, void* out, size_t frames, int channels);

private:
    int targetBits_;
    float quantStep_;
    std::mt19937 rng_;
    std::uniform_real_distribution<float> dist_;
};

#pragma once

#include <cstddef>
#include <vector>

// Simple 2nd-order noise shaper (feedback error diffusion)
// Shapes quantization noise to higher frequencies
class NoiseShaper {
public:
    NoiseShaper(int targetBits = 16);
    void process(const float* in, void* out, size_t frames, int channels);
    void reset();

private:
    int targetBits_;
    float quantStep_;
    std::vector<float> errorZ1_;  // previous error per channel
    std::vector<float> errorZ2_;  // 2nd previous error per channel
};

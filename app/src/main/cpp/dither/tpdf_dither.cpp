#include "tpdf_dither.h"
#include <cstring>
#include <cmath>

TpdfDither::TpdfDither(int targetBits)
    : targetBits_(targetBits),
      quantStep_(2.0f / (1 << targetBits_)),
      dist_(-0.5f, 0.5f) {
    rng_.seed(42);
}

void TpdfDither::process(const float* in, void* out, size_t frames, int channels) {
    if (targetBits_ == 32) {
        // No dither needed for 32-bit
        std::memcpy(out, in, frames * channels * sizeof(float));
        return;
    }

    if (targetBits_ == 24) {
        // 24-bit: negligible benefit from dither, but do it anyway
        int32_t* dst = static_cast<int32_t*>(out);
        float scale = static_cast<float>(1 << 23);
        for (size_t i = 0; i < frames * channels; i++) {
            float d1 = dist_(rng_);
            float d2 = dist_(rng_);
            float tpdf = d1 - d2;  // triangular PDF
            float sample = in[i] * scale + tpdf;
            if (sample > scale - 1) sample = scale - 1;
            if (sample < -scale) sample = -scale;
            dst[i] = static_cast<int32_t>(sample);
        }
    } else if (targetBits_ == 16) {
        int16_t* dst = static_cast<int16_t*>(out);
        float scale = static_cast<float>(1 << 15);
        for (size_t i = 0; i < frames * channels; i++) {
            float d1 = dist_(rng_);
            float d2 = dist_(rng_);
            float tpdf = d1 - d2;
            float sample = in[i] * scale + tpdf;
            if (sample > scale - 1) sample = scale - 1;
            if (sample < -scale) sample = -scale;
            dst[i] = static_cast<int16_t>(sample);
        }
    }
}

#include "noise_shaper.h"
#include <cstring>
#include <cmath>

NoiseShaper::NoiseShaper(int targetBits)
    : targetBits_(targetBits),
      quantStep_(2.0f / (1 << targetBits_)) {
}

void NoiseShaper::reset() {
    errorZ1_.clear();
    errorZ2_.clear();
}

void NoiseShaper::process(const float* in, void* out, size_t frames, int channels) {
    if (targetBits_ >= 24) {
        std::memcpy(out, in, frames * channels * sizeof(float));
        return;
    }

    if (errorZ1_.size() != static_cast<size_t>(channels)) {
        errorZ1_.resize(channels, 0.0f);
        errorZ2_.resize(channels, 0.0f);
    }

    if (targetBits_ == 16) {
        int16_t* dst = static_cast<int16_t*>(out);
        float scale = static_cast<float>(1 << 15);
        for (size_t f = 0; f < frames; f++) {
            for (int ch = 0; ch < channels; ch++) {
                size_t idx = f * channels + ch;
                // Feedback: add shaped error from previous samples
                float feedback = 2.0f * errorZ1_[ch] - errorZ2_[ch];
                float sample = in[idx] * scale + feedback;

                // Quantize
                int16_t quantized = static_cast<int16_t>(std::round(sample));

                // Update error history
                errorZ2_[ch] = errorZ1_[ch];
                errorZ1_[ch] = sample - static_cast<float>(quantized);

                dst[idx] = quantized;
            }
        }
    }
}

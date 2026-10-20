#pragma once

#include <soxr.h>
#include <cstddef>

enum class SoxrPhase {
    LINEAR,
    INTERMEDIATE,
    MINIMUM
};

enum class SoxrBandwidth {
    STANDARD,
    WIDE
};

class SoxrResampler {
public:
    bool init(double inRate, double outRate, int channels,
              SoxrPhase phase = SoxrPhase::LINEAR,
              SoxrBandwidth bw = SoxrBandwidth::STANDARD,
              double quality = 8);  // 8 = VHQ
    size_t process(const float* in, size_t inLen, float* out, size_t outLen);
    void drain(float* out, size_t outLen, size_t* done);
    void reset();
    ~SoxrResampler();

private:
    soxr_t resampler_ = nullptr;
};

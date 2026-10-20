#pragma once

#include <cstddef>
#include <vector>
#include <memory>

enum class R8brainPreset {
    STANDARD,   // 2% transition / 207dB / linear phase
    MIN_PHASE,  // minimum phase
    FAST,       // 10% transition / 120dB
    CUSTOM
};

struct R8brainCustomConfig {
    double transitionBand = 2.0;   // %
    double stopBandAtten = 207.0;  // dB
    bool linearPhase = true;
    double headroomDb = 0.0;
};

// r8brain-free-src wrapper
// Requires: r8brain-free-src headers + CDSPResampler.h
class R8brainResampler {
public:
    bool init(double inRate, double outRate, int channels,
              R8brainPreset preset = R8brainPreset::STANDARD,
              const R8brainCustomConfig& custom = {});
    size_t process(const float* in, size_t inLen, float* out, size_t outLen);
    void reset();
    ~R8brainResampler();

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

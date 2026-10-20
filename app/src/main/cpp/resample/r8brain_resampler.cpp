#include "r8brain_resampler.h"
#include <android/log.h>
#include <cmath>
#include <algorithm>
#include <cstring>
#include <memory>
#include <vector>

// Note: r8brain-free-src must be placed in libs/r8brain-free-src/
// and included in CMakeLists.txt
#include "../../../../../../libs/r8brain-free-src/CDSPResampler.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "MiyamizuR8", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "MiyamizuR8", __VA_ARGS__)

struct R8brainResampler::Impl {
    std::vector<std::unique_ptr<r8brain::CDSPResampler24>> resamplers;
    int channels = 2;
    double headroomDb = 0.0;
    double headroomGain = 1.0;
    std::vector<double> inBuf[2];
    std::vector<double> outBuf[2];
};

bool R8brainResampler::init(double inRate, double outRate, int channels,
                            R8brainPreset preset, const R8brainCustomConfig& custom) {
    impl_ = std::make_unique<Impl>();
    impl_->channels = channels;
    impl_->headroomDb = custom.headroomDb;
    impl_->headroomGain = std::pow(10.0, -custom.headroomDb / 20.0);

    double reqTransBand = 2.0;
    double reqAtten = 207.0;
    bool reqLinearPhase = true;

    switch (preset) {
        case R8brainPreset::STANDARD:
            reqTransBand = 2.0; reqAtten = 207.0; reqLinearPhase = true; break;
        case R8brainPreset::MIN_PHASE:
            reqTransBand = 2.0; reqAtten = 207.0; reqLinearPhase = false; break;
        case R8brainPreset::FAST:
            reqTransBand = 10.0; reqAtten = 120.0; reqLinearPhase = true; break;
        case R8brainPreset::CUSTOM:
            reqTransBand = custom.transitionBand;
            reqAtten = custom.stopBandAtten;
            reqLinearPhase = custom.linearPhase;
            break;
    }

    try {
        for (int ch = 0; ch < channels; ch++) {
            auto resampler = std::make_unique<r8brain::CDSPResampler24>(
                inRate, outRate,
                4096,  // max input buffer
                reqTransBand,
                reqAtten,
                reqLinearPhase ? 0 : 1
            );
            impl_->resamplers.push_back(std::move(resampler));
        }
    } catch (...) {
        LOGE("r8brain init failed");
        return false;
    }

    LOGI("r8brain initialized: %.0f -> %.0f Hz, ch=%d, preset=%d",
         inRate, outRate, channels, static_cast<int>(preset));
    return true;
}

size_t R8brainResampler::process(const float* in, size_t inLen, float* out, size_t outLen) {
    if (!impl_ || impl_->resamplers.empty()) return 0;

    // Deinterleave float -> double, apply headroom
    for (int ch = 0; ch < impl_->channels; ch++) {
        impl_->inBuf[ch].resize(inLen);
        for (size_t i = 0; i < inLen; i++) {
            impl_->inBuf[ch][i] = in[i * impl_->channels + ch] * impl_->headroomGain;
        }
    }

    // Process each channel
    size_t maxOut = 0;
    for (int ch = 0; ch < impl_->channels; ch++) {
        double* outPtr = nullptr;
        int written = impl_->resamplers[ch]->process(
            impl_->inBuf[ch].data(), static_cast<int>(inLen), outPtr
        );
        if (written > 0) {
            impl_->outBuf[ch].resize(written);
            std::memcpy(impl_->outBuf[ch].data(), outPtr, written * sizeof(double));
            maxOut = static_cast<size_t>(written);
        }
    }

    // Interleave double -> float
    size_t toWrite = std::min(maxOut, outLen);
    for (size_t i = 0; i < toWrite; i++) {
        for (int ch = 0; ch < impl_->channels; ch++) {
            double v = (i < impl_->outBuf[ch].size()) ? impl_->outBuf[ch][i] : 0.0;
            // Clamp
            if (v > 1.0) v = 1.0;
            if (v < -1.0) v = -1.0;
            out[i * impl_->channels + ch] = static_cast<float>(v);
        }
    }

    return toWrite;
}

void R8brainResampler::reset() {
    if (!impl_) return;
    for (auto& r : impl_->resamplers) {
        r->clear();
    }
}

R8brainResampler::~R8brainResampler() = default;

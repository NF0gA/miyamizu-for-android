#include "soxr_resampler.h"
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "MiyamizuSoxr", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "MiyamizuSoxr", __VA_ARGS__)

bool SoxrResampler::init(double inRate, double outRate, int channels,
                         SoxrPhase phase, SoxrBandwidth bw, double quality) {
    soxr_error_t err;

    soxr_quality_spec_t qspec = soxr_quality_spec(
        quality,
        SOXR_VR | SOXR_FLOAT32_I | SOXR_VHQ
    );

    // Phase setting
    switch (phase) {
        case SoxrPhase::LINEAR:
            qspec.phase = 0; break;
        case SoxrPhase::INTERMEDIATE:
            qspec.phase = 50; break;
        case SoxrPhase::MINIMUM:
            qspec.phase = -1; break;
    }

    // Bandwidth
    if (bw == SoxrBandwidth::WIDE) {
        qspec.passband_end = 0.976;
    }

    resampler_ = soxr_create(inRate, outRate, channels,
                             &err, nullptr, &qspec, nullptr);
    if (err) {
        LOGE("soxr_create failed: %s", soxr_strerror(err));
        return false;
    }
    LOGI("soxr initialized: %.0f -> %.0f Hz, ch=%d, quality=%.0f",
         inRate, outRate, channels, quality);
    return true;
}

size_t SoxrResampler::process(const float* in, size_t inLen, float* out, size_t outLen) {
    if (!resampler_) return 0;
    soxr_error_t err;
    size_t idone, odone;
    err = soxr_process(resampler_, in, inLen, &idone, out, outLen, &odone);
    if (err) {
        LOGE("soxr_process error: %s", soxr_strerror(err));
    }
    return odone;
}

void SoxrResampler::drain(float* out, size_t outLen, size_t* done) {
    if (!resampler_) { *done = 0; return; }
    soxr_error_t err;
    err = soxr_process(resampler_, nullptr, 0, nullptr, out, outLen, done);
    if (err) {
        LOGE("soxr drain error: %s", soxr_strerror(err));
    }
}

void SoxrResampler::reset() {
    if (resampler_) {
        soxr_clear(resampler_);
    }
}

SoxrResampler::~SoxrResampler() {
    if (resampler_) soxr_delete(resampler_);
}

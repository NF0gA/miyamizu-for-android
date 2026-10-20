#pragma once

#include <aaudio/AAudio.h>
#include <atomic>
#include <cstdint>
#include <vector>

struct AAudioTelemetry {
    double jitterRmsUs = 0.0;
    double jitterMeanUs = 0.0;
    uint32_t xrunCount = 0;
    uint32_t glitchCount = 0;
    bool isBitPerfect = false;
    bool isMmap = false;
    int actualSampleRate = 0;
    int32_t driftPpm = 0;  // 実際のサンプルレートドリフト (parts per million)
};

class AAudioOutput {
public:
    bool open(int sampleRate, int bitDepth, int channels);
    void close();
    bool start();
    void stop();
    void write(const float* buffer, int32_t numFrames);
    AAudioTelemetry getTelemetry() const;

private:
    AAudioStream* stream_ = nullptr;
    std::atomic<bool> running_{false};
    int channels_ = 2;
    bool isMmap_ = false;
    bool isBitPerfect_ = false;
    int actualSampleRate_ = 0;

    // Jitter計算（移動ウィンドウ標準偏差）
    static constexpr size_t JITTER_WINDOW_SIZE = 128;
    mutable std::vector<double> jitterWindow_;

    mutable uint64_t lastTimestamp_ = 0;
    mutable int64_t framePositionAtLastTimestamp_ = 0;
    mutable uint32_t glitchCount_ = 0;
    mutable uint32_t xrunCount_ = 0;
    mutable uint32_t lastXrunReported_ = 0;
    mutable int64_t startTime_ = 0;
    mutable int64_t totalFramesWritten_ = 0;

    int64_t getMonotonicTimeUs() const;
};

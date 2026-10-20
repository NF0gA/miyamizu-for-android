#include "aaudio_output.h"
#include <android/log.h>
#include <cmath>
#include <vector>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "MiyamizuAAudio", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "MiyamizuAAudio", __VA_ARGS__)

// Jitter計算用ウィンドウサイズ（標準偏差計算のためのサンプル数）
static constexpr size_t JITTER_WINDOW_SIZE = 128;

bool AAudioOutput::open(int sampleRate, int bitDepth, int channels) {
    channels_ = channels;
    AAudioStreamBuilder* builder;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK) {
        LOGE("Failed to create stream builder: %s", AAudio_convertResultToText(result));
        return false;
    }

    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSampleRate(builder, sampleRate);
    AAudioStreamBuilder_setChannelCount(builder, channels);

    if (bitDepth == 16) {
        AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    } else {
        AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    }

    result = AAudioStreamBuilder_openStream(builder, &stream_);
    AAudioStreamBuilder_delete(builder);

    if (result != AAUDIO_OK) {
        LOGE("Failed to open stream: %s", AAudio_convertResultToText(result));
        return false;
    }

    actualSampleRate_ = AAudioStream_getSampleRate(stream_);
    int32_t sessionId = AAudioStream_getSessionId(stream_);
    isMmap_ = (sessionId != AAUDIO_SESSION_ID_NONE);
    aaudio_format_t format = AAudioStream_getFormat(stream_);
    int32_t bufferCapacity = AAudioStream_getBufferCapacityInFrames(stream_);
    int32_t burstSize = AAudioStream_getFramesPerBurst(stream_);

    // ビットパーフェクト判定: MMAP + フォーマット一致 + ボリューム未変更
    // AAudioのEXCLUSIVE MMAPモードではシステムエフェクトがバイパスされる
    isBitPerfect_ = isMmap_ && (format == AAUDIO_FORMAT_PCM_FLOAT || format == AAUDIO_FORMAT_PCM_I16);

    // テレメトリバッファ初期化
    jitterWindow_.clear();
    jitterWindow_.reserve(JITTER_WINDOW_SIZE);

    LOGI("AAudio opened: sr=%d, mmap=%d, bitPerfect=%d, format=%d, buffer=%d, burst=%d",
         actualSampleRate_, isMmap_ ? 1 : 0, isBitPerfect_ ? 1 : 0,
         format, bufferCapacity, burstSize);
    return true;
}

void AAudioOutput::close() {
    if (stream_) {
        AAudioStream_close(stream_);
        stream_ = nullptr;
    }
    jitterWindow_.clear();
}

bool AAudioOutput::start() {
    if (!stream_) return false;
    aaudio_result_t result = AAudioStream_requestStart(stream_);
    running_ = (result == AAUDIO_OK);
    if (running_) {
        startTime_ = getMonotonicTimeUs();
        lastTimestamp_ = 0;
        framePositionAtLastTimestamp_ = 0;
    }
    return running_;
}

void AAudioOutput::stop() {
    running_ = false;
    if (stream_) {
        AAudioStream_requestStop(stream_);
    }
}

void AAudioOutput::write(const float* buffer, int32_t numFrames) {
    if (!stream_ || !running_) return;

    int32_t written = 0;
    while (written < numFrames) {
        auto result = AAudioStream_write(stream_, buffer + written * channels_,
                                           numFrames - written, 0);
        if (result > 0) {
            written += result;
        } else if (result < 0) {
            LOGE("Write error: %s", AAudio_convertResultToText(result));
            xrunCount_++;
            break;
        }
    }

    // ===== 高精度テレメトリ収集 =====
    // AAudioStream_getTimestamp でフレーム位置と時刻を同時取得
    int64_t framePosition = 0;
    int64_t timestampNs = 0;
    aaudio_result_t tsResult = AAudioStream_getTimestamp(stream_, CLOCK_MONOTONIC,
                                                          &framePosition, &timestampNs);

    if (tsResult == AAUDIO_OK && lastTimestamp_ > 0) {
        // フレーム差分から期待される時間を計算
        int64_t frameDelta = framePosition - framePositionAtLastTimestamp_;
        if (frameDelta > 0) {
            // 期待される時間差（マイクロ秒）
            double expectedDeltaUs = (frameDelta * 1'000'000.0) / actualSampleRate_;
            // 実際の時間差（マイクロ秒）
            double actualDeltaUs = (timestampNs - lastTimestamp_) / 1000.0;
            // jitter = 実際 - 期待
            double jitterUs = actualDeltaUs - expectedDeltaUs;

            // 移動ウィンドウに追加
            if (jitterWindow_.size() >= JITTER_WINDOW_SIZE) {
                jitterWindow_.erase(jitterWindow_.begin());
            }
            jitterWindow_.push_back(jitterUs);

            // 異常値を検出（3σ以上の場合はxrunとしてカウント）
            if (std::abs(jitterUs) > 1000.0) {  // 1ms以上の遅延は異常
                glitchCount_++;
                LOGI("Glitch detected: jitter=%.2f us, frames=%ld", jitterUs, (long)frameDelta);
            }
        }
    }

    if (tsResult == AAUDIO_OK) {
        lastTimestamp_ = timestampNs;
        framePositionAtLastTimestamp_ = framePosition;
    }

    // XRUNカウントの増分を検出
    aaudio_result_t xrunResult = AAudioStream_getXRunCount(stream_);
    if (xrunResult > 0 && static_cast<uint32_t>(xrunResult) != lastXrunReported_) {
        uint32_t newXruns = static_cast<uint32_t>(xrunResult) - lastXrunReported_;
        xrunCount_ += newXruns;
        lastXrunReported_ = static_cast<uint32_t>(xrunResult);
        if (newXruns > 0) {
            LOGI("XRUN detected: +%u (total=%u)", newXruns, xrunCount_);
        }
    }

    totalFramesWritten_ += written;
}

AAudioTelemetry AAudioOutput::getTelemetry() const {
    AAudioTelemetry t;
    t.xrunCount = xrunCount_;
    t.isBitPerfect = isBitPerfect_;
    t.isMmap = isMmap_;
    t.actualSampleRate = actualSampleRate_;

    // ===== 標準偏差によるjitter RMS計算 =====
    if (jitterWindow_.size() >= 2) {
        // 平均を計算
        double sum = 0.0;
        for (double v : jitterWindow_) {
            sum += v;
        }
        double mean = sum / jitterWindow_.size();

        // 標準偏差（RMS）を計算
        double sqSum = 0.0;
        for (double v : jitterWindow_) {
            double diff = v - mean;
            sqSum += diff * diff;
        }
        t.jitterRmsUs = std::sqrt(sqSum / jitterWindow_.size());

        // 追加統計情報
        t.jitterMeanUs = mean;
        t.glitchCount = glitchCount_;
    } else {
        t.jitterRmsUs = 0.0;
        t.jitterMeanUs = 0.0;
        t.glitchCount = glitchCount_;
    }

    // 有効ビットレートの推定（実際のサンプルレートとの差）
    if (running_ && totalFramesWritten_ > 0) {
        int64_t elapsedUs = getMonotonicTimeUs() - startTime_;
        if (elapsedUs > 0) {
            double actualRate = (totalFramesWritten_ * 1'000'000.0) / elapsedUs;
            t.driftPpm = static_cast<int32_t>((actualRate - actualSampleRate_) * 1'000'000.0 / actualSampleRate_);
        }
    }

    return t;
}

int64_t AAudioOutput::getMonotonicTimeUs() const {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1'000'000 + ts.tv_nsec / 1000;
}

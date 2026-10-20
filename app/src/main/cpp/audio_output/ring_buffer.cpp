#include "ring_buffer.h"
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "MiyamizuRing", __VA_ARGS__)

SpscFloatRing::SpscFloatRing(size_t capacityFrames, int channels)
    : capacity_(capacityFrames), mask_(capacityFrames - 1), channels_(channels) {
    // Ensure power of 2
    if ((capacityFrames & mask_) != 0) {
        LOGI("Warning: capacity not power of 2, ring buffer may malfunction");
    }
    buffer_ = new float[capacityFrames * channels_]();
}

SpscFloatRing::~SpscFloatRing() {
    delete[] buffer_;
}

size_t SpscFloatRing::write(const float* data, size_t frames) {
    const size_t writeIdx = writeIndex_.load(std::memory_order_relaxed);
    const size_t readIdx = readIndex_.load(std::memory_order_acquire);
    const size_t available = mask_ - (writeIdx - readIdx);
    // Leave 1 frame gap to distinguish full from empty
    const size_t toWrite = (frames > available - 1) ? (available > 0 ? available - 1 : 0) : frames;

    if (toWrite == 0) return 0;

    const size_t sampleCount = toWrite * channels_;
    const size_t writePos = (writeIdx & mask_) * channels_;
    const size_t contiguous = (capacity_ - (writeIdx & mask_)) * channels_;

    if (sampleCount <= contiguous) {
        std::memcpy(buffer_ + writePos, data, sampleCount * sizeof(float));
    } else {
        std::memcpy(buffer_ + writePos, data, contiguous * sizeof(float));
        std::memcpy(buffer_, data + contiguous, (sampleCount - contiguous) * sizeof(float));
    }

    writeIndex_.store(writeIdx + toWrite, std::memory_order_release);
    return toWrite;
}

size_t SpscFloatRing::read(float* out, size_t maxFrames) {
    const size_t writeIdx = writeIndex_.load(std::memory_order_acquire);
    const size_t readIdx = readIndex_.load(std::memory_order_relaxed);
    const size_t available = writeIdx - readIdx;
    const size_t toRead = (maxFrames > available) ? available : maxFrames;

    if (toRead == 0) return 0;

    const size_t sampleCount = toRead * channels_;
    const size_t readPos = (readIdx & mask_) * channels_;
    const size_t contiguous = (capacity_ - (readIdx & mask_)) * channels_;

    if (sampleCount <= contiguous) {
        std::memcpy(out, buffer_ + readPos, sampleCount * sizeof(float));
    } else {
        std::memcpy(out, buffer_ + readPos, contiguous * sizeof(float));
        std::memcpy(out + contiguous, buffer_, (sampleCount - contiguous) * sizeof(float));
    }

    readIndex_.store(readIdx + toRead, std::memory_order_release);
    return toRead;
}

void SpscFloatRing::reset() {
    writeIndex_.store(0, std::memory_order_relaxed);
    readIndex_.store(0, std::memory_order_relaxed);
    if (buffer_) {
        std::memset(buffer_, 0, capacity_ * channels_ * sizeof(float));
    }
}

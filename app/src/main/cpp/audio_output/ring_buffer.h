#pragma once

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <cstring>

/**
 * Lock-free SPSC ring buffer for float PCM interleaved data.
 * Capacity must be a power of 2 for mask-based indexing.
 */
class SpscFloatRing {
public:
    explicit SpscFloatRing(size_t capacityFrames, int channels);
    ~SpscFloatRing();

    // Writer (decode/resample thread)
    size_t write(const float* data, size_t frames);
    size_t writableFrames() const;

    // Reader (output thread)
    size_t read(float* out, size_t maxFrames);
    size_t readableFrames() const;

    void reset();

    size_t capacity() const { return capacity_; }

    // Accessors for USB output thread
    float* buffer() { return buffer_; }
    std::atomic<size_t>* writeIndexPtr() { return &writeIndex_; }
    std::atomic<size_t>* readIndexPtr() { return &readIndex_; }
    int channels() const { return channels_; }

private:
    float* buffer_ = nullptr;
    const size_t capacity_;  // power of 2
    const size_t mask_;
    const int channels_;

    alignas(64) std::atomic<size_t> writeIndex_{0};
    alignas(64) std::atomic<size_t> readIndex_{0};
};

inline size_t SpscFloatRing::writableFrames() const {
    const size_t writeIdx = writeIndex_.load(std::memory_order_relaxed);
    const size_t readIdx = readIndex_.load(std::memory_order_acquire);
    return mask_ - (writeIdx - readIdx);
}

inline size_t SpscFloatRing::readableFrames() const {
    const size_t writeIdx = writeIndex_.load(std::memory_order_acquire);
    const size_t readIdx = readIndex_.load(std::memory_order_relaxed);
    return writeIdx - readIdx;
}

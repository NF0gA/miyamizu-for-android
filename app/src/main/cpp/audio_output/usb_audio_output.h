#pragma once

#include <pthread.h>
#include <atomic>
#include <cstdint>
#include <linux/usbdevice_fs.h>

// Forward declaration
class SpscFloatRing;

struct UsbAudioConfig {
    int fd = -1;
    int interfaceNum = 0;
    int altSetting = 0;
    int endpoint = 0;
    int frameBytes = 0;
    int sampleRate = 44100;
    int bitDepth = 16;
    int channels = 2;
    bool useAsync = false;
};

struct UsbTelemetry {
    double jitterRmsUs = 0.0;
    uint32_t xrunCount = 0;
    bool isBitPerfect = true;
    int actualSampleRate = 0;
};

class UsbAudioOutput {
public:
    UsbAudioOutput();
    ~UsbAudioOutput();

    bool open(const UsbAudioConfig& config);
    void close();
    bool start();
    void stop();

    // SPSC ring buffer feed
    void setRingBuffer(SpscFloatRing* ring);
    UsbTelemetry getTelemetry() const;

private:
    static void* rtThreadFunc(void* arg);
    void realtimeLoop();
    void submitIsoUrbs();
    void reapAndRefill();

    UsbAudioConfig config_;
    std::atomic<bool> running_{false};
    pthread_t thread_ = 0;

    // SPSC ring buffer reference
    SpscFloatRing* ring_ = nullptr;

    // URB pool (decent-player lesson: 80 URBs)
    static constexpr int URB_COUNT = 80;
    struct usbdevfs_urb* urbs_[URB_COUNT];
    uint8_t* isoBuffers_[URB_COUNT];
    static constexpr int ISO_PACKETS_PER_URB = 8;
    static constexpr int ISO_PACKET_SIZE = 192;  // bytes per iso frame

    // Telemetry
    mutable std::atomic<double> jitterRms_{0.0};
    mutable std::atomic<uint32_t> xrunCount_{0};
    mutable uint64_t lastTimestampNs_ = 0;
    mutable double jitterAccum_ = 0.0;
    mutable uint32_t jitterCount_ = 0;
};

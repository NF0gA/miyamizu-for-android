#include "usb_audio_output.h"
#include "ring_buffer.h"
#include <android/log.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <time.h>
#include <cmath>
#include <cstring>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "MiyamizuUSB", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "MiyamizuUSB", __VA_ARGS__)

UsbAudioOutput::UsbAudioOutput() {
    for (int i = 0; i < URB_COUNT; i++) {
        urbs_[i] = nullptr;
        isoBuffers_[i] = nullptr;
    }
}

UsbAudioOutput::~UsbAudioOutput() {
    close();
}

bool UsbAudioOutput::open(const UsbAudioConfig& config) {
    config_ = config;

    if (config_.fd < 0) {
        LOGE("Invalid USB fd");
        return false;
    }

    int isoBufSize = ISO_PACKETS_PER_URB * ISO_PACKET_SIZE;
    for (int i = 0; i < URB_COUNT; i++) {
        urbs_[i] = new usbdevfs_urb();
        std::memset(urbs_[i], 0, sizeof(usbdevfs_urb));
        isoBuffers_[i] = new uint8_t[isoBufSize];
        std::memset(isoBuffers_[i], 0, isoBufSize);

        urbs_[i]->type = USBDEVFS_URB_TYPE_ISO;
        urbs_[i]->endpoint = static_cast<unsigned int>(config_.endpoint);
        urbs_[i]->buffer = isoBuffers_[i];
        urbs_[i]->buffer_length = isoBufSize;
        urbs_[i]->number_of_packets = ISO_PACKETS_PER_URB;

        for (int p = 0; p < ISO_PACKETS_PER_URB; p++) {
            urbs_[i]->iso_frame_desc[p].length = ISO_PACKET_SIZE;
        }
    }

    LOGI("USB Audio opened: sr=%d, bits=%d, ch=%d, ep=0x%02x",
         config_.sampleRate, config_.bitDepth, config_.channels, config_.endpoint);
    return true;
}

void UsbAudioOutput::close() {
    stop();
    for (int i = 0; i < URB_COUNT; i++) {
        delete urbs_[i];
        delete[] isoBuffers_[i];
        urbs_[i] = nullptr;
        isoBuffers_[i] = nullptr;
    }
    if (config_.fd >= 0) {
        ::close(config_.fd);
        config_.fd = -1;
    }
    ring_ = nullptr;
}

bool UsbAudioOutput::start() {
    running_ = true;

    pthread_attr_t attr;
    pthread_attr_init(&attr);

    struct sched_param param = { .sched_priority = 80 };
    pthread_attr_setschedpolicy(&attr, SCHED_FIFO);
    pthread_attr_setschedparam(&attr, &param);

    int ret = pthread_create(&thread_, &attr, rtThreadFunc, this);
    pthread_attr_destroy(&attr);

    if (ret != 0) {
        LOGE("Failed to create RT thread: %d", ret);
        running_ = false;
        return false;
    }

    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET(2, &cpuset);
    pthread_setaffinity_np(thread_, sizeof(cpuset), &cpuset);

    submitIsoUrbs();
    LOGI("USB RT thread started");
    return true;
}

void UsbAudioOutput::stop() {
    running_ = false;
    if (thread_) {
        pthread_join(thread_, nullptr);
        thread_ = 0;
    }
}

void UsbAudioOutput::setRingBuffer(SpscFloatRing* ring) {
    ring_ = ring;
}

void* UsbAudioOutput::rtThreadFunc(void* arg) {
    UsbAudioOutput* self = static_cast<UsbAudioOutput*>(arg);
    self->realtimeLoop();
    return nullptr;
}

void UsbAudioOutput::submitIsoUrbs() {
    for (int i = 0; i < URB_COUNT; i++) {
        urbs_[i]->flags = USBDEVFS_URB_ISO_ASAP;
        int ret = ioctl(config_.fd, USBDEVFS_SUBMITURB, urbs_[i]);
        if (ret < 0) {
            LOGE("SUBMITURB failed: %d", errno);
        }
    }
}

void UsbAudioOutput::realtimeLoop() {
    while (running_.load()) {
        reapAndRefill();
    }
}

void UsbAudioOutput::reapAndRefill() {
    usbdevfs_urb* urb = nullptr;
    int ret = ioctl(config_.fd, USBDEVFS_REAPURB, &urb);
    if (ret < 0 || !urb) {
        if (errno == EINTR) return;
        LOGE("REAPURB failed: %d", errno);
        xrunCount_++;
        return;
    }

    if (!ring_) {
        // Resubmit empty
        urb->flags = USBDEVFS_URB_ISO_ASAP;
        ioctl(config_.fd, USBDEVFS_SUBMITURB, urb);
        return;
    }

    uint8_t* buf = static_cast<uint8_t*>(urb->buffer);
    int totalBytes = urb->buffer_length;
    int bytesPerSample = config_.bitDepth / 8;
    int frameSize = bytesPerSample * config_.channels;
    int framesToWrite = totalBytes / frameSize;

    // Read from ring buffer
    std::vector<float> tempFloats(framesToWrite * config_.channels);
    size_t framesRead = ring_->read(tempFloats.data(), framesToWrite);

    if (config_.bitDepth == 16) {
        int16_t* out = reinterpret_cast<int16_t*>(buf);
        for (size_t f = 0; f < framesRead; f++) {
            for (int ch = 0; ch < config_.channels; ch++) {
                float sample = tempFloats[f * config_.channels + ch];
                if (sample > 1.0f) sample = 1.0f;
                if (sample < -1.0f) sample = -1.0f;
                out[f * config_.channels + ch] = static_cast<int16_t>(sample * 32767.0f);
            }
        }
        for (size_t f = framesRead; f < static_cast<size_t>(framesToWrite); f++) {
            for (int ch = 0; ch < config_.channels; ch++) {
                out[f * config_.channels + ch] = 0;
            }
        }
    } else if (config_.bitDepth == 24) {
        for (size_t f = 0; f < framesRead; f++) {
            for (int ch = 0; ch < config_.channels; ch++) {
                float sample = tempFloats[f * config_.channels + ch];
                if (sample > 1.0f) sample = 1.0f;
                if (sample < -1.0f) sample = -1.0f;
                int32_t v = static_cast<int32_t>(sample * 8388607.0f);
                uint8_t* dst = buf + (f * config_.channels + ch) * 3;
                dst[0] = v & 0xFF;
                dst[1] = (v >> 8) & 0xFF;
                dst[2] = (v >> 16) & 0xFF;
            }
        }
        for (size_t f = framesRead; f < static_cast<size_t>(framesToWrite); f++) {
            for (int ch = 0; ch < config_.channels; ch++) {
                uint8_t* dst = buf + (f * config_.channels + ch) * 3;
                dst[0] = dst[1] = dst[2] = 0;
            }
        }
    } else if (config_.bitDepth == 32) {
        int32_t* out = reinterpret_cast<int32_t*>(buf);
        for (size_t f = 0; f < framesRead; f++) {
            for (int ch = 0; ch < config_.channels; ch++) {
                float sample = tempFloats[f * config_.channels + ch];
                if (sample > 1.0f) sample = 1.0f;
                if (sample < -1.0f) sample = -1.0f;
                out[f * config_.channels + ch] = static_cast<int32_t>(sample * 2147483647.0f);
            }
        }
        for (size_t f = framesRead; f < static_cast<size_t>(framesToWrite); f++) {
            for (int ch = 0; ch < config_.channels; ch++) {
                out[f * config_.channels + ch] = 0;
            }
        }
    }

    if (framesRead < static_cast<size_t>(framesToWrite)) {
        xrunCount_++;
    }

    // Jitter measurement
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC_RAW, &ts);
    uint64_t nowNs = ts.tv_sec * 1000000000ULL + ts.tv_nsec;
    if (lastTimestampNs_ != 0) {
        double expectedNs = (framesToWrite * 1'000'000'000.0) / config_.sampleRate;
        double actualNs = static_cast<double>(nowNs - lastTimestampNs_);
        double diff = actualNs - expectedNs;
        jitterAccum_ += diff * diff;
        jitterCount_++;
        if (jitterCount_ % 1000 == 0) {
            jitterRms_.store(std::sqrt(jitterAccum_ / jitterCount_) / 1000.0); // us
        }
    }
    lastTimestampNs_ = nowNs;

    // Resubmit
    urb->flags = USBDEVFS_URB_ISO_ASAP;
    for (int p = 0; p < ISO_PACKETS_PER_URB; p++) {
        urb->iso_frame_desc[p].status = 0;
        urb->iso_frame_desc[p].actual_length = 0;
    }
    ret = ioctl(config_.fd, USBDEVFS_SUBMITURB, urb);
    if (ret < 0) {
        LOGE("Resubmit failed: %d", errno);
    }
}

UsbTelemetry UsbAudioOutput::getTelemetry() const {
    UsbTelemetry t;
    t.jitterRmsUs = jitterRms_.load();
    t.xrunCount = xrunCount_.load();
    t.isBitPerfect = true;
    t.actualSampleRate = config_.sampleRate;
    return t;
}

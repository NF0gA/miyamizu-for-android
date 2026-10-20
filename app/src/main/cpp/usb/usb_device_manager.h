#pragma once

#include "uac2_descriptor_parser.h"
#include <jni.h>
#include <string>

/**
 * Manages USB DAC device lifecycle from native side.
 * Coordinates with Java UsbManager for permission and fd access.
 */
class UsbDeviceManager {
public:
    static UsbDeviceManager& instance();

    // Called from Java after UsbManager.openDevice() succeeds
    bool attachDevice(int fd, int vendorId, int productId,
                      const uint8_t* rawDescriptor, size_t descriptorLength);
    void detachDevice();

    bool isDeviceAttached() const { return deviceAttached_; }

    const Uac2DeviceInfo& getDeviceInfo() const { return deviceInfo_; }

    // For a given sample rate/bit depth, return the best alt setting params
    struct OutputParams {
        uint8_t interfaceNum;
        uint8_t altSetting;
        uint8_t endpointAddress;
        uint8_t attributes;
        uint8_t subslotSize;
        uint8_t bitResolution;
        uint8_t numChannels;
        int frameBytes;
    };
    bool getOutputParams(int sampleRate, int bitDepth, OutputParams& out);

    // Clock source control (for async endpoints)
    bool setClockFrequency(int clockId, uint32_t frequency);

    std::string getDeviceSummary() const;

private:
    UsbDeviceManager() = default;
    ~UsbDeviceManager() = default;

    bool deviceAttached_ = false;
    int currentFd_ = -1;
    Uac2DeviceInfo deviceInfo_;
};

// JNI helpers
extern "C" {
JNIEXPORT jboolean JNICALL
Java_com_miyamizu_player_playback_NativeEngine_nativeUsbAttachDevice(
    JNIEnv* env, jobject thiz,
    jint fd, jint vendorId, jint productId,
    jbyteArray rawDescriptor);

JNIEXPORT void JNICALL
Java_com_miyamizu_player_playback_NativeEngine_nativeUsbDetachDevice(JNIEnv* env, jobject thiz);

JNIEXPORT jboolean JNICALL
Java_com_miyamizu_player_playback_NativeEngine_nativeUsbIsAttached(JNIEnv* env, jobject thiz);

JNIEXPORT jstring JNICALL
Java_com_miyamizu_player_playback_NativeEngine_nativeUsbGetDeviceSummary(JNIEnv* env, jobject thiz);
}

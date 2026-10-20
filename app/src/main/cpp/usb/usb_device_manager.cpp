#include "usb_device_manager.h"
#include <android/log.h>
#include <cstring>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "MiyamizuUsbMgr", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "MiyamizuUsbMgr", __VA_ARGS__)

UsbDeviceManager& UsbDeviceManager::instance() {
    static UsbDeviceManager mgr;
    return mgr;
}

bool UsbDeviceManager::attachDevice(int fd, int vendorId, int productId,
                                    const uint8_t* rawDescriptor, size_t descriptorLength) {
    if (deviceAttached_) {
        LOGI("Device already attached, detaching first");
        detachDevice();
    }

    Uac2DeviceInfo info;
    if (!parseUac2Descriptors(rawDescriptor, descriptorLength, info)) {
        LOGE("Failed to parse UAC2 descriptors");
        return false;
    }

    info.vendorId = static_cast<uint16_t>(vendorId);
    info.productId = static_cast<uint16_t>(productId);

    deviceInfo_ = std::move(info);
    currentFd_ = fd;
    deviceAttached_ = true;

    LOGI("USB DAC attached: VID=0x%04x PID=0x%04x, %d streaming interfaces",
         vendorId, productId, (int)deviceInfo_.streamingInterfaces.size());
    LOGI("Best playback: iface=%d, alt=%d, ep=0x%02x, bits=%d",
         deviceInfo_.bestPlayback.interfaceNum,
         deviceInfo_.bestPlayback.altSetting,
         deviceInfo_.bestPlayback.endpointAddress,
         deviceInfo_.bestPlayback.bitResolution);

    return true;
}

void UsbDeviceManager::detachDevice() {
    if (currentFd_ >= 0) {
        ::close(currentFd_);
        currentFd_ = -1;
    }
    deviceAttached_ = false;
    deviceInfo_ = Uac2DeviceInfo{};
    LOGI("USB DAC detached");
}

bool UsbDeviceManager::getOutputParams(int sampleRate, int bitDepth, OutputParams& out) {
    if (!deviceAttached_) return false;

    Uac2AltSetting best;
    if (!selectBestAltSetting(deviceInfo_, static_cast<uint32_t>(sampleRate), bitDepth, best)) {
        LOGE("No suitable alt setting for %d Hz / %d bit", sampleRate, bitDepth);
        return false;
    }

    out.interfaceNum = deviceInfo_.bestPlayback.interfaceNum;
    out.altSetting = best.altSetting;
    out.endpointAddress = best.endpointAddress;
    out.attributes = best.attributes;
    out.subslotSize = best.subslotSize;
    out.bitResolution = best.bitResolution;
    out.numChannels = best.numChannels > 0 ? best.numChannels : 2;
    out.frameBytes = out.numChannels * out.subslotSize;

    return true;
}

bool UsbDeviceManager::setClockFrequency(int clockId, uint32_t frequency) {
    // UAC2 Clock Source control request
    // bmRequestType = 0x21 (OUT | CLASS | INTERFACE)
    // bRequest = CUR (0x01)
    // wValue = selector << 8 | channel
    // wIndex = interface << 8 | entityId (clockId)
    // wLength = 4 (32-bit frequency)

    if (currentFd_ < 0) return false;

    // This requires usbdevfs control transfer
    // struct usbdevfs_ctrltransfer ctrl;
    // ctrl.bRequestType = 0x21;
    // ctrl.bRequest = 0x01;  // CUR
    // ctrl.wValue = 0x0100;  // CS = Clock Frequency Control
    // ctrl.wIndex = (interface << 8) | clockId;
    // ctrl.wLength = 4;
    // ctrl.timeout = 5000;
    // ctrl.data = &frequency;
    // ioctl(fd, USBDEVFS_CONTROL, &ctrl);

    LOGI("Clock frequency set request: clockId=%d, freq=%u Hz", clockId, frequency);
    return true;  // Placeholder - actual implementation needs usbdevfs
}

std::string UsbDeviceManager::getDeviceSummary() const {
    return formatDeviceInfo(deviceInfo_);
}

// === JNI implementations ===

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_miyamizu_player_playback_NativeEngine_nativeUsbAttachDevice(
    JNIEnv* env, jobject thiz,
    jint fd, jint vendorId, jint productId,
    jbyteArray rawDescriptor) {

    jsize len = env->GetArrayLength(rawDescriptor);
    jbyte* bytes = env->GetByteArrayElements(rawDescriptor, nullptr);

    bool ok = UsbDeviceManager::instance().attachDevice(
        fd, vendorId, productId,
        reinterpret_cast<const uint8_t*>(bytes), static_cast<size_t>(len));

    env->ReleaseByteArrayElements(rawDescriptor, bytes, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_miyamizu_player_playback_NativeEngine_nativeUsbDetachDevice(JNIEnv* env, jobject thiz) {
    UsbDeviceManager::instance().detachDevice();
}

JNIEXPORT jboolean JNICALL
Java_com_miyamizu_player_playback_NativeEngine_nativeUsbIsAttached(JNIEnv* env, jobject thiz) {
    return UsbDeviceManager::instance().isDeviceAttached() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_miyamizu_player_playback_NativeEngine_nativeUsbGetDeviceSummary(JNIEnv* env, jobject thiz) {
    std::string summary = UsbDeviceManager::instance().getDeviceSummary();
    return env->NewStringUTF(summary.c_str());
}

} // extern "C"

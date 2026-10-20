#pragma once

#include <cstdint>
#include <vector>
#include <string>

// USB Audio Class 2.0 descriptor structures
// Based on USB Audio Devices Release 2.0 specification

// Descriptor types
constexpr uint8_t DESC_TYPE_INTERFACE = 0x04;
constexpr uint8_t DESC_TYPE_ENDPOINT = 0x05;
constexpr uint8_t DESC_TYPE_INTERFACE_ASSOC = 0x0B;

// Audio class-specific interface descriptor subtypes
constexpr uint8_t UAC2_AC_HEADER = 0x01;
constexpr uint8_t UAC2_AC_INPUT_TERMINAL = 0x02;
constexpr uint8_t UAC2_AC_OUTPUT_TERMINAL = 0x03;
constexpr uint8_t UAC2_AC_MIXER_UNIT = 0x04;
constexpr uint8_t UAC2_AC_SELECTOR_UNIT = 0x05;
constexpr uint8_t UAC2_AC_FEATURE_UNIT = 0x06;
constexpr uint8_t UAC2_AC_CLOCK_SOURCE = 0x0A;
constexpr uint8_t UAC2_AS_GENERAL = 0x01;
constexpr uint8_t UAC2_FORMAT_TYPE = 0x02;
constexpr uint8_t UAC2_ENCODER = 0x03;

// Audio class-specific endpoint descriptor subtypes
constexpr uint8_t UAC2_EP_GENERAL = 0x01;

// Format type codes
constexpr uint8_t UAC2_FORMAT_TYPE_I = 0x01;

// Audio class codes
constexpr uint8_t AUDIO_CLASS = 0x01;
constexpr uint8_t AUDIO_SUBCLASS_CONTROL = 0x01;
constexpr uint8_t AUDIO_SUBCLASS_STREAMING = 0x02;

// Endpoint attributes
constexpr uint8_t EP_ATTR_ADAPTIVE = 0x08;
constexpr uint8_t EP_ATTR_ASYNC = 0x04;
constexpr uint8_t EP_ATTR_SYNC = 0x00;

// Supported PCM format bitmasks (bSubslotSize in UAC2)
constexpr uint8_t UAC2_SAMPLE_SIZE_8BIT = 0x01;
constexpr uint8_t UAC2_SAMPLE_SIZE_16BIT = 0x02;
constexpr uint8_t UAC2_SAMPLE_SIZE_24BIT = 0x03;
constexpr uint8_t UAC2_SAMPLE_SIZE_32BIT = 0x04;

// bmFormats (Format Type I) - bitmasks
constexpr uint32_t UAC2_FORMAT_PCM = 0x00000001;
constexpr uint32_t UAC2_FORMAT_PCM8 = 0x00000002;
constexpr uint32_t UAC2_FORMAT_IEEE_FLOAT = 0x00000004;
constexpr uint32_t UAC2_FORMAT_ALAW = 0x00000008;
constexpr uint32_t UAC2_FORMAT_MULAW = 0x00000010;

struct Uac2ClockSource {
    uint8_t id;
    uint8_t attr;  // D1..0: 0=ext, 1=int fixed, 2=int var, 3=int locked
};

struct Uac2AltSetting {
    uint8_t altSetting;
    uint8_t endpointAddress;
    uint8_t attributes;  // ISO type (sync/adaptive/async)
    uint16_t maxPacketSize;
    uint8_t interval;
    uint8_t refresh;     // for async endpoints
    uint8_t synchAddress;

    // Class-specific AS General
    uint8_t terminalLink;
    uint8_t delay;
    uint32_t bmFormats;  // Format Type I bitmask

    // Format Type I Descriptor
    uint8_t formatType;
    uint8_t subslotSize;  // bytes per sample (1,2,3,4)
    uint8_t bitResolution; // actual bits (e.g. 24 in 32-bit slot)
    uint8_t numChannels;
    uint32_t channelConfig;

    // Supported sample rates
    std::vector<uint32_t> sampleRates;
    bool continuousRate;
    uint32_t minRate;
    uint32_t maxRate;
};

struct Uac2AudioInterface {
    uint8_t interfaceNum;
    uint8_t numAltSettings;
    std::vector<Uac2AltSetting> altSettings;
};

struct Uac2DeviceInfo {
    uint16_t vendorId;
    uint16_t productId;
    std::string manufacturer;
    std::string product;

    uint8_t controlInterface;
    std::vector<Uac2ClockSource> clockSources;
    std::vector<Uac2AudioInterface> streamingInterfaces;

    // Best alt setting for bit-perfect playback (auto-selected)
    struct {
        uint8_t interfaceNum = 0;
        uint8_t altSetting = 0;
        uint8_t endpointAddress = 0;
        uint8_t attributes = 0;
        uint8_t subslotSize = 0;
        uint8_t bitResolution = 0;
        uint8_t numChannels = 0;
        std::vector<uint32_t> sampleRates;
    } bestPlayback;
};

/**
 * Parse USB Audio Class 2.0 descriptors from raw USB config descriptor.
 * Returns true if a valid UAC2 streaming interface was found.
 */
bool parseUac2Descriptors(const uint8_t* descriptor, size_t length, Uac2DeviceInfo& info);

/**
 * Select the best alt setting for a given sample rate and bit depth.
 * Prefers: async endpoint > adaptive, max bit depth, matching sample rate.
 */
bool selectBestAltSetting(const Uac2DeviceInfo& info, uint32_t targetRate,
                          int targetBits, Uac2AltSetting& out);

/**
 * Get human-readable description of the parsed device.
 */
std::string formatDeviceInfo(const Uac2DeviceInfo& info);

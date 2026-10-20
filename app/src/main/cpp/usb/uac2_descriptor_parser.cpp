#include "uac2_descriptor_parser.h"
#include <android/log.h>
#include <cstring>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "MiyamizuUAC2", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "MiyamizuUAC2", __VA_ARGS__)

// USB descriptor parsing helpers
static inline uint16_t readU16LE(const uint8_t* p) {
    return p[0] | (p[1] << 8);
}

static inline uint32_t readU24LE(const uint8_t* p) {
    return p[0] | (p[1] << 8) | (p[2] << 16);
}

static inline uint32_t readU32LE(const uint8_t* p) {
    return p[0] | (p[1] << 8) | (p[2] << 16) | (p[3] << 24);
}

static inline uint32_t parseSampleRateTriplet(const uint8_t* p) {
    return readU24LE(p);
}

bool parseUac2Descriptors(const uint8_t* desc, size_t length, Uac2DeviceInfo& info) {
    if (!desc || length < 9) return false;

    info = Uac2DeviceInfo{};

    size_t pos = 0;
    uint8_t currentInterface = 0;
    uint8_t currentAltSetting = 0;
    bool inAudioControl = false;
    bool inAudioStreaming = false;
    Uac2AltSetting* currentAlt = nullptr;

    while (pos + 2 <= length) {
        uint8_t bLength = desc[pos];
        uint8_t bDescriptorType = desc[pos + 1];

        if (bLength < 2 || pos + bLength > length) {
            pos++;
            continue;
        }

        switch (bDescriptorType) {
            case DESC_TYPE_INTERFACE: {
                if (bLength >= 9) {
                    currentInterface = desc[pos + 2];
                    currentAltSetting = desc[pos + 3];
                    uint8_t bInterfaceClass = desc[pos + 5];
                    uint8_t bInterfaceSubClass = desc[pos + 6];

                    inAudioControl = (bInterfaceClass == AUDIO_CLASS &&
                                      bInterfaceSubClass == AUDIO_SUBCLASS_CONTROL);
                    inAudioStreaming = (bInterfaceClass == AUDIO_CLASS &&
                                        bInterfaceSubClass == AUDIO_SUBCLASS_STREAMING);

                    if (inAudioStreaming) {
                        // Find or create streaming interface
                        Uac2AudioInterface* iface = nullptr;
                        for (auto& si : info.streamingInterfaces) {
                            if (si.interfaceNum == currentInterface) {
                                iface = &si;
                                break;
                            }
                        }
                        if (!iface) {
                            info.streamingInterfaces.push_back({});
                            iface = &info.streamingInterfaces.back();
                            iface->interfaceNum = currentInterface;
                        }

                        iface->altSettings.push_back({});
                        currentAlt = &iface->altSettings.back();
                        currentAlt->altSetting = currentAltSetting;
                        iface->numAltSettings = static_cast<uint8_t>(iface->altSettings.size());
                    } else {
                        currentAlt = nullptr;
                    }
                }
                break;
            }

            case DESC_TYPE_ENDPOINT: {
                if (bLength >= 7 && currentAlt) {
                    uint8_t epAddr = desc[pos + 2];
                    uint8_t epAttr = desc[pos + 3];
                    uint16_t maxPkt = readU16LE(desc + pos + 4);
                    uint8_t interval = desc[pos + 6];

                    // Only OUT isochronous endpoints for playback
                    if ((epAddr & 0x80) == 0 && (epAttr & 0x03) == 0x01) {
                        currentAlt->endpointAddress = epAddr;
                        currentAlt->attributes = epAttr;
                        currentAlt->maxPacketSize = maxPkt;
                        currentAlt->interval = interval;
                    }
                }
                break;
            }

            // Audio class-specific interface descriptors
            case 0x24: {  // CS_INTERFACE
                if (bLength < 3) break;
                uint8_t subtype = desc[pos + 2];

                if (inAudioControl) {
                    switch (subtype) {
                        case UAC2_AC_HEADER:
                            if (bLength >= 9) {
                                info.controlInterface = currentInterface;
                            }
                            break;
                        case UAC2_AC_CLOCK_SOURCE:
                            if (bLength >= 5) {
                                Uac2ClockSource clk;
                                clk.id = desc[pos + 3];
                                clk.attr = desc[pos + 4];
                                info.clockSources.push_back(clk);
                            }
                            break;
                    }
                } else if (inAudioStreaming && currentAlt) {
                    switch (subtype) {
                        case UAC2_AS_GENERAL:
                            if (bLength >= 7) {
                                currentAlt->terminalLink = desc[pos + 3];
                                currentAlt->delay = desc[pos + 4];
                                currentAlt->bmFormats = readU32LE(desc + pos + 5);
                            }
                            break;
                        case UAC2_FORMAT_TYPE:
                            if (bLength >= 6) {
                                currentAlt->formatType = desc[pos + 3];
                                currentAlt->subslotSize = desc[pos + 4];
                                currentAlt->bitResolution = desc[pos + 5];

                                if (currentAlt->formatType == UAC2_FORMAT_TYPE_I && bLength >= 7) {
                                    uint8_t numSamFreq = desc[pos + 6];
                                    if (numSamFreq == 0) {
                                        // Continuous frequency range
                                        currentAlt->continuousRate = true;
                                        if (bLength >= 13) {
                                            currentAlt->minRate = readU24LE(desc + pos + 7);
                                            currentAlt->maxRate = readU24LE(desc + pos + 10);
                                        }
                                    } else {
                                        currentAlt->continuousRate = false;
                                        for (uint8_t i = 0; i < numSamFreq && (pos + 8 + i * 3) <= length; i++) {
                                            uint32_t rate = readU24LE(desc + pos + 7 + i * 3);
                                            currentAlt->sampleRates.push_back(rate);
                                        }
                                    }
                                }
                            }
                            break;
                    }
                }
                break;
            }

            // Audio class-specific endpoint descriptor
            case 0x25: {  // CS_ENDPOINT
                if (bLength >= 4 && currentAlt) {
                    uint8_t subtype = desc[pos + 2];
                    if (subtype == UAC2_EP_GENERAL && bLength >= 8) {
                        currentAlt->attributes = desc[pos + 3];  // bmAttributes
                        currentAlt->refresh = desc[pos + 7];
                        currentAlt->synchAddress = desc[pos + 8];
                    }
                }
                break;
            }
        }

        pos += bLength;
    }

    // Auto-select best playback configuration
    if (!info.streamingInterfaces.empty()) {
        const Uac2AudioInterface& iface = info.streamingInterfaces[0];
        Uac2AltSetting best;
        if (selectBestAltSetting(info, 44100, 24, best)) {
            info.bestPlayback.interfaceNum = iface.interfaceNum;
            info.bestPlayback.altSetting = best.altSetting;
            info.bestPlayback.endpointAddress = best.endpointAddress;
            info.bestPlayback.attributes = best.attributes;
            info.bestPlayback.subslotSize = best.subslotSize;
            info.bestPlayback.bitResolution = best.bitResolution;
            info.bestPlayback.numChannels = best.numChannels;
            info.bestPlayback.sampleRates = best.sampleRates;
        }
    }

    LOGI("Parsed UAC2 device: %d clock sources, %d streaming interfaces",
         (int)info.clockSources.size(), (int)info.streamingInterfaces.size());

    return !info.streamingInterfaces.empty();
}

bool selectBestAltSetting(const Uac2DeviceInfo& info, uint32_t targetRate,
                          int targetBits, Uac2AltSetting& out) {
    const Uac2AltSetting* best = nullptr;
    int bestScore = -1;

    for (const auto& iface : info.streamingInterfaces) {
        for (const auto& alt : iface.altSettings) {
            // Must have an OUT endpoint
            if (alt.endpointAddress == 0) continue;

            // Must support PCM format
            if ((alt.bmFormats & UAC2_FORMAT_PCM) == 0) continue;

            int score = 0;

            // Prefer async endpoint (lower jitter)
            uint8_t syncType = (alt.attributes >> 2) & 0x03;
            if (syncType == 0x01) score += 100;  // adaptive
            else if (syncType == 0x02) score += 200;  // async (best)

            // Prefer matching bit depth
            if (alt.bitResolution >= targetBits) score += 50;
            else if (alt.bitResolution >= 16) score += 20;

            // Prefer matching sample rate
            bool rateMatch = false;
            if (alt.continuousRate) {
                if (targetRate >= alt.minRate && targetRate <= alt.maxRate) {
                    rateMatch = true;
                    score += 30;
                }
            } else {
                for (uint32_t r : alt.sampleRates) {
                    if (r == targetRate) {
                        rateMatch = true;
                        score += 30;
                        break;
                    }
                }
            }

            // Prefer higher subslot size (more bandwidth = better)
            score += alt.subslotSize * 5;

            if (score > bestScore) {
                bestScore = score;
                best = &alt;
            }
        }
    }

    if (best) {
        out = *best;
        LOGI("Selected alt setting: iface=%d, alt=%d, ep=0x%02x, bits=%d, sr=%lu",
             info.bestPlayback.interfaceNum, out.altSetting, out.endpointAddress,
             out.bitResolution, (unsigned long)targetRate);
        return true;
    }

    return false;
}

std::string formatDeviceInfo(const Uac2DeviceInfo& info) {
    std::string result;
    result += "UAC2 Device:\n";
    result += "  Clock Sources: " + std::to_string(info.clockSources.size()) + "\n";
    result += "  Streaming Interfaces: " + std::to_string(info.streamingInterfaces.size()) + "\n";
    for (const auto& iface : info.streamingInterfaces) {
        result += "  Interface " + std::to_string(iface.interfaceNum) + ":\n";
        for (const auto& alt : iface.altSettings) {
            result += "    Alt " + std::to_string(alt.altSetting) + ":\n";
            result += "      EP: 0x" + std::to_string(alt.endpointAddress) + "\n";
            result += "      MaxPkt: " + std::to_string(alt.maxPacketSize) + "\n";
            result += "      Subslot: " + std::to_string(alt.subslotSize) + " bits\n";
            result += "      Resolution: " + std::to_string(alt.bitResolution) + " bits\n";
            if (alt.continuousRate) {
                result += "      Rates: " + std::to_string(alt.minRate) + "-" +
                          std::to_string(alt.maxRate) + " Hz (continuous)\n";
            } else {
                result += "      Rates: ";
                for (auto r : alt.sampleRates) {
                    result += std::to_string(r) + " ";
                }
                result += "Hz\n";
            }
        }
    }
    return result;
}

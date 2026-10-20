#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <atomic>
#include <memory>
#include <vector>

#include "audio_output/aaudio_output.h"
#include "audio_output/usb_audio_output.h"
#include "audio_output/ring_buffer.h"
#include "resample/soxr_resampler.h"
#include "resample/r8brain_resampler.h"
#include "dither/tpdf_dither.h"
#include "dither/noise_shaper.h"
#include "decode/flac_decoder.h"
#include "usb/usb_device_manager.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "MiyamizuJNI", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "MiyamizuJNI", __VA_ARGS__)

// === Global engine state ===
struct EngineState {
    std::unique_ptr<AAudioOutput> aaudio;
    std::unique_ptr<UsbAudioOutput> usbAudio;
    std::unique_ptr<SpscFloatRing> ringBuffer;
    std::unique_ptr<FlacDecoder> flacDecoder;
    std::unique_ptr<SoxrResampler> soxr;
    std::unique_ptr<R8brainResampler> r8brain;
    std::unique_ptr<TpdfDither> dither;
    std::unique_ptr<NoiseShaper> noiseShaper;
};

static EngineState g_engine;

// Helper: copy jfloatArray to vector
static std::vector<float> jfloatArrayToVector(JNIEnv* env, jfloatArray arr, jint offset, jint len) {
    jfloat* elems = env->GetFloatArrayElements(arr, nullptr);
    std::vector<float> result(elems + offset, elems + offset + len);
    env->ReleaseFloatArrayElements(arr, elems, JNI_ABORT);
    return result;
}

// Helper: copy vector to jfloatArray
static jfloatArray vectorToJfloatArray(JNIEnv* env, const std::vector<float>& vec) {
    jfloatArray arr = env->NewFloatArray(static_cast<jsize>(vec.size()));
    env->SetFloatArrayRegion(arr, 0, static_cast<jsize>(vec.size()), vec.data());
    return arr;
}

extern "C" {

// ===== AAudio =====

JNIEXPORT jboolean JNICALL
Java_com_miyamizu_player_playback_NativeEngine_aaudioOpen(
    JNIEnv* env, jobject thiz,
    jint sampleRate, jint bitDepth, jint channels) {
    g_engine.aaudio = std::make_unique<AAudioOutput>();
    return g_engine.aaudio->open(sampleRate, bitDepth, channels);
}

JNIEXPORT void JNICALL
Java_com_miyamizu_player_playback_NativeEngine_aaudioClose(JNIEnv* env, jobject thiz) {
    if (g_engine.aaudio) g_engine.aaudio->close();
    g_engine.aaudio.reset();
}

JNIEXPORT jboolean JNICALL
Java_com_miyamizu_player_playback_NativeEngine_aaudioStart(JNIEnv* env, jobject thiz) {
    return g_engine.aaudio ? g_engine.aaudio->start() : false;
}

JNIEXPORT void JNICALL
Java_com_miyamizu_player_playback_NativeEngine_aaudioStop(JNIEnv* env, jobject thiz) {
    if (g_engine.aaudio) g_engine.aaudio->stop();
}

JNIEXPORT void JNICALL
Java_com_miyamizu_player_playback_NativeEngine_aaudioWrite(
    JNIEnv* env, jobject thiz,
    jfloatArray buffer, jint offset, jint numFrames, jint channels) {
    if (!g_engine.aaudio) return;
    jfloat* data = env->GetFloatArrayElements(buffer, nullptr);
    g_engine.aaudio->write(data + offset * channels, numFrames);
    env->ReleaseFloatArrayElements(buffer, data, JNI_ABORT);
}

JNIEXPORT jfloatArray JNICALL
Java_com_miyamizu_player_playback_NativeEngine_aaudioGetTelemetry(JNIEnv* env, jobject thiz) {
    jfloatArray arr = env->NewFloatArray(5);
    if (!g_engine.aaudio) return arr;
    auto t = g_engine.aaudio->getTelemetry();
    float vals[5] = {
        static_cast<float>(t.jitterRmsUs),
        static_cast<float>(t.xrunCount),
        t.isBitPerfect ? 1.0f : 0.0f,
        t.isMmap ? 1.0f : 0.0f,
        static_cast<float>(t.actualSampleRate)
    };
    env->SetFloatArrayRegion(arr, 0, 5, vals);
    return arr;
}

// ===== USB Audio =====

JNIEXPORT jboolean JNICALL
Java_com_miyamizu_player_playback_NativeEngine_usbOpen(
    JNIEnv* env, jobject thiz,
    jint fd, jint interfaceNum, jint altSetting, jint endpoint,
    jint frameBytes, jint sampleRate, jint bitDepth, jint channels) {

    UsbAudioConfig config;
    config.fd = fd;
    config.interfaceNum = interfaceNum;
    config.altSetting = altSetting;
    config.endpoint = endpoint;
    config.frameBytes = frameBytes;
    config.sampleRate = sampleRate;
    config.bitDepth = bitDepth;
    config.channels = channels;

    g_engine.usbAudio = std::make_unique<UsbAudioOutput>();
    return g_engine.usbAudio->open(config);
}

JNIEXPORT void JNICALL
Java_com_miyamizu_player_playback_NativeEngine_usbClose(JNIEnv* env, jobject thiz) {
    if (g_engine.usbAudio) g_engine.usbAudio->close();
    g_engine.usbAudio.reset();
    g_engine.ringBuffer.reset();
}

JNIEXPORT jboolean JNICALL
Java_com_miyamizu_player_playback_NativeEngine_usbStart(JNIEnv* env, jobject thiz) {
    return g_engine.usbAudio ? g_engine.usbAudio->start() : false;
}

JNIEXPORT void JNICALL
Java_com_miyamizu_player_playback_NativeEngine_usbStop(JNIEnv* env, jobject thiz) {
    if (g_engine.usbAudio) g_engine.usbAudio->stop();
}

JNIEXPORT void JNICALL
Java_com_miyamizu_player_playback_NativeEngine_usbSetupRingBuffer(
    JNIEnv* env, jobject thiz,
    jint capacityFrames, jint channels) {
    // Capacity must be power of 2
    size_t cap = 1;
    while (cap < static_cast<size_t>(capacityFrames)) cap <<= 1;
    g_engine.ringBuffer = std::make_unique<SpscFloatRing>(cap, channels);
    if (g_engine.usbAudio) {
        g_engine.usbAudio->setRingBuffer(
            g_engine.ringBuffer->buffer(),
            g_engine.ringBuffer->capacity(),
            g_engine.ringBuffer->writeIndexPtr(),
            g_engine.ringBuffer->readIndexPtr(),
            channels);
    }
}

JNIEXPORT void JNICALL
Java_com_miyamizu_player_playback_NativeEngine_usbWriteRing(
    JNIEnv* env, jobject thiz,
    jfloatArray buffer, jint offset, jint numFrames, jint channels) {
    if (!g_engine.ringBuffer) return;
    jfloat* data = env->GetFloatArrayElements(buffer, nullptr);
    g_engine.ringBuffer->write(data + offset * channels, numFrames);
    env->ReleaseFloatArrayElements(buffer, data, JNI_ABORT);
}

JNIEXPORT jfloatArray JNICALL
Java_com_miyamizu_player_playback_NativeEngine_usbGetTelemetry(JNIEnv* env, jobject thiz) {
    jfloatArray arr = env->NewFloatArray(4);
    if (!g_engine.usbAudio) return arr;
    auto t = g_engine.usbAudio->getTelemetry();
    float vals[4] = {
        static_cast<float>(t.jitterRmsUs),
        static_cast<float>(t.xrunCount),
        t.isBitPerfect ? 1.0f : 0.0f,
        static_cast<float>(t.actualSampleRate)
    };
    env->SetFloatArrayRegion(arr, 0, 4, vals);
    return arr;
}

// ===== FLAC Decode =====

JNIEXPORT jfloatArray JNICALL
Java_com_miyamizu_player_playback_NativeEngine_decodeFlacToRam(
    JNIEnv* env, jobject thiz,
    jstring path, jlong startSample, jlong endSample) {

    const char* cpath = env->GetStringUTFChars(path, nullptr);

    g_engine.flacDecoder = std::make_unique<FlacDecoder>();
    bool ok = g_engine.flacDecoder->open(cpath);
    env->ReleaseStringUTFChars(path, cpath);

    if (!ok) {
        LOGE("Failed to open FLAC");
        return env->NewFloatArray(0);
    }

    std::vector<float> pcm;
    int64_t framesDecoded = 0;

    if (endSample > startSample) {
        // Range decode (CUE track)
        framesDecoded = g_engine.flacDecoder->decodeRange(pcm, startSample, endSample);
    } else {
        // Full decode
        framesDecoded = g_engine.flacDecoder->decodeAll(pcm);
    }

    g_engine.flacDecoder->close();
    g_engine.flacDecoder.reset();

    LOGI("Decoded FLAC: %ld frames, %zu samples", (long)framesDecoded, pcm.size());
    return vectorToJfloatArray(env, pcm);
}

JNIEXPORT jobject JNICALL
Java_com_miyamizu_player_playback_NativeEngine_getFlacInfo(
    JNIEnv* env, jobject thiz, jstring path) {

    const char* cpath = env->GetStringUTFChars(path, nullptr);
    FlacDecoder decoder;
    bool ok = decoder.open(cpath);
    env->ReleaseStringUTFChars(path, cpath);

    if (!ok) return nullptr;

    FlacInfo info = decoder.getInfo();
    decoder.close();

    // Create FlacInfo data class
    jclass infoClass = env->FindClass("com/miyamizu/player/playback/FlacInfoData");
    if (!infoClass) {
        LOGE("FlacInfoData class not found");
        return nullptr;
    }
    jmethodID ctor = env->GetMethodID(infoClass, "<init>",
        "(IIIJDJ)V");
    if (!ctor) {
        LOGE("FlacInfoData constructor not found");
        return nullptr;
    }

    jobject obj = env->NewObject(infoClass, ctor,
        info.sampleRate,
        info.channels,
        info.bitsPerSample,
        info.totalSamples,
        info.durationSec,
        static_cast<jlong>(0)  // placeholder for metadata
    );
    return obj;
}

// ===== soxr Resampler =====

JNIEXPORT jlong JNICALL
Java_com_miyamizu_player_playback_NativeEngine_soxrCreate(
    JNIEnv* env, jobject thiz,
    jdouble inRate, jdouble outRate, jint channels,
    jint phase, jint bandwidth, jdouble quality) {
    auto* r = new SoxrResampler();
    SoxrPhase p = (phase == 0) ? SoxrPhase::LINEAR :
                    (phase == 1) ? SoxrPhase::INTERMEDIATE : SoxrPhase::MINIMUM;
    SoxrBandwidth bw = (bandwidth == 0) ? SoxrBandwidth::STANDARD : SoxrBandwidth::WIDE;
    r->init(inRate, outRate, channels, p, bw, quality);
    return reinterpret_cast<jlong>(r);
}

JNIEXPORT jint JNICALL
Java_com_miyamizu_player_playback_NativeEngine_soxrProcess(
    JNIEnv* env, jobject thiz, jlong handle,
    jfloatArray inArr, jint inOffset, jint inLen,
    jfloatArray outArr, jint outLen) {
    auto* r = reinterpret_cast<SoxrResampler*>(handle);
    if (!r) return 0;
    jfloat* in = env->GetFloatArrayElements(inArr, nullptr);
    jfloat* out = env->GetFloatArrayElements(outArr, nullptr);
    size_t done = r->process(in + inOffset, inLen, out, outLen);
    env->ReleaseFloatArrayElements(inArr, in, JNI_ABORT);
    env->ReleaseFloatArrayElements(outArr, out, 0);
    return static_cast<jint>(done);
}

JNIEXPORT void JNICALL
Java_com_miyamizu_player_playback_NativeEngine_soxrDestroy(JNIEnv* env, jobject thiz, jlong handle) {
    auto* r = reinterpret_cast<SoxrResampler*>(handle);
    delete r;
}

// ===== r8brain Resampler =====

JNIEXPORT jlong JNICALL
Java_com_miyamizu_player_playback_NativeEngine_r8brainCreate(
    JNIEnv* env, jobject thiz,
    jdouble inRate, jdouble outRate, jint channels,
    jint preset, jdouble transBand, jdouble stopAtten,
    jboolean linearPhase, jdouble headroom) {
    auto* r = new R8brainResampler();
    R8brainPreset p = static_cast<R8brainPreset>(preset);
    R8brainCustomConfig cfg;
    cfg.transitionBand = transBand;
    cfg.stopBandAtten = stopAtten;
    cfg.linearPhase = linearPhase;
    cfg.headroomDb = headroom;
    r->init(inRate, outRate, channels, p, cfg);
    return reinterpret_cast<jlong>(r);
}

JNIEXPORT jint JNICALL
Java_com_miyamizu_player_playback_NativeEngine_r8brainProcess(
    JNIEnv* env, jobject thiz, jlong handle,
    jfloatArray inArr, jint inOffset, jint inLen,
    jfloatArray outArr, jint outLen) {
    auto* r = reinterpret_cast<R8brainResampler*>(handle);
    if (!r) return 0;
    jfloat* in = env->GetFloatArrayElements(inArr, nullptr);
    jfloat* out = env->GetFloatArrayElements(outArr, nullptr);
    size_t done = r->process(in + inOffset, inLen, out, outLen);
    env->ReleaseFloatArrayElements(inArr, in, JNI_ABORT);
    env->ReleaseFloatArrayElements(outArr, out, 0);
    return static_cast<jint>(done);
}

JNIEXPORT void JNICALL
Java_com_miyamizu_player_playback_NativeEngine_r8brainDestroy(JNIEnv* env, jobject thiz, jlong handle) {
    auto* r = reinterpret_cast<R8brainResampler*>(handle);
    delete r;
}

// ===== USB Device Manager (delegated to usb_device_manager.cpp) =====
// The JNI methods for USB device manager are defined in usb_device_manager.cpp



// ===== FLAC Cover Art Extraction =====

// Simple Base64 decoder
static std::vector<uint8_t> base64Decode(const char* input) {
    static const unsigned char decodeTable[256] = {
        64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,
        64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,
        64,64,64,64,64,64,64,64,64,64,64,62,64,64,64,63,
        52,53,54,55,56,57,58,59,60,61,64,64,64,64,64,64,
        64, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12,13,14,
        15,16,17,18,19,20,21,22,23,24,25,64,64,64,64,64,
        64,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,
        41,42,43,44,45,46,47,48,49,50,51,64,64,64,64,64,
        64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,
        64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,
        64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,
        64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,
        64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,
        64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,
        64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,
        64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64
    };

    size_t len = strlen(input);
    if (len == 0 || len % 4 != 0) return {};

    std::vector<uint8_t> out;
    out.reserve(len / 4 * 3);

    for (size_t i = 0; i < len; i += 4) {
        unsigned char b0 = decodeTable[static_cast<unsigned char>(input[i])];
        unsigned char b1 = decodeTable[static_cast<unsigned char>(input[i+1])];
        unsigned char b2 = decodeTable[static_cast<unsigned char>(input[i+2])];
        unsigned char b3 = decodeTable[static_cast<unsigned char>(input[i+3])];

        if (b0 > 63 || b1 > 63) return {};

        out.push_back(static_cast<uint8_t>((b0 << 2) | (b1 >> 4)));
        if (input[i+2] != '=') {
            if (b2 > 63) return {};
            out.push_back(static_cast<uint8_t>((b1 << 4) | (b2 >> 2)));
        }
        if (input[i+3] != '=') {
            if (b3 > 63) return {};
            out.push_back(static_cast<uint8_t>((b2 << 6) | b3));
        }
    }
    return out;
}

// Serialize FLAC PICTURE block to binary format that matches Kotlin parsePictureBlock()
static jbyteArray serializePictureBlock(JNIEnv* env, const FLAC__StreamMetadata_Picture& pic) {
    // Calculate total size:
    // 4 (type) + 4 (mime_len) + mime_len + 4 (desc_len) + desc_len + 4 (width) + 4 (height)
    // + 4 (depth) + 4 (colors) + 4 (data_len) + data_len
    size_t mimeLen = strlen(pic.mime_type);
    size_t descLen = strlen((const char*)pic.description);
    size_t totalSize = 4 + 4 + mimeLen + 4 + descLen + 4 + 4 + 4 + 4 + 4 + pic.data_length;

    std::vector<uint8_t> buffer;
    buffer.reserve(totalSize);

    auto writeBE32 = [&buffer](uint32_t val) {
        buffer.push_back(static_cast<uint8_t>((val >> 24) & 0xFF));
        buffer.push_back(static_cast<uint8_t>((val >> 16) & 0xFF));
        buffer.push_back(static_cast<uint8_t>((val >> 8) & 0xFF));
        buffer.push_back(static_cast<uint8_t>(val & 0xFF));
    };

    writeBE32(static_cast<uint32_t>(pic.type));
    writeBE32(static_cast<uint32_t>(mimeLen));
    for (size_t i = 0; i < mimeLen; i++) buffer.push_back(static_cast<uint8_t>(pic.mime_type[i]));
    writeBE32(static_cast<uint32_t>(descLen));
    for (size_t i = 0; i < descLen; i++) buffer.push_back(pic.description[i]);
    writeBE32(pic.width);
    writeBE32(pic.height);
    writeBE32(pic.depth);
    writeBE32(pic.colors);
    writeBE32(pic.data_length);
    for (uint32_t i = 0; i < pic.data_length; i++) buffer.push_back(pic.data[i]);

    jbyteArray result = env->NewByteArray(static_cast<jsize>(buffer.size()));
    if (result) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(buffer.size()),
                                reinterpret_cast<const jbyte*>(buffer.data()));
    }
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_miyamizu_player_playback_NativeEngine_extractFlacPictureBlock(
    JNIEnv* env, jobject thiz, jstring path) {

    const char* cpath = env->GetStringUTFChars(path, nullptr);

    FLAC__Metadata_Chain* chain = FLAC__metadata_chain_new();
    FLAC__Metadata_Iterator* iterator = FLAC__metadata_iterator_new();

    jbyteArray result = nullptr;

    if (chain && iterator) {
        if (FLAC__metadata_chain_read(chain, cpath)) {
            FLAC__metadata_iterator_init(iterator, chain);
            do {
                FLAC__StreamMetadata* block = FLAC__metadata_iterator_get_block(iterator);
                if (block && block->type == FLAC__METADATA_TYPE_PICTURE) {
                    result = serializePictureBlock(env, block->data.picture);
                    break;
                }
            } while (FLAC__metadata_iterator_next(iterator));
        } else {
            LOGE("Failed to read FLAC metadata: %s", cpath);
        }
    }

    if (iterator) FLAC__metadata_iterator_delete(iterator);
    if (chain) FLAC__metadata_chain_delete(chain);
    env->ReleaseStringUTFChars(path, cpath);

    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_miyamizu_player_playback_NativeEngine_extractVorbisCommentPicture(
    JNIEnv* env, jobject thiz, jstring path) {

    const char* cpath = env->GetStringUTFChars(path, nullptr);

    FLAC__Metadata_Chain* chain = FLAC__metadata_chain_new();
    FLAC__Metadata_Iterator* iterator = FLAC__metadata_iterator_new();

    jbyteArray result = nullptr;

    if (chain && iterator) {
        if (FLAC__metadata_chain_read(chain, cpath)) {
            FLAC__metadata_iterator_init(iterator, chain);
            do {
                FLAC__StreamMetadata* block = FLAC__metadata_iterator_get_block(iterator);
                if (block && block->type == FLAC__METADATA_TYPE_VORBIS_COMMENT) {
                    const FLAC__StreamMetadata_VorbisComment& vc = block->data.vorbis_comment;
                    for (uint32_t i = 0; i < vc.num_comments; i++) {
                        const FLAC__StreamMetadata_VorbisComment_Entry& entry = vc.comments[i];
                        const char* comment = (const char*)entry.entry;
                        const char* eq = strchr(comment, '=');
                        if (eq) {
                            size_t keyLen = eq - comment;
                            if (keyLen == 24 && strncasecmp(comment, "METADATA_BLOCK_PICTURE", 22) == 0) {
                                // Found METADATA_BLOCK_PICTURE field
                                std::vector<uint8_t> decoded = base64Decode(eq + 1);
                                if (!decoded.empty()) {
                                    result = env->NewByteArray(static_cast<jsize>(decoded.size()));
                                    if (result) {
                                        env->SetByteArrayRegion(result, 0,
                                            static_cast<jsize>(decoded.size()),
                                            reinterpret_cast<const jbyte*>(decoded.data()));
                                    }
                                    LOGI("Found Vorbis Comment PICTURE: %zu bytes", decoded.size());
                                }
                                break;
                            }
                        }
                    }
                    if (result) break;
                }
            } while (FLAC__metadata_iterator_next(iterator));
        }
    }

    if (iterator) FLAC__metadata_iterator_delete(iterator);
    if (chain) FLAC__metadata_chain_delete(chain);
    env->ReleaseStringUTFChars(path, cpath);

    return result;
}

} // extern "C"

#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

struct FlacInfo {
    int sampleRate = 0;
    int channels = 0;
    int bitsPerSample = 0;
    int64_t totalSamples = 0;
    double durationSec = 0.0;
};

/**
 * FLAC decoder with full decode-to-RAM support.
 * Uses libFLAC StreamDecoder.
 */
class FlacDecoder {
public:
    FlacDecoder();
    ~FlacDecoder();

    // Open file and read stream info (does not decode yet)
    bool open(const char* filePath);

    // Decode entire file to interleaved float [-1.0, 1.0]
    // Returns number of frames decoded
    int64_t decodeAll(std::vector<float>& out);

    // Decode a range (for CUE track slicing)
    // startSample and endSample are frame indices
    int64_t decodeRange(std::vector<float>& out, int64_t startSample, int64_t endSample);

    // Seek to sample position
    bool seek(int64_t sample);

    // Get stream info (valid after open())
    FlacInfo getInfo() const { return info_; }

    void close();

private:
    void* decoder_ = nullptr;  // FLAC__StreamDecoder*
    FlacInfo info_;
    bool isOpen_ = false;

    // Internal decode helpers
    struct DecodeContext {
        std::vector<float>* outBuffer = nullptr;
        int64_t targetFrames = 0;
        int64_t framesReceived = 0;
        int64_t skipFrames = 0;      // for range decode
        int channels = 0;
        int bitsPerSample = 0;
        bool done = false;
    };
};

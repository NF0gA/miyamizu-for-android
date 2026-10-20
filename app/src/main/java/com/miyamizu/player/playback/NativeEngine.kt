package com.miyamizu.player.playback

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

/**
 * MiyamizuネイティブエンジンのJNIブリッジ。
 * AAudio / USB出力 / FLACデコード / リサンプル / USBデバイス管理 / カバーアート抽出を担当。
 */
object NativeEngine {

    private const val TAG = "MiyamizuNative"

    init {
        try {
            System.loadLibrary("miyamizu")
            Log.i(TAG, "Native library loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }

    // ===== AAudio =====
    external fun aaudioOpen(sampleRate: Int, bitDepth: Int, channels: Int): Boolean
    external fun aaudioClose()
    external fun aaudioStart(): Boolean
    external fun aaudioStop()
    external fun aaudioWrite(buffer: FloatArray, offset: Int, numFrames: Int, channels: Int)
    /** @return float[5]: jitterRmsUs, xrunCount, isBitPerfect(1/0), isMmap(1/0), actualSampleRate */
    external fun aaudioGetTelemetry(): FloatArray

    // ===== USB Audio =====
    external fun usbOpen(
        fd: Int,
        interfaceNum: Int,
        altSetting: Int,
        endpoint: Int,
        frameBytes: Int,
        sampleRate: Int,
        bitDepth: Int,
        channels: Int
    ): Boolean

    external fun usbClose()
    external fun usbStart(): Boolean
    external fun usbStop()
    external fun usbSetupRingBuffer(capacityFrames: Int, channels: Int)
    external fun usbWriteRing(buffer: FloatArray, offset: Int, numFrames: Int, channels: Int)
    /** @return float[4]: jitterRmsUs, xrunCount, isBitPerfect(1/0), actualSampleRate */
    external fun usbGetTelemetry(): FloatArray

    // ===== FLAC Decode =====
    external fun decodeFlacToRam(path: String, startSample: Long, endSample: Long): FloatArray
    external fun getFlacInfo(path: String): FlacInfoData?

    // ===== FLAC Cover Art =====
    /**
     * FLACファイルのPICTUREメタデータブロックを抽出。
     * @param path ファイルパス
     * @return PICTUREブロックの生バイト列（MIMEタイプ含む）、なければnull
     */
    external fun extractFlacPictureBlock(path: String): ByteArray?

    /**
     * FLACファイルのVorbis CommentからMETADATA_BLOCK_PICTUREをBase64デコードして取得。
     * 一部エンコーダーはPICTUREブロックではなくVorbis Commentに埋め込む。
     * @param path ファイルパス
     * @return 画像バイト列、なければnull
     */
    external fun extractVorbisCommentPicture(path: String): ByteArray?

    // ===== soxr Resampler =====
    external fun soxrCreate(
        inRate: Double,
        outRate: Double,
        channels: Int,
        phase: Int,
        bandwidth: Int,
        quality: Double
    ): Long

    external fun soxrProcess(
        handle: Long,
        inArray: FloatArray,
        inOffset: Int,
        inLen: Int,
        outArray: FloatArray,
        outLen: Int
    ): Int

    external fun soxrDestroy(handle: Long)

    // ===== r8brain Resampler =====
    external fun r8brainCreate(
        inRate: Double,
        outRate: Double,
        channels: Int,
        preset: Int,
        transBand: Double,
        stopAtten: Double,
        linearPhase: Boolean,
        headroom: Double
    ): Long

    external fun r8brainProcess(
        handle: Long,
        inArray: FloatArray,
        inOffset: Int,
        inLen: Int,
        outArray: FloatArray,
        outLen: Int
    ): Int

    external fun r8brainDestroy(handle: Long)

    // ===== USB Device Manager =====
    external fun nativeUsbAttachDevice(
        fd: Int,
        vendorId: Int,
        productId: Int,
        rawDescriptor: ByteArray
    ): Boolean

    external fun nativeUsbDetachDevice()
    external fun nativeUsbIsAttached(): Boolean
    external fun nativeUsbGetDeviceSummary(): String

    // ===== カバーアートユーティリティ（Kotlin側） =====

    /**
     * FLACファイルからカバーアートBitmapを取得。
     * PICTUREブロック優先、次にVorbis CommentのMETADATA_BLOCK_PICTUREを試行。
     * @param path FLACファイルパス
     * @return Bitmap、取得失敗時はnull
     */
    fun getCoverArt(path: String): Bitmap? {
        // 1. PICTUREブロックを試行
        val pictureBlock = extractFlacPictureBlock(path)
        if (pictureBlock != null && pictureBlock.isNotEmpty()) {
            val bitmap = parsePictureBlock(pictureBlock)
            if (bitmap != null) return bitmap
        }

        // 2. Vorbis CommentのMETADATA_BLOCK_PICTUREを試行
        val vorbisPicture = extractVorbisCommentPicture(path)
        if (vorbisPicture != null && vorbisPicture.isNotEmpty()) {
            return BitmapFactory.decodeByteArray(vorbisPicture, 0, vorbisPicture.size)
        }

        return null
    }

    /**
     * FLAC PICTUREブロックをパースしてBitmapに変換。
     * PICTUREブロックフォーマット:
     *   [4: picture type]
     *   [4: MIME type length]
     *   [N: MIME type string]
     *   [4: description length]
     *   [N: description UTF-8]
     *   [4: width]
     *   [4: height]
     *   [4: color depth]
     *   [4: indexed colors]
     *   [4: data length]
     *   [N: image data]
     */
    private fun parsePictureBlock(data: ByteArray): Bitmap? {
        return try {
            if (data.size < 32) return null

            val buffer = java.nio.ByteBuffer.wrap(data)
            buffer.order(java.nio.ByteOrder.BIG_ENDIAN)

            val pictureType = buffer.int
            val mimeLen = buffer.int
            if (mimeLen < 0 || mimeLen > data.size - buffer.position()) return null

            val mimeBytes = ByteArray(mimeLen)
            buffer.get(mimeBytes)
            val mimeType = String(mimeBytes, Charsets.US_ASCII)

            val descLen = buffer.int
            if (descLen < 0 || descLen > data.size - buffer.position()) return null
            buffer.position(buffer.position() + descLen) // skip description

            val width = buffer.int
            val height = buffer.int
            val colorDepth = buffer.int
            val indexedColors = buffer.int

            val dataLen = buffer.int
            if (dataLen < 0 || dataLen > data.size - buffer.position()) return null

            val imageData = ByteArray(dataLen)
            buffer.get(imageData)

            Log.d(TAG, "PICTURE block: type=$pictureType, mime=$mimeType, " +
                    "${width}x${height}, ${dataLen} bytes")

            BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse PICTURE block", e)
            null
        }
    }
}

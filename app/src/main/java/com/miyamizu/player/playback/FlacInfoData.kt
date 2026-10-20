package com.miyamizu.player.playback

/**
 * FLACファイルのメタデータをJNIから受け取るためのデータクラス
 */
data class FlacInfoData(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val totalSamples: Long,
    val durationSec: Double,
    val metadataFlags: Long  // 将来拡張用（カバーアート有無など
)

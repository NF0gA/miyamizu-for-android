package com.miyamizu.player.data

import android.net.Uri
import java.io.File

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val sampleRate: Int,
    val bitDepth: Int,
    val uri: Uri,
    val albumArtPath: String? = null,
    // CUEシート由来の場合
    val cueStartSample: Long = 0,
    val cueEndSample: Long = 0,
    val parentCueFile: File? = null,
    val trackNumber: Int = 0
) {
    val isCueTrack: Boolean get() = parentCueFile != null
    val isBitPerfectCandidate: Boolean
        get() = bitDepth >= 16 && (sampleRate == 44100 || sampleRate == 48000 ||
                sampleRate == 88200 || sampleRate == 96000 ||
                sampleRate == 176400 || sampleRate == 192000)

    companion object {
        fun fromFile(file: File): Track {
            return Track(
                id = file.absolutePath,
                title = file.nameWithoutExtension,
                artist = "Unknown",
                album = file.parentFile?.name ?: "",
                durationMs = 0,
                sampleRate = 44100,
                bitDepth = 16,
                uri = Uri.fromFile(file)
            )
        }
    }
}

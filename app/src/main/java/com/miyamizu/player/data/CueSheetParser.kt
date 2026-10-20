package com.miyamizu.player.data

import java.io.File

data class CueTrack(
    val number: Int,
    val title: String,
    val performer: String,
    val index01: Long,
    val index00: Long? = null
)

data class CueSheet(
    val file: String,
    val performer: String,
    val title: String,
    val tracks: List<CueTrack>
) {
    fun getTrackDurationSamples(trackIndex: Int, totalSamples: Long, sampleRate: Int = 44100): Long {
        val start = tracks.getOrNull(trackIndex)?.index01 ?: 0
        val end = if (trackIndex + 1 < tracks.size) tracks[trackIndex + 1].index01 else totalSamples
        return end - start
    }
}

object CueSheetParser {
    fun parse(cueText: String, parentDir: File, sampleRate: Int = 44100): CueSheet {
        val lines = cueText.lines().map { it.trim() }
        var fileName = ""
        val tracks = mutableListOf<CueTrack>()
        var currentTrack: CueTrack? = null
        var globalPerformer = ""
        var globalTitle = ""

        for (line in lines) {
            when {
                line.startsWith("FILE ", ignoreCase = true) -> {
                    val match = Regex("""([^"]+)""").find(line)
                    fileName = match?.groupValues?.get(1) ?: ""
                }
                line.startsWith("PERFORMER ", ignoreCase = true) && !line.startsWith("TRACK ", ignoreCase = true) -> {
                    globalPerformer = extractQuoted(line)
                }
                line.startsWith("TITLE ", ignoreCase = true) && !line.startsWith("TRACK ", ignoreCase = true) -> {
                    globalTitle = extractQuoted(line)
                }
                line.startsWith("TRACK ", ignoreCase = true) -> {
                    currentTrack?.let { tracks.add(it) }
                    val num = line.split(" ").getOrNull(1)?.toIntOrNull() ?: 0
                    currentTrack = CueTrack(num, "", "", 0)
                }
                line.startsWith("TITLE ", ignoreCase = true) && currentTrack != null -> {
                    currentTrack = currentTrack!!.copy(title = extractQuoted(line))
                }
                line.startsWith("PERFORMER ", ignoreCase = true) && currentTrack != null -> {
                    currentTrack = currentTrack!!.copy(performer = extractQuoted(line))
                }
                line.startsWith("INDEX 01 ", ignoreCase = true) -> {
                    val timeStr = line.substringAfter("INDEX 01 ").trim()
                    val samples = timeToSamples(timeStr, sampleRate)
                    currentTrack = currentTrack!!.copy(index01 = samples)
                }
                line.startsWith("INDEX 00 ", ignoreCase = true) -> {
                    val timeStr = line.substringAfter("INDEX 00 ").trim()
                    val samples = timeToSamples(timeStr, sampleRate)
                    currentTrack = currentTrack!!.copy(index00 = samples)
                }
            }
        }
        currentTrack?.let { tracks.add(it) }

        return CueSheet(fileName, globalPerformer, globalTitle, tracks)
    }

    private fun extractQuoted(line: String): String {
        val match = Regex("""([^"]+)""").find(line)
        return match?.groupValues?.get(1) ?: ""
    }

    private fun timeToSamples(time: String, sampleRate: Int): Long {
        // MM:FF:FF format (75 frames/sec)
        val parts = time.split(":")
        if (parts.size != 3) return 0
        val minutes = parts[0].toIntOrNull() ?: 0
        val seconds = parts[1].toIntOrNull() ?: 0
        val frames = parts[2].toIntOrNull() ?: 0
        return (minutes * 60L + seconds) * sampleRate + (frames * sampleRate / 75L)
    }
}

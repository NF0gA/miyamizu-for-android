package com.miyamizu.player.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.miyamizu.player.playback.NativeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 音楽ライブラリ管理。
 * 指定ディレクトリを走査してFLACファイルとCUEシートを検出し、
 * Trackリストを構築する。
 *
 * FLACメタデータはJNI経由で取得し、正確なサンプルレート・ビット深度を設定する。
 */
object MusicLibrary {

    private const val TAG = "MusicLibrary"

    // ライブラリルートディレクトリ（外部ストレージ）
    private var libraryRoot: File? = null

    // 全トラックリスト
    private val allTracks = mutableListOf<Track>()

    // アルバムごとのトラックマップ（ディレクトリパス → トラックリスト）
    private val albumMap = mutableMapOf<String, List<Track>>()

    // スキャン済みフラグ
    private var isScanned = false

    /**
     * ライブラリルートを設定。
     */
    fun setRoot(root: File) {
        libraryRoot = root
        isScanned = false
    }

    /**
     * ディレクトリを走査してライブラリを構築。
     * FLACファイルとCUEシートを検出し、Trackオブジェクトを生成。
     */
    suspend fun scanDirectory(context: Context) = withContext(Dispatchers.IO) {
        val root = libraryRoot ?: return@withContext

        allTracks.clear()
        albumMap.clear()

        Log.i(TAG, "Scanning library: ${root.absolutePath}")

        // 再帰的にFLACファイルを検索
        val flacFiles = root.walkTopDown()
            .filter { it.isFile && it.extension.equals("flac", ignoreCase = true) }
            .toList()

        Log.i(TAG, "Found ${flacFiles.size} FLAC files")

        for (flacFile in flacFiles) {
            val cueFile = File(flacFile.parentFile, flacFile.nameWithoutExtension + ".cue")

            if (cueFile.exists()) {
                // CUEシートあり: トラック単位で分割
                parseCueAlbum(flacFile, cueFile)
            } else {
                // CUEなし: ファイル単体で1トラック
                parseSingleTrack(flacFile)
            }
        }

        // アルバムマップ構築
        buildAlbumMap()

        isScanned = true
        Log.i(TAG, "Library scan complete: ${allTracks.size} tracks, ${albumMap.size} albums")
    }

    /**
     * 単一FLACファイルを1トラックとして登録。
     */
    private fun parseSingleTrack(flacFile: File) {
        // JNI経由でFLACメタデータを取得
        val info = NativeEngine.getFlacInfo(flacFile.absolutePath)

        val track = Track(
            id = flacFile.absolutePath.hashCode().toLong(),
            title = flacFile.nameWithoutExtension,
            artist = "Unknown Artist",
            album = flacFile.parentFile?.name ?: "Unknown Album",
            albumArtist = "Unknown Artist",
            trackNumber = 0,
            discNumber = 0,
            durationSec = info?.durationSec ?: 0.0,
            sampleRate = info?.sampleRate ?: 44100,
            bitsPerSample = info?.bitsPerSample ?: 16,
            channels = info?.channels ?: 2,
            filePath = flacFile.absolutePath,
            coverArtPath = null,
            isCueTrack = false,
            cueStartSample = 0,
            cueEndSample = 0
        )

        allTracks.add(track)
    }

    /**
     * CUEシート付きFLACアルバムをパース。
     */
    private fun parseCueAlbum(flacFile: File, cueFile: File) {
        val cueSheet = CueSheetParser.parse(cueFile)
        if (cueSheet == null) {
            Log.w(TAG, "Failed to parse CUE: ${cueFile.absolutePath}")
            // CUEパース失敗時は単一トラックとして扱う
            parseSingleTrack(flacFile)
            return
        }

        // JNI経由でFLACメタデータを取得
        val info = NativeEngine.getFlacInfo(flacFile.absolutePath)
        val flacTotalSamples = info?.totalSamples ?: 0

        for ((index, cueTrack) in cueSheet.tracks.withIndex()) {
            // 開始サンプル
            val startSample = CueSheetParser.timecodeToSamples(
                cueTrack.index01, info?.sampleRate ?: 44100
            )

            // 終了サンプル（次トラックの開始 or ファイル終了）
            val endSample = if (index + 1 < cueSheet.tracks.size) {
                CueSheetParser.timecodeToSamples(
                    cueSheet.tracks[index + 1].index01,
                    info?.sampleRate ?: 44100
                )
            } else {
                flacTotalSamples.toInt()
            }

            val duration = if (info?.sampleRate != null && info.sampleRate > 0) {
                (endSample - startSample).toDouble() / info.sampleRate
            } else {
                0.0
            }

            val track = Track(
                id = "${flacFile.absolutePath}_${cueTrack.trackNumber}".hashCode().toLong(),
                title = cueTrack.title.ifEmpty { "Track ${cueTrack.trackNumber}" },
                artist = cueTrack.performer.ifEmpty { cueSheet.performer },
                album = cueSheet.title.ifEmpty { flacFile.parentFile?.name ?: "Unknown Album" },
                albumArtist = cueSheet.performer,
                trackNumber = cueTrack.trackNumber,
                discNumber = 0,
                durationSec = duration,
                sampleRate = info?.sampleRate ?: 44100,
                bitsPerSample = info?.bitsPerSample ?: 16,
                channels = info?.channels ?: 2,
                filePath = flacFile.absolutePath,
                coverArtPath = null,
                isCueTrack = true,
                cueStartSample = startSample,
                cueEndSample = endSample
            )

            allTracks.add(track)
        }

        Log.i(TAG, "Parsed CUE album: ${cueSheet.title}, ${cueSheet.tracks.size} tracks")
    }

    /**
     * アルバムマップを構築。
     */
    private fun buildAlbumMap() {
        albumMap.clear()
        val grouped = allTracks.groupBy { it.album }
        albumMap.putAll(grouped)
    }

    /**
     * 全トラックを取得。
     */
    fun getAllTracks(): List<Track> = allTracks.toList()

    /**
     * アルバム一覧を取得。
     */
    fun getAlbums(): List<String> = albumMap.keys.toList().sorted()

    /**
     * アルバム名からトラックリストを取得。
     */
    fun getTracksByAlbum(album: String): List<Track> {
        return albumMap[album]?.sortedBy { it.trackNumber } ?: emptyList()
    }

    /**
     * アルバムカバーアートパスを取得（将来拡張）。
     */
    fun getAlbumCoverPath(album: String): String? {
        // ディレクトリ内のcover.jpg / folder.jpg / front.jpgを探す
        val tracks = albumMap[album] ?: return null
        val firstTrack = tracks.firstOrNull() ?: return null
        val dir = File(firstTrack.filePath).parentFile ?: return null

        val coverNames = listOf("cover.jpg", "folder.jpg", "front.jpg",
                                "cover.png", "folder.png", "front.png")
        for (name in coverNames) {
            val cover = File(dir, name)
            if (cover.exists()) return cover.absolutePath
        }
        return null
    }

    /**
     * 検索。
     */
    fun search(query: String): List<Track> {
        val q = query.lowercase()
        return allTracks.filter {
            it.title.lowercase().contains(q) ||
            it.artist.lowercase().contains(q) ||
            it.album.lowercase().contains(q)
        }
    }

    /**
     * スキャン済みか。
     */
    fun isLibraryScanned(): Boolean = isScanned
}

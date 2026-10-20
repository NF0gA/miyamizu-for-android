package com.miyamizu.player.playback

import android.app.Application
import android.content.Context
import com.miyamizu.player.db.PlaylistRepository

/**
 * MiyamizuPlayerのシングルトン提供クラス。
 * Applicationクラス経由でアプリ全体で1つのインスタンスを共有する。
 *
 * 依存注入フレームワーク（Hilt等）を使わないシンプルな実装。
 * 将来的にHilt等に置き換え可能な設計。
 */
object MiyamizuPlayerProvider {

    @Volatile
    private var instance: MiyamizuPlayer? = null

    /**
     * MiyamizuPlayerのングルトンインスタンスを取得。
     * 初回呼び出し時に初期化される。
     */
    fun getInstance(context: Context): MiyamizuPlayer {
        return instance ?: synchronized(this) {
            instance ?: createPlayer(context.applicationContext).also {
                instance = it
            }
        }
    }

    /**
     * MiyamizuPlayerを初期化して構築する。
     * PlaybackEngine + GaplessController + RamPlaybackQueue + PlaylistRepository を組み立てる。
     */
    private fun createPlayer(context: Context): MiyamizuPlayer {
        val engine = PlaybackEngine.getInstance()
        val gaplessController = GaplessController(engine)
        val ramQueue = RamPlaybackQueue(engine)
        val playlistRepository = PlaylistRepository(context)

        // 各コンポーネントを相互に接続
        engine.setGaplessController(gaplessController)

        // 設定をPlaybackEngineに適用
        val settingsRepo = com.miyamizu.player.settings.SettingsRepository(context)
        // 非同期で設定を読み込んで適用
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val config = settingsRepo.getResamplerConfig()
            engine.setResamplerConfig(config)

            // RAM再生設定も適用
            settingsRepo.ramPlaybackEnabled.collect { enabled ->
                if (enabled) ramQueue.enable() else ramQueue.disable()
            }
        }

        return MiyamizuPlayer(engine, gaplessController, ramQueue, playlistRepository)
    }

    /**
     * インスタンスが初期化済みか確認。
     */
    fun isInitialized(): Boolean = instance != null

    /**
     * プレーヤーを解放（アプリ終了時に呼ぶ）。
     */
    fun release() {
        instance?.release()
        instance = null
    }
}

// GlobalScopeの代わりに適切なスコープを使うための拡張
import kotlinx.coroutines.*

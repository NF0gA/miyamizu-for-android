package com.miyamizu.player.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miyamizu.player.playback.PlaybackEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 設定画面のViewModel。
 * SettingsRepositoryのFlowをUI用に開し、設定変更を処理する。
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    // ===== リサンプラー設定 =====
    val resamplerEnabled: StateFlow<Boolean> = repository.resamplerEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val resamplerEngine: StateFlow<String> = repository.resamplerEngine
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "soxr")

    val resamplerTargetRate: StateFlow<Int> = repository.resamplerTargetRate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 48000)

    val resamplerSoxrPhase: StateFlow<Int> = repository.resamplerSoxrPhase
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val resamplerSoxrBandwidth: StateFlow<Int> = repository.resamplerSoxrBandwidth
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val resamplerSoxrQuality: StateFlow<Double> = repository.resamplerSoxrQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10.0)

    val resamplerR8brainPreset: StateFlow<Int> = repository.resamplerR8brainPreset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    // ===== 出力モード =====
    val outputMode: StateFlow<String> = repository.outputMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "auto")

    // ===== RAM再生 =====
    val ramPlaybackEnabled: StateFlow<Boolean> = repository.ramPlaybackEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ===== テレメトリ =====
    val telemetryEnabled: StateFlow<Boolean> = repository.telemetryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // ===== テーマ =====
    val followSystemTheme: StateFlow<Boolean> = repository.followSystemTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val darkTheme: StateFlow<Boolean> = repository.darkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // ===== 設定変更 =====

    fun setResamplerEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setResamplerEnabled(enabled) }
    }

    fun setResamplerEngine(engine: String) {
        viewModelScope.launch { repository.setResamplerEngine(engine) }
    }

    fun setResamplerTargetRate(rate: Int) {
        viewModelScope.launch { repository.setResamplerTargetRate(rate) }
    }

    fun setResamplerSoxrPhase(phase: Int) {
        viewModelScope.launch { repository.setResamplerSoxrPhase(phase) }
    }

    fun setResamplerSoxrBandwidth(bandwidth: Int) {
        viewModelScope.launch { repository.setResamplerSoxrBandwidth(bandwidth) }
    }

    fun setResamplerSoxrQuality(quality: Double) {
        viewModelScope.launch { repository.setResamplerSoxrQuality(quality) }
    }

    fun setResamplerR8brainPreset(preset: Int) {
        viewModelScope.launch { repository.setResamplerR8brainPreset(preset) }
    }

    fun setOutputMode(mode: String) {
        viewModelScope.launch { repository.setOutputMode(mode) }
    }

    fun setRamPlaybackEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setRamPlaybackEnabled(enabled) }
    }

    fun setTelemetryEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setTelemetryEnabled(enabled) }
    }

    fun setFollowSystemTheme(follow: Boolean) {
        viewModelScope.launch { repository.setFollowSystemTheme(follow) }
    }

    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch { repository.setDarkTheme(dark) }
    }

    /**
     * 現在の設定をPlaybackEngineに適用。
     */
    fun applyToPlaybackEngine(engine: PlaybackEngine) {
        viewModelScope.launch {
            val config = repository.getResamplerConfig()
            engine.setResamplerConfig(config)
        }
    }
}

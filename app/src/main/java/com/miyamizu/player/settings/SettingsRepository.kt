package com.miyamizu.player.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.miyamizu.player.playback.PlaybackEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 設定の永続化リポジトリ。
 * DataStore Preferencesを使用。
 */
class SettingsRepository(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "miyamizu_settings")

    // ===== 設定キー =====
    companion object Keys {
        val RESAMPLER_ENABLED = booleanPreferencesKey("resampler_enabled")
        val RESAMPLER_ENGINE = stringPreferencesKey("resampler_engine")
        val RESAMPLER_TARGET_RATE = intPreferencesKey("resampler_target_rate")
        val RESAMPLER_SOXR_PHASE = intPreferencesKey("resampler_soxr_phase")
        val RESAMPLER_SOXR_BANDWIDTH = intPreferencesKey("resampler_soxr_bandwidth")
        val RESAMPLER_SOXR_QUALITY = doublePreferencesKey("resampler_soxr_quality")
        val RESAMPLER_R8BRAIN_PRESET = intPreferencesKey("resampler_r8brain_preset")
        val OUTPUT_MODE = stringPreferencesKey("output_mode")
        val RAM_PLAYBACK_ENABLED = booleanPreferencesKey("ram_playback_enabled")
        val TELEMETRY_ENABLED = booleanPreferencesKey("telemetry_enabled")
        val FOLLOW_SYSTEM_THEME = booleanPreferencesKey("follow_system_theme")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }

    // ===== デフォルト値 =====
    object Defaults {
        const val RESAMPLER_ENABLED = false
        const val RESAMPLER_ENGINE = "soxr"
        const val RESAMPLER_TARGET_RATE = 48000
        const val RESAMPLER_SOXR_PHASE = 1
        const val RESAMPLER_SOXR_BANDWIDTH = 0
        const val RESAMPLER_SOXR_QUALITY = 10.0
        const val RESAMPLER_R8BRAIN_PRESET = 1
        const val OUTPUT_MODE = "auto"
        const val RAM_PLAYBACK_ENABLED = false
        const val TELEMETRY_ENABLED = true
        const val FOLLOW_SYSTEM_THEME = true
        const val DARK_THEME = true
    }

    // ===== 読み取りFlow =====
    val resamplerEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.RESAMPLER_ENABLED] ?: Defaults.RESAMPLER_ENABLED }

    val resamplerEngine: Flow<String> = context.dataStore.data
        .map { it[Keys.RESAMPLER_ENGINE] ?: Defaults.RESAMPLER_ENGINE }

    val resamplerTargetRate: Flow<Int> = context.dataStore.data
        .map { it[Keys.RESAMPLER_TARGET_RATE] ?: Defaults.RESAMPLER_TARGET_RATE }

    val resamplerSoxrPhase: Flow<Int> = context.dataStore.data
        .map { it[Keys.RESAMPLER_SOXR_PHASE] ?: Defaults.RESAMPLER_SOXR_PHASE }

    val resamplerSoxrBandwidth: Flow<Int> = context.dataStore.data
        .map { it[Keys.RESAMPLER_SOXR_BANDWIDTH] ?: Defaults.RESAMPLER_SOXR_BANDWIDTH }

    val resamplerSoxrQuality: Flow<Double> = context.dataStore.data
        .map { it[Keys.RESAMPLER_SOXR_QUALITY] ?: Defaults.RESAMPLER_SOXR_QUALITY }

    val resamplerR8brainPreset: Flow<Int> = context.dataStore.data
        .map { it[Keys.RESAMPLER_R8BRAIN_PRESET] ?: Defaults.RESAMPLER_R8BRAIN_PRESET }

    val outputMode: Flow<String> = context.dataStore.data
        .map { it[Keys.OUTPUT_MODE] ?: Defaults.OUTPUT_MODE }

    val ramPlaybackEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.RAM_PLAYBACK_ENABLED] ?: Defaults.RAM_PLAYBACK_ENABLED }

    val telemetryEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.TELEMETRY_ENABLED] ?: Defaults.TELEMETRY_ENABLED }

    val followSystemTheme: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.FOLLOW_SYSTEM_THEME] ?: Defaults.FOLLOW_SYSTEM_THEME }

    val darkTheme: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.DARK_THEME] ?: Defaults.DARK_THEME }

    // ===== 書き込み =====
    suspend fun setResamplerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RESAMPLER_ENABLED] = enabled }
    }

    suspend fun setResamplerEngine(engine: String) {
        context.dataStore.edit { it[Keys.RESAMPLER_ENGINE] = engine }
    }

    suspend fun setResamplerTargetRate(rate: Int) {
        context.dataStore.edit { it[Keys.RESAMPLER_TARGET_RATE] = rate }
    }

    suspend fun setResamplerSoxrPhase(phase: Int) {
        context.dataStore.edit { it[Keys.RESAMPLER_SOXR_PHASE] = phase }
    }

    suspend fun setResamplerSoxrBandwidth(bandwidth: Int) {
        context.dataStore.edit { it[Keys.RESAMPLER_SOXR_BANDWIDTH] = bandwidth }
    }

    suspend fun setResamplerSoxrQuality(quality: Double) {
        context.dataStore.edit { it[Keys.RESAMPLER_SOXR_QUALITY] = quality }
    }

    suspend fun setResamplerR8brainPreset(preset: Int) {
        context.dataStore.edit { it[Keys.RESAMPLER_R8BRAIN_PRESET] = preset }
    }

    suspend fun setOutputMode(mode: String) {
        context.dataStore.edit { it[Keys.OUTPUT_MODE] = mode }
    }

    suspend fun setRamPlaybackEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RAM_PLAYBACK_ENABLED] = enabled }
    }

    suspend fun setTelemetryEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TELEMETRY_ENABLED] = enabled }
    }

    suspend fun setFollowSystemTheme(follow: Boolean) {
        context.dataStore.edit { it[Keys.FOLLOW_SYSTEM_THEME] = follow }
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = dark }
    }

    /**
     * 現在の設定をPlaybackEngine.ResamplerConfigに変換。
     */
    suspend fun getResamplerConfig(): PlaybackEngine.ResamplerConfig {
        val prefs = context.dataStore.data.first()
        return PlaybackEngine.ResamplerConfig(
            enabled = prefs[Keys.RESAMPLER_ENABLED] ?: Defaults.RESAMPLER_ENABLED,
            engine = when (prefs[Keys.RESAMPLER_ENGINE] ?: Defaults.RESAMPLER_ENGINE) {
                "r8brain" -> PlaybackEngine.ResamplerEngine.R8BRAIN
                else -> PlaybackEngine.ResamplerEngine.SOXR
            },
            targetSampleRate = prefs[Keys.RESAMPLER_TARGET_RATE] ?: Defaults.RESAMPLER_TARGET_RATE,
            soxrPhase = prefs[Keys.RESAMPLER_SOXR_PHASE] ?: Defaults.RESAMPLER_SOXR_PHASE,
            soxrBandwidth = prefs[Keys.RESAMPLER_SOXR_BANDWIDTH] ?: Defaults.RESAMPLER_SOXR_BANDWIDTH,
            soxrQuality = prefs[Keys.RESAMPLER_SOXR_QUALITY] ?: Defaults.RESAMPLER_SOXR_QUALITY,
            r8brainPreset = prefs[Keys.RESAMPLER_R8BRAIN_PRESET] ?: Defaults.RESAMPLER_R8BRAIN_PRESET
        )
    }
}

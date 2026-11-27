package com.victustune.player.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.victustune.player.data.model.AudioQuality
import com.victustune.player.data.model.RepeatMode
import com.victustune.player.data.model.SliderColorMode
import com.victustune.player.data.model.SliderPreferences
import com.victustune.player.data.model.SliderStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "victustune_settings")

enum class SortOption {
    TITLE,
    ARTIST,
    DATE_ADDED,
    DURATION
}

class PreferencesManager(private val context: Context) {

    private object PreferencesKeys {
        val SHUFFLE = booleanPreferencesKey("shuffle_mode")
        val REPEAT = stringPreferencesKey("repeat_mode")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_BANDS = stringPreferencesKey("eq_bands")
        val SLEEP_TIMER_END = longPreferencesKey("sleep_timer_end")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val MAX_CACHE_SIZE_MB = intPreferencesKey("max_cache_size_mb")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val SLIDER_STYLE = stringPreferencesKey("slider_style")
        val SLIDER_COLOR_MODE = stringPreferencesKey("slider_color_mode")
        val SHOW_SLIDER_THUMB = booleanPreferencesKey("show_slider_thumb")
    }

    val downloadQualityFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DOWNLOAD_QUALITY] ?: "High"
    }

    suspend fun setDownloadQuality(quality: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DOWNLOAD_QUALITY] = quality
        }
    }

    val downloadWifiOnlyFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DOWNLOAD_WIFI_ONLY] ?: false
    }

    suspend fun setDownloadWifiOnly(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DOWNLOAD_WIFI_ONLY] = enabled
        }
    }

    val audioQualityFlow: Flow<AudioQuality> = context.dataStore.data.map { preferences ->
        val name = preferences[PreferencesKeys.AUDIO_QUALITY] ?: AudioQuality.AUTO.name
        try {
            AudioQuality.valueOf(name)
        } catch (e: Exception) {
            AudioQuality.AUTO
        }
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUDIO_QUALITY] = quality.name
        }
    }

    val maxCacheSizeMbFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.MAX_CACHE_SIZE_MB] ?: 512
    }

    suspend fun setMaxCacheSizeMb(sizeMb: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAX_CACHE_SIZE_MB] = sizeMb
        }
    }

    val shuffleFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SHUFFLE] ?: false
    }

    suspend fun setShuffle(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHUFFLE] = enabled
        }
    }

    val repeatModeFlow: Flow<RepeatMode> = context.dataStore.data.map { preferences ->
        val name = preferences[PreferencesKeys.REPEAT] ?: RepeatMode.OFF.name
        try {
            RepeatMode.valueOf(name)
        } catch (e: Exception) {
            RepeatMode.OFF
        }
    }

    suspend fun setRepeatMode(mode: RepeatMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.REPEAT] = mode.name
        }
    }

    val sortOptionFlow: Flow<SortOption> = context.dataStore.data.map { preferences ->
        val name = preferences[PreferencesKeys.SORT_OPTION] ?: SortOption.TITLE.name
        try {
            SortOption.valueOf(name)
        } catch (e: Exception) {
            SortOption.TITLE
        }
    }

    suspend fun setSortOption(option: SortOption) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_OPTION] = option.name
        }
    }

    val equalizerEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.EQ_ENABLED] ?: false
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EQ_ENABLED] = enabled
        }
    }

    val equalizerPresetFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.EQ_PRESET] ?: "Flat"
    }

    suspend fun setEqualizerPreset(presetName: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EQ_PRESET] = presetName
        }
    }

    val equalizerBandsFlow: Flow<List<Int>> = context.dataStore.data.map { preferences ->
        val raw = preferences[PreferencesKeys.EQ_BANDS] ?: "0,0,0,0,0"
        raw.split(",").mapNotNull { it.trim().toIntOrNull() }.takeIf { it.size == 5 } ?: listOf(0, 0, 0, 0, 0)
    }

    suspend fun setEqualizerBands(bands: List<Int>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EQ_BANDS] = bands.joinToString(",")
        }
    }

    val sliderPreferencesFlow: Flow<SliderPreferences> = context.dataStore.data.map { preferences ->
        val styleName = preferences[PreferencesKeys.SLIDER_STYLE] ?: SliderStyle.WAVEFORM.name
        val style = try {
            SliderStyle.valueOf(styleName)
        } catch (_: Exception) {
            SliderStyle.WAVEFORM
        }

        val modeName = preferences[PreferencesKeys.SLIDER_COLOR_MODE] ?: SliderColorMode.DYNAMIC.name
        val colorMode = try {
            SliderColorMode.valueOf(modeName)
        } catch (_: Exception) {
            SliderColorMode.DYNAMIC
        }

        val showThumb = preferences[PreferencesKeys.SHOW_SLIDER_THUMB] ?: true
        SliderPreferences(
            style = style,
            colorMode = colorMode,
            showThumb = showThumb
        )
    }

    suspend fun setSliderStyle(style: SliderStyle) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SLIDER_STYLE] = style.name
        }
    }

    suspend fun setSliderColorMode(mode: SliderColorMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SLIDER_COLOR_MODE] = mode.name
        }
    }

    suspend fun setShowSliderThumb(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_SLIDER_THUMB] = show
        }
    }
}

package com.victustune.player.data.model

import androidx.compose.runtime.Immutable

enum class SliderStyle(val label: String) {
    SQUIGGLY("Squiggly"),
    WAVEFORM("Waveform"),
    MINIMAL("Minimal"),
    THICK_PILL("Thick Pill")
}

enum class SliderColorMode(val label: String) {
    DYNAMIC("Dynamic (Cover Art)"),
    MONOCHROME("Monochrome White")
}

@Immutable
data class SliderPreferences(
    val style: SliderStyle = SliderStyle.WAVEFORM,
    val colorMode: SliderColorMode = SliderColorMode.DYNAMIC,
    val showThumb: Boolean = true
)

package com.victustune.player.playback

import android.os.Bundle
import com.victustune.player.data.model.LoopRegion

object LoopSessionCommands {
    const val ACTION_SET_LOOP_START = "com.victustune.player.action.SET_LOOP_START"
    const val ACTION_SET_LOOP_END = "com.victustune.player.action.SET_LOOP_END"
    const val ACTION_TOGGLE_LOOP = "com.victustune.player.action.TOGGLE_LOOP"
    const val ACTION_CLEAR_LOOP = "com.victustune.player.action.CLEAR_LOOP"

    const val EXTRA_POSITION_MS = "ms"
    const val EXTRA_FROM_DRAG = "from_drag"

    const val EXTRA_LOOP_HAS_START = "loop_has_start"
    const val EXTRA_LOOP_START_MS = "loop_start_ms"
    const val EXTRA_LOOP_HAS_END = "loop_has_end"
    const val EXTRA_LOOP_END_MS = "loop_end_ms"
    const val EXTRA_LOOP_ACTIVE = "loop_active"

    fun LoopRegion.toSessionBundle(): Bundle = Bundle().apply {
        putBoolean(EXTRA_LOOP_HAS_START, startMs != null)
        startMs?.let { putLong(EXTRA_LOOP_START_MS, it) }
        putBoolean(EXTRA_LOOP_HAS_END, endMs != null)
        endMs?.let { putLong(EXTRA_LOOP_END_MS, it) }
        putBoolean(EXTRA_LOOP_ACTIVE, isActive)
    }

    fun fromBundle(bundle: Bundle?): LoopRegion? {
        if (bundle == null || !bundle.containsKey(EXTRA_LOOP_ACTIVE)) return null
        val start = if (bundle.getBoolean(EXTRA_LOOP_HAS_START, false)) {
            bundle.getLong(EXTRA_LOOP_START_MS, 0L)
        } else null
        val end = if (bundle.getBoolean(EXTRA_LOOP_HAS_END, false)) {
            bundle.getLong(EXTRA_LOOP_END_MS, 0L)
        } else null
        val active = bundle.getBoolean(EXTRA_LOOP_ACTIVE, false)
        return LoopRegion(startMs = start, endMs = end, isActive = active)
    }
}

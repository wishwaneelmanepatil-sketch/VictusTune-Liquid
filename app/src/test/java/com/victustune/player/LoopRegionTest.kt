package com.victustune.player

import com.victustune.player.data.model.LoopRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopRegionTest {

    @Test
    fun settingEndBeforeStartDefaultsStartToZeroAndActivates() {
        val region = LoopRegion().withEndSet(ms = 45_000L, maxDurationMs = 180_000L)
        assertEquals(0L, region.startMs)
        assertEquals(45_000L, region.endMs)
        assertTrue(region.isActive)
        assertTrue(region.isValidActive)
    }

    @Test
    fun settingStartPastEndClearsEndAndDeactivatesLoop() {
        val active = LoopRegion(startMs = 20_000L, endMs = 40_000L, isActive = true)
        val updated = active.withStartSet(ms = 55_000L, maxDurationMs = 180_000L, fromDrag = false)
        assertEquals(55_000L, updated.startMs)
        assertNull(updated.endMs)
        assertFalse(updated.isActive)
        assertFalse(updated.isValidActive)
    }

    @Test
    fun draggingHandlesEnforcesMinimumOneSecondSpanAndPreventsCrossing() {
        val active = LoopRegion(startMs = 20_000L, endMs = 30_000L, isActive = true)

        val draggedA = active.withStartSet(ms = 35_000L, maxDurationMs = 120_000L, fromDrag = true)
        assertEquals(29_000L, draggedA.startMs)
        assertEquals(30_000L, draggedA.endMs)
        assertTrue(draggedA.isValidActive)

        val draggedB = active.withEndSet(ms = 10_000L, maxDurationMs = 120_000L, fromDrag = true)
        assertEquals(20_000L, draggedB.startMs)
        assertEquals(21_000L, draggedB.endMs)
        assertTrue(draggedB.isValidActive)
    }

    @Test
    fun settingEndVeryCloseToStartEnforcesOneSecondMinimum() {
        val withStart = LoopRegion().withStartSet(ms = 10_000L, maxDurationMs = 120_000L)
        val updated = withStart.withEndSet(ms = 10_300L, maxDurationMs = 120_000L, fromDrag = false)
        assertEquals(10_000L, updated.startMs)
        assertEquals(11_000L, updated.endMs)
        assertTrue(updated.isValidActive)
    }

    @Test
    fun toggleRequiresBothValidBounds() {
        val incomplete = LoopRegion(startMs = 5_000L, endMs = null, isActive = false).toggled()
        assertFalse(incomplete.isActive)

        val ready = LoopRegion(startMs = 5_000L, endMs = 15_000L, isActive = false).toggled()
        assertTrue(ready.isActive)

        val paused = ready.toggled()
        assertFalse(paused.isActive)
    }
}

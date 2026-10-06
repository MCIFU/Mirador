package com.mcifu.mirador.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackRulesTest {

    @Test
    fun `gesture zones split the screen 35-30-35`() {
        assertEquals(GestureZone.LEFT, PlaybackRules.zoneOf(100f, 1080f))
        assertEquals(GestureZone.CENTER, PlaybackRules.zoneOf(540f, 1080f))
        assertEquals(GestureZone.RIGHT, PlaybackRules.zoneOf(1000f, 1080f))
        assertEquals(GestureZone.CENTER, PlaybackRules.zoneOf(10f, 0f))
    }

    @Test
    fun `fast seek escalates 2x 4x 8x 16x and stays at 16x`() {
        assertEquals(2, PlaybackRules.fastSeekRate(0))
        assertEquals(2, PlaybackRules.fastSeekRate(1_499))
        assertEquals(4, PlaybackRules.fastSeekRate(1_500))
        assertEquals(8, PlaybackRules.fastSeekRate(3_000))
        assertEquals(16, PlaybackRules.fastSeekRate(4_500))
        assertEquals(16, PlaybackRules.fastSeekRate(60_000))
    }

    @Test
    fun `fast seek steps respect rate and bounds`() {
        // 8x durante 150 ms = 1,2 s de vídeo.
        assertEquals(11_200, PlaybackRules.fastSeekStep(10_000, 8, forward = true, elapsedMs = 150, durationMs = 60_000))
        assertEquals(8_800, PlaybackRules.fastSeekStep(10_000, 8, forward = false, elapsedMs = 150, durationMs = 60_000))
        assertEquals(0, PlaybackRules.fastSeekStep(500, 16, forward = false, elapsedMs = 150, durationMs = 60_000))
        assertEquals(60_000, PlaybackRules.fastSeekStep(59_900, 16, forward = true, elapsedMs = 150, durationMs = 60_000))
    }

    @Test
    fun `double tap seeks 10 seconds within bounds`() {
        assertEquals(40_000L, PlaybackRules.doubleTapTarget(30_000, GestureZone.RIGHT, 60_000))
        assertEquals(0L, PlaybackRules.doubleTapTarget(4_000, GestureZone.LEFT, 60_000))
        assertEquals(60_000L, PlaybackRules.doubleTapTarget(55_000, GestureZone.RIGHT, 60_000))
        assertNull(PlaybackRules.doubleTapTarget(30_000, GestureZone.CENTER, 60_000))
    }

    @Test
    fun `seek bar fraction to position`() {
        assertEquals(30_000L, PlaybackRules.positionForFraction(0.5f, 60_000))
        assertEquals(60_000L, PlaybackRules.positionForFraction(1.4f, 60_000))
        assertEquals(0L, PlaybackRules.positionForFraction(0.5f, 0))
    }

    @Test
    fun `speeds include the requested values with 1x default`() {
        assertEquals(listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f), PlaybackRules.SPEEDS)
        assertEquals(1f, PlaybackRules.DEFAULT_SPEED)
    }

    @Test
    fun `next playable wraps around and skips photos`() {
        val items = listOf(item("a.jpg"), item("v1.mp4"), item("b.jpg"), item("song.mp3"), item("c.jpg"))
        assertEquals(3, PlaybackRules.nextPlayableIndex(items, 1))
        assertEquals(1, PlaybackRules.nextPlayableIndex(items, 3))
        assertNull(PlaybackRules.nextPlayableIndex(listOf(item("a.jpg"), item("only.mp4")), 1))
    }

    @Test
    fun `resume position ignores the very beginning and the end`() {
        assertEquals(0L, PlaybackRules.resumePosition(null, 60_000))
        assertEquals(0L, PlaybackRules.resumePosition(2_000, 60_000))
        assertEquals(30_000L, PlaybackRules.resumePosition(30_000, 60_000))
        assertEquals(0L, PlaybackRules.resumePosition(57_000, 60_000))
    }
}

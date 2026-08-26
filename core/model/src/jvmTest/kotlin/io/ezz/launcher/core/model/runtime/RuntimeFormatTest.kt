package io.ezz.launcher.core.model.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeFormatTest {

    @Test
    fun testZeroSecondsFormat() {
        assertEquals("00:00:00", formatRuntime(0L))
    }

    @Test
    fun testSecondsOnly() {
        assertEquals("00:00:05", formatRuntime(5L))
        assertEquals("00:00:59", formatRuntime(59L))
    }

    @Test
    fun testMinutesAndSeconds() {
        assertEquals("00:01:00", formatRuntime(60L))
        assertEquals("00:01:32", formatRuntime(92L))
        assertEquals("00:15:47", formatRuntime(947L))
        assertEquals("00:23:41", formatRuntime(1421L))
    }

    @Test
    fun testHoursMinutesAndSeconds() {
        assertEquals("01:00:00", formatRuntime(3600L))
        assertEquals("01:04:21", formatRuntime(3861L))
        assertEquals("02:14:09", formatRuntime(8049L))
        assertEquals("12:35:48", formatRuntime(45348L))
    }

    @Test
    fun testNegativeClamping() {
        assertEquals("00:00:00", formatRuntime(-10L))
    }
}

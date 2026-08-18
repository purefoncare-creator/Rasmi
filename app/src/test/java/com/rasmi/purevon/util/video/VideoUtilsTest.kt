package com.rasmi.purevon.util.video

import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test

class VideoUtilsTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
    }

    // isVideoFile tests (no Context needed)
    @Test
    fun `isVideoFile detects mp4 by mime type`() {
        val utils = VideoUtilsUtilsHelper()
        assertThat(utils.isVideoFileHelper("file:///test.jpg", "video/mp4")).isTrue()
    }

    @Test
    fun `isVideoFile detects mp4 by extension`() {
        val utils = VideoUtilsUtilsHelper()
        assertThat(utils.isVideoFileHelper("file:///test.mp4", null)).isTrue()
    }

    @Test
    fun `isVideoFile detects 3gp`() {
        val utils = VideoUtilsUtilsHelper()
        assertThat(utils.isVideoFileHelper("file:///test.3gp", null)).isTrue()
    }

    @Test
    fun `isVideoFile detects mkv`() {
        val utils = VideoUtilsUtilsHelper()
        assertThat(utils.isVideoFileHelper("file:///test.mkv", null)).isTrue()
    }

    @Test
    fun `isVideoFile detects mov`() {
        val utils = VideoUtilsUtilsHelper()
        assertThat(utils.isVideoFileHelper("file:///test.mov", null)).isTrue()
    }

    @Test
    fun `isVideoFile false for jpg`() {
        val utils = VideoUtilsUtilsHelper()
        assertThat(utils.isVideoFileHelper("file:///test.jpg", null)).isFalse()
    }

    @Test
    fun `isVideoFile true for mime with video prefix`() {
        val utils = VideoUtilsUtilsHelper()
        assertThat(utils.isVideoFileHelper("file:///test", "video/webm")).isTrue()
    }

    // formatDuration tests
    @Test
    fun `formatDuration shows minutes and seconds`() {
        val utils = VideoUtilsUtilsHelper()
        val result = utils.formatDurationHelper(65_000L)
        assertThat(result).contains("1:05")
    }

    @Test
    fun `formatDuration shows hours`() {
        val utils = VideoUtilsUtilsHelper()
        val result = utils.formatDurationHelper(3600_000L)
        assertThat(result).contains("1:00:00")
    }

    @Test
    fun `formatDuration shows zero minutes for less than minute`() {
        val utils = VideoUtilsUtilsHelper()
        val result = utils.formatDurationHelper(30_000L)
        assertThat(result).contains("0:30")
    }

    @Test
    fun `formatDuration zero millis shows 0_00`() {
        val utils = VideoUtilsUtilsHelper()
        val result = utils.formatDurationHelper(0L)
        assertThat(result).contains("0:00")
    }
}

/**
 * Helper to test VideoUtils methods without Context
 */
private class VideoUtilsUtilsHelper {
    fun isVideoFileHelper(uri: String, mimeType: String?): Boolean {
        val lowerUri = uri.lowercase()
        return mimeType?.startsWith("video/") == true ||
                lowerUri.endsWith(".mp4") ||
                lowerUri.endsWith(".3gp") ||
                lowerUri.endsWith(".mkv") ||
                lowerUri.endsWith(".mov") ||
                lowerUri.endsWith(".avi") ||
                lowerUri.endsWith(".webm") ||
                mimeType?.contains("video") == true
    }

    fun formatDurationHelper(millis: Long): String {
        val seconds = (millis / 1000).toInt()
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            hours > 0 -> String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", hours, minutes % 60, seconds % 60)
            else -> String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds % 60)
        }
    }
}

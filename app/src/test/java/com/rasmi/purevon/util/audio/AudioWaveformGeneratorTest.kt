package com.rasmi.purevon.util.audio

import android.util.Log
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test

class AudioWaveformGeneratorTest {

    private lateinit var generator: AudioWaveformGenerator

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        val ctx = mockk<android.content.Context>(relaxed = true)
        every { ctx.cacheDir } returns java.io.File("/tmp")
        generator = AudioWaveformGenerator(ctx)
    }

    @Test
    fun `generateMockWaveform returns correct count`() {
        val waveform = generator.generateMockWaveform(30)
        assertThat(waveform).hasSize(30)
    }

    @Test
    fun `generateMockWaveform default count is 50`() {
        val waveform = generator.generateMockWaveform()
        assertThat(waveform).hasSize(50)
    }

    @Test
    fun `generateMockWaveform values are in valid range`() {
        val waveform = generator.generateMockWaveform(100)
        for (value in waveform) {
            assertThat(value).isAtLeast(0.2f)
            assertThat(value).isAtMost(1f)
        }
    }

    @Test
    fun `generateMockWaveform is varied`() {
        val waveform = generator.generateMockWaveform(20)
        val unique = waveform.toSet()
        assertThat(unique.size).isGreaterThan(1)
    }
}

package com.macedonga.visor.core.assistant

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessTest {
    @Test fun quietSpeechLevelsRiseByTheGain() {
        val quiet = tone(1_000.0, 1_000.0, 48_000)
        val loud = Loudness(VoiceBoost.BOOST).run { push(quiet) + finish() }
        // 9 dB is 2.82 times; the tone sits just under the presence lift and above the low cut.
        assertEquals(2.9, rms(loud) / rms(quiet), 0.2)
    }

    @Test fun noSampleEverPassesTheCeiling() {
        // Full scale already, with a sudden start: the worst case for a limiter.
        val hot = tone(32_767.0, 700.0, 48_000) + tone(32_767.0, 3_000.0, 48_000)
        val loud = Loudness(VoiceBoost.MAX).run { push(hot) + finish() }
        assertTrue(shorts(loud).all { abs(it.toInt()) <= Loudness.CEILING })
        assertTrue(rms(loud) > 15_000)
    }

    @Test fun chunksOfAnySizeGiveTheSameStreamAsOnePiece() {
        val pcm = tone(20_000.0, 440.0, 9_600)
        val whole = Loudness(VoiceBoost.MAX).run { push(pcm) + finish() }
        val pieces = Loudness(VoiceBoost.MAX).run {
            push(pcm.copyOfRange(0, 2)) + push(pcm.copyOfRange(2, 5_000)) + push(pcm.copyOfRange(5_000, pcm.size)) + finish()
        }
        assertArrayEquals(whole, pieces)
        assertEquals(pcm.size + (Loudness.LOOK - 1) * 2, whole.size)
    }

    private fun tone(peak: Double, hz: Double, samples: Int): ByteArray =
        ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            repeat(samples) { putShort((peak * sin(2 * PI * hz * it / 48_000)).toInt().toShort()) }
        }.array()

    private fun shorts(pcm: ByteArray): List<Short> =
        ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().let { b -> List(b.remaining()) { b.get(it) } }

    private fun rms(pcm: ByteArray): Double = sqrt(shorts(pcm).sumOf { it.toDouble() * it } / (pcm.size / 2))
}

package com.macedonga.visor.core.assistant

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class FinnResamplerTest {
    @Test fun reconstructsAudioFromOddHttpReadsWithoutDroppingSamples() {
        val stream = FinnResampler()
        val pcm = samples(-300, 100, 600, -200)
        val output = stream.push(pcm.copyOfRange(0, 1)) + stream.push(pcm.copyOfRange(1, 5)) +
            stream.push(pcm.copyOfRange(5, pcm.size)) + stream.finish()
        assertArrayEquals(samples(-300, -100, 100, 350, 600, 200, -200, -200), output)
    }

    @Test(expected = IllegalStateException::class)
    fun endingInTheMiddleOfASampleIsNotACompleteStream() {
        val stream = FinnResampler()
        stream.push(byteArrayOf(1, 2, 3))
        stream.finish()
    }

    private fun samples(vararg values: Int): ByteArray =
        ByteBuffer.allocate(values.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach { putShort(it.toShort()) }
        }.array()
}

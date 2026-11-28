package com.macedonga.visor.core.assistant

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

/** How much louder than the voice's own level the phone's speech reaches the glasses. */
enum class VoiceBoost(val label: String, val gainDb: Double) {
    OFF("Off", 0.0), BOOST("Boost", 9.0), MAX("Max", 13.0),
}

/**
 * Louder speech for small open speakers, on 48 kHz mono 16-bit PCM. The glasses have no gain past full volume, so
 * the samples themselves carry it: the lows those speakers cannot play are cut, presence is lifted, and the gain
 * goes through a soft clip into a look-ahead limiter. On Finn's voice Boost measures about 6 dB louder and Max about
 * 8 dB. State carries across chunks, so a stream has no seams.
 */
internal class Loudness(boost: VoiceBoost) {
    private val highPass = Biquad.highPass(180.0)
    private val presence = Biquad.peak(2_800.0, 3.0)
    private val gain = 10.0.pow(boost.gainDb / 20)

    // The least gain any sample in the next 5 ms needs, kept in a monotonic queue.
    private val queued = DoubleArray(LOOK)
    private val queuedAt = LongArray(LOOK)
    private var head = 0
    private var size = 0
    private var count = 0L

    // That gain released slowly, then averaged over the same 5 ms so it ramps instead of stepping.
    private val held = DoubleArray(LOOK) { 1.0 }
    private val late = DoubleArray(LOOK)
    private var at = 0
    private var released = 1.0
    private var sum = LOOK.toDouble()

    /** The same number of samples, [LOOK] - 1 behind; [finish] has the rest. */
    fun push(pcm: ByteArray): ByteArray {
        val out = ByteArray(pcm.size and 1.inv())
        var i = 0
        while (i < out.size) {
            val sample = (pcm[i].toInt() and 255) or (pcm[i + 1].toInt() shl 8)
            val loud = step(sample.toDouble()).roundToInt()
            out[i] = loud.toByte()
            out[i + 1] = (loud shr 8).toByte()
            i += 2
        }
        return out
    }

    fun finish(): ByteArray = push(ByteArray((LOOK - 1) * 2))

    private fun step(sample: Double): Double {
        // Speech peaks are brief: rounding them off buys more loudness than the limiter alone, which would pump.
        val x = KNEE * tanh(presence.step(highPass.step(sample)) * gain / KNEE)
        val level = abs(x)
        released = minOf(least(if (level > CEILING) CEILING / level else 1.0), released + (1 - released) * RELEASE)
        sum += released - held[at]
        held[at] = released
        late[at] = x
        at = (at + 1) % LOOK
        // Every gain in the average was already low enough for this sample, the oldest one kept.
        return (late[at] * sum / LOOK).coerceIn(-CEILING, CEILING)
    }

    private fun least(need: Double): Double {
        while (size > 0 && queued[(head + size - 1) % LOOK] >= need) size--
        val tail = (head + size) % LOOK
        queued[tail] = need
        queuedAt[tail] = count
        size++
        if (queuedAt[head] <= count - LOOK) {
            head = (head + 1) % LOOK
            size--
        }
        count++
        return queued[head]
    }

    companion object {
        const val LOOK = 240
        // 1 dB under full scale, for the peaks between samples.
        const val CEILING = 29_205.0
        private const val KNEE = CEILING * 1.5
        private const val RATE = 48_000.0
        private val RELEASE = 1 - exp(-1 / (0.04 * RATE))
    }

    /** One second-order filter, coefficients as in the Audio EQ Cookbook. */
    private class Biquad(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) {
        private val b0 = b0 / a0
        private val b1 = b1 / a0
        private val b2 = b2 / a0
        private val a1 = a1 / a0
        private val a2 = a2 / a0
        private var z1 = 0.0
        private var z2 = 0.0

        fun step(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }

        companion object {
            fun highPass(hz: Double): Biquad {
                val w = 2 * PI * hz / RATE
                val alpha = sin(w) / (2 * 0.7071)
                val c = cos(w)
                return Biquad((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
            }

            fun peak(hz: Double, db: Double): Biquad {
                val w = 2 * PI * hz / RATE
                val a = 10.0.pow(db / 40)
                val alpha = sin(w) / 2
                val c = cos(w)
                return Biquad(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
            }
        }
    }
}

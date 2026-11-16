package org.marouane.glassesspeech;

import java.io.ByteArrayOutputStream;

/** Speech boundaries for 16 kHz mono PCM16; short thinking pauses stay inside the same question. */
final class PcmUtterances {
    private static final int SILENCE_BYTES = 48000; // 1.5 seconds, rather than the old 0.9 seconds.
    private static final int MIN_VOICED_BYTES = 4800;
    private static final int MAX_BYTES = 1920000; // Up to 60 seconds of continuous speech.
    private final ByteArrayOutputStream utterance = new ByteArrayOutputStream();
    private byte[] pre = new byte[0];
    private int silent, voiced;

    boolean active() { return utterance.size() > 0; }

    byte[] accept(byte[] frame, int n) {
        long energy = 0;
        for (int i = 0; i + 1 < n; i += 2) {
            int value = (short)((frame[i] & 255) | (frame[i + 1] << 8));
            energy += (long)value * value;
        }
        boolean speech = n > 1 && energy / (n / 2) > 400L * 400;
        if (speech) {
            if (!active()) utterance.write(pre, 0, pre.length);
            voiced += n; silent = 0;
        } else silent += n;
        if (active() || speech) utterance.write(frame, 0, n);
        else {
            byte[] next = new byte[Math.min(8000, pre.length + n)];
            int retain = next.length - n;
            if (retain > 0) System.arraycopy(pre, pre.length - retain, next, 0, retain);
            System.arraycopy(frame, 0, next, retain, n);
            pre = next;
        }
        return active() && (silent >= SILENCE_BYTES || utterance.size() >= MAX_BYTES) ? finish() : null;
    }

    byte[] finish() {
        byte[] pcm = voiced >= MIN_VOICED_BYTES ? utterance.toByteArray() : null;
        utterance.reset(); voiced = 0; silent = 0; pre = new byte[0];
        return pcm;
    }
}

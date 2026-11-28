package org.marouane.glassesspeech;

import java.io.ByteArrayOutputStream;

/** Speech boundaries for 16 kHz mono PCM16; short thinking pauses stay inside the same question. */
final class PcmUtterances {
    private static final int SILENCE_BYTES = 48000; // 1.5 seconds, rather than the old 0.9 seconds.
    private static final int MIN_VOICED_BYTES = 4800;
    private static final int TALKING_BYTES = 6400; // 0.2 seconds of voice: someone talking, not a click.
    private static final int MAX_BYTES = 1920000; // Up to 60 seconds of continuous speech.
    private final ByteArrayOutputStream utterance = new ByteArrayOutputStream();
    private final int earlyBytes;
    private byte[] pre = new byte[0];
    private int silent, voiced;
    // Whether this pause already gave its early copy, and whether that copy is still the whole utterance.
    private boolean earlyGiven, earlyStands, matchedEarly, lastSpeech;

    PcmUtterances() { this(0); }
    /** [earlySilenceMs] into a pause, early() hands out the utterance so far; 0 never does. */
    PcmUtterances(int earlySilenceMs) { earlyBytes = earlySilenceMs <= 0 ? 0 : earlySilenceMs * 32; }

    boolean active() { return utterance.size() > 0; }

    /** An utterance is under way with enough voice in it that it will be returned when it ends. */
    boolean talking() { return active() && voiced >= TALKING_BYTES; }

    byte[] accept(byte[] frame, int n) {
        long energy = 0;
        for (int i = 0; i + 1 < n; i += 2) {
            int value = (short)((frame[i] & 255) | (frame[i + 1] << 8));
            energy += (long)value * value;
        }
        boolean speech = n > 1 && energy / (n / 2) > 400L * 400;
        lastSpeech = speech;
        if (speech) {
            if (!active()) utterance.write(pre, 0, pre.length);
            voiced += n; silent = 0; earlyGiven = false; earlyStands = false;
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

    /** Whether the frame accept() last took was speech. */
    boolean lastWasSpeech() { return lastSpeech; }

    /** Once per pause: the utterance so far, to transcribe while the rest of the pause decides whether it is over. */
    byte[] early() {
        if (earlyBytes == 0 || earlyGiven || !active() || silent < earlyBytes || voiced < MIN_VOICED_BYTES) return null;
        earlyGiven = true; earlyStands = true;
        return utterance.toByteArray();
    }

    /** Whether the utterance accept() or finish() last returned is what early() gave, with only silence after it. */
    boolean matchedEarly() { return matchedEarly; }

    byte[] finish() {
        byte[] pcm = voiced >= MIN_VOICED_BYTES ? utterance.toByteArray() : null;
        matchedEarly = pcm != null && earlyStands;
        utterance.reset(); voiced = 0; silent = 0; pre = new byte[0]; earlyGiven = false; earlyStands = false;
        return pcm;
    }
}

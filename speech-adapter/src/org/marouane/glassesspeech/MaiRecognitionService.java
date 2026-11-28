package org.marouane.glassesspeech;

import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.speech.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Receives PCM supplied by a client, never opens the phone's microphone. */
public class MaiRecognitionService extends RecognitionService {
    /** From the client: this long into a pause, transcribe what was said so far, so the text is ready when the pause proves final. */
    static final String EXTRA_EARLY_SILENCE_MILLIS = "org.marouane.glassesspeech.extra.EARLY_SILENCE_MILLIS";
    /** In each result: whether another piece of the same question comes right behind it. */
    static final String EXTRA_MORE_FOLLOWS = "org.marouane.glassesspeech.extra.MORE_FOLLOWS";
    /** In each result: how long ago its last spoken frame was read, for timing the wait after a question. */
    static final String EXTRA_SINCE_SPEECH_MILLIS = "org.marouane.glassesspeech.extra.SINCE_SPEECH_MILLIS";
    private volatile Session active;
    private SpeechRecognizer fallback;
    public void onCheckRecognitionSupport(Intent intent, SupportCallback callback) {
        callback.onSupportResult(new RecognitionSupport.Builder().setOnlineLanguages(Arrays.asList("ar-SA", "ar-MA", "en-US", "fr-FR")).build());
    }
    protected synchronized void onStartListening(Intent intent, Callback callback) {
        if (active != null) { fail(callback, SpeechRecognizer.ERROR_RECOGNIZER_BUSY); return; }
        // Attribute the client-provided audio session correctly. This service does not open a microphone.
        android.content.Context attributed = createContext(new android.content.ContextParams.Builder()
            .setNextAttributionSource(callback.getCallingAttributionSource()).build());
        ParcelFileDescriptor source = intent.getParcelableExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, ParcelFileDescriptor.class);
        if (source == null) { delegatePhoneRecognition(intent, callback); return; }
        if (intent.getIntExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000) != 16000 || intent.getIntExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1) != 1
            || intent.getIntExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, 2) != 2) { fail(callback, SpeechRecognizer.ERROR_AUDIO); return; }
        String credential = "gemini".equals(Cloud.transcriptionProvider(this)) ? "gemini" : "mai";
        if (Vault.get(this, credential).isEmpty()) { fail(callback, SpeechRecognizer.ERROR_SERVER); return; }
        Session session = new Session(source, callback, intent.hasExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION), attributed,
            intent.getIntExtra(EXTRA_EARLY_SILENCE_MILLIS, 0)); active = session;
        Cloud.status(this, "recognition", true);
        new Thread(session::readAudio, "mai-audio-pipe").start();
    }
    protected void onStopListening(Callback callback) { Session s = active; if (s != null) s.finishInput(); if (fallback != null) fallback.stopListening(); }
    protected void onCancel(Callback callback) { Session s = active; if (s != null) s.cancel(); active = null; if (fallback != null) { fallback.cancel(); fallback.destroy(); fallback = null; } }
    public void onDestroy() { Session s = active; if (s != null) s.cancel(); if (fallback != null) fallback.destroy(); super.onDestroy(); }
    // Preserve normal microphone recognition for apps other than clients supplying glasses audio.
    private void delegatePhoneRecognition(Intent intent, Callback cb) {
        if (fallback != null) fallback.destroy();
        fallback = SpeechRecognizer.createSpeechRecognizer(this, android.content.ComponentName.unflattenFromString("com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService"));
        fallback.setRecognitionListener(new RecognitionListener() {
            private void forward(Throwing task) { try { task.run(); } catch (Exception ignored) {} }
            public void onReadyForSpeech(Bundle b) { forward(() -> cb.readyForSpeech(b)); }
            public void onBeginningOfSpeech() { forward(() -> cb.beginningOfSpeech()); }
            public void onRmsChanged(float rms) { forward(() -> cb.rmsChanged(rms)); }
            public void onBufferReceived(byte[] b) { forward(() -> cb.bufferReceived(b)); }
            public void onEndOfSpeech() { forward(() -> cb.endOfSpeech()); }
            public void onError(int error) { forward(() -> cb.error(error)); }
            public void onResults(Bundle b) { forward(() -> cb.results(b)); }
            public void onPartialResults(Bundle b) { forward(() -> cb.partialResults(b)); }
            public void onEvent(int type, Bundle b) {}
            public void onSegmentResults(Bundle b) { forward(() -> cb.segmentResults(b)); }
            public void onEndOfSegmentedSession() { forward(() -> cb.endOfSegmentedSession()); }
        });
        fallback.startListening(intent);
    }
    private interface Throwing { void run() throws Exception; }
    private void fail(Callback callback, int error) { try { callback.error(error); } catch (Exception ignored) {} }
    private final class Session {
        final ParcelFileDescriptor fd; final Callback cb; final boolean segmented;
        final android.content.Context attributed;
        final ExecutorService work = Executors.newSingleThreadExecutor();
        final ExecutorService ahead = Executors.newCachedThreadPool();
        final ScheduledExecutorService later = Executors.newSingleThreadScheduledExecutor();
        // How long after the last word a question that stopped mid-sentence is still waited for.
        static final long UNFINISHED_WAIT_MS = 4000;
        final int earlySilenceMs;
        volatile boolean cancelled, failed, talking, stirring; InputStream input;
        // Counts the pieces heard, so a wait set for one piece is void once another has come. Work thread only.
        int pieces;
        // Pieces of a question kept back while the speaker goes on after a long pause. Only the work thread touches it.
        final List<String> held = new ArrayList<>();
        Session(ParcelFileDescriptor source, Callback callback, boolean segments, android.content.Context context, int earlyMs) {
            fd = source; cb = callback; segmented = segments; attributed = context; earlySilenceMs = earlyMs;
        }
        void finishInput() { try { fd.close(); } catch (IOException ignored) {} }
        void cancel() { cancelled = true; finishInput(); }
        void readAudio() {
            PcmUtterances utterances = new PcmUtterances(earlySilenceMs);
            Future<Cloud.Heard> early = null;
            long spokeAt = android.os.SystemClock.elapsedRealtime();
            try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
                input = in; cb.readyForSpeech(new Bundle());
                byte[] frame = new byte[640]; int n;
                while (!cancelled && (n = in.readNBytes(frame, 0, frame.length)) > 0) {
                    // Fixed complete frames preserve sample alignment even if pipe reads arrive in odd sizes.
                    boolean wasActive = utterances.active();
                    byte[] completed = utterances.accept(frame, n);
                    talking = utterances.talking(); stirring = utterances.active();
                    if (utterances.lastWasSpeech()) spokeAt = android.os.SystemClock.elapsedRealtime();
                    if (!wasActive && utterances.active()) cb.beginningOfSpeech();
                    byte[] soFar = utterances.early();
                    if (soFar != null) early = ahead.submit(() -> Cloud.hear(attributed, soFar));
                    if (completed != null) submit(completed, utterances.matchedEarly() ? early : null, spokeAt);
                }
            } catch (Exception e) { /* A client closing the pipe ends its current utterance. */ }
            talking = false; stirring = false;
            byte[] tail = utterances.finish();
            if (!cancelled && tail != null) submit(tail, utterances.matchedEarly() ? early : null, spokeAt);
            ahead.shutdown();
            if (!work.isShutdown()) {
                work.execute(() -> { later.shutdownNow(); if (!cancelled && !failed) try { deliver(android.os.SystemClock.elapsedRealtime()); if (segmented) cb.endOfSegmentedSession(); else if (!sentResult) fail(cb, SpeechRecognizer.ERROR_NO_MATCH); } catch (Exception ignored) {} synchronized (MaiRecognitionService.this) { if (active == this) active = null; } });
                work.shutdown();
            }
        }
        volatile boolean sentResult;
        /** [ready]: the same words already being transcribed, from before the pause proved final. */
        void submit(byte[] pcm, Future<Cloud.Heard> ready, long spokeAt) {
            work.execute(() -> {
                if (cancelled || failed) return;
                String text = null; boolean finished = true;
                if (ready != null) try { Cloud.Heard heard = ready.get(); text = heard.text; finished = heard.finished; } catch (Exception ignored) { /* Transcribed again below. */ }
                try {
                    if (text == null && earlySilenceMs > 0) { Cloud.Heard heard = Cloud.hear(attributed, pcm); text = heard.text; finished = heard.finished; }
                    if (text == null) text = Cloud.transcribe(attributed, pcm);
                    Cloud.status(MaiRecognitionService.this, "stt", true);
                } catch (Exception e) {
                    if (cancelled) return;
                    Cloud.status(MaiRecognitionService.this, "stt", false);
                    Cloud.recognitionFailure(MaiRecognitionService.this, "provider", e);
                    // One failed segment must not kill every later question in the conversation.
                    if (!segmented) { failed = true; fail(cb, SpeechRecognizer.ERROR_NETWORK); return; }
                    // Nor strand the pieces held before it.
                    text = "";
                }
                if (cancelled) return;
                int mine = ++pieces;
                if (!text.isEmpty()) held.add(text);
                // The speaker went on after a pause long enough to end an utterance: the question is not over. Its
                // pieces wait for the one being spoken, which always comes through here when it ends.
                if (segmented && earlySilenceMs > 0 && talking) return;
                // Stopped mid-sentence, looking for a word: the rest is waited for a little longer.
                if (segmented && earlySilenceMs > 0 && !finished && !held.isEmpty()) {
                    settleLater(mine, spokeAt, spokeAt + UNFINISHED_WAIT_MS - android.os.SystemClock.elapsedRealtime());
                    return;
                }
                deliver(spokeAt);
            });
        }
        void settleLater(int piece, long spokeAt, long delayMs) {
            try {
                later.schedule(() -> work.execute(() -> {
                    // Another piece came, or is being spoken and will come: it settles the question.
                    if (cancelled || piece != pieces || held.isEmpty() || talking) return;
                    // A sound has just begun: it may be the next word.
                    if (stirring) settleLater(piece, spokeAt, 300); else deliver(spokeAt);
                }), Math.max(0, delayMs), TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ended) { /* The session is over; its last task sends what is held. */ }
        }
        void deliver(long spokeAt) {
            try {
                for (int i = 0; i < held.size(); i++) {
                    Bundle result = new Bundle(); result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, new ArrayList<>(List.of(held.get(i))));
                    result.putBoolean(EXTRA_MORE_FOLLOWS, i + 1 < held.size());
                    result.putLong(EXTRA_SINCE_SPEECH_MILLIS, android.os.SystemClock.elapsedRealtime() - spokeAt);
                    if (segmented) cb.segmentResults(result); else cb.results(result); sentResult = true;
                }
            } catch (Exception e) {
                Cloud.recognitionFailure(MaiRecognitionService.this, "callback", e);
                cancel();
            }
            held.clear();
        }
    }
}

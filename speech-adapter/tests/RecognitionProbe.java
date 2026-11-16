package org.marouane.glassesspeech.test;

import android.app.Instrumentation;
import android.content.*;
import android.os.*;
import android.speech.*;
import java.io.*;
import java.util.concurrent.*;

/** An actual Android pipe -> RecognitionService -> MAI -> callback check, using a synthetic sentence. */
public class RecognitionProbe extends Instrumentation {
    private SpeechRecognizer recognizer;
    private volatile String text = "";
    private volatile int error = 0;
    private Bundle arguments;
    private final java.util.concurrent.atomic.AtomicInteger segments = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.ArrayList<String> transcripts = new java.util.ArrayList<>();
    private final CountDownLatch done = new CountDownLatch(1);
    public void onCreate(Bundle arguments) { super.onCreate(arguments); this.arguments = arguments; start(); }
    public void onStart() {
        long began = System.currentTimeMillis(); Bundle result = new Bundle();
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            byte[] pcm;
            try (InputStream in = getContext().getAssets().open(arguments.getString("fixture", "test.pcm"))) { pcm = in.readAllBytes(); }
            runOnMainSync(() -> {
                recognizer = SpeechRecognizer.createSpeechRecognizer(getTargetContext(), new ComponentName("org.marouane.glassesspeech", "org.marouane.glassesspeech.MaiRecognitionService"));
                recognizer.setRecognitionListener(new RecognitionListener() {
                    public void onReadyForSpeech(Bundle b) {}
                    public void onBeginningOfSpeech() {}
                    public void onRmsChanged(float rms) {}
                    public void onBufferReceived(byte[] b) {}
                    public void onEndOfSpeech() {}
                    public void onError(int code) { error = code; done.countDown(); }
                    public void onPartialResults(Bundle b) {}
                    public void onEvent(int type, Bundle b) {}
                    public void onResults(Bundle b) { heard(b); done.countDown(); }
                    public void onSegmentResults(Bundle b) { heard(b); }
                    public void onEndOfSegmentedSession() { done.countDown(); }
                });
                Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, 2)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                    .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
                    .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE);
                recognizer.startListening(i);
            });
            try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) { out.write(pcm); }
            boolean completed = done.await(35, TimeUnit.SECONDS);
            result.putBoolean("completed", completed); result.putInt("recognition_error", error);
            result.putInt("segments", segments.get()); result.putString("transcript", text);
            result.putStringArrayList("transcripts", transcripts);
            result.putBoolean("passed", completed && error == 0 && !text.isBlank() && segments.get() == Integer.parseInt(arguments.getString("expectedSegments", "1")));
        } catch (Exception e) { result.putString("failure", e.getClass().getSimpleName()); }
        finally { runOnMainSync(() -> { if (recognizer != null) recognizer.destroy(); }); }
        result.putLong("elapsed_ms", System.currentTimeMillis() - began); finish(-1, result);
    }
    private void heard(Bundle b) { java.util.ArrayList<String> words = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if (words != null && !words.isEmpty()) { text = words.get(0); transcripts.add(text); segments.incrementAndGet(); } }
}

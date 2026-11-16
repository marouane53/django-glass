package org.marouane.glassesspeech;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;

public class MainActivity extends Activity {
    private EditText gemini, mai, local;
    private TextView status;
    public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        // USB provisioning in the development build only; the release build cannot be accessed with run-as.
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            java.io.File file = new java.io.File(getFilesDir(), "provision.json");
            if (file.isFile()) try {
                org.json.JSONObject config = new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8));
                for (String name : new String[]{"gemini", "mai", "local"}) Vault.put(this, name, config.getString(name));
            } catch (Exception ignored) {} finally { file.delete(); }
        }
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(40, 72, 40, 40);
        ScrollView scroll = new ScrollView(this); scroll.addView(layout); setContentView(scroll);
        TextView title = new TextView(this); title.setText("Glasses Speech"); title.setTextSize(26); layout.addView(title);
        TextView info = new TextView(this); info.setText("Gemini 3.8 Flash TTS · Finn\nGemini Flash-Lite or MAI-Transcribe-2 input\n\nGemini expects mostly English, plus French and Moroccan Darija, preserving language changes. Django selects this service directly.\n\nKeys are encrypted on this phone. The adapter only listens on 127.0.0.1:8766. Audio and transcripts are sent to the selected cloud speech provider, then discarded here."); layout.addView(info);
        gemini = field(layout, "Gemini API key", "gemini"); mai = field(layout, "OpenRouter API key for MAI", "mai"); local = field(layout, "Local access key for Django Glass", "local");
        Button save = new Button(this); save.setText("Save and start"); layout.addView(save);
        status = new TextView(this); layout.addView(status);
        Button useMai = new Button(this); useMai.setText("Use MAI transcription"); layout.addView(useMai);
        useMai.setOnClickListener(v -> { getSharedPreferences("settings", 0).edit().putString("stt_provider", "mai").commit(); refresh(); });
        Button useGemini = new Button(this); useGemini.setText("Use Gemini transcription"); layout.addView(useGemini);
        useGemini.setOnClickListener(v -> { getSharedPreferences("settings", 0).edit().putString("stt_provider", "gemini").commit(); refresh(); });
        Button refresh = new Button(this); refresh.setText("Refresh status"); layout.addView(refresh); refresh.setOnClickListener(v -> refresh());
        Button probe = new Button(this); probe.setText("Check on-device Arabic recognition"); layout.addView(probe);
        probe.setOnClickListener(v -> probeArabic());
        Button stop = new Button(this); stop.setText("Stop adapter"); layout.addView(stop); stop.setOnClickListener(v -> { stopService(new Intent(this, BridgeService.class)); status.setText("Adapter stopped"); });
        save.setOnClickListener(v -> {
            try {
                if (gemini.getText().toString().trim().isEmpty() || local.getText().toString().trim().length() < 24) { status.setText("Add the Gemini key and a local access key of at least 24 characters."); return; }
                Vault.put(this, "gemini", gemini.getText().toString().trim()); Vault.put(this, "mai", mai.getText().toString().trim()); Vault.put(this, "local", local.getText().toString().trim());
                startForegroundService(new Intent(this, BridgeService.class)); status.setText("Settings saved. Adapter starting.");
            } catch (Exception e) { status.setText("Could not save credentials or start the adapter."); }
        });
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECORD_AUDIO}, 1);
        if (!Vault.get(this, "gemini").isEmpty() && !Vault.get(this, "local").isEmpty()) startForegroundService(new Intent(this, BridgeService.class));
        refresh();
    }
    private EditText field(LinearLayout layout, String label, String name) {
        TextView text = new TextView(this); text.setText(label); layout.addView(text);
        EditText field = new EditText(this); field.setContentDescription(label); field.setSingleLine(true); field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); field.setImportantForAutofill(2); field.setText(Vault.get(this, name)); layout.addView(field); return field;
    }
    private void refresh() {
        android.content.SharedPreferences p = getSharedPreferences("status", 0);
        status.setText("TTS requests: " + p.getInt("tts_count", 0) + " · last success: " + p.getBoolean("tts_ok", false)
            + "\nInput: " + Cloud.transcriptionProvider(this) + " · requests: " + p.getInt("stt_count", 0) + " · last success: " + p.getBoolean("stt_ok", false));
    }
    private void probeArabic() {
        if (!android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) { status.setText("No on-device recognizer"); return; }
        android.speech.SpeechRecognizer r = android.speech.SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
        Intent i = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ar-SA");
        r.checkRecognitionSupport(i, getMainExecutor(), new android.speech.RecognitionSupportCallback() {
            public void onSupportResult(android.speech.RecognitionSupport s) {
                status.setText("On-device installed: " + s.getInstalledOnDeviceLanguages() + "\nOn-device available: " + s.getSupportedOnDeviceLanguages()); r.destroy();
            }
            public void onError(int error) { status.setText("On-device Arabic support error: " + error); r.destroy(); }
        });
    }
}

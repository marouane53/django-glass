package org.marouane.glassesspeech;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public class BridgeService extends Service {
    public static final int PORT = 8766;
    private volatile ServerSocket server;
    private final ExecutorService workers = Executors.newFixedThreadPool(3);
    private final Semaphore speech = new Semaphore(1);
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("speech", "Glasses speech adapter", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, BridgeService.class).setAction("stop");
        Notification n = new Notification.Builder(this, "speech").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Glasses Speech is running").setContentText("Gemini Finn · cloud transcription").setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Stop", PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE)).build()).build();
        startForeground(1, n);
        new Thread(this::serve, "speech-loopback").start();
    }
    public int onStartCommand(Intent i, int flags, int startId) { if (i != null && "stop".equals(i.getAction())) { stopSelf(); return START_NOT_STICKY; } return START_STICKY; }
    public IBinder onBind(Intent i) { return null; }
    public void onDestroy() { try { if (server != null) server.close(); } catch (IOException ignored) {} workers.shutdownNow(); super.onDestroy(); }
    private void serve() {
        try { server = new ServerSocket(); server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT));
            while (!server.isClosed()) { Socket s = server.accept(); workers.execute(() -> handle(s)); }
        } catch (Exception e) { stopSelf(); }
    }
    private String line(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); int c;
        while ((c = in.read()) != -1 && c != '\n') { if (b.size() >= 8192) throw new IOException("Header too long"); if (c != '\r') b.write(c); }
        return b.toString("US-ASCII");
    }
    private void send(Socket s, int status, String type, byte[] body) throws IOException {
        OutputStream out = s.getOutputStream();
        out.write(("HTTP/1.1 " + status + " Response\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length + "\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").getBytes(StandardCharsets.US_ASCII)); out.write(body); out.flush();
    }
    private void json(Socket s, int status, String text) throws IOException { send(s, status, "application/json", text.getBytes(StandardCharsets.UTF_8)); }
    // A failed or cancelled stream closes without the terminal chunk, so the client can detect a partial reply.
    private static final class PcmResponse {
        final OutputStream out;
        boolean started;
        PcmResponse(OutputStream out) { this.out = out; }
        void write(byte[] pcm) throws IOException {
            if (!started) {
                started = true;
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: audio/l16; rate=24000; channels=1\r\n"
                    + "Transfer-Encoding: chunked\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            }
            out.write((Integer.toHexString(pcm.length) + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(pcm); out.write('\r'); out.write('\n'); out.flush();
        }
        void finish() throws IOException { out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)); out.flush(); }
    }
    private void handle(Socket s) {
        try (s) {
            s.setSoTimeout(10000); InputStream in = new BufferedInputStream(s.getInputStream());
            String[] request = line(in).split(" "); if (request.length < 2) return;
            Map<String, String> headers = new HashMap<>(); String h; int headerCount = 0;
            while (!(h = line(in)).isEmpty()) { if (++headerCount > 50) throw new IOException("Too many headers"); int colon = h.indexOf(':'); if (colon > 0) headers.put(h.substring(0, colon).toLowerCase(Locale.ROOT), h.substring(colon + 1).trim()); }
            if (request[1].equals("/health")) { json(s, 200, "{\"running\":true}"); return; }
            String token = Vault.get(this, "local");
            if (token.isEmpty() || !MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8), headers.getOrDefault("authorization", "").getBytes(StandardCharsets.UTF_8))) { json(s, 401, "{\"error\":{\"message\":\"Local access key required\"}}"); return; }
            if (request[1].equals("/v1/status") && request[0].equals("GET")) {
                android.content.SharedPreferences p = getSharedPreferences("status", 0); JSONObject result = new JSONObject();
                for (String kind : new String[]{"tts", "stt", "recognition"}) result.put(kind, new JSONObject().put("count", p.getInt(kind + "_count", 0)).put("ok", p.getBoolean(kind + "_ok", false)).put("at", p.getLong(kind + "_at", 0)));
                result.put("transcription_provider", Cloud.transcriptionProvider(this));
                result.put("actual_transcription_provider", p.getString("stt_actual_provider", ""));
                result.put("primary_provider_error", p.getString("stt_primary_error", ""));
                result.put("last_failure_stage", p.getString("stt_failure_stage", ""));
                result.put("last_error", p.getString("stt_error", ""));
                json(s, 200, result.toString()); return;
            }
            if (request[1].equals("/v1/transcription-provider") && request[0].equals("POST")) {
                int length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
                if (length < 1 || length > 256 || headers.containsKey("transfer-encoding")) { json(s, 400, "{}"); return; }
                byte[] change = in.readNBytes(length); if (change.length != length) throw new IOException("Incomplete provider setting");
                String provider = new JSONObject(new String(change, StandardCharsets.UTF_8)).getString("provider");
                if (!provider.equals("mai") && !provider.equals("gemini")) { json(s, 400, "{}"); return; }
                getSharedPreferences("settings", 0).edit().putString("stt_provider", provider).commit(); json(s, 200, new JSONObject().put("provider", provider).toString()); return;
            }
            if (request[1].equals("/v1/models") && request[0].equals("GET")) { json(s, 200, "{\"object\":\"list\",\"data\":[{\"id\":\"" + Cloud.TTS_MODEL + "\",\"object\":\"model\"}]}"); return; }
            boolean stream = request[1].equals("/v1/audio/speech/stream");
            if ((!stream && !request[1].equals("/v1/audio/speech")) || !request[0].equals("POST")) { json(s, 404, "{}"); return; }
            int len = Integer.parseInt(headers.getOrDefault("content-length", "0"));
            if (len < 1 || len > 128000 || headers.containsKey("transfer-encoding")) { json(s, 400, "{\"error\":{\"message\":\"Invalid request size\"}}"); return; }
            byte[] body = in.readNBytes(len); if (body.length != len) throw new IOException("Incomplete request");
            if (!speech.tryAcquire()) { json(s, 429, "{\"error\":{\"message\":\"Speech already running\"}}"); return; }
            try {
                if (stream) {
                    PcmResponse response = new PcmResponse(s.getOutputStream());
                    try {
                        Cloud.streamSpeech(this, new JSONObject(new String(body, StandardCharsets.UTF_8)), response::write);
                        response.finish(); Cloud.status(this, "tts", true);
                    } catch (Exception e) {
                        if (!response.started) json(s, 502, "{\"error\":{\"message\":\"Gemini speech stream unavailable\"}}");
                        Cloud.status(this, "tts", false);
                    }
                } else {
                    byte[] wav = Cloud.speak(this, new JSONObject(new String(body, StandardCharsets.UTF_8)));
                    Cloud.status(this, "tts", true); send(s, 200, "audio/wav", wav);
                }
            } catch (Exception e) { Cloud.status(this, "tts", false); if (!stream) json(s, 502, "{\"error\":{\"message\":\"Gemini speech failed; check adapter settings and provider access\"}}"); }
            finally { speech.release(); }
        } catch (Exception ignored) {}
    }
}

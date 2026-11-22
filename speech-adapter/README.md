# Glasses Speech

Android speech companion for [Django Glass](../README.md). It stores speech
credentials separately and provides a loopback TTS bridge plus a recognition
service for client-supplied glasses audio.

## Speech paths

- TTS translates OpenAI-shaped `/audio/speech` requests into Google's Interactions
  API and returns WAV audio. The tested setup uses `gemini-3.8-flash-tts` and Finn
  voice `en-us-finn`. The authenticated `/v1/audio/speech/stream` route in 0.8
  also supports the same voice on Flash and Flash-Lite and returns 24 kHz mono
  PCM incrementally. The original WAV route is still available.
- Recognition accepts supplied 16 kHz mono PCM from Django. The tested provider
  is `gemini-3.5-flash-lite`. MAI-Transcribe-2 through OpenRouter remains selectable,
  with bounded retry and Gemini fallback on provider errors.
- Transcription guidance expects mostly English, plus French and Moroccan
  Darija. It preserves code-switching instead of translating or answering.
- Segmentation permits a 1.5-second pause and bounds continuous speech at
  60 seconds. Ordinary microphone clients are delegated to the configured Google
  recognizer; glasses audio is supplied by Django.

Model identifiers are the tested configuration, not a promise of provider or
account availability. Version 0.7 also adds camera-command and agent-name hints.

## Configure

Open **Glasses Speech**, enter the Gemini key, an OpenRouter key if using MAI,
and a random local access key of at least 24 characters. Choose the provider and
tap **Save and start**. The ongoing notification indicates the service is running.
Reopen after a reboot or force-stop to restart it.

In Django's assistant settings, choose cloud speech, address
`http://127.0.0.1:8766/v1`, the same local access key, the speech model and voice.
The loopback key authenticates the phone-local bridge; it is separate from the
cloud-provider credentials. Select the phone's English language route for the
tested glasses recognition configuration.

## Build and test

From the repository root:

```sh
zsh speech-adapter/build.sh
adb install -r speech-adapter/build/glasses-speech.apk
```

The script respects `SPEECH_JAVA_HOME` / `JAVA_HOME` and
`SPEECH_ANDROID_SDK` / `ANDROID_HOME` / `ANDROID_SDK_ROOT`. It uses platform 36,
build tools 36.0.0 and JDK 21. A local development key is generated in ignored
`build/` on the first build; preserve it for future updates.

See [BUILDING.md](../BUILDING.md) for pure Java tests. Recognition instrumentation
under `tests/` requires a locally created synthetic PCM fixture. Fixtures, APKs,
provider keys and signing material are excluded from this repository.

For Django's notification extraction checks:

```sh
zsh speech-adapter/build-ui-tools.sh
adb install -r speech-adapter/build/glasses-ui-tools.apk
python3 speech-adapter/run-notification-checks.py
adb uninstall org.marouane.glassesspeech.ui
```

Use the wrapper to restore already enabled Django accessibility controls after
target instrumentation. It does not enable a service that was off before testing.

## Data and diagnostics

- Credentials use AES-GCM with a non-exportable Android Keystore key. The app
  disables backup and debugging in its release manifest.
- HTTP binds only to `127.0.0.1:8766`. API requests require the local bearer key;
  `/health` exposes only running status.
- Authenticated `/v1/status` returns controlled counts, timestamps and success
  flags, without keys, audio or transcripts.
- Google receives speech text and Gemini transcription audio. OpenRouter receives
  audio when MAI is selected, and provider-error fallback can also send it to Google.
  Provider retention policies apply.
- Audio and text are not written to files by the companion. Provider errors are
  reduced to controlled messages and codes; raw response bodies are not logged.

This companion is distributed with Django Glass under [AGPL-3.0](../LICENSE).
See the [project attribution](../NOTICE.md) for the Visor foundation.

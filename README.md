# Django Glass

**Your assistant and AI agents, through your glasses.**

Django Glass is an Android client for Meta smart glasses with conversational AI,
cloud speech, a separate OpenAI Dots call mode and selectable spoken notifications.

**Built on [Visor](https://github.com/macedonga/visor), by macedonga and its contributors.**
Visor provides the glasses client and protocol foundation. This repository is an
independent derivative of Visor v3.0.4. See [NOTICE.md](NOTICE.md) for the original
commit, credits and modifications, and [LICENSE](LICENSE) for the retained AGPL-3.0
license. The [original Visor README](README-VISOR.md) is also preserved.

## What Django adds

| Feature | Behavior |
| --- | --- |
| Natural conversations | Say “Hey Meta” once, then ask follow-ups with context. Tap the right touchpad to end the normal assistant conversation. |
| Your ChatGPT account | The normal assistant can use an existing signed-in ChatGPT account. The tested configuration uses `gpt-6.1-sol` with low reasoning effort. |
| Cloud speech | A separate Android companion provides transcription and Finn speech synthesis. Recognition guidance covers English, French and Moroccan Darija. |
| OpenAI Dots calls | “Hey Meta, open ChatGPT” hands off to an existing dot in the native ChatGPT app. Ask the dot to end the call and keep working. |
| Call aliases | Call/open ChatGPT, Django, Dots and Jarvis, plus exact phrase variants. |
| Voice capture | “Take a picture” captures a photo; “Start recording” launches a video. |
| Photo analysis | “Look and…” attaches a glasses photo and your question to ChatGPT. Follow-ups reuse that photo; “look again and…” requests a new view. |
| Spoken notifications | Choose sources and how much Finn reads. Dots and texts can read full messages; WhatsApp announces only the sender. |
| Visor foundation | Retains upstream pairing, gallery, media import, device settings and other client features. |

## Status and compatibility

This is an experimental personal build. The tested setup is Ray-Ban Meta Skyler
on firmware `126.0.0.207.412`, paired with a Samsung Android 17 phone. Compatibility
with other glasses, firmware and Android versions needs separate testing.

Current source versions: **Django 0.24**, **Glasses Speech 0.7**. The application IDs
remain `org.marouane.djangoglasses` and `org.marouane.glassesspeech`; the upstream
`com.macedonga.visor` code namespace is retained to keep the changes reviewable.
The speech companion requires Android 13 or later. Existing Dots access in the
ChatGPT Android app is required for the separate call mode.

Validated on hardware:

- Longer questions, English/Darija answers and contextual follow-ups.
- Tap-to-stop, followed by a fresh wake phrase.
- Dots call launch, voice hang-up and return to the normal assistant.
- Screen-off call launch during Android's trusted-device window.
- Photo capture and video launch with “Start recording.”
- Photo analysis, same-photo follow-ups, and an explicit “look again” replacement capture (0.24).
- Notification tone and Finn playback through the paired glasses.

The notification policies have 12 unit tests and 10 on-device Android extraction
checks. Real incoming app notifications and ringing calls still need live
verification beyond the local preview and synthetic checks. There are 73
app/assistant/media unit tests in total, plus the companion's Java tests.

## Set up

1. Build and install both apps using [BUILDING.md](BUILDING.md). Sign-in credentials,
   client configuration and signing keys are supplied locally. None are bundled here.
2. Open Django Glass, grant the needed permissions, sign in to Meta and pair the
   glasses. Use one owner app for the glasses at a time.
3. Sign in to ChatGPT in Django's assistant settings, or configure another supported
   model provider.
4. Open Glasses Speech. Enter the speech-provider credentials and a random local
   access key of at least 24 characters, choose a transcription provider, and tap
   **Save and start**. Reopen the companion after a phone reboot to restart it.
5. In Django's assistant settings, choose cloud speech and configure:

   | Setting | Tested value |
   | --- | --- |
   | Speech service address | `http://127.0.0.1:8766/v1` |
   | Speech service key | The companion's local access key |
   | Speech model | `gemini-3.8-flash-tts` |
   | Voice ID | `en-us-finn` |

6. Select the phone's English language route. Django explicitly selects the
   companion recognition service for glasses audio. The companion's tested input
   provider is `gemini-3.5-flash-lite`; MAI-Transcribe-2 is also supported. Model
   availability depends on your provider and account.

The glasses assistant calls itself **Django**. Its normal conversation and the
existing OpenAI dot named Django are separate modes. See [USAGE.md](USAGE.md).

For Dots calls, enable **Django dot call controls** in Android Accessibility settings.
The service is restricted to ChatGPT's Call/End controls after an explicit request.
See [DOTS.md](DOTS.md), including recovery if Android disables the service.

For readouts, open **Settings → Notifications → Spoken notifications**. See
[NOTIFICATIONS.md](NOTIFICATIONS.md) for the defaults and controls.

## Data flow

- Meta authentication and glasses ownership use the existing upstream setup path.
- The configured assistant provider receives the questions, context and requested
  scene photos needed to answer. Conversations and analysis photos may be stored
  in Django's local diary.
- The speech companion sends audio to the selected transcription provider and
  readout text to Google for Finn synthesis. MAI transcription uses OpenRouter;
  the MAI path can fall back to Gemini on provider errors.
- Notification text goes directly to speech synthesis, rather than being treated
  as instructions for the assistant. WhatsApp message bodies are excluded from
  the speech request.
- The native ChatGPT app handles Dots authentication, call audio and agent work.
- The companion's local HTTP bridge listens only on `127.0.0.1` and requires a local
  bearer key. Cloud credentials are encrypted using Android Keystore.

The repository contains source and public upstream assets. It excludes personal
media, device logs, account credentials, pairing keys, signing keys and generated APKs.

## Build, test and contribute

Start with [BUILDING.md](BUILDING.md), [CONTRIBUTING.md](CONTRIBUTING.md) and
[NOTICE.md](NOTICE.md). The code builds with JDK 21 and Android SDK platform 37.0;
the speech companion uses platform 36 and build tools 36.0.0. CI checks compilation
and unit tests. On-device checks are separate and require a paired phone/glasses.

There is no automated release publishing in this repository. Changes to the
protocol, microphone routing and follow-up timing should be verified on hardware.

## Credits

The largest share of this client comes from **macedonga and the Visor contributors**.
Please visit and support [Visor](https://github.com/macedonga/visor). Its
[protocol documentation](https://visor.macedonga.com/protocol/) and
[Visor Development Kit](https://github.com/macedonga/visor-kit) are valuable parts
of the original project. Django-specific changes are maintained by
[marouane53](https://github.com/marouane53).

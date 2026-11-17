# Using Django Glass

## Normal assistant

Say **“Hey Meta, hello”** to begin. Wait for the response, then ask follow-up
questions without repeating the wake phrase. The assistant calls itself Django
and follows the latest question's language. The tested languages are English,
French and Moroccan Darija; recognition quality depends on the selected provider.

Tap the right touchpad once to end the conversation and stop listening, including
while an answer is playing. Say “Hey Meta” to begin again. A conversation also ends
after its idle timeout. The normal assistant's touch-stop behavior is separate
from the native Dots call controls.

## Call your dot

Say **“Hey Meta, open ChatGPT.”** This hands off to the existing dot named Django
in the native ChatGPT app. Ask **“End this call and keep working”** to close the call.
The normal assistant becomes available again shortly afterwards.

Supported exact command families include call/open ChatGPT, Django, Dots and
Jarvis; “talk to,” “start a call with” and “connect me to” variants are supported
for the configured aliases. These are launch aliases for the same dot. Ordinary
questions mentioning those names remain questions.

The current launcher recognizes the ChatGPT composer's **Message Django** and
**Call** controls. Using another dot name requires changing that matching logic.
The native ChatGPT app and its current interface are dependencies of this path.
See [DOTS.md](DOTS.md).

## Camera commands

For photo analysis, say **“Hey Meta, look and tell me what this is.”** Django
requests a photo from the glasses and attaches it with your question to the
normal assistant's ChatGPT request. Finn reads the answer, and the conversation
stays available for follow-ups. Those follow-ups reattach the same reference
photo, even if you turn your head. They do not automatically take another photo.
Say **“Look again and…”** or repeat **“Look and…”** to request a new view.

Other examples:

- “Hey Meta, look and read this label.”
- “Hey Meta, look and translate this into English.”
- “Hey Meta, look and explain what is written here.”
- After the reply, without a wake phrase: “What does the second line mean?”
- To change the picture: “Look again and describe the new object.”

The phrase after “look and” can be English, French or Darija. **Use the camera**
must be enabled in assistant settings. If the photo does not arrive, Django
reports that instead of claiming to see the scene. Photo analysis is part of
the normal assistant mode.

The reference image is held for that open glasses conversation. Tap-to-stop,
idle expiry or ending the conversation clears its in-memory reference. A failed
request for a new view does not silently fall back to the old scene. The diary
can still retain the analysis photo with its recorded turn.

- **“Hey Meta, take a picture”** or **“take a photo.”** Enable Voice photos first.
- **“Hey Meta, start recording.”** This is the hardware-tested video launch phrase.
  “Take a video” is also mapped but was less reliable with the tested transcription.

Video launch ends the assistant conversation so the camera can use the microphone.
The normal recording duration and physical capture controls apply. A spoken
stop command during video recording has not been established by this build.

## Notifications

Open **Settings → Notifications → Spoken notifications**. Each source has a
separate control. WhatsApp offers Off or Sender only. Dots and texts can read the
full message. Other apps are off until individually selected.

Announcements wait until assistant conversations and calls end, expire while
queued, and respect Do Not Disturb. They use the configured cloud voice and
the paired glasses' Bluetooth audio route. See [NOTIFICATIONS.md](NOTIFICATIONS.md).

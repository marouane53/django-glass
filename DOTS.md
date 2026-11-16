# OpenAI Dots call mode

The normal Django assistant and an existing OpenAI dot are separate modes.
This project uses the signed-in ChatGPT Android app for Dots calls. It does not
implement a separate Dots API or use the assistant's ChatGPT session for the call.

## Setup

1. Sign in to the native ChatGPT Android app and open the dot named **Django**.
2. In Android Accessibility settings, enable **Django dot call controls**.
3. With connected, awake glasses, say **“Hey Meta, open ChatGPT.”**
4. Speak to the dot. Ask **“End this call and keep working”** to hang up.
5. Use “Hey Meta” again for the normal Finn assistant.

The service is scoped to ChatGPT window/content events and only operates the
Call/End controls after an explicit call request. It does not type instructions
or send chat messages. The launcher matches the **Message Django** composer and
a unique Call control. A different dot name or changed ChatGPT UI needs an update
to that matcher.

The aliases ChatGPT, Django, Dots and Jarvis all route to that same configured dot.
Double-tapping the glasses did not reliably end Dots calls in the tested setup;
voice hang-up through the dot is the accepted method.

## Phone screen off

The accepted Samsung setup uses Android Extend Unlock with the glasses as a
trusted Bluetooth device, after the owner authenticates normally. Django's private
launcher can dismiss a trusted keyguard only while Android reports that credential
authentication is not required. Reboot, trust expiry or Lockdown requires the
owner's normal unlock. Extend Unlock allows the phone to open without credentials
during its trusted window, so configure it deliberately in Android settings.

## If it asks for Accessibility again

Android can remember the permission while its service is disconnected or marked
as crashed, especially after target-app instrumentation. Open Android Settings →
Accessibility → Installed apps → **Django dot call controls**, switch it off, and
switch it back on. Retry the wake command after it reconnects.

Development device checks should use `speech-adapter/run-notification-checks.py`.
It records whether this service was enabled and restores its binding after the
target-app test exits, preserving other enabled accessibility services. It does
not grant access if Django was not already enabled before the test.

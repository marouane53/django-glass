# Building Django

## Requirements

- JDK 21, with `JAVA_HOME` set.
- Android SDK, with `ANDROID_HOME` set; platform 37.0 for Django, platform 36 and
  build tools 36.0.0 for the speech companion.
- Python 3 and zsh for the local helper scripts.

## Compile and test

From the repository root:

```sh
./gradlew :app:testDebugUnitTest :core:assistant:testDebugUnitTest :core:media:testDebugUnitTest :core:settings:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

The build succeeds without Meta client configuration. Pairing and Meta sign-in
require the three client configuration values described below.

The companion's pure Java tests can run without an Android phone:

```sh
mkdir -p speech-adapter/build/unit-tests
javac -d speech-adapter/build/unit-tests \
  speech-adapter/src/org/marouane/glassesspeech/PcmUtterances.java \
  speech-adapter/src/org/marouane/glassesspeech/TranscriptionPolicy.java \
  speech-adapter/tests/PcmUtterancesTest.java \
  speech-adapter/tests/TranscriptionPolicyTest.java
java -cp speech-adapter/build/unit-tests org.marouane.glassesspeech.PcmUtterancesTest
java -cp speech-adapter/build/unit-tests org.marouane.glassesspeech.TranscriptionPolicyTest
```

## Build the companion

```sh
zsh speech-adapter/build.sh
```

This creates `speech-adapter/build/glasses-speech.apk` and a local development
signing key under the ignored `speech-adapter/build/` directory. Preserve your
signing key for later updates. The repository contains no signing material.

`SPEECH_JAVA_HOME` and `SPEECH_ANDROID_SDK` can override the standard environment
variables for this companion build. The scripts also retain Homebrew defaults
for the original development environment.

## Configure Meta setup locally

The inherited setup path needs `VISOR_META_HW_TOKEN`, `VISOR_META_AR_TOKEN` and
`VISOR_META_FRL_TOKEN`. Supply these through the build process environment or an
ignored local configuration file. They are client configuration from the Meta
application, separate from your personal Meta login and the speech-provider keys.
This repository does not redistribute their values.

The helper can also read a private generated Java BuildConfig supplied through
`--identity-config`. Keep that file outside the repository and remove it after use.

```sh
python3 build-django.py --identity-config /private/path/to/BuildConfig.java
```

By default the helper runs the app, assistant, media and settings tests and assembles a signed
release using the companion's local development key. You can instead supply
`VISOR_KEYSTORE`, `VISOR_KEYSTORE_PASSWORD` and `VISOR_KEY_ALIAS` through the process
environment. Existing Visor environment-variable names are retained intentionally.

## Install your builds

```sh
adb install -r speech-adapter/build/glasses-speech.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

An update must use the same signing key as the installed app. Keep the original
application IDs to retain compatibility with the recognition service and call
launcher. Enter personal credentials on the phone through the companion and the
apps' sign-in flows.

## Device checks

The notification extraction test uses synthetic objects and does not send
messages. It temporarily restarts Django. Use the wrapper, which restores an
already enabled Django accessibility service after instrumentation ends:

```sh
zsh speech-adapter/build-ui-tools.sh
adb install -r speech-adapter/build/glasses-ui-tools.apk
python3 speech-adapter/run-notification-checks.py
adb uninstall org.marouane.glassesspeech.ui
```

Additional recognition instrumentation is under `speech-adapter/tests/`. It needs
a synthetic PCM fixture created locally; no audio fixtures are shipped. Real
glasses tests remain necessary for microphone timing, gestures and Bluetooth audio.

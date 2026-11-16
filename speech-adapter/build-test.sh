#!/bin/zsh
set -eu
cd "${0:A:h}"
export JAVA_HOME="${SPEECH_JAVA_HOME:-${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}}"
sdk="${SPEECH_ANDROID_SDK:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}}}"
bt="$sdk/build-tools/36.0.0"
android="$sdk/platforms/android-36/android.jar"
export PATH="$JAVA_HOME/bin:$PATH"
[[ -f build/test-assets/test.pcm && -f build/local-signing.jks ]]
mkdir -p build/test-classes build/test-dex
"$bt/aapt2" link -o build/test-base.apk --manifest tests/AndroidManifest.xml -I "$android" -A build/test-assets --min-sdk-version 33 --target-sdk-version 36
javac --release 17 -classpath "$android" -d build/test-classes tests/RecognitionProbe.java
jar --create --file build/test-classes.jar -C build/test-classes .
"$bt/d8" --lib "$android" --min-api 33 --output build/test-dex build/test-classes.jar
cp build/test-base.apk build/test-unsigned.apk
(cd build/test-dex && zip -q -u ../test-unsigned.apk classes.dex)
"$bt/zipalign" -f 4 build/test-unsigned.apk build/test-aligned.apk
"$bt/apksigner" sign --ks build/local-signing.jks --ks-key-alias local --ks-pass pass:android --key-pass pass:android --out build/glasses-speech-test.apk build/test-aligned.apk

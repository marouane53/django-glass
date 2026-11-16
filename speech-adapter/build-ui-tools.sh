#!/bin/zsh
set -eu
cd "${0:A:h}"
export JAVA_HOME="${SPEECH_JAVA_HOME:-${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}}"
sdk="${SPEECH_ANDROID_SDK:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}}}"
bt="$sdk/build-tools/36.0.0"
android="$sdk/platforms/android-36/android.jar"
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p build/ui-classes build/ui-dex
"$bt/aapt2" link -o build/ui-base.apk --manifest ui-tools/AndroidManifest.xml -I "$android" --min-sdk-version 33 --target-sdk-version 36
javac --release 17 -classpath "$android" -d build/ui-classes ui-tools/*.java
jar --create --file build/ui-classes.jar -C build/ui-classes .
"$bt/d8" --lib "$android" --min-api 33 --output build/ui-dex build/ui-classes.jar
cp build/ui-base.apk build/ui-unsigned.apk
(cd build/ui-dex && zip -q -u ../ui-unsigned.apk classes.dex)
"$bt/zipalign" -f 4 build/ui-unsigned.apk build/ui-aligned.apk
"$bt/apksigner" sign --ks build/local-signing.jks --ks-key-alias local --ks-pass pass:android --key-pass pass:android --out build/glasses-ui-tools.apk build/ui-aligned.apk

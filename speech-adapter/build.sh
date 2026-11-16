#!/bin/zsh
set -eu
cd "${0:A:h}"
export JAVA_HOME="${SPEECH_JAVA_HOME:-${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}}"
sdk="${SPEECH_ANDROID_SDK:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}}}"
bt="$sdk/build-tools/36.0.0"
android="$sdk/platforms/android-36/android.jar"
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p build/classes build/dex
"$bt/aapt2" compile --dir res -o build/resources.zip
"$bt/aapt2" link -o build/base.apk --manifest AndroidManifest.xml -I "$android" --min-sdk-version 33 --target-sdk-version 36 build/resources.zip
sources=(src/**/*.java)
javac --release 17 -classpath "$android" -d build/classes $sources
jar --create --file build/classes.jar -C build/classes .
"$bt/d8" --lib "$android" --min-api 33 --output build/dex build/classes.jar
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q -u ../unsigned.apk classes.dex)
"$bt/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [[ ! -f build/local-signing.jks ]]; then
  keytool -genkeypair -keystore build/local-signing.jks -storepass android -keypass android -alias local -keyalg RSA -keysize 3072 -validity 3650 -dname 'CN=Marouane Local Glasses Speech' >/dev/null 2>&1
fi
"$bt/apksigner" sign --ks build/local-signing.jks --ks-key-alias local --ks-pass pass:android --key-pass pass:android --out build/glasses-speech.apk build/aligned.apk
"$bt/apksigner" verify build/glasses-speech.apk
echo 'Built build/glasses-speech.apk'

#!/bin/bash
# Builds build/focusguard.apk with the Android SDK command-line tools (no Gradle).
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-36/android.jar"
rm -rf build && mkdir -p build/classes build/dex build/gen
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml --min-sdk-version 29 --target-sdk-version 36 \
  --java build/gen -o build/base.apk build/res.zip
javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$JAR" -d build/classes \
  $(find src build/gen -name '*.java')
"$BT/d8" --min-api 29 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [ ! -f focusguard.keystore ]; then
  keytool -genkeypair -keystore focusguard.keystore -alias fg -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass focusguard -keypass focusguard -dname "CN=Focus Guard" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks focusguard.keystore --ks-pass pass:focusguard --out build/focusguard.apk build/aligned.apk
echo "Built build/focusguard.apk"

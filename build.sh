#!/usr/bin/env bash
# Builds a signed APK without Gradle, using the command-line Android build tools.
#
# Needs: a JDK (javac), and an Android SDK providing android.jar, aapt, dx (or d8),
# zipalign and apksigner. On Debian/Ubuntu everything is available via:
#   sudo apt-get install android-sdk-platform-23 aapt dalvik-exchange zipalign apksigner
# Or point ANDROID_JAR / BUILD_TOOLS at a regular Android SDK installation.
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
BUILD_TOOLS=${BUILD_TOOLS:-/usr/lib/android-sdk/build-tools/debian}
AAPT=${AAPT:-$BUILD_TOOLS/aapt}
ZIPALIGN=${ZIPALIGN:-$(command -v zipalign || echo "$BUILD_TOOLS/zipalign")}
APKSIGNER=${APKSIGNER:-$(command -v apksigner || echo "$BUILD_TOOLS/apksigner")}
KEYSTORE=${KEYSTORE:-keystore/debug.keystore}
KS_PASS=${KS_PASS:-android}
KEY_ALIAS=${KEY_ALIAS:-androiddebugkey}

SRC=app/src/main
OUT=build
APK=$OUT/LittleRoads.apk

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "[1/5] Compiling resources"
"$AAPT" package -f -m -J "$OUT/gen" -M "$SRC/AndroidManifest.xml" -S "$SRC/res" \
    -I "$ANDROID_JAR" -F "$OUT/resources.apk" -0 arsc

echo "[2/5] Compiling Java"
find "$SRC/java" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -Xlint:none -source 8 -target 8 -encoding UTF-8 -bootclasspath "$ANDROID_JAR" \
    -d "$OUT/classes" @"$OUT/sources.txt" 2>&1 | grep -v -e '^warning: \[options\]' -e 'Picked up JAVA_TOOL_OPTIONS' -e '^[0-9]* warnings\?$' || true
[ -f "$OUT/classes/com/littleroads/game/MainActivity.class" ] || { echo "javac failed"; exit 1; }

echo "[3/5] Converting to dex"
if [ -x "$BUILD_TOOLS/d8" ]; then
    "$BUILD_TOOLS/d8" --min-api 21 --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
else
    "$BUILD_TOOLS/dx" --dex --min-sdk-version=21 --output="$OUT/dex/classes.dex" "$OUT/classes"
fi

echo "[4/5] Packaging"
cp "$OUT/resources.apk" "$OUT/unaligned.apk"
(cd "$OUT/dex" && zip -q -X ../unaligned.apk classes.dex)
"$ZIPALIGN" -f -p 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

echo "[5/5] Signing"
if [ ! -f "$KEYSTORE" ]; then
    mkdir -p "$(dirname "$KEYSTORE")"
    keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" -alias "$KEY_ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias "$KEY_ALIAS" \
    --min-sdk-version 21 --out "$APK" "$OUT/aligned.apk"
"$APKSIGNER" verify "$APK"
echo "Built $APK ($(du -h "$APK" | cut -f1))"

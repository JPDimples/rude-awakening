#!/bin/bash
# Rude Awakening — native Kotlin manual build (aapt2 + kotlinc + d8).
# Proven pattern from the TapTalk build on this VM. No Gradle, no external
# dependencies: only android.jar + kotlin-stdlib.
# Env overrides (used by GitHub Actions):
#   JAVA_HOME, ANDROID_SDK_ROOT, KOTLINC_BIN
set -e
export JAVA_HOME="${JAVA_HOME:-$HOME/workspace/taptalk/build-tools/jdk17}"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_SDK_ROOT:-$HOME/workspace/taptalk/build-tools/android-sdk}"
KOTLINC_BIN="${KOTLINC_BIN:-$HOME/workspace/taptalk/build-tools/kotlinc/bin/kotlinc}"
STDLIB="${KOTLIN_STDLIB:-$HOME/workspace/taptalk/build-tools/kotlinc/lib/kotlin-stdlib.jar}"
APP="$PWD/app/src/main"
OUT="$PWD/manual-out"
rm -rf "$OUT"
mkdir -p "$OUT/compiled" "$OUT/gen" "$OUT/classes" "$OUT/apk"

# 1. Compile resources
"$SDK/build-tools/34.0.0/aapt2" compile --dir "$APP/res" -o "$OUT/compiled.zip"

# 2. Link -> APK without dex, generates R.java, includes assets/
"$SDK/build-tools/34.0.0/aapt2" link -o "$OUT/base.apk" \
  -I "$SDK/platforms/android-34/android.jar" \
  --manifest "$APP/AndroidManifest.xml" \
  -A "$APP/assets" \
  "$OUT/compiled.zip" \
  --auto-add-overlay \
  --java "$OUT/gen"

echo "--- R.java ---"
find "$OUT/gen" -name "*.java" | head

# 3. Compile Kotlin + R.java
"$KOTLINC_BIN" -cp "$SDK/platforms/android-34/android.jar" \
  -d "$OUT/classes" \
  "$APP/java/com/johnparnham/rudeawakening/"*.kt \
  $(find "$OUT/gen" -name "*.java")

# 4. Jar the classes first, then dex the jar
(cd "$OUT/classes" && "$JAVA_HOME/bin/jar" cf "$OUT/classes.jar" .)
"$SDK/build-tools/34.0.0/d8" --output "$OUT/apk" "$OUT/classes.jar" "$STDLIB"
find "$OUT/apk" -type f | head -20

# 5. Add dex to APK
cd "$OUT/apk"
if [ -f classes.dex ]; then
  zip -q "$OUT/base.apk" classes.dex
  echo "dex added"
else
  echo "NO DEX FOUND"
  exit 1
fi
cd "$OLDPWD"

# 6. Align
"$SDK/build-tools/34.0.0/zipalign" -f 4 "$OUT/base.apk" "$OUT/RudeAwakening-aligned.apk"

# 7. Debug keystore + sign (keystore lives OUTSIDE the wiped output dir,
# so the same signing key is reused for every build).
KEYSTORE="$PWD/rude-awakening.keystore"
if [ ! -f "$KEYSTORE" ]; then
  "$JAVA_HOME/bin/keytool" -genkey -v -keystore "$KEYSTORE" -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Rude Awakening, OU=Rude Awakening, O=Rude Awakening, L=Toronto, ST=Ontario, C=CA" 2>&1 | tail -5
fi
"$SDK/build-tools/34.0.0/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android --out "$PWD/rude-awakening-native-v6.apk" "$OUT/RudeAwakening-aligned.apk"
"$SDK/build-tools/34.0.0/apksigner" verify "$PWD/rude-awakening-native-v6.apk" && echo "APK VERIFIED"
ls -lh "$PWD/rude-awakening-native-v6.apk"

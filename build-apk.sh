#!/usr/bin/env bash
# Run from the folder that contains blendx3d-apk/  (the unzipped project root)
set -euo pipefail
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

cd blendx3d-apk
npm install
[ -d android ] || npx cap add android
npm install --no-save @capacitor/assets
npx capacitor-assets generate --android
npx cap sync android

echo "sdk.dir=$ANDROID_HOME" > android/local.properties
cd android
./gradlew assembleDebug
echo "APK: $(pwd)/app/build/outputs/apk/debug/app-debug.apk"

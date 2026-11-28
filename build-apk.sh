#!/usr/bin/env bash
# Build BlendX3D Android APK from THIS repository root.
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v npm >/dev/null; then echo "Node.js/npm required"; exit 1; fi
if [ ! -f Index.html ]; then echo "Missing Index.html"; exit 1; fi

mkdir -p www assets
cp Index.html www/index.html
[ ! -f ic_launcher.png ] || cp ic_launcher.png assets/icon-only.png
[ ! -f splash.png ] || cp splash.png assets/splash.png

npm install --legacy-peer-deps
if [ ! -d android ]; then npx cap add android; fi
npx cap sync android
(cd android && chmod +x gradlew && ./gradlew assembleDebug)
echo "APK: android/app/build/outputs/apk/debug/app-debug.apk"

#!/usr/bin/env bash
# Installs JDK 17, Node 20, Android SDK (cmdline-tools, platform-tools, platform 34, build-tools 34.0.0)
# for Ubuntu/Debian (x86_64). Run: bash setup-android-env.sh
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"

echo "==> Installing system packages"
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk unzip curl ca-certificates git

if ! command -v node >/dev/null || [ "$(node -v | cut -d. -f1 | tr -d v)" -lt 18 ]; then
  echo "==> Installing Node 20"
  curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
  sudo apt-get install -y nodejs
fi

echo "==> Installing Android command-line tools to $ANDROID_HOME"
mkdir -p "$ANDROID_HOME/cmdline-tools"
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  tmp="$(mktemp -d)"
  curl -fL -o "$tmp/$CMDLINE_TOOLS_ZIP" "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"
  unzip -q "$tmp/$CMDLINE_TOOLS_ZIP" -d "$tmp"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp"
fi

export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "==> Accepting licenses and installing SDK packages"
yes | sdkmanager --licenses >/dev/null || true
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

PROFILE="$HOME/.bashrc"
if ! grep -q "ANDROID_HOME=" "$PROFILE"; then
  cat >> "$PROFILE" <<EOP

# Android SDK
export JAVA_HOME=\$(dirname \$(dirname \$(readlink -f \$(which javac))))
export ANDROID_HOME="$ANDROID_HOME"
export ANDROID_SDK_ROOT="\$ANDROID_HOME"
export PATH="\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH"
EOP
fi
echo "==> Done. Open a new shell (or: source ~/.bashrc), then run: bash build-apk.sh"

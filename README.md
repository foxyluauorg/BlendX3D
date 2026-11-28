# BlendX3D — Android 3D editor

BlendX3D is an experimental Blender-inspired 3D editing app for Android, built from the existing `Index.html` using **Capacitor** and Android WebView. This repository is **not the Blender desktop engine** and does not implement every Blender feature.

## Download / build Android APK using GitHub

1. Open [Build BlendX3D APK](https://github.com/foxyluauorg/BlendX3D/actions/workflows/build-apk.yml).
2. Select the newest **successful** workflow run on `main`.
3. Under **Artifacts**, download `BlendX3D-APK`.
4. Extract the ZIP and install `app-debug.apk` on Android. This is a debug build, not a signed Play Store release.

Each push to `main` triggers a new build. You can also use **Run workflow**.

## Build locally

Requires Node.js 20, Java 21 and an Android SDK with platform 35. From the repository root:

```bash
bash build-apk.sh
```

The APK is generated at `android/app/build/outputs/apk/debug/app-debug.apk`. The script copies `Index.html` and `three.min.js` to Capacitor's `www/` assets, creates the native Android project if necessary, and builds a debug APK.

## Project files

- `Index.html` — BlendX3D editor and Mesh Core v2 upgrades
- `three.min.js` — locally bundled Three.js r128 for core WebGL rendering without downloading the library at startup
- `THREE-LICENSE.txt` — MIT license for the vendored Three.js library
- `capacitor.config.json` / `package.json` — Android WebView configuration
- `.github/workflows/build-apk.yml` — automated GitHub Actions APK build
- `build-apk.sh` — local build helper

## Status and compatibility

- Existing Blender-like editor and its current functions are retained, with incremental mesh editing upgrades.
- This is **not** full Blender parity: complex mesh-editing operators, full sculpting, advanced rigs, Cycles rendering, Geometry Nodes and native Blender plug-ins are not all implemented.
- Core Three.js is bundled locally, but fonts or optional network integrations may still require Internet.
- Performance depends on Android WebView/WebGL capability and available device memory.
- Any localhost AI/MCP service in the web editor points to the phone itself, not your PC. Use your PC's LAN address for remote services.

## Blender source and licenses

Official Blender source: https://github.com/blender/blender (GPL-3.0 for Blender as a whole; individual files may differ). Its BMesh, Animation/Rigging, Sculpt/Paint and Cycles implementations are useful **references**, but Blender's native C/C++ source cannot simply be pasted into the HTML editor and run in Android WebView. **No Blender engine code was copied into this project in this update.** If Blender GPL code is incorporated later, comply with all applicable copyleft, source distribution and attribution requirements.

Three.js r128 was copied from https://github.com/mrdoob/three.js/tree/r128 under its MIT license (see `THREE-LICENSE.txt`). The project license is in `LICENSE`.

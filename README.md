# BlendX3D -> APK

Layout:
- www/index.html   the editor (your blendx3d.html)
- capacitor.config.json, package.json   Capacitor wrapper
- mcp-server/      your MCP server (runs on a PC, NOT part of the APK)

## Build the APK (needs Node 18+, Android Studio)
    npm install
    npx cap add android
    npx cap sync android
    npx cap open android
In Android Studio: Build > Build Bundle(s)/APK(s) > Build APK(s).
Command line instead:  cd android && ./gradlew assembleDebug
APK: android/app/build/outputs/apk/debug/app-debug.apk

## No Android Studio?
Zip only the www/ folder contents and upload to an online builder
(e.g. AppMySite / Median / WebIntoApp), or host www/ and use PWABuilder.

## Notes
- The editor loads three.js (r128) and the Inter font from the internet, so the app needs
  a connection. For offline use, download
  https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js into www/three.min.js
  and change the <script src> in www/index.html to "three.min.js".
- The AI / MCP Connect feature talks to http://localhost:3333, which on a phone means the
  phone itself. To use it, run mcp-server on a PC (node server.js) and enter the PC's
  LAN address (http://192.168.x.x:3333) in the app's AI / MCP Connect panel.

<p align="center">
  <img alt="OpenQR icon" src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_legacy.png" width="96">
</p>

<h1 align="center">OpenQR</h1>

<h3 align="center">A QR code reader for Meta Quest 3 and 3S</h3>

<p align="center">
  <a href="#install">Install</a> ·
  <a href="#build-from-source">Build</a> ·
  <a href="https://orgista.com">orgista.com</a>
</p>

<p align="center">
  <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/badge/license-MIT-2ea44f?style=flat-square"></a>
  <a href="#compatibility"><img alt="Platform: Meta Quest" src="https://img.shields.io/badge/platform-Meta%20Quest-1c1e20?style=flat-square"></a>
  <a href="#compatibility"><img alt="Min API 34" src="https://img.shields.io/badge/min%20API-34-3ddc84?style=flat-square"></a>
</p>

OpenQR reads QR codes through the Quest passthrough camera and opens the link in the system browser. Look at a code, tap Scan, and the page opens.

## Features

- Reads QR codes from the headset passthrough camera
- Decodes on the device with ZXing, no network needed to scan
- Opens `http` and `https` links in the external browser
- Rejects everything else (other schemes, links with embedded credentials) and shows "Web link only"
- Camera mode toggle shows a live preview before you scan
- Runs as a resizable 2D window (384 x 500 dp default) beside your other apps
- Stops the camera when the window loses focus or pauses
- Native Kotlin app, ZXing core for decoding

## Install

There are no published releases yet. Build the APK (see below), then install it over ADB with your headset in developer mode:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug build installs as `com.orgista.openqr.debug`. On first launch, allow camera access when prompted.

## Compatibility

| Device | Minimum | Notes |
| --- | --- | --- |
| Meta Quest 3 | Android API 34 | Passthrough camera access required |
| Meta Quest 3S | Android API 34 | Passthrough camera access required |

The manifest restricts the app to `quest3` and `quest3s`. It requests the standard `CAMERA` permission and the Horizon OS `HEADSET_CAMERA` permission.

## Build from source

Prerequisites: Java 17, Android SDK 34, and the Gradle wrapper included in this repo.

```bash
git clone https://github.com/orgista/openQR.git
cd openQR
./gradlew assembleDebug
```

| Command | Description |
| --- | --- |
| `./gradlew assembleDebug` | Debug APK at `app/build/outputs/apk/debug/` |
| `./gradlew assembleManaged` | Minified build signed with the debug key, installs as `com.orgista.openqr.debug` |
| `./gradlew test` | Unit tests |

## Privacy

Camera frames are processed on the device. OpenQR asks for no network permission and has no account, backend, or analytics. The only thing it sends anywhere is the link you scan, and it hands that to your browser.

## More from orgista

- [openPDF](https://github.com/orgista/openPDF): a PDF reader for Meta Quest

## Contributing

Issues and pull requests are welcome. Keep changes small and describe how you tested them on a headset.

## License

[MIT](LICENSE)

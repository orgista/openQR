<p align="center">
  <img alt="OpenQR icon" src=".github/assets/openqr-icon.png" width="96">
</p>

<h1 align="center">OpenQR</h1>

<h3 align="center">A QR code reader for Meta Quest 3 and 3S</h3>

<p align="center">
  <a href="https://www.meta.com/experiences/openqr/1236442736226659/"><strong>Get it on the Meta Horizon Store</strong></a> ·
  <a href="https://github.com/orgista/openQR/releases/latest">Download the APK</a> ·
  <a href="#build-from-source">Build from source</a>
</p>

<p align="center">
  <a href="LICENSE"><img alt="License: Apache 2.0" src="https://img.shields.io/badge/license-Apache%202.0-2ea44f?style=flat-square"></a>
  <a href="https://www.meta.com/experiences/openqr/1236442736226659/"><img alt="Meta Horizon Store" src="https://img.shields.io/badge/Meta%20Horizon%20Store-live-0081fb?style=flat-square"></a>
  <a href="https://github.com/orgista/openQR/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/orgista/openQR?style=flat-square"></a>
</p>

OpenQR reads QR codes through the Quest passthrough camera and opens what's in them. It runs in a normal window, so you can keep it next to whatever else you have open.

## What it opens

- **Websites** open in your browser, or in an installed app that claims that site's links.
- **YouTube links** play in YouTube VR, 360° videos included.
- **Meta Horizon Store links** open the app's page in the Store.
- **Matterport tours** open with the tour already running, one tap from VR.
- **App deep links** (`myapp://anything`) open the app that declares that scheme.

Close whatever it opened and you're back at the scanner. OpenQR refuses scripts, files and `intent:` links. Wi-Fi codes, contacts and plain text show as unsupported for now.

### For developers

Put any of these in a QR code:

```
myapp://anything
https://yoursite.com/page
https://www.youtube.com/watch?v=VIDEO_ID
https://www.meta.com/experiences/APP_ID/
https://my.matterport.com/show/?m=MODEL_ID
```

A deep link needs an intent filter with the `BROWSABLE` category in your app's manifest. A website opens in your app when your app is the one installed app that claims its links.

## Install

**Meta Horizon Store:** [OpenQR on the Meta Horizon Store](https://www.meta.com/experiences/openqr/1236442736226659/). This is the easiest way, and it keeps the app updated.

**Sideload:** download `openqr-quest-v*.apk` from the [latest release](https://github.com/orgista/openQR/releases/latest) and install it with your headset in developer mode:

```bash
adb install -r openqr-quest-v1.0.60.apk
```

The release APK is the same build that ships on the Store. Check it against `SHA256SUMS` in the release.

## Compatibility

| Device | Minimum | Notes |
| --- | --- | --- |
| Meta Quest 3 | Horizon OS, Android API 34 | Passthrough camera access required |
| Meta Quest 3S | Horizon OS, Android API 34 | Passthrough camera access required |

OpenQR asks for the standard `CAMERA` permission and the Horizon OS `HEADSET_CAMERA` permission. Meta only gives apps camera access on Quest 3 and 3S.

## Build from source

Prerequisites: JDK 17 or newer, Android SDK 36, and the Gradle wrapper in this repo.

```bash
git clone https://github.com/orgista/openQR.git
cd openQR
./gradlew assembleQuestDebug
```

| Command | Description |
| --- | --- |
| `./gradlew assembleQuestDebug` | Quest debug APK at `app/build/outputs/apk/quest/debug/`, installs as `com.orgista.openqr.debug` |
| `./gradlew assemblePlayDebug` | Phone and tablet build |
| `./gradlew testQuestDebugUnitTest` | Unit tests |

Release builds need your own signing key, passed through the `OPENQR_KEYSTORE_FILE`, `OPENQR_KEYSTORE_PASS`, `OPENQR_KEY_ALIAS` and `OPENQR_KEY_PASS` environment variables.

## Privacy

OpenQR has no internet permission, no account and no analytics. It reads camera frames on the headset to find the code, then discards them. It keeps no scan history and hands the link straight to the app that opens it.

## Contributing

Issues and pull requests are welcome. Keep changes small and say how you tested them on a headset.

## License

[Apache 2.0](LICENSE). Third-party notices are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

---

<p align="center">
  <a href="https://orgista.com">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset=".github/assets/orgista-mark-dark.svg">
      <img alt="Orgista" src=".github/assets/orgista-mark-light.svg" width="40">
    </picture>
  </a>
  <br>
  Made by <a href="https://orgista.com">Orgista</a>. More apps at <a href="https://github.com/orgista">github.com/orgista</a>.
</p>

# Boot splash

The splash Horizon OS shows while OpenQR's mixed reality mode loads: the Corner mark above the "OpenQR" wordmark, on the graphite app-icon plate.

- `vr_splash.png`: 1024×1024 PNG, transparent outside the rounded plate (rx 232, the app icon's corner).
- `splash.svg`: the source. The wordmark is live text in Inter 600 (the store art's face), so render the PNG somewhere Inter is loaded; the PNG here was rendered that way.

## Why the plate

The splash can show over black or over the person's room (`passthrough-contextual`). Brand-ink brackets alone would vanish against a bright wall, so the mark and wordmark always sit on the graphite tile (`brand-tile-top` → `brand-tile-bottom`).

## Wiring (immersive / Spatial SDK activity)

Per Meta's native Android splash docs, the system shows `assets/vr_splash.png` while the app loads and fades it out on the first frame:

1. Copy `vr_splash.png` to `src/main/assets/vr_splash.png` (the name is case-sensitive).
2. Add inside `<application>`:

```xml
<meta-data android:name="com.oculus.ossplash" android:value="true" />
<meta-data android:name="com.oculus.ossplash.type" android:value="mono" />
<meta-data android:name="com.oculus.ossplash.background" android:value="passthrough-contextual" />
```

`passthrough-contextual` keeps the person in their room while OpenQR loads, matching the MR direction; use `black` if that looks wrong on device.

## To check on a headset

- **Size:** the displayed size comes from the image's proportions. Tune it with `adb shell setprop debug.oculus.ossplash.ratio <float>` before changing the PNG.
- **Transparency:** Meta's docs don't say how a transparent PNG composites over passthrough. If the corners show black, crop to the plate or switch to `black`.
- **2D panel launch:** the docs only cover immersive apps. The 2D scanner panel keeps Horizon OS's default launch behaviour.

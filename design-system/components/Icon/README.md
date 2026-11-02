# Icon

OpenQR's 24dp icons: the app's own drawables (scan, check, warning, lock, camera), plus stand-ins for the Meta UI Set icons the 1.1 screens use.

- Icons take `currentColor`: `text-primary` on surfaces, `action-button-text` on white buttons, a status token (`positive`, `warning`, `negative`) only next to words.
- Stand-ins (internet, youtube, store, app, wifi, bluetooth, unsupported, stop, open, play, positive, negative) are not Meta's artwork. In the app, use `metavrx.uiset.compose.theme.icons.Icons.Regular.*`.

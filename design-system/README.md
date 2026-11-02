OpenQR, by Orgista, reads a QR code through the Quest 3 / 3S passthrough camera and gets the person to its destination in seconds: a web or app link, a Wi-Fi network, or Bluetooth pairing. It has no account, no history and no network permission of its own. OpenQR is moving to **mixed reality**: no scanner window. An always-on pill floats near the person, and every QR code in passthrough gets a tag drawn on it that they point at and click. Input is controller ray or hand pinch, so hover comes before every click. This system covers the pill, the tags on codes and the brand they share; the Mixed reality feasibility section says what the platform allows.

## Principles

1. **One job, finished fast.** Every state either moves toward the destination or says exactly how to recover.
2. **Native to Horizon OS.** 48dp targets (`target`), hover states on everything, UI placed in the room, never locked to the head.
3. **Say what happened and what to do next.** Never a bare status word.
4. **Calm by default.** Motion only when it carries state (scanning), never decorative idle loops.
5. **Privacy you can see.** No history, nothing stored, and the UI never implies otherwise.

## Content

- Short, plain, calm. Sentence case, no exclamation marks, no hype, no emoji.
- Say what happened, then the next step. Real lines from the app: "Camera access is off" / "Turn it on in Settings." · "No app can open this" / "Install one, then scan again." · "Paused" / "Nothing scanned for 10 minutes." In MR the pill carries one line at a time, so lead with the part that matters: "Opening example.com", "Spatial data is off".
- Buttons start with a verb: "Allow camera", "Scan again", "Open Settings", "Join network", "Copy password".
- Questions only for a confirmation: "Join Cafe-North?", "Pair Desk Speaker?".
- Terms: "scan", "link", "code", "spatial data" (Meta's name for the permission). Never "history". The app is always "OpenQR".
- Name the destination, never the payload: "example.com", "YouTube", "Meta Horizon Store", "Join <SSID>". Never show or log passwords, full URLs with tokens, or Bluetooth addresses.

## Colour

- One theme, **Graphite**. The pill is `surface`; icon targets are `preview-frame`. v0 drew `outer-border` around its panel; new work doesn't, since Horizon OS draws the panel edge.
- Text on `surface`: `text-primary` for titles and icons, `text-secondary` for supporting lines, `text-tertiary` for hints only.
- Buttons are white capsules (`button-fill`, label `action-button-text`). While their action runs they turn `scan-button-fill-active` with an `on-accent` label. The quiet second choice is `secondary-button-fill`.
- The brand hue lives in the mark's core: `brand-sky` → `brand-blue`. In the UI, `brand-sky` draws the tags on real codes, the scanning ring and the hover/focus ring (`ring-hover`); `brand-blue` is the spinner on a white chip. Neither fills a button.
- `scan-frame` and `scan-bracket` are v0's scanner-window blues (about 2:1). The MR design doesn't use them: tags sit on real walls, so they need `brand-sky`'s contrast.
- Status: `positive` (success), `warning`, `negative`. These three stand in for Meta UI Set roles the repo doesn't contain. Swap in the UI Set values when they're available. Status is never colour alone: always an icon plus words.
- The 1.1 Compose screens take their colours from the Meta UI Set theme (owner decision, 2026-09-25). These tokens are v0's values and the brand; use them where the UI Set doesn't reach (brand surfaces, docs, store art).

## Type

- Roboto (Android `sans-serif` / `sans-serif-medium`), `--font-sans`.
- 1.1 screens use the Meta UI Set scale in `--font-ui`. On device that's Meta's Optimistic face; Inter stands in off-device. Use `headline` (20sp bold) for the state title and the "Scanned" heading, `title` (17sp) for dialog titles, and `body` (14sp medium) for everything else. 14sp is the legibility floor: `label`, `bodySmall` and `caption` stay out of the headset UI.
- v0: `app-title` 22sp medium in a 48dp row, `button` 14sp medium. v0's `status` is 13sp and falls below the floor: don't use it in new work.

## Space, size and shape

- Grid: `space-8` pill padding and the gap between notices, `space-16` between a code and its chip at panel scale, `space-24` around a result line. v0 used `space-10` / `space-14`.
- In the room: the pill sits at `world-pill-distance` (about 1 m), `world-pill-drop` below eye level, body-locked with lazy follow. Chips sit `world-tag-gap` under their code and never shrink below `world-text-min`. Tag codes within `world-code-range`.
- The pill is 72dp (the scan toggle alone), widening up to `pill-width` (360dp) only for a one-line message. Notices are `notice-width` wide, stacked `space-8` above it.
- Shapes: every control is a capsule or a disc (`radius-pill`, or `radius-22` on a 44dp UI Set button). Tag brackets are rounded with `radius-8`. Dialogs are `radius-24`, tooltips and cards `radius-8`, menus and fields `radius-12`. v0 used `radius-16` wells and a `radius-20` panel. No shadows, and no app-drawn panel border: Horizon OS draws the panel edge itself.

## Motion

- The only loop is the scan: the ring on the scan toggle turns once every 2000ms while the tracker runs. Opening shows a 900ms spinner. Both hold still when the system remove-animations setting is on.
- Tags don't animate to follow codes: MRUK updates poses slower than the frame rate, so a tag jumps to the new position rather than sliding.
- Timings that shape behaviour: nothing opens by itself in MR; the person clicks. A tag whose code is lost fades to `out-of-view` for 8 s, then goes. A handed-off code won't reopen until it has been out of view for 30 s. Tracking pauses after 10 minutes with no code.
- Tooltips and hover rings fade in at 120ms.

## The always-on pill and the tags

- **The pill** is a small capsule near the person: just the scan toggle (a `brand-sky` arc turning while the tracker runs; tap to stop), or one line for a message about OpenQR itself ("Spatial data is off"). Codes never go inside it.
- **The tags** sit on the real codes: corner brackets around the code and a destination chip under it. Point at the chip and click; the chip turns into "Opening example.com" with a spinner.
- **Notices above the pill** (`NoticeStack`) list every code found, newest nearest the pill. They're the accessible path (focus, keyboard, people who can't point precisely at a far code), and with Spatial Data off they're the only path.
- **"Always on" means while OpenQR's MR mode runs.** The person starts that mode; OpenQR never switches into it by itself. Opening a link hands off to the browser, which likely ends or backgrounds the session.
- **Fallback without Spatial Data:** when Spatial Data is denied or withheld (managed headsets), there are no tags. The camera reads codes instead, and the notices above the pill carry everything.
- **Platform limits:** MRUK reads QR up to version 10, no Micro QR or logo codes, best close, large and well lit. It needs Spatial Data permission, which managed (sideloaded) headsets may withhold. Details are in the Mixed reality feasibility section.

## Iconography

- The mark is the "Corner": a heavy round-capped corner bracket in `brand-ink`, a dim opposite bracket at `mark-dim`, and a lit rounded core from `brand-sky` to `brand-blue`, on a 48-unit grid. Use the files in **Logos** as they are. Never redraw it, recolour the core, or set "OpenQR" beside it in a custom face.
- **Boot splash** (**Splash**): the mark above the "OpenQR" wordmark in Inter 600, on the graphite app-icon plate, so it reads over black or passthrough. It ships as `assets/vr_splash.png` with `com.oculus.ossplash` set in the manifest; the setup notes are in the repo at `design-system/splash/README.md`.
- UI icons are 24dp at `text-primary`. The app's own drawables are scan, check, warning, lock and camera. The 1.1 screens use Meta UI Set icons (`Icons.Regular.*`); the Icon component carries stand-ins for those, which are not Meta's artwork.

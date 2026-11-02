# NoticeStack

Every code OpenQR finds appears as a notice stacked above the pill, never inside it. Point at a notice and click to open. With Spatial Data on, the notices sit alongside the tags on the codes: they're the way to pick a code that's far away or awkward to point at, and the path for focus and keyboard navigation. Without Spatial Data (permission denied, or a managed headset that withholds it), they're the only way.

**Without Spatial Data.** MRUK can't say where a code is in the room, so no tags can be drawn. The fallback reads codes the way OpenQR does today, through the passthrough camera (Camera2 + ZXing, `HEADSET_CAMERA` permission, frames kept in memory only), and lists what it found next to the pill.

## Anatomy

- **One notice per code:** a 48dp kind icon, the destination as the title ("example.com", "Join Cafe-North"), the kind as a second line ("Website", "Video", "Store page", "Wi-Fi network"), and a `brand-sky` open glyph. The whole notice is the button.
- **Order:** first seen at the top, newest nearest the pill. Entries keep their place; nothing reorders under the pointer.
- **Up to three** (`max`); older ones collapse into "N more" above the stack.
- **Width:** `notice-width`, `space-8` above the pill and `space-8` apart; corners `radius-24`; fill `surface`.

## States

| `state` | Look |
| --- | --- |
| `inView` | full strength, open glyph |
| `lingering` | the code left view: `out-of-view` opacity and "Out of view" for 8 s, then the notice goes. Still clickable |
| `opening` | after a click: spinner, "Opening example.com", `ring-hover`, no further clicks |
| `unsupported` | "Can't open this code", greyed, not clickable |

## Rules

- Nothing opens by itself; a lone code waits for a click too.
- Names, never payloads: no raw URLs, SSIDs or passwords on a notice.
- The pill never lists codes: every code is a notice. Opening a notice and clicking its tag do the same thing, and both show "Opening …" together.
- A notice is a status, not an alert: no sound, no motion beyond a 300ms fade in and out.

## Consumer provides

`notices` (`{id, kind, label?, state?}`), `max` (default 3) and `onOpen`.

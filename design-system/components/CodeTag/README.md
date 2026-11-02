# CodeTag

What OpenQR draws on a real QR code in passthrough: four `brand-sky` corner brackets around the code, and a destination chip just below it. Point at the chip and click to open.

**Where it lives.** One small panel entity per `TrackedQrCode`, following that code's MRUK anchor, with the chip turned to face the person. Size the brackets from the code's bounds when the SDK provides them; otherwise use a fixed size centred on the anchor.

## States

| `state` | Look |
| --- | --- |
| `found` | `brand-sky` brackets (`tag-stroke`), a white chip naming the destination ("example.com", "YouTube") |
| `hover` | the ray or hand is on it: brackets turn `brand-ink`, the chip gets `ring-hover` |
| `opening` | after a click: "Opening example.com" with a spinner; the chip stops taking clicks |
| `unsupported` | dashed `text-tertiary` brackets, a disabled "Can't open" chip |
| `lost` | tracking dropped: the whole tag fades to `out-of-view` for 8 s, then goes |

## Rules

- **The chip is the target, not the code.** It's at least 48dp, sits `world-tag-gap` below the code, and scales up on far codes so its text holds `world-text-min` (1° of visual arc).
- **Tags stay put; they don't chase.** MRUK updates poses slower than the frame rate, so a moving code (a phone screen) re-tags rather than sliding smoothly.
- **Only tag what MRUK reads:** QR up to version 10, no Micro QR, no logo codes, best within `world-code-range`.
- **Every tag has a matching notice above the pill.** Clicking either opens the code, and both show "Opening …".
- **Use the same link rules as today:** web and app links open; `intent:` and `javascript:` are refused (the `unsupported` state).

## Consumer provides

`state`, `kind`, `label` (the destination's name), `size` (px at panel scale), and `onClick`.

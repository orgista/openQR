# PillReader

The always-on pill for OpenQR's mixed reality mode: a small capsule near the person that shows scanning is on. Codes it finds never go inside it; they appear as notices stacked above it (`NoticeStack`), and as tags on the codes themselves when Spatial Data is on.

**Where it lives.** A panel entity in OpenQR's Spatial SDK scene, body-locked with lazy follow at `world-pill-distance` (about 1 m) and `world-pill-drop` (about 20° below eye level). It never locks to the head. "Always on" means while OpenQR's MR mode is running; see the Mixed reality feasibility section.

## Modes

| `mode` | Pill |
| --- | --- |
| `scanning` | the `ScanToggle` alone, a 72dp disc: a turning `brand-sky` arc; hover shows Stop, a tap stops |
| `paused` | `ScanToggle` off, "Paused. Resume". Tracking stops after 10 minutes with no code, to save power |
| `off` | `ScanToggle` off, "Scan" |
| `status` | one line for a message about OpenQR itself, not a code: "Spatial data is off" → Allow, "Join Cafe-North?" → Join, "No app can open this" |

## Rules

- The pill holds the toggle or one line, nothing else. Codes, and their "Opening …" state, live on notices and tags.
- Notices sit `space-8` above the pill and grow upward; the pill never moves to make room.
- Every target is 48dp (`target`); hover and focus show the label as a tooltip.
- No panel border: the system draws panel edges.

## Consumer provides

`mode`, `status` (`{kind: positive | warning | negative | opening, title, action?: {label}}`), and the handlers `onToggleScan` and `onAction`.

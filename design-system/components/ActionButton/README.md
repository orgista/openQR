# ActionButton

The capsule button from the v0 drawables: white fill at rest, grey `scan-button-fill-active` while its action is running.

- **primary**: `button-fill` with an `action-button-text` label; on hover, `button-fill-hovered` and `button-stroke-hovered`; pressed, `button-fill-pressed`.
- **active** (`active`): "Scanning" and similar running states. Fill `scan-button-fill-active` with an `on-accent` label.
- **quiet**: `secondary-button-fill` with a `text-primary` label, for the second choice ("Copy password").
- Height 48dp, min width 88dp, padding `space-18`, label in `button`. `expanded` fills the row up to `primary-button-max`.
- The label says what happens next, verb first: "Scan again", "Allow camera", "Open Settings", "Join network".

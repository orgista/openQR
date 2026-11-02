# ScanToggle

The pill's only control: a 56dp disc that shows scanning is on with a sweeping ring, and turns it off on tap.

- **On:** the app's own scan glyph (`ic_state_scan`), with a 3dp ring: an `accent-soft` track and a `brand-sky` arc turning once every 2000ms, linear. Hover or focus swaps the glyph to Stop and adds `ring-hover`. Label: "Scanning. Stop".
- **Off:** the play glyph in `text-secondary`, with no ring. Label: "Scan" or "Paused. Resume".
- **Reduced motion:** the arc stops turning and becomes a dashed full ring, still distinct from off.
- **Keep it running when the panel loses focus.** Unfocused panels stay visible, and the ring is state, not decoration.
- **Interaction:** press scales to 0.95 (UI Set standard indication). Tap only: no long press, which is unreliable with a controller.

# CodeChip

One decoded code as a button, named by its destination: under the code in the room (inside a `CodeTag`), or as a row in a list.

- **On a code** (default): a white capsule just below the code. It is the click target, so it stays at least 48dp.
- **Row** (`variant="row"`): a full-width quiet capsule for lists; `inView={false}` dims it to `out-of-view`.
- **`opening`**: after a click, the chip reads "Opening <label>" with a spinner and a `ring-hover` outline, and stops taking clicks.
- **Kinds and default labels:** web (the host), youtube "YouTube", store "Meta Horizon Store", app "Open app", wifi "Join <SSID>", bluetooth "Pair <name>", unsupported "Can't open" (disabled).

# OpenQR design system (snapshot)

A copy of the OpenQR design system so agents working in this repo can read it. The live version, with rendered previews, is the claude.ai Design System artifact: https://claude.ai/artifact/4P3zzndPxfSPjmVbZXLSLC (private to the owner's account). If the two differ, the artifact wins.

Read in this order:

1. `README.md`: the brand book (principles, content, colour, type, space, motion, the pill and tags).
2. `mixed-reality.md`: feasibility of the MR direction (Spatial SDK + MRUK QR tracking), constraints, the fallback, and the headset checks.
3. `horizon-review.md`: the components checked against the Meta skills in `.claude/skills/`.
4. `components/<Name>/README.md`: guidelines per component: PillReader, CodeTag, NoticeStack, ScanToggle, CodeChip, ActionButton, Icon. `components/Passthrough/preview.html` is a mock of the MR scene.
5. `tokens.json`: every token with its value and usage note.

`components/bundle.js` and `bundle.css` are web mocks (React 18) for the previews, not app code. The previews expect the artifact viewer to provide `tokens.css`, so they won't render as plain files. The logos are the existing files in `branding/`.

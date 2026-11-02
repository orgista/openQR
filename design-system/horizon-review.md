# Horizon OS review

The components checked against the repo's Meta skills: `hz-metavrx-ui-set` (theming, components, icons), `hz-android-2d-porting` (panel layout, input) and `hz-immersive-designer` (accessibility, interaction). Read on 2026-10-22.

## Fixed in this version

- **No app-drawn panel border.** Horizon OS draws the panel edge itself. The pill dropped its `outer-border` edge. Don't use `outer-border` around a whole panel in new work.
- **UI Set type scale added** (`display` 32, `headline` 20 bold, `title` 17, `body` 14 medium, then `label`, `bodySmall`, `caption`). On device it renders in Meta's Optimistic face; Inter is the off-device stand-in. `label`, `bodySmall` and `caption` fall below OpenQR's 14sp floor, so keep them out of the headset UI.
- **UI Set shapes added:** button 22dp, dialog 24dp, tooltip, card and menu item 8dp, menu and text field 12dp, control 4dp. Tooltips are 8dp-cornered, not capsules.
- **Press feedback:** every control scales to 0.95 on press, as the UI Set's standard indication does.
- **Status not by colour alone:** the full pill's green/grey dot is still colour-coded. The dot is gone in the MR design: the scan toggle says the state with a moving ring and a changing glyph.

## Holds up

- Every target is at least 48dp; icon-only controls all have names (`contentDescription`).
- Hover precedes every click: each control shows a hover ring and a tooltip, and tooltips also show on focus for controller and keyboard navigation.
- No long press, swipe or multi-touch.
- Panels are opaque and dark, as recommended over passthrough.
- At most one Primary button per surface; the pill's status action is the one.
- Motion carries state only, and stops under reduced motion.
- The scan animation keeps running when the panel loses focus (unfocused panels stay visible; VRC requires live rendering).

## Still to check on a headset

- **MR placement and comfort.** The pill is body-locked at about 1 m and 20° below eye level, with lazy follow (designer guidance: UI between 1.0 and 2.0 m, never head-locked). Tags are world-locked to codes. Confirm on device that the pill doesn't block codes straight ahead.
- **Point-and-click precision.** Chips on far codes must keep 48dp targets and 1° text; check ray accuracy on posters at 1.5 m.
- **Text size at distance.** Body text is 14sp. The designer guidance asks for at least 1° of visual arc, so confirm legibility on device.
- **Status colours.** `positive`, `warning` and `negative` are stand-ins; replace them with the UI Set palette values from the AAR.
- **High contrast and text size.** The guidance recommends adjustable text and a high-contrast option; OpenQR has neither yet.

# Mixed reality feasibility

OpenQR's direction: no scanner window. An always-on pill floats near the person, and every QR code in passthrough gets a tag drawn on it that they point at and click. Checked against Meta's public Spatial SDK and MRUK docs on 2026-10-23.

## Verdict

Feasible, as a **Spatial SDK mixed reality app**, not a 2D panel. A 2D panel can't draw on the real view (`SYSTEM_ALERT_WINDOW` is prohibited), so the tags need an immersive scene with passthrough. Every piece the design needs exists in the SDK. Two platform behaviours shape the product more than the code does: exclusive mode, and permissions on managed headsets.

## How it works

1. **Scene:** a Spatial SDK activity with `scene.enablePassthrough(true)`. The person sees their room, and OpenQR draws only the pill and the tags.
2. **Tracking:** `mrukFeature.configureTrackers(setOf(Tracker.QrCode))` starts MRUK's QR tracker. Each detected code becomes an entity with a `TrackedQrCode` component (an anchor plus the payload, `getPayloadAsString()`, string or binary). `mrukFeature.stopTrackers()` stops it.
3. **Tags:** a small panel entity follows each code's anchor: corner brackets on the code and a destination chip just below it, turned to face the person.
4. **Point and click:** the chip is a hittable panel, so controller ray and hand pinch both hover it and click it. A click runs the same `LinkRouter` rules as today (web and app links only; `intent:` and `javascript:` blocked), then hands off.
5. **The pill and notices:** a small panel entity, body-locked with lazy follow at about 1 m and below eye level, never head-locked. Every code found also appears as a notice stacked above it, so far codes can be picked without pointing at them.

## What the docs confirm

- **Devices:** Quest 3 and 3S. MRUK QR detection needs Meta XR Core and MRUK v83 or later; the Spatial SDK sample asks for Horizon OS 69 or later.
- **Permission:** Spatial Data (`com.oculus.permission.USE_SCENE`). The app gets payloads and poses, never camera frames, which keeps "nothing leaves the headset" easy to say.
- **What reads:** QR up to version 10. No Micro QR, and no codes customised with logos. Works best with codes that are large, close and well lit.
- **Tracking:** pose updates run slower than the frame rate. A code that moves is re-found, not followed smoothly, so tags lag rather than stick to a moving phone screen.
- **Cost:** tracking uses system resources, so stop the tracker when it isn't needed. This matches the 10-minute pause.
- **API status:** the Spatial SDK MRUK sample still marks `configureTrackers()` experimental; the Unity MRUK page no longer does. Expect API churn.

## Constraints that change the product

- **Exclusive mode.** Meta's Spatial SDK design tips say that entering an immersive app makes "all other applications disappear", and that only one exclusive app runs at a time. Horizon OS v74 notes describe keeping up to three windows open during immersive experiences. Which one applies on v209 for a passthrough app is the first headset test. Either way, MR scanning must be a mode the person starts, not something OpenQR switches into by itself.
- **Opening a link leaves the scene.** The browser is a 2D app. Expect the hand-off to end or background the MR session; the Hybrid sample's `extra_launch_in_home_pending_intent` pattern is the way back. Design the return: when they come back, tags reappear on codes still in view.
- **Managed headsets.** ManageXR reports that sideloaded APKs on managed devices lose `USE_SCENE` silently, because `ACCESS_MR_SENSORS` resets on every launch. Meta has said it won't fix this. Shipping as a Meta Horizon Private App is the workaround. ArborXR fleets (OpenQR's education and enterprise users) are likely affected the same way: test before relying on MR there.
- **No tag size from the SDK sample.** Unity's MRUK returns a 2D bounding box for each code; the Spatial SDK reference shows only the anchor and payload. If bounds aren't exposed, draw the brackets at a fixed size centred on the anchor.

## Recommendation

Build it as a hybrid app, so the MR mode is something the person enters on purpose.

- **MR mode (the design in this system):** the always-on pill plus tags on every code, point and click.
- **Fallback without Spatial Data (designed: `NoticeStack`).** Same MR scene, pill and notices, no tags. Codes are read through the passthrough camera the way OpenQR reads them today (Camera2 + ZXing, `HEADSET_CAMERA`, frames in memory only), and the notices above the pill are the only way to pick one. It kicks in when `USE_SCENE` is denied, or when `configureTrackers` doesn't return `SUCCESS`.

## The fallback, checked

- **Why it should work on managed headsets:** ManageXR's report is about `USE_SCENE` / `ACCESS_MR_SENSORS`, and today's 1.0.60 app already scans through the camera on ArborXR fleets. Check that `HEADSET_CAMERA` survives relaunches there too.
- **Passthrough without Spatial Data:** the fallback assumes the scene can still show passthrough with `USE_SCENE` denied. Verify that on a headset before building on it.
- **What changes for the person:** no tags on codes, so with several codes in view they pick by name from the notices instead of pointing at the code. Notices keep first-seen order, linger 8 s after a code leaves view, and never reorder under the pointer.
- **Same rules as MR:** nothing opens by itself, names not payloads, the same link rules.

Order of headset checks: (1) does entering MR hide the person's other windows on v209; (2) what happens to the session when a link opens; (3) tag stability on posters at 0.5–1.5 m; (4) `USE_SCENE` on an ArborXR-managed headset; (5) the fallback: passthrough with `USE_SCENE` denied, and `HEADSET_CAMERA` across relaunches on a managed headset.

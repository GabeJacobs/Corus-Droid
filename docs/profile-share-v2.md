# Profile sharing V2

The shared copy and event contract lives at
[`Corus-Web/app/docs/profile-share-v2.md`](../../Corus-Web/app/docs/profile-share-v2.md).

Translations are available through generated `fm.corus.android.localization.CorusStrings`.
All page copy (including grid labels and 5×5 unlock counts), loading/error states,
effect accessibility states and destination controls comes from the shared
catalog and is bundled in all nine supported locales. Edit the shared locale
JSON once, generate all platforms, then run the shared catalog checks and
`ProfileShareV2LocalizationTest`; do not maintain separate Android translations.
`ProfileShareAnalytics` produces the same `profile_share_event` parameters as iOS
and Web and has shared-fixture tests. The Android own-profile page is now wired
under the existing `profile_sharing_v2` Remote Config key. This implementation
does not change the Remote Config template, version, or release distribution.

## Behavior

- Flag off: original page, OG preview, controls, URLs and analytics.
- Flag on, 0–8 posts: original page plus compact collage progress teaser; X
  sends the localized “Follow me on Corus” caption and link without a mention.
- 9+ posts: 3×3; 16 unlocks 4×4; 25 unlocks the Android 5×5 experiment; 28
  unlocks Full. Full is selected synchronously at 28, otherwise 3×3. Picker
  order: Full, 5×5, 4×4, 3×3. Locked choices explain the exact remaining count
  without selecting/exporting them. All choices remain under the same flag.
- Corus Blue defaults even in dark mode, with white branding and readable username.
  Palette order is Blue, Purple, Rose, Orange, Green, Black, White.
- Film-primary profiles use a centered, fitted poster instead of cropping it.
- Rain and Blizzard Snow are mutually exclusive; no Disco choice. Existing
  profile particle factories are reused on a fixed 360×640 logical canvas.

`ProfileShareV2Renderer` is the single composition for preview, static image,
transparent foreground and video. Full eagerly draws all 28 slots in a 4×7
Story; X reflows them to 7×4 in a 1400×800 JPEG. Smaller X grids are square.
Missing artwork retains its slot. Coil decodes tiles at 384px, with four concurrent
loads, and reuses them across color/layout changes. The profile's existing
30-post page supplies metadata; no database count read or additional profile
fetch is added. Bounded artwork is warmed while the profile is visible; cold
opens show a neutral loading state, never an empty blue card.
Once a preview exists, layout changes retain its content and background until
the replacement is ready, without remounting the weather overlay. Image/video
sharing stays disabled during that render so it cannot export the stale layout.
5×5 uses 25 of the already-prepared 28 slots, so it adds no artwork/database reads.

## Export and handoff

Standard grids and fitted posters send separate solid background and compact
transparent sticker PNGs. Instagram controls the sticker's initial size, so exact
editor scaling cannot be guaranteed while preserving movable layers. Full is
a flattened Story. Effects export a populated-first-frame 10-second H.264 MP4
at 1080×1920, 60fps where supported (30fps on slower encoders). Memory stays
bounded to one frame rather than retaining 600 bitmaps. Cancellation releases
the codec/muxer and deletes the partial export.

Android sends X its JPEG and caption together using package-targeted ACTION_SEND;
there is no iOS-style clipboard workaround. If direct sharing isn't available,
the system chooser retains the image and text. Instagram gets both URI grants;
its fallback uses the flattened card. The profile URL is copied for Instagram's
manual link sticker. OS acceptance is not proof of publishing.

`profile_share_event` logs open/dismiss, controls, preview preparation, export
timing/failure/cancellation and destination handoffs. `profile_share_discovery`
tracks teaser impressions and locked-layout taps separately. Both allowlist
parameters and omit username, UID, content, URLs and raw errors.
The Android experiment reports `layout=5x5` for controls, exports and locked
discovery taps. iOS/Web builders recognize the same enum, and all three use a
canonical 5×5 fixture as well as the existing privacy/count cases. This extends
the data contract without adding the experiment to the other platforms' UI.
Compose tests verify controls, copied-link handoff, one open/dismiss pair per
session and identity-free payloads, in addition to renderer/intent tests.

## Validation

Policy, renderer, intent, Compose sheet and shared analytics fixtures live in
`app/src/test`. `ProfileStoryVideoTest` in `app/src/androidTest` exercises the real
encoder, dimensions, duration, and populated first frame. Actual Instagram/X
editor behavior must still be checked on a device with those apps installed.

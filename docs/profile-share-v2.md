# Profile sharing V2

The shared copy and event contract lives at
[`Corus-Web/app/docs/profile-share-v2.md`](../../Corus-Web/app/docs/profile-share-v2.md).

Translations are available through generated `fm.corus.android.localization.CorusStrings`.
`ProfileShareAnalytics` produces the same `profile_share_event` parameters as iOS
and Web and has shared-fixture tests. The Android own-profile page is now wired
under the existing `profile_sharing_v2` Remote Config key. This implementation
does not change the Remote Config template, version, or release distribution.

## Behavior

- Flag off: original page, OG preview, controls, URLs and analytics.
- Flag on, 0–8 posts: original page plus compact collage progress teaser; X
  sends the localized “Follow me on Corus” caption and link without a mention.
- 9+ posts: 3×3; 16 unlocks 4×4; 28 unlocks Full. Full is selected synchronously
  at 28, otherwise 3×3. Picker order: Full, 4×4, 3×3. Locked choices explain the
  exact remaining count without selecting/exporting them.
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

## Validation

Policy, renderer, intent, Compose sheet and shared analytics fixtures live in
`app/src/test`. `ProfileStoryVideoTest` in `app/src/androidTest` exercises the real
encoder, dimensions, duration, and populated first frame. Actual Instagram/X
editor behavior must still be checked on a device with those apps installed.

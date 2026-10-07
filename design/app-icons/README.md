# Corus Blue for Android

The existing Corus vinyl mark appears in white on `#5E91F0`, matching the iOS
Corus Blue option. Android controls the icon outline. Artwork is centered in a
108dp adaptive canvas, with the complete mark inside the central 66dp safe zone.
There are no baked corners or shadows in the adaptive layers.

| Asset | Format and size |
| --- | --- |
| Adaptive icon and round icon | `mipmap-anydpi-v26/ic_launcher_blue{,_round}.xml` |
| Foreground | Transparent vector drawable, 108 × 108dp, any density |
| Background | Opaque color resource, fills the adaptive canvas |
| Themed monochrome | Transparent vector drawable, 108 × 108dp, any density |
| Legacy square and circular fallback | PNGs in mdpi 48px, hdpi 72px, xhdpi 96px, xxhdpi 144px, xxxhdpi 192px |
| Picker thumbnails | 320px PNGs in `drawable-nodpi` |
| Reusable foreground source | `corus-blue-foreground.svg` |
| Optional store artwork | `corus-blue-play-store-512.png`, 512px RGBA; does not change the Play listing |

Re-export with `python3 scripts/export_app_icons.py` (Pillow, CairoSVG and Cairo).
The mark comes from the existing `drawable/logo_no_background.xml` vector; no
sibling iOS repository is required to regenerate it. The current Default assets
retain their artwork and size. Both options receive a horizontal placement
correction with a final 1dp nudge right of vinyl-center alignment as an optical
compromise between circular and square masks. Default's exports
only translate the original pixels; immutable originals are saved under
`source/default` so re-exporting cannot accumulate the correction. Corus Blue's
vector receives a net 1.5dp leftward translation on its 108dp adaptive canvas.
The mark's paths and scale are unchanged; vertical placement is unchanged.

Settings → Appearance → Theme → App Icon opens the same two choices as iOS.
Club/full-access users can change icons; other users see disabled choices and
the existing Settings Club offer. Selection is read from Android's enabled
launcher components, so it survives process restarts and updates. MainActivity
stays enabled for deep links, notifications and previously pinned shortcuts.

Both aliases enter a no-display LauncherActivity that immediately opens the
stable MainActivity. Disabling the old alias therefore cannot close the open app
or strand a closing window over the home screen.

Android 13+ applies the two alias state changes atomically. Android 8–12 enables
the destination before disabling the previous entry and restores the previous
overrides on failure. Launchers may take a moment to refresh. If themed icons
are enabled, the launcher chooses colors for the monochrome mark; Corus Blue
then has the same silhouette as Default.

References: [Android adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive),
[activity aliases](https://developer.android.com/guide/topics/manifest/activity-alias-element),
[component enablement](https://developer.android.com/reference/android/content/pm/PackageManager#setComponentEnabledSettings(java.util.List)).

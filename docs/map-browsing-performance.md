# Android map browsing performance

October 27, 2026. These changes reduce work during ordinary map browsing.

- Compose remembers the filtered/sorted city list and full map payload until
  their actual inputs change. Payload construction no longer serializes JSON,
  parses it back into an object, then serializes it again.
- A delivery gate belongs to the retained WebView bridge. Identical updates
  skip evaluateJavascript; page-finished/retry and retained-surface attachment
  explicitly replay the latest payload, including updates supplied before the
  inline bridge was ready. The page also skips duplicate annotation work while
  still acknowledging readiness to native loading/snapshot UI.
- The full map mounts cities within a padded viewport plus the focused city.
  The complete authorized directory stays in memory. No visible-city cap,
  thinning, backend point changes or count changes are applied.
- During MapKit region movement, a lightweight animation-frame loop checks the
  buffered region at most every 100 ms. Hysteresis replenishes it only near its
  edges; region-change-end contracts it after zoom-in. Only the settled camera
  is sent back to Compose. Detaching the surface cancels the loop.
- Focus, labels and artwork update independently of avatar DOM. Actual changes
  to avatar identity/URL, count or density still rebuild that marker's faces.
  Annotation identity remains stable inside the retained region.

Validation:

- New JavaScript regressions failed against the original map for offscreen
  retention, duplicate update work and avatar replacement on focus changes.
- All 16 production-page JavaScript checks pass, including startup/recovery,
  long pans, wraparound, dense visible cities, zoom-in, suspend/resume, renames,
  avatar changes and the existing one-cover listening treatment.
- Selected Kotlin run: 41/44 pass, including all three new payload gate tests.
  Three existing MapParityTest source-string assertions still fail. Their
  expected strings (`interactive?7:125`, the select event registration, and the
  older focusRevision-only key) are also absent from the original HEAD source.
  They are not failures introduced by these changes.
- Debug APK assembly succeeds. Physical Android pan/pinch responsiveness and
  the actual browser/MapKit visual behavior still need device testing.

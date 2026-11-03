# Android 1.6.5 release preparation

Requested scope: publish to Google Play internal testing and prepare for a later production review. Do not promote to production or send changes for review.

## Artifact

- Version name: 1.6.5
- Version code: 138 (selected by the internal lane from all Play tracks)
- Package: fm.corus.android
- Build: signed release AAB from `fastlane android internal`
- Initial upload attempted the no-review flag, which Google rejected for this app state. The identical signed artifact was then uploaded through Fastlane Supply to internal only with `changesInReviewBehavior=ERROR_IF_IN_REVIEW`, without store-listing metadata changes. This guard refuses to cancel or resubmit an existing review.

Verified on November 3, 2026:

- Internal: 1.6.5 (138), completed; independently confirmed with `google_play_track_version_codes` and a fresh Play API read.
- Production remains 1.6.4 (132), completed. No production promotion was performed.
- Both release-note languages were read back from Google Play and match the local note files exactly.
- SHA-256: `f21c3a6f5d3007b693cd6e22065aa8d6972fe84a281e6ef2c328bb0c1b91266a`
- Archived AAB: `/Users/gabejacobs/Apps/Corus/outputs/android/1.6.5/Corus-1.6.5-138.aab`
- Readback: `fastlane/release-notes/1.6.5/play-verification.json`.

Google documents that updates to existing internal test tracks normally do not require review: https://support.google.com/googleplay/android-developer/answer/9859654?hl=en-EN . The API guard is documented at https://developers.google.com/android-publisher/api-ref/rest/v3/edits/commit .

## Release notes

English and Brazilian Portuguese source files are in `fastlane/release-notes/1.6.5/`.

Notes cover hashtag Play/Pause and export-menu changes, the daily Trending hashtag banner, and profile shuffle. The live Trending and banner flags default to true without conditional targeting. Profile shuffle is exposed in the profile overflow menu for normal profiles; both `getProfileShuffleIndex` and `getProfileShufflePosts` are deployed and active. In-app hashtag playback uses the existing `getHashtagPosts` callable.

## Validation completed before upload

- Android compilation, signed release build/signing validation (Fastlane log is the final build authority).
- Daily hashtag eligibility tests and playback-session tests, including empty sources, film-only pages, chronological continuation, retry cursors, and retained pagination.
- Existing profile-shuffle session tests after sharing the normal playback-routing entry point.
- Shared translation catalog generation check for all nine languages.
- The debug build was installed on the connected Samsung for user review.

## Internal testing before production approval

- Open a hashtag, confirm Follow is secondary and Play is the primary action, and verify Play/Pause and mini-player Next using the chosen music service.
- Continue past the loaded page and navigate away while listening to verify retained pagination.
- Open the top-right overflow menu and verify Export playlist still reaches its service chooser, confirmation, and existing Club gating as applicable.
- When a tag qualifies for the current UTC day, verify the Trending banner opens the tag and dismissal hides that day/tag. Absence of the banner on quiet days is expected.
- Open a normal profile's overflow menu and verify Shuffle starts that profile's songs.

Production promotion and review submission require a later explicit request after internal testing.

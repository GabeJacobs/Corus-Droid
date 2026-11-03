# Android referral release preparation — November 3, 2026

## Candidate scope

Production candidate **1.6.5 (138)**, built from the isolated referral candidate rooted at Android commit `9370c006bdfd3bc3621910b433ef58696a0da700`, incorporating the latest referral UI fixes. Unrelated dirty working-tree work, including unfinished Qobuz integration, is excluded. The main checkout's version.properties is intentionally unchanged; version values are supplied as Gradle build properties. Code 138 is the next candidate after the live Play read below, not a reservation: re-query before uploading.

- Claim sheet remains at 86% height for ready, claiming, applied, saved, and history states.
- Claim disables duplicate taps and replaces the label with a centered white spinner in the blue button without resizing it.
- The pre-claim page remains stable until the server result is ready, then crossfades into active/saved confirmation.
- Active confirmation shows the actual server expiry and a white checkmark. Saved confirmation explains waiting behind paid access. Pending/failure do not show active access.
- Done dismisses the normal sheet. There is no automatic success-dismiss timer. Exiting the standalone debug preview is not production behavior.
- Corus Club precedes Friends. Invite Friends uses standard Settings icon/text insets and sizes.
- Debug preview Activity and launcher alias are excluded from the release source set and manifest. Preview simulation is only called by that debug Activity; production callers supply no preview status.

## Independently checked live state

Read Google Play tracks through one temporary API edit, then deleted the edit without committing anything: production **1.6.4 (132)** completed; internal **1.6.4-inviteqa.2 (137)** completed; alpha 15 halted / 14 completed; beta has no version code. No release was uploaded or promoted.

Remote Config version **376**: referrals_enabled default false, existing named-user and exact QA-version audiences retained. Two new mobile release conditions enable iOS >=1.8.3 and Android >=1.6.5. Backend system/referrals enabled true. The real fifth-friend QA inviter reward grant is applied. Four earlier progress counts were explicit QA offsets, not four real people; that test does not establish five independent fresh signups. No account, grant, notification or campaign changes were made. Only the two described mobile Remote Config conditions were published, preserving every unrelated template field.

The production branded-link → Play install → signup → 14-day gift path was verified on internal Play 137 earlier today, with backend and RevenueCat readback. The latest claim UX was reviewed by the user through a debug preview; its final minified Play-delivered artifact has not been exercised on-device.

## Launch boundary

The signed AAB is a local candidate. It is not uploaded, Play-verified, or publicly released. The release skill requires an internal upload and independent version-code readback before promoting the exact verified artifact; public promotion requires approval of that specific release.

`referral-mobile-rollout-state.json` records the published version-376 conditions. Compatible future mobile versions will activate after a fresh client fetch. Web remains OFF. No release has been uploaded or promoted.

Do not deploy the local backend's newer global Remote Config gate incidentally: default-OFF / audience-specific activation differs from that global gate and could disable working referral qualification. No backend deployment is needed for these Android presentation changes.

Public referral release notes should only be used after the Android release audience is actually enabled. While the feature remains private, keep store notes limited to a maintenance update; do not advertise unavailable invitations. Preparing this file does not change store notes.

## Build verification

The initial signed 1.6.5 (138) bundle built before the follow-up tracking audit and is superseded by source fixes. Final release packaging is deferred until web and the remaining feature work are finished; do not upload that earlier artifact. The main checkout version remains unchanged. The isolated candidate contains the latest source fixes for focused compilation/tests.

Mobile audit additions: Android pre-login UDL event persistence, debug/release analytics labels, missing gift/error/validation funnel events, and nonblocking claim-attempt analytics; install-specific referral preferences excluded from backup. iOS permanently rejected analytics no longer poison its queue. Both avoid duplicating Firebase Analytics when replaying pre-login attribution. The targeted backend tracking update is live; see the sibling Web mobile-launch audit for exact source/deployment and pending device proof.

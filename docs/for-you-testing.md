# Your Mix Android local testing

Build and run the debug app locally, then sign in as @gabe. No version bump,
Play upload or production promotion is part of this change. The local APK is
`../output/android-your-mix-fallback-2026-10-05/corus-your-mix-debug.apk` (relative to Corus-Droid).
The final build and reports use their own output directory to avoid concurrent
Gradle cache/result writes.

Server Remote Config template 99 enables `for_you_prototype_enabled` for the
verified iOS and Android identities of @gabe, @aiden, @arielle, @clifton and
@risingyeehaw, plus @damesblog and @gulches. The default is OFF, and unlisted users remain OFF. Availability
is checked through `getForYouPrototypeAccess`; no debug flag bypass is needed.

Japanese uses the compact label For You on both platforms; other languages
keep their localized Your Mix title.

For enabled accounts, the tabs are Following, Your Mix, Trending, and Favorites
when eligible. Entering Your Mix shows Tune Your Feed once per account. The tune
icon beside the selected tab opens it again. Eclectic calls
`getForYouEclecticFeed`; Balanced and Stay Close call `getForYouPrototypeFeed`.
Balanced is the current remote default. Selecting an option is a draft until
Apply; Close or dismiss discards it. Apply saves the choice per account and
starts a fresh feed session even when the choice is unchanged.

Check all three modes, pull to refresh, pagination, music/film/new-release and
energy filters, return trips between tabs, and relaunch persistence. Prototype
sessions/cache signatures are separate from the released feed, and prototype
requests do not send or update released seen IDs. Playlist export is hidden in
Your Mix, matching iOS; other feeds keep it.

Swipe slowly into and out of Your Mix from both sides: the controls fade with
pager progress, and their 28dp slot collapses while the icon keeps its size.
Controls are interactive and exposed to accessibility only on the selected
Your Mix tab. Tapping the selected Your Mix label also opens Tune Your Feed;
tapping it from another tab switches to Your Mix. Other selected tabs retain
their scroll-to-top behavior. With Android's Remove animations enabled,
controls use selected-tab visibility. A locked Stay Close option shows Locked
without a Default badge.

Startup settles both Your Mix access and the account's Remote Config feed-tab
layout behind the Corus launch logo, then fades the cover over the feed in 320ms.
Each check has a one-second presentation deadline. Slow or failed requests use
that account's prior confirmed layout. On upgrade, ordinary feed tabs also retain
the legacy cache, so Matches remains visible when enabled; pilot and tester
access require an account-scoped cache. Otherwise the in-app defaults apply. Late
replies and subsequent config refreshes warm the next launch rather than moving
visible tabs.
If the first fresh local launch does not show Your Mix, fully close and relaunch
after the background access request finishes (up to ten seconds). Repeated
same-account auth callbacks do not reconsider the visible layout.

Automated validation: 202 selected Android tests and debug assembly passed,
including the production store, actual tuning-sheet interactions, endpoint
payloads, feed filters and existing response-race tests. 42 focused backend
tests passed. Only the three prototype functions were updated from their own
deployed sources; ranking and runtime configuration were preserved. The full
344-function read-back confirmed all unrelated functions were unchanged.
These are the October 3 implementation checks. The October 4 parity review and
additional controls regression evidence are in
`../output/android-for-you-parity-2026-10-04/parity-review.md`.
The initial 116 selected parity checks passed, including nine rendered swipe positions,
and the updated debug APK assembled successfully. Installing that APK on the
saved emulator failed with insufficient storage; app data was preserved.
Signed-in physical-device feed inspection remains Gabe's local check.

Evidence is in `../output/android-for-you-2026-10-03/` relative to the Corus
workspace root's Corus-Droid folder.

The selected-label tap change also passed seven focused controls checks using
actual taps on the label text, and the debug APK was rebuilt. iOS was rebuilt
and verified in its saved simulator: a selected-label tap opens controls, the
first tap from another feed switches tabs, and Close preserves the saved mode.

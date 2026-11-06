# Invite system

The Android invite system mirrors the current iOS referral service and views: Friends settings, short codes and history, redemption receipts, feed/Search banners, animated EP-to-LP progress, five-friend reward claim, gift confirmation and onboarding failure states. The campaign gives the inviter three months and each qualifying recipient fourteen days; the backend owns all grant and eligibility logic.

AppsFlyer 7.0.4 is integrated with UDL, explicit session readiness, launcher activity collection, account-bound registration retries, verified HTTPS routes, legacy Install Referrer and merged backup exclusions. Advertising identifiers, Android ID, AppSet ID, network details and SDK debug logs are disabled.

Supply the SDK developer key through ignored `local.properties` as `appsflyer.dev.key`, or through `CORUS_APPSFLYER_DEV_KEY` in CI. A release without the key fails its pre-build check. Do not commit the key or print generated BuildConfig contents.

Run referral unit tests with `./gradlew :app:testDebugUnitTest --tests 'fm.corus.android.service.Referral*'`. Build checks include `:app:assembleDebug` and `:app:assembleRelease`.

`ReferralPreviewActivity` and its **Preview Club claim** entry exist only in debug builds. Open the ready scenario with `adb shell am start -n fm.corus.android/.ClaimRewardPreview`. It can run while signed in and never resets referral state, records claim analytics, submits a grant, or updates seen-progress preferences. It simulates a 1.2-second claim and a sample 90-day expiry. Other scenarios still require a signed-out emulator. Done exits the standalone preview; in the normal Settings flow it only dismisses the sheet.

Android claim feedback keeps the same sheet height, displays a centered white spinner in the disabled blue button, and crossfades to a confirmation with the server expiry and white checkmark. Applied and saved confirmations stay open until Done. Settings puts Corus Club before Friends and uses the same icon/text geometry for Invite Friends as standard navigation rows.

The complete cross-platform implementation and QA notes are in the sibling `Corus-Web/docs/referral-parity/README.md`. Production Android OneLink routing is enabled. A user-run production branded-link → Play install → signup → gift test passed on build 137, with backend and RevenueCat verification. The public referral flag remains OFF with selected test audiences; preparing a release does not publish a store release or enable a public audience. See `docs/referral-production-prep-2026-11-03.md` for the next candidate and launch boundary.

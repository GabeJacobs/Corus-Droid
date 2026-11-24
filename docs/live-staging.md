# Android Live staging

Build with `./gradlew :app:assembleStaging`. Use Android Studio's JBR as
JAVA_HOME when a shell JDK is unavailable. The APK is
`app/build/outputs/apk/staging/app-staging.apk`.

The separate app is named Corus Staging, package `fm.corus.android.staging`, and
uses only Firebase project `cymbal-remake-staging`. The normal debug/release
configurations retain their production identities. Staging has a distinct
`corus-staging` URL scheme and removes production universal-link filters.

Staging uses Spotify app `Corus Staging`, public client ID
`237e99bc39e14255aae3eb460c748fa9`, and callback
`corus-staging://spotify-auth`. No Spotify client secret belongs in the APK.
User Management was not changed. Web Live remains disabled.

The private local staging test keystore is
`/Users/gabejacobs/.corus-keys/corus-android-staging.keystore`, outside the repo.
Its SHA-1 is `51:F6:42:57:9F:FE:8A:AD:55:55:9C:E9:6E:1F:80:98:17:F1:67:C2`.
That certificate is registered only with the separate staging configurations.

Install the APK alongside the normal Corus app. Register the physical device's
App Check debug token only in the staging Firebase app; never paste it into
source or public output. The staging login form creates a synthetic anonymous
test account through the attested `stagingEnroll` callable. Test Club access
comes from the staging profile. RevenueCat and AppsFlyer are disabled, and
production debug tokens and emulator overrides are not inherited.

The signed APK's package, label, callback and signing certificate were inspected
on November 23, 2026. This establishes packaging, not real-device Spotify audio
or Live synchronization. The staging APK was installed on the connected Android
device alongside Corus. No production APK was uploaded or replaced. The focused
staging and normal-debug configuration tests both passed.

Focused configuration checks:

```sh
./gradlew :app:testStagingUnitTest --tests fm.corus.android.StagingEnvironmentTest :app:testDebugUnitTest --tests fm.corus.android.StagingEnvironmentTest
```

See `Corus-Web/docs/live-staging.md` for the staging backend boundary and cloud
smoke test. The first device baseline should cover foreground song handoff,
locked-phone handoff, and leaving/reopening the app before changing sync logic.

# Apple Music on Android

Download Android MusicKit SDK 1.1.4 from [Apple Developer](https://developer.apple.com/download/all/?q=Android+MusicKit) and extract `MediaPlayback-release-1.1.4.aar` and `MusicKitAuth-release-1.1.5.aar` into this directory. Builds with both libraries include full Apple Music playback on supported ARM devices. Builds without the SDK keep Apple Music previews and links. Release builders that expect streaming can pass `-PrequireAppleMusicSdk=true` to fail the build when the libraries are missing.

To verify, install a build with both libraries on a physical Android device. Select Apple Music in Music settings, connect with a subscribed account, and play an Apple Music track from the feed. Check seek, pause, Next, auto-advance, and lock-screen controls. Try a post matched from Spotify, a track unavailable in the account's storefront, and switching between Full and 30s.

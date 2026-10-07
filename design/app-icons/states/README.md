# Android App Icon visual review

Captured on an Android 16 (API 36) Pixel 9 emulator at 1080 × 2424.

These screenshots render `AppIconSettingsContent` from the production source. Its body was checked byte-for-byte against the app source. The preview also uses the production Corus theme, Nunito font, spacing, strings, header button, and final approved icon assets. The error dialog matches the production dialog.

Membership, selected icon, switching, failure, and text size are explicit preview inputs. No account, subscription, or launcher state was changed to stage these screenshots. The 200% text preview overrides the Compose font scale. This review demonstrates the shown layout on one representative emulator, not every Android device or launcher.

| Screenshot | State |
| --- | --- |
| free-light.png | Free account, light theme, Default selected, choices locked |
| free-dark.png | Free account, dark theme, Default selected, choices locked |
| club-default.png | Club access, light theme, Default selected |
| club-blue.png | Club access, dark theme, Corus Blue selected |
| switching.png | Club access, dark theme, switch in progress, controls disabled |
| error.png | Club access, dark theme, failure dialog, Default still selected |
| large-text.png | Free account, light theme, 200% text |
| expired.png | Access expired, dark theme, Blue still selected, choices locked |

Comparison sheets are `../android-picker-states.png` and `../android-picker-accessibility.png`. Launcher shape and themed-palette examples are in `../android-icon-formats.png`; themed colors are illustrative and chosen by the launcher.

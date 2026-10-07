# Shared translations

The editable translation source is in the sibling **Corus-Web/translations** directory. Its README describes generation, review and placeholder mapping.

Most simple string entries in this app are generated from that source. Edit the shared locale JSON once, then run `python3 Corus-Web/translations/scripts/generate.py` from the parent workspace and commit the generated resources in this repository. Preserve resource keys. Do not translate managed entries independently here.

Existing disagreements remain listed in `Corus-Web/translations/review/CONFLICTS.md`. The generator retains each app's candidate until a decision is approved. Native plural/rich formats and explicitly listed malformed legacy placeholders remain unmanaged.

Bundled resources remain committed, so the app can build and release without fetching another repository or a translation service. With sibling repositories available, `python3 Corus-Web/translations/scripts/generate.py --check` checks consistency.

Product code uses the generated `fm.corus.android.localization.CorusStrings` semantic adapters described in the shared README. Existing native keys remain compatibility resources; new labels should use the shared message identity. Do not edit generated adapter files.

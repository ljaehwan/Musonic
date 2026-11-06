# Contributing to Musonic

Thanks for taking the time to help. Bug reports, translations and fixes are all welcome.

For a bug, include your app and Android versions, server software, what you expected, and the shortest steps that reproduce it. Use a test account when possible. Never attach passwords, tokens, keystores or logs containing authenticated server URLs.

For a new feature or a broad refactor, open an issue before starting. This helps keep changes focused and avoids overlapping work.

## Working on the app

1. Fork this repository and create a branch from `dev`.
2. Make a focused change and update relevant documentation or translations.
3. Run the checks in [BUILDING.md](docs/BUILDING.md). Add a regression test when fixing behavior that can be tested reliably.
4. Open a pull request against `dev`, with the problem, the change and how you checked it. For UI changes, attach screenshots using demo content.

The public CI builds and tests code without release credentials. Official APK delivery is maintained separately. CI artifacts are development builds, not signed release updates.

## Translations

Language resources live in `app/src/main/res/values*/strings.xml`; some shared labels are in `settings/UiText.kt`. Keep format arguments intact and check longer strings on a narrow display. New languages also need an entry in `AppPreferences.languages`.

## Code and licensing

Use the existing Kotlin/Compose style and keep changes limited to the issue being addressed. Contributions are accepted under this repository's MIT license. Keep the attribution and license of any third-party material you add.

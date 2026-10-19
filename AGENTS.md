# Ranged Timer: guide for coding agents

Android app in Kotlin and Jetpack Compose. Before any change, read this file, then `README.md` sections "How the code is laid out" and "Decisions that should not be undone by accident", then `docs/superpowers/specs/2026-10-19-ranged-timer-design.md` including its addenda. Those decisions protect the secret end time and alarm reliability; a change that touches them needs the owner's explicit agreement.

## Making a change

1. **Locate.** Find on-screen text with `grep -rn "the exact text" app/src/main`. Find behaviour through the README code map. Logic lives in `core/` (plain Kotlin, no Android), Android glue in `alarm/` and `data/`, screens in `ui/`, colours in `ui/Theme.kt`.
2. **Test first.** For logic, write a failing test in `app/src/test/java/com/loudwhisper/rangedtimer/core/`, then change `core/`. For screens and flows, add or adjust a case in `AppFlowTest.kt`.
3. **Run** `./gradlew testDebugUnitTest` with JDK 21. Done when every test passes; report the count from `app/build/test-results/testDebugUnitTest/`.
4. **Look** at visual changes: `./gradlew testDebugUnitTest --tests '*ScreenshotTest*' -Pscreenshots.dir=/tmp/shots` writes dark-mode PNGs of each screen.
5. **Document** in the same commit: update the README feature list or decisions when behaviour changes, and append a dated addendum to the design spec for any new or changed behaviour.
6. **Release** only when the owner wants an APK (next section).

The change is complete when tests are green, the README and spec match the new behaviour, and the commit is pushed.

## Releasing an APK

1. In `app/build.gradle.kts`, raise `versionCode` by 1 and set `versionName`. Phones refuse an update whose `versionCode` is not higher.
2. Run `./gradlew testDebugUnitTest assembleRelease`. Release signing reads `rangedTimer.signingProps` from `~/.gradle/gradle.properties`; it exists only on the owner's machine, and the key never enters this repository.
3. Verify the APK:
   - `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` shows certificate SHA-256 `6c512c1635945073e5674f4bd7a8eeee1975c4c47452ebd23f1b52e1369f516f`. Any other key cannot update installed copies, and users would have to uninstall and lose their history.
   - `aapt2 dump badging` on the APK shows the new `versionCode`.
4. Commit and push, copy the APK to `RangedTimer-X.Y.apk`, then `gh release create vX.Y RangedTimer-X.Y.apk --title "Ranged Timer X.Y"` with short notes of what changed, "Installs over earlier versions and keeps your history", and the certificate digest.

## Keeping old data loadable

Users install every release over the previous one, so each release must read every older history file and export.

- The file is `ranged-timer.json` (`core/Codec.kt`, `data/Store.kt`), `schemaVersion` 1, decoded with `ignoreUnknownKeys`.
- Add a field with a default value and older files keep loading; `schemaVersion` stays 1. Example: `Session.label` defaults to "Meditation".
- Renaming or removing a field, or changing what one means, requires bumping `SCHEMA_VERSION`, a migration in `Codec.decode`, and a test that decodes the old shape.
- `Session.label` stores the label text as it was when the session ran. Only the History relabel editor changes a recorded session.

## Test-suite gotchas (Robolectric)

- `AppFlowTest` runs on an S23 Ultra sized screen. Call `performScrollTo()` before tapping or typing into anything that may sit below the fold; a touch on an off-screen node is silently dropped.
- After calling `SessionController` directly (`start`, `onAlarm`), call `compose.activityRule.scenario.recreate()`. The UI re-reads storage every 2 seconds through a delay the test clock does not advance.
- A text field inside an `AlertDialog` never goes idle under Robolectric (`AppNotIdleException`). Build editors inline in the screen, as `RelabelEditor` in `ui/HistoryScreen.kt` does.
- Gradle and the Android plugin need JDK 21; Java 25 fails. Robolectric's `--add-exports` flags are in `app/build.gradle.kts`.
- The Android emulator crashed on the owner's machine. Alarm timing, vibration on Silent and Samsung battery limits are confirmed only with the real-phone checklist in the README; say so when a change touches `alarm/`.

## Owner's writing rules

- Plain words in UI text, docs and commit messages. Punctuate with commas, colons, full stops or ASCII hyphens; this project uses no emojis and no em or en dashes anywhere.
- Public prose (README, release notes) gets an unslop pass: keep facts and uncertainty, add no claims the owner did not make.
- Commit messages: a short imperative summary line, then bullets for the parts of the change.
- Keep machine paths, keys and passwords out of the repository. The owner's machine-specific build and release notes live in their private records.

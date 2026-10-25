# Ranged Timer

There are thousands of timers and stopwatches. This one does something specific I needed: a random timer for something new you are practicing.

You set a range instead of a fixed time, say 60 to 90 minutes. Press Start and the app secretly picks a moment inside that range. It goes off then, and not before. You don't see the picked time until the session is over, so there is no countdown to watch and no final stretch to pace yourself toward.

I built it for meditation. Phone on Silent, screen off, a vibration when the time is up.

The idea works anywhere that knowing the end changes how you behave near it. During focus work or reading, you can't start winding down early if you don't know when early is. On a run or a hard cardio block, you hold your pace until the buzz, because the finish could be a minute away or twenty. For something new you are practicing, like an instrument or a language, the app varies each session's length for you, and the history keeps count of the time you put in.

The timer gives one alert per session. It does not run a series of intervals.

## What it does

- Picks a random end time inside your range, anywhere from 0 to 1000 minutes. Every second in the range is equally likely.
- Keeps the picked time hidden while the session runs, including from the lock screen and status bar.
- Checks the phone's sound mode when the time comes: on Silent or Vibrate it vibrates, with sound on it plays the ringtone you chose and vibrates. It stops when you tap Stop or after 3 minutes.
- Gives a short buzz when you press Start, so you know the session is running without looking.
- Lets you end early by holding a button for 1.5 seconds (a quick tap does nothing). It records what you sat and shows what it had picked: "You sat 52 minutes. The timer had picked 74."
- Keeps a history on the phone: average minutes per day over the last 7, 21, 30, 60 and 90 days (rolling, counted back from today), days practiced, average session length, a daily graph with a 7-day trend line, and 90-day cycles you can compare with each other.
- Tags each session with a label: Meditation, Reading, Workout, or a fourth one you type on the timer screen (Cardio one day, HIIT the next). Your last five names come back as one-tap picks. A session saved under the wrong label can be changed later from History.
- Shows History for everything or one label at a time.
- Exports a spreadsheet report (CSV) with one row per session: date, start time, label, minutes done, minutes picked and how it ended.
- Exports and imports the full history as a backup file, and can clear it after a warning.
- Has an OLED black dark theme and a light theme, or follows the phone.

It has no internet permission, no accounts, no ads and no tracking. The app does not send your history to a server. Android's backup settings can include app data; you can also export a backup yourself. The app is about 2 MB.

## Install

Ranged Timer is not on the Play Store. Download the APK from the [Releases](../../releases) page and open it on your phone.

1. Allow "install unknown apps" for the app you opened the file with (Files, Drive, your browser).
2. On Samsung phones, Auto Blocker (Settings, Security and privacy) can block the install. Turn it off for the install and back on afterwards.
3. Play Protect may warn that the developer is unknown. Choose to install anyway.
4. Open the app, go to Settings and make all three setup rows say Done: Notifications, Alarms and reminders, Battery: Unrestricted.
5. Still in Settings, use **Test in 10 seconds** with the phone on Silent and the screen locked. If it vibrates, you are set. Run it again after a phone update.

Requires Android 13 or later. Phone makers handle background apps differently. Confirm alarm timing, vibration and lock-screen controls on your phone before relying on a long session.

## Building from source

You need JDK 21 and the Android SDK with platform 37 installed.

```bash
./gradlew testDebugUnitTest   # unit tests plus app flows under Robolectric
./gradlew assembleDebug       # debug APK in app/build/outputs/apk/debug/
```

Release builds are signed only when you point Gradle at your own keystore. Put a properties file somewhere outside the repository:

```properties
storeFile=/path/to/your-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Then add `rangedTimer.signingProps=/path/to/that.properties` to `~/.gradle/gradle.properties` and run `./gradlew assembleRelease`. Never commit the keystore or its passwords. An APK signed with your key cannot update one signed with mine, so uninstall first if you switch, after exporting your history.

Optional screenshots of each screen in dark mode:

```bash
./gradlew testDebugUnitTest --tests '*ScreenshotTest*' -Pscreenshots.dir=/tmp/shots
```

## How the code is laid out

The design document is [docs/DESIGN.md](docs/DESIGN.md). Read it before changing behaviour.

| What | Where (under `app/src/main/java/com/loudwhisper/rangedtimer/`) |
|---|---|
| Data shapes: sessions, settings, the running session | `core/Model.kt` |
| Random pick and range rules | `core/DurationPicker.kt` |
| Averages, trend line, 90-day cycles | `core/Stats.kt` |
| End early, restart and missed-alarm rules | `core/SessionLogic.kt` |
| Labels and the History filter | `core/Labels.kt` |
| Spreadsheet report (CSV) | `core/Report.kt` |
| History file format (JSON) | `core/Codec.kt` |
| Reading, writing, import, export, clear | `data/Store.kt` |
| Start, stop and alarm handling | `alarm/SessionController.kt` |
| The single exact wake-up | `alarm/Scheduler.kt` |
| The 3-minute vibrate or ringtone alert | `alarm/AlertService.kt` |
| Start vibration | `alarm/Haptics.kt` |
| Restart and app-update handling | `alarm/Receivers.kt` |
| Screens | `ui/HomeScreen.kt`, `ui/HistoryScreen.kt`, `ui/SettingsScreen.kt`, `ui/Graph.kt` |
| Colours | `ui/Theme.kt` |

Everything in `core/` is plain Kotlin with unit tests. Change behaviour there first, with a test.

Decisions that should not be undone by accident:

- `AlarmManager.setAlarmClock()` is never used. It shows the next alarm time on the lock screen and status bar, which would reveal the end. The app uses `setExactAndAllowWhileIdle` on the elapsed-time clock, so changing the phone's clock or time zone cannot move the alarm.
- The sound mode is read when the alert fires, not when the session starts.
- The alert and the start buzz use the alarm channel (`USAGE_ALARM`), which Android normally lets through Silent mode and Do Not Disturb.
- The alert runs in a foreground service of type `specialUse`. `shortService` has a time limit close to the 3-minute alert.
- A session is saved the moment the alarm fires, so it counts even if Stop is never tapped.
- Android 9 and later do not tell a stopped app when the phone shuts down. If the end passes while the phone is off, the session is recorded at its picked length and marked interrupted.
- After a restart, recovery waits for the first unlock, because app storage is locked until then.
- Each session stores its label name as it was when it ran, so typing a new fourth label never rewrites past sessions; only an explicit change from History does. Sessions from before labels existed read as Meditation. The History filter "Other" groups every name that is not one of the three built-in labels.
- The CSV report prefixes any label starting with `=`, `+`, `-` or `@` with an apostrophe, so a spreadsheet cannot run it as a formula.
- Import replaces only the session history. It keeps the running session and the phone's settings, and saves a copy of the old file first. Clear history deletes those copies too.

App flows are exercised with Robolectric. Alarm timing, vibration on Silent and battery limits also require checks on a real phone.

## License and support

Ranged Timer is licensed under the Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

If you find a problem, please [open an issue](../../issues) with your phone model, Android version and what happened.

If any of you wish to buy me a subscription, help me toward a new desktop PC with 32 GB or more VRAM to run LLMs locally, or help me toward a MacBook with a large amount of unified memory for running LLMs locally, you can support me through [GitHub Sponsors](https://github.com/sponsors/loud-whisper) or [Ko-fi](https://ko-fi.com/vm1700) :)

## Release identity

Launch edition: 1.0, published 2026-10-25. Application ID: com.loudwhisper.rangedtimer.mobile.

Release certificate SHA-256: 93:05:C0:AC:2F:F7:B5:9E:EB:32:5E:87:4C:53:D1:E1:D3:8E:E0:59:E2:C4:4D:45:F5:63:4F:32:38:1D:E2:D8. Verify this fingerprint before installing an APK.

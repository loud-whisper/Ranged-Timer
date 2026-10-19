# Ranged Timer

A one-purpose Android meditation timer. You set a range, for example 60 to 90 minutes. Each time you press Start, the app secretly picks a random moment inside that range and alerts you then. You never see the picked time until the session is over.

- Phone on Silent or Vibrate when the alert fires: it vibrates.
- Phone with sound on: it plays your chosen ringtone and vibrates.
- The alert runs until you tap Stop, or for 3 minutes.
- End early by holding the button for 1.5 seconds. The app records what you sat and reveals what it had picked.
- History stays on the phone: averages over 1 week, 3 weeks, 1 month, 2 months and 3 months, a daily graph with a 7-day trend line, and 90-day cycles to compare.
- No internet permission, no accounts, no ads. About 2 MB.

Owner: loud-whisper. Built for a Samsung Galaxy S23 Ultra on Android 16; runs on Android 13 and later.

Full design: [docs/superpowers/specs/2026-10-19-ranged-timer-design.md](docs/superpowers/specs/2026-10-19-ranged-timer-design.md). Build plan: [docs/superpowers/plans/2026-10-19-ranged-timer.md](docs/superpowers/plans/2026-10-19-ranged-timer.md).

## For a future AI changing this app

Read the design document first. Then:

| What | Where |
|---|---|
| Data shapes (sessions, settings, running session) | `app/src/main/java/com/loudwhisper/rangedtimer/core/Model.kt` |
| Random pick and range rules | `core/DurationPicker.kt` |
| Averages, trend line, 90-day cycles | `core/Stats.kt` |
| What happens on end early, restart, missed alarm | `core/SessionLogic.kt` |
| History file format (JSON) | `core/Codec.kt` |
| Reading and writing the file, import and export | `data/Store.kt` |
| Start, stop, alarm handling (glue) | `alarm/SessionController.kt` |
| The single exact wake-up | `alarm/Scheduler.kt` |
| The 3-minute vibrate or ringtone alert | `alarm/AlertService.kt` |
| Restart and app-update handling | `alarm/Receivers.kt` |
| Screens | `ui/HomeScreen.kt`, `ui/HistoryScreen.kt`, `ui/SettingsScreen.kt`, `ui/Graph.kt` |
| Colours, light and dark | `ui/Theme.kt` |
| Tests | `app/src/test/java/com/loudwhisper/rangedtimer/` |

Everything in `core/` is plain Kotlin with unit tests. Change behaviour there first, with a test.

Decisions that must not be undone by accident:

- **Never use `AlarmManager.setAlarmClock()`.** It shows the next alarm time on the lock screen and status bar, which would reveal the secret end time. The app uses `setExactAndAllowWhileIdle` with the elapsed-time clock, so changing the phone's clock or time zone cannot move the alarm.
- **The phone's mode is read when the alert fires,** not when the session starts.
- **Sound and vibration use the alarm channel** (`USAGE_ALARM`), which Android normally lets through Silent and Do Not Disturb.
- **The alert service uses foreground service type `specialUse`.** Do not switch to `shortService`: its time limit is close to the 3-minute alert.
- **A session is saved the moment the alarm fires,** so it counts even if Stop is never tapped.
- **The shutdown time cannot be captured.** Android 9 and later do not send the shutdown signal to apps that are not running. A session whose end passed while the phone was off is recorded at its picked length and marked interrupted.
- **After a restart, recovery waits for the first unlock,** because app storage is locked until then.
- **Clear history (Settings) deletes every session and the app's internal backup copies,** after a warning with "Proceed with full history wipe" and "Cancel". Settings and a running session are kept.
- **Import replaces only the session history.** It keeps the running session and this phone's settings, and saves a copy of the old file first.

## Building

Requirements on the Fedora workstation (all already installed):

- Java 21: `~/.jdks/jdk-21.0.12.1+1`. The Android build tools do not run on Java 25 yet.
- Android SDK: `~/Android/Sdk`, with platform `android-37.0` (needed to compile against the current libraries; the app still targets Android 16).

```bash
cd ~/git/Ranged-Timer
export JAVA_HOME=~/.jdks/jdk-21.0.12.1+1
./gradlew testDebugUnitTest        # 54 tests: logic plus app flows under Robolectric
./gradlew testDebugUnitTest --tests '*ScreenshotTest*' -Pscreenshots.dir=/tmp/shots   # optional dark-mode screenshots
./gradlew assembleRelease          # signed APK at app/build/outputs/apk/release/app-release.apk
```

Before each new release, raise `versionCode` (and usually `versionName`) in `app/build.gradle.kts`, or the phone will refuse to install it over the old one.

## The signing key (do not lose it)

The release APK is signed with a private key stored outside this repository:

- Key: `/mnt/wdc/Gold/.secrets/ranged-timer/ranged-timer-release.jks`
- Passwords: `/mnt/wdc/Gold/.secrets/ranged-timer/keystore.properties`
- Certificate SHA-256: `6c512c1635945073e5674f4bd7a8eeee1975c4c47452ebd23f1b52e1369f516f`

The phone accepts an update only if it is signed with the same key. If the key is lost, the only route is uninstalling, which erases the history unless it was exported first. Keep a second copy of that folder offline. The build reads the key from that path; override it with `-PrangedTimer.signingProps=/path/to/keystore.properties`. Never commit the key or its passwords.

## Installing on the phone

1. Copy the APK to the phone (the owner uses the OneDrive folder `Sideprojects-new/Ranged-Timer/`).
2. Open it from My Files or the OneDrive app. Allow "install unknown apps" for that app when asked.
3. If Samsung Auto Blocker is on (Settings, Security and privacy), turn it off for the install, then back on.
4. If Play Protect warns about an unknown developer, choose to install anyway.
5. Open the app, go to Settings, and make all three setup rows show Done: Notifications, Alarms and reminders, Battery: Unrestricted. On Samsung also make sure the app is not in Sleeping or Deep sleeping apps.

Google's developer verification for sideloaded apps began on 2026-09-30 in Brazil, Indonesia, Singapore and Thailand and is planned worldwide for 2027. It did not apply in Canada when this was built. If installs start being blocked, check Google's current rules for personal-use apps.

## Real-phone checklist

Run these before trusting a real sit, and again after any change to `alarm/`:

1. Silent mode, Test in 10 seconds, lock the screen: it vibrates.
2. Vibrate mode: same.
3. Sound on: ringtone plus vibration.
4. Stop from the lock screen notification works.
5. Leave it: it stops by itself after 3 minutes.
6. Start, then hold to end early: the minutes and the picked time are shown and appear in History.
7. Start a short range (for example 2 to 3 minutes), restart the phone, unlock: it alerts on time or immediately if the time passed.
8. If you use Do Not Disturb while meditating, test once with it on.

## Testing notes

The Android emulator (37.2.12) crashed with a segmentation fault on this workstation before booting Android, in both headless and windowed modes, so app flows are tested with Robolectric instead (`AppFlowTest`, screen sized like an S23 Ultra). Alarm timing, vibration through Silent mode and Samsung battery behaviour can only be confirmed on the real phone with the checklist above.

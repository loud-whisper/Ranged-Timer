# Ranged-Timer Design

Date: 2026-10-19
Status: Approved in conversation section by section; written spec awaiting owner review.

## Purpose

A single-purpose Android meditation timer. The owner sets a range (for example 60 to 90 minutes). Each time a session starts, the app secretly picks a random moment inside the range and alerts at that moment. Not knowing when the session ends is the point.

The owner meditates with the screen off and the phone on Silent or Vibrate. Typical sessions are at least 60 minutes.

Success means:

- The alert fires at the chosen moment every time, with the screen off, on a Samsung Galaxy S23 Ultra running Android 16.
- The chosen moment is never visible before the session ends.
- History, averages, and 90-day cycle comparisons are correct and survive app updates.
- The app stays lightweight: no background work between Start and the alert, a few megabytes installed.

## Scope

In scope: one Android app, four screens, on-device storage only, sideloaded APK.

Out of scope: iPhone, Play Store publishing, accounts, network access, ads, analytics, cloud sync, widgets, multiple timers.

## Technology

- Native Android, Kotlin, Jetpack Compose (Material 3).
- No database library. History is one small JSON file in app-private storage.
- No chart library. The graph is drawn with Compose Canvas.
- Target SDK: Android 16 (API 36). Minimum SDK: Android 13 (API 33).
- Built with JDK 21 and the Android SDK command-line tools.

## Screens

### 1. Home

- Two number fields: Shortest and Longest, in whole minutes. Allowed values: 0 to 1000, Shortest less than or equal to Longest. Values are remembered.
- Start button.
- While a session runs, the screen shows only "Session in progress, started <time>". No countdown, no hint of the end.
- End early: a hold-to-confirm button (about 1.5 seconds) so a pocket bump cannot end the session.
- Test in 10 seconds: runs the full alert path 10 seconds later, using the real mode detection. Test runs are never recorded in history.

### 2. Alert

- Alert behavior is chosen by the phone's ringer mode at the moment the alert fires, not at Start:
  - Silent or Vibrate: continuous vibration.
  - Normal (sound on): the owner's chosen ringtone plus vibration.
- The alert continues until Stop is tapped or 3 minutes pass, whichever comes first.
- Stop is available from the lock screen through the notification, without unlocking.
- After the session ends (Stop or 3-minute timeout), opening the app shows "Today: <N> minutes".

### 3. History

- Tabs: 1 week, 3 weeks, 1 month, 2 months, 3 months.
- Per tab: average minutes per day, days meditated, average session length, and comparison with the previous equal-length period ("Up 6 min/day vs the previous 3 weeks").
- Graph: one bar per day (minutes sat), with a trailing 7-day average trend line.
- 90-day cycle comparison list below the graph, newest first, current cycle shown as "Day X of 90".
- Each session row can show the picked length alongside the actual length.

### 4. Settings

- Ringtone: Android's standard ringtone picker (`RingtoneManager.ACTION_RINGTONE_PICKER`). Default: the phone's default alarm sound.
- Theme: Follow phone (default), Light, Dark.
- Setup check: one row per requirement, each showing a green tick or a button that opens the exact Settings page:
  - Notifications allowed (`POST_NOTIFICATIONS`).
  - Exact alarms allowed (`AlarmManager.canScheduleExactAlarms()`).
  - Battery: Unrestricted (`PowerManager.isIgnoringBatteryOptimizations()`).
- Export history and Import history (Storage Access Framework: the owner chooses where the file goes).

## Session lifecycle

### Start

1. Pick a uniformly random duration in seconds between Shortest x 60 and Longest x 60, inclusive, using `java.security.SecureRandom` or `kotlin.random.Random`. Uniform: every second in the range is equally likely.
2. Persist the active session immediately: start wall-clock time, start elapsed-realtime, picked duration.
3. Schedule the alert with `AlarmManager.setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, ...)`.
   - Elapsed realtime is immune to clock and time zone changes.
   - `setAlarmClock()` is deliberately NOT used: it publishes the next alarm time to the system UI (status bar or lock screen), which would reveal the end time.
4. Post a low-importance ongoing notification: "Session in progress" with no time shown.
5. Nothing else runs until the alarm fires.

### Exact-alarm permission

Declare `USE_EXACT_ALARM` (granted at install for apps targeting API 33+, appropriate for a timer app that is not distributed through Play). Also declare `SCHEDULE_EXACT_ALARM` as a fallback and verify with `canScheduleExactAlarms()` in the setup check. The implementation plan must confirm current Android 16 behavior for both before relying on either.

### Alert fires

1. The alarm receiver starts a short-lived foreground service for the alert. Starting a foreground service from an exact alarm is an allowed background-start exemption. The plan must confirm the correct foreground service type for Android 16 and must not use `shortService`, whose timeout is close to the 3-minute alert length.
2. Record the session immediately with actual length = picked length and ended-early = false. The record is saved even if Stop is never tapped.
3. Read `AudioManager.getRingerMode()`:
   - `RINGER_MODE_SILENT` or `RINGER_MODE_VIBRATE`: vibrate with a repeating waveform using alarm usage (`VibrationAttributes.USAGE_ALARM`).
   - `RINGER_MODE_NORMAL`: play the chosen ringtone with `AudioAttributes.USAGE_ALARM` and also vibrate.
4. Hold a partial wake lock only for the alert duration (maximum 3 minutes plus a small margin).
5. Show a high-importance notification with a Stop action, visible on the lock screen.
6. Stop or the 3-minute timeout ends sound and vibration, releases the wake lock, and removes the notification.

Alarm usage is normally allowed through Silent mode and Do Not Disturb. This is unverified on the owner's device; the Test in 10 seconds button and the real-phone checklist confirm it.

### End early

1. Compute actual length from elapsed realtime since start.
2. Cancel the scheduled alarm.
3. Record the session with ended-early = true, actual length, and picked length.
4. Show "You sat <actual> minutes. The timer had picked <picked>."

### Reboot and recovery

- Elapsed realtime resets on reboot, so persist both the start wall-clock time and start elapsed-realtime.
- Android does not deliver the shutdown broadcast to apps that are not running (manifest receivers stopped receiving `ACTION_SHUTDOWN` in Android 9), so the shutdown time cannot be captured. (Revised 2026-10-19 during implementation.)
- On `BOOT_COMPLETED`, reload the active session:
  - If the picked end is still in the future (computed from wall-clock time, since elapsed realtime no longer applies), reschedule the alarm for the remaining time.
  - If the picked end already passed while the phone was off, alert immediately and record outcome `interrupted` at the picked length.
  - `LOCKED_BOOT_COMPLETED` is not used: app storage is unavailable until the first unlock, so recovery runs after the owner unlocks the phone.
- On `MY_PACKAGE_REPLACED` (app update), reschedule the active session's alarm.
- If the app is opened and finds an active session whose end passed with no recorded alert (the alarm was missed while the phone was on), record outcome `interrupted` with the picked length and tell the owner the alert was missed.

### Battery

Samsung can put background apps to sleep and cancel their alarms. The setup check requires Battery: Unrestricted and shows a red warning if it is turned off later.

## Data model

One JSON file in app-private storage, written atomically (write to temp file, then rename).

```json
{
  "schemaVersion": 1,
  "settings": {
    "shortestMinutes": 60,
    "longestMinutes": 90,
    "ringtoneUri": null,
    "theme": "system"
  },
  "activeSession": null,
  "sessions": [
    {
      "id": "uuid",
      "startEpochMillis": 0,
      "pickedSeconds": 4440,
      "actualSeconds": 4440,
      "outcome": "completed"
    }
  ]
}
```

`outcome` is one of `completed`, `ended_early`, `interrupted`. All sessions are kept permanently. The data is tiny: a few dozen bytes per session.

## Statistics

All calculations live in pure Kotlin with no Android dependencies, so they can be unit tested.

- Day assignment: a session belongs to the local calendar day on which it started.
- Day total: the sum of `actualSeconds` for that day.
- Period windows end today and are rolling: 1 week = 7 days, 3 weeks = 21, 1 month = 30, 2 months = 60, 3 months = 90.
- Average minutes per day = total minutes in window / days in window. Skipped days count as 0.
- Short history: if the first session is more recent than the window start, the denominator is the days since the first session (inclusive), and the tab is labeled "based on N days so far".
- Days meditated: the count of days in the window with a total above 0.
- Average session length: the mean `actualSeconds` of sessions in the window.
- Trend comparison: the current window average against the immediately preceding window of the same length. Omitted when there is not enough history.
- Trend line: a trailing 7-day mean of day totals.

### 90-day cycles

- Cycle 1 starts on the local day of the first recorded session.
- Each cycle is exactly 90 calendar days. The next cycle starts the day after the previous one ends, whether or not there were sessions.
- Cycle summaries (average minutes per day over 90 days, days meditated, average session length, total hours) are computed from the stored sessions, not stored separately, so they can never drift from the data.
- The current cycle shows "Day X of 90" and its running summary.

## Export and import

- Export writes the full JSON file to a location the owner picks.
- Import validates `schemaVersion` and structure, shows a summary (session count, date range), and asks to confirm before replacing. The current data is backed up in app storage before replacement.
- Android Auto Backup is enabled for the data file as a secondary safety net.

## Build, signing, and install

- Repository: private `loud-whisper/Ranged-Timer`, local clone `~/git/Ranged-Timer`.
- Release signing key: generated once, stored in a private folder outside the repository. Key passwords are never committed. The README states the key location only. The owner keeps a second copy offline.
- Losing the key means updates require uninstalling, which erases history unless it was exported.
- Install: the owner moves the APK to the phone (Drive, USB, or email), allows "install unknown apps" for the opening app, and may need to turn Samsung Auto Blocker off temporarily and accept a Play Protect warning.
- Before building, verify the current status of Google's Android developer verification requirement for sideloaded apps in Canada, and plan the free personal-use route if it applies.

## Testing

- Unit tests (written first): random pick bounds and inclusivity, day assignment across midnight, period averages, short-history denominator, trend comparison, cycle boundaries, JSON round trip, import validation.
- Instrumented or manual checks on an emulator where practical.
- Real-phone checklist on the S23 Ultra before trusting it in a session:
  1. Test in 10 seconds on Silent, screen locked.
  2. Same on Vibrate.
  3. Same with sound on.
  4. Stop from the lock screen.
  5. 3-minute auto-stop.
  6. End early (record and reveal are correct).
  7. Restart the phone mid-session.
  8. A real session with Do Not Disturb on, if the owner uses it.

## Repository contents

- `README.md`: plain-language overview, how to build, how to install, where the signing key lives, where each part of the code lives, written for a future AI or the owner.
- This design document and the implementation plan under `docs/superpowers/`.
- Source, tests, Gradle wrapper.

## Brain integration

A one-line entry in `shared/projects/README.md` in `loud-whisper/claude-memory` pointing to this repository, added through a branch and pull request.

## Addendum 2026-10-19: labels and report (v1.3)

Approved by the owner after v1.2.

- Label chips above Start: Meditation, Reading, Workout, and a fourth label whose name the owner sets in Settings (default "Other", up to 24 characters, cannot repeat a built-in name). The selected chip is remembered (`Settings.labelChoice`).
- `Session.label` and `ActiveSession.label` store the label text at the time of the session. Missing labels in older files decode as "Meditation"; `schemaVersion` stays 1 because the change only adds optional fields.
- History has a label filter: All, the three built-in labels, and the fourth chip, which groups every label name outside the built-in three. Period stats use the filtered sessions; 90-day cycles stay anchored to the first session of any label.
- Settings adds "Export report (spreadsheet)": a CSV with Date, Start time, Label, Minutes sat, Minutes picked, Outcome, oldest first. Labels beginning with `=`, `+`, `-`, `@`, tab or carriage return get a leading apostrophe to block spreadsheet formula injection.
- Earlier changes in v1.1 and v1.2: Clear history (with warning), bottom navigation, vertically centred timer screen, Test in 10 seconds moved to Settings, start vibration on the alarm channel, status bar icons follow the app theme.

## Addendum 2026-10-19: typed fourth label, relabelling, period names (v1.4)

- The fourth label is typed in a name box on the timer screen, shown when the fourth chip is selected. The chip shows the typed name. Settings no longer has a rename field. The typed name is normalized at Start (trimmed, 1 to 24 characters, any spelling of a built-in name maps to it). Start is disabled while the box is empty.
- `Settings.recentCustomLabels` keeps the five most recent custom names, newest first, shown as one-tap picks under the name box.
- History: tapping a session opens an inline editor in the list (built-in chips, recent names, a name box, Save and Cancel) to fix a session saved under the wrong label. It is inline rather than a dialog because a text field inside a dialog never settled under Robolectric, and an on-device freeze could not be ruled out from the build machine.
- The History filter's fourth chip is always "Other" and groups every custom name.
- Period buttons read "Last 7 days" to "Last 90 days"; comparisons read "vs the previous 7 days". The windows were already rolling; only the names changed.

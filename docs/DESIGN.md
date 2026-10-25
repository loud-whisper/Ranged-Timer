# Ranged Timer design

## Purpose and boundaries

Choose a duration uniformly at random inside a user-selected range. Keep the chosen end time hidden until the session ends. The app runs one session at a time, stores data on the phone, and has no network permission.

The timer, history and settings screens use Kotlin and Jetpack Compose. Pure calculations live in `core/`; platform alarms and vibration live in `alarm/`; `data/Store.kt` owns the JSON file. Read the README code map before making a change.

## Timing and alerts

- Accept a shortest and longest duration from 0 to 1000 whole minutes, shortest at most longest. Pick uniformly over the inclusive range in seconds.
- Save the active session before scheduling its alert. Persist wall-clock start, elapsed-realtime start, boot count, chosen duration and label.
- Use `setExactAndAllowWhileIdle` on the elapsed-realtime clock. `setAlarmClock` would expose the selected end in system UI and is unsuitable here.
- While running, show the start time and session state without an end time or countdown. End early requires a 1.5-second hold.
- Read the ringer mode when the alert fires. Silent/Vibrate uses vibration; normal mode uses the selected ringtone and vibration. Use alarm audio/vibration attributes.
- Record completion when the alarm fires, so the history survives an untapped Stop button. Stop or three minutes ends the foreground alert service and releases its wake lock.
- After reboot and first unlock, reschedule a future end using the persisted wall clock. An end missed while powered off is recorded as interrupted at the picked duration. App updates reschedule an active alarm.
- Notifications, exact-alarm permission and battery exemptions belong to the installed app identity. Recheck them on each device.

## History and labels

Each finished session stores its ID, start timestamp, chosen seconds, actual seconds, outcome and label. Outcomes are completed, ended early or interrupted. Built-in labels are Meditation, Reading and Workout. A fourth label is typed on the timer screen; trim it, accept 1 to 24 characters, and keep the five most recent custom names. Editing a preference never changes existing session labels. Only a deliberate History edit relabels a saved session.

History windows cover the last 7, 21, 30, 60 or 90 calendar days, including today. Assign a session to its local start day. Skipped days count as zero. A new history uses the available days since its first session as the denominator. Compare equal adjacent windows only when enough history exists. The graph uses a trailing seven-day mean.

Ninety-day cycles start on the date of the first session of any label and remain anchored there when a label filter changes. Cycle summaries are derived from sessions, not stored independently.

## Storage and portability

`ranged-timer.json` uses schema version 1. `core/Model.kt` defines its fields; `core/Codec.kt` validates and encodes them. Additive fields need compatible defaults. Renaming, removing or changing a field's meaning needs a schema migration and a test for the previous shape. Reject unsupported versions and negative session durations.

Writes replace the file atomically. Unreadable files are retained for recovery. JSON export includes the complete stored data. The existing importer replaces only session history, keeps the destination installation's settings and active session, and backs up its previous file. It does not merge histories. Confirm the displayed session count and date range before importing.

The CSV report is for spreadsheets, not restoration: it rounds durations and omits session IDs. Prefix formula-like labels with an apostrophe. Clear history requires confirmation and removes retained history recovery copies while keeping settings and an active session.

## Release and device checks

The launch application ID is `com.loudwhisper.rangedtimer.mobile`; the Kotlin namespace remains `com.loudwhisper.rangedtimer`. Keep that separation explicit in Gradle. Future updates must retain this application ID and signing key and increase the version code. The private release key stays outside source control.

Run the unit and Robolectric suites with JDK 21. On a real phone, check Silent/Vibrate/sound modes with the screen locked, notification Stop, three-minute timeout, end early, and reboot recovery. Do Not Disturb behavior depends on device settings. Passing a desktop test is not evidence of those device behaviors.

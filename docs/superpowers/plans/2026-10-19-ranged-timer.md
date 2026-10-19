# Ranged-Timer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the sideloadable Ranged-Timer APK described in the spec.

**Architecture:** A pure-Kotlin `core` package (models, random pick, statistics, cycles, JSON codec, recovery decisions) carries all logic that can be wrong silently and is unit tested on the JVM. A thin Android layer (`data`, `alarm`, `ui`) persists the JSON file, schedules one exact alarm, runs a short foreground alert service, and renders four Compose screens.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), kotlinx.serialization, JUnit 4, Gradle wrapper, Android Gradle Plugin, JDK 21.

**Spec:** `docs/superpowers/specs/2026-10-19-ranged-timer-design.md`

## Global Constraints

- Application id and package: `com.loudwhisper.rangedtimer`.
- minSdk 33, targetSdk 36, compileSdk 36 or the lowest level the chosen libraries require.
- No database library, no chart library, no network permission.
- Range: whole minutes 0 to 1000, Shortest <= Longest.
- Alert cap: 3 minutes (180 000 ms).
- Data file: `filesDir/ranged-timer.json`, `schemaVersion` 1, atomic write via temp file and rename.
- Outcomes: `completed`, `ended_early`, `interrupted`.
- Windows: 7, 21, 30, 60, 90 days. Cycle length: 90 days.
- Never use `AlarmManager.setAlarmClock()`; never use foreground service type `shortService`.
- Signing key and passwords live only in a private folder outside the repository, never in the repository.
- User-facing text: no emojis, no em or en dashes.

## Review Focus

1. Shortest equals Longest, and Shortest is 0: the pick must stay inside the inclusive range; 0 seconds fires immediately. Test in Task 2.
2. Daylight saving changes and sessions crossing midnight: days are local calendar dates from `ZoneId`, never 24-hour multiples. Test in Task 3.
3. Corrupt or missing data file: the app starts with empty data and moves the unreadable file aside instead of crashing or overwriting it. Test in Task 4.
4. Stale alarm after End early or a second fire: the receiver acts only when the alarm's session id matches the active session. Test in Task 5.
5. Opening the app moments after the end before the alarm is delivered: a 60-second grace period prevents a false "missed" record. Test in Task 5.

---

### Task 1: Project scaffold that builds

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, Gradle wrapper files, `.gitignore`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/loudwhisper/rangedtimer/MainActivity.kt` (placeholder text).

- [ ] **Step 1:** Generate the wrapper with the cached Gradle distribution running on JDK 21; use current stable AGP, Kotlin, Compose BOM. Declare plugins: Android application, Kotlin Compose compiler, Kotlin serialization.
- [ ] **Step 2:** `./gradlew assembleDebug` (JDK 21) succeeds.
- [ ] **Step 3:** Commit.

### Task 2: Core models and random pick

**Files:**
- Create: `app/src/main/java/com/loudwhisper/rangedtimer/core/Model.kt`, `core/DurationPicker.kt`
- Test: `app/src/test/java/com/loudwhisper/rangedtimer/core/DurationPickerTest.kt`

**Interfaces:**
- Produces: `@Serializable enum class Outcome { completed, ended_early, interrupted }` (serial names as listed); `@Serializable data class Session(id: String, startEpochMillis: Long, pickedSeconds: Long, actualSeconds: Long, outcome: Outcome)`; `ActiveSession(id: String, startEpochMillis: Long, startElapsedMillis: Long, bootCount: Int, pickedSeconds: Long, shutdownEpochMillis: Long? = null)`; `Settings(shortestMinutes: Int = 60, longestMinutes: Int = 90, ringtoneUri: String? = null, theme: Theme = Theme.system)`; `enum class Theme { system, light, dark }`; `AppData(schemaVersion: Int = 1, settings: Settings = Settings(), activeSession: ActiveSession? = null, sessions: List<Session> = emptyList())`.
- Produces: `fun pickSeconds(shortestMinutes: Int, longestMinutes: Int, random: Random = Random.Default): Long`; `fun validateRange(shortest: Int, longest: Int): String?` (null when valid, else a user-facing message).

- [ ] **Step 1: Tests:** `pick stays in inclusive bounds over 10 000 draws` for (60, 90) asserts every value in 3600..5400 and that both 3600 and 5400 are reachable with a seeded `Random` over enough draws or via a stub; `equal bounds returns exact value` (45, 45) == 2700; `zero range returns zero` (0, 0) == 0; `validateRange rejects shortest above longest`, `rejects above 1000`, `rejects negative`, `accepts 0 and 1000`.
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest`; expect failures.
- [ ] **Step 3:** Implement with `random.nextLong(lo, hi + 1)`.
- [ ] **Step 4:** Tests pass. Commit.

### Task 3: Statistics and cycles

**Files:**
- Create: `core/Stats.kt`
- Test: `core/StatsTest.kt`

**Interfaces:**
- Consumes: `Session`.
- Produces: `fun dayTotals(sessions: List<Session>, zone: ZoneId): Map<LocalDate, Long>` (seconds); `data class PeriodStats(windowDays: Int, daysCounted: Int, partial: Boolean, avgMinutesPerDay: Double, daysMeditated: Int, avgSessionMinutes: Double?, changeVsPrevious: Double?)`; `fun periodStats(sessions: List<Session>, today: LocalDate, windowDays: Int, zone: ZoneId): PeriodStats?` (null when no sessions); `fun trend(dayTotals: Map<LocalDate, Long>, firstDay: LocalDate, from: LocalDate, to: LocalDate): List<Pair<LocalDate, Double>>` (trailing mean of up to 7 days, never earlier than `firstDay`, minutes); `data class CycleSummary(index: Int, start: LocalDate, end: LocalDate, isCurrent: Boolean, dayOfCycle: Int, avgMinutesPerDay: Double, daysMeditated: Int, avgSessionMinutes: Double?, totalHours: Double)`; `fun cycles(sessions: List<Session>, today: LocalDate, zone: ZoneId): List<CycleSummary>` newest first.

Rules (from spec): window = `today - (windowDays-1)` .. `today`. If the first session day is after the window start, `daysCounted` = days from first session day to today inclusive and `partial` = true. `changeVsPrevious` is the current average minus the previous equal-length window average, computed only when `partial` is false; the previous window applies the same first-day rule. Cycle n (index 1-based) starts `firstDay + 90*(n-1)` days; the current cycle's averages divide by `dayOfCycle`.

- [ ] **Step 1: Tests:** `session crossing midnight counts on start day`; `two sessions same day sum`; `skipped days count as zero` (sessions 60 and 0-day gap: 7-day window with 2 sessions of 60 min on a full history gives 120/7); `partial window divides by days so far` (first session 3 days ago, 90-day window, daysCounted 4, partial true); `change vs previous only when full`; `DST spring-forward day still one day` (America/Toronto 2026-03-08); `cycle boundaries` (first day D: day D+89 is cycle 1 day 90, D+90 is cycle 2 day 1); `cycle with no sessions still appears`; `trend never starts before first day`.
- [ ] **Step 2:** Run; expect failures.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Tests pass. Commit.

### Task 4: JSON codec and store

**Files:**
- Create: `core/Codec.kt`, `data/Store.kt`
- Test: `core/CodecTest.kt`

**Interfaces:**
- Produces: `object Codec { fun encode(data: AppData): String; fun decode(text: String): Result<AppData> }` (ignoreUnknownKeys, rejects schemaVersion != 1, rejects negative seconds); `class Store(dir: File) { fun load(): AppData; fun save(data: AppData); fun update(block: (AppData) -> AppData): AppData; fun exportTo(out: OutputStream); fun importFrom(text: String): Result<AppData> }`. `load()` on unreadable content renames the file to `ranged-timer.corrupt-<epochMillis>.json` and returns `AppData()`. Writes are synchronized and atomic. `importFrom` saves a copy of the current file as `ranged-timer.before-import-<epochMillis>.json` before replacing.

- [ ] **Step 1: Tests:** `round trip preserves everything`; `unknown keys ignored`; `wrong schemaVersion fails`; `garbage text fails`; `Store load of corrupt file returns empty and keeps the file aside` (JVM test using a temp dir; Store has no Android dependency).
- [ ] **Step 2:** Run; expect failures. **Step 3:** Implement. **Step 4:** Pass. Commit.

### Task 5: Session decisions (pure)

**Files:**
- Create: `core/SessionLogic.kt`
- Test: `core/SessionLogicTest.kt`

**Interfaces:**
- Produces: `fun endEarly(active: ActiveSession, nowElapsed: Long): Session`; `fun complete(active: ActiveSession): Session`; `sealed interface BootDecision { data class Reschedule(val remainingMillis: Long); data class FireNow(val session: Session) }`; `fun onBoot(active: ActiveSession, nowEpoch: Long): BootDecision`; `fun onOpen(active: ActiveSession, nowElapsed: Long, currentBootCount: Int, nowEpoch: Long): Session?` (returns an `interrupted` record when the end passed more than 60 000 ms ago, else null); `fun alarmMatches(active: ActiveSession?, sessionId: String): Boolean`.

Rules: `onBoot` end = start epoch + picked. If end > now: Reschedule(end - now). Else FireNow with `interrupted`, actual = min(picked, (shutdown - start)/1000) when shutdown is known, else picked. `onOpen` uses elapsed time when boot counts match, epoch time otherwise.

- [ ] **Step 1: Tests:** `end early records elapsed and flag`; `complete records picked`; `boot before end reschedules remaining`; `boot after end with shutdown caps actual`; `boot after end without shutdown uses picked`; `open within grace returns null`; `open after grace returns interrupted`; `stale alarm id does not match`; `null active does not match`.
- [ ] **Step 2 to 4:** Fail, implement, pass. Commit.

### Task 6: Alarm, alert service, receivers

**Files:**
- Create: `alarm/Scheduler.kt`, `alarm/AlarmReceiver.kt`, `alarm/SystemReceiver.kt` (BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, ACTION_SHUTDOWN), `alarm/AlertService.kt`, `alarm/Notifications.kt`, `alarm/SessionController.kt`
- Modify: `AndroidManifest.xml`

**Interfaces:**
- Consumes: Tasks 2, 4, 5.
- Produces: `object SessionController { fun start(ctx): ActiveSession; fun endEarly(ctx): Session?; fun onAlarm(ctx, sessionId: String?, isTest: Boolean); fun stopAlert(ctx); fun recoverOnOpen(ctx): Session?; fun testIn10s(ctx) }`; `object Scheduler { fun schedule(ctx, triggerElapsed: Long, sessionId: String?, isTest: Boolean); fun cancel(ctx) }` using `setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)`, request code 1 for sessions and 2 for tests.

Decisions: manifest declares `USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `VIBRATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; `AlertService` uses `foregroundServiceType="specialUse"` with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` "meditation timer alert". Alert: read `AudioManager.ringerMode` at fire; vibrate with repeating waveform and `VibrationAttributes.USAGE_ALARM`; in normal mode also loop `MediaPlayer` with `USAGE_ALARM` on the chosen or default alarm URI (fallback to default ringtone, then vibration only). Partial wake lock 190 s. Auto-stop after 180 s. Channels: "Session" (low, silent, ongoing "Session in progress") and "Alert" (high, channel sound and vibration off, lock-screen public, Stop action). Test runs never touch `activeSession` or history.

- [ ] **Step 1:** Implement; `./gradlew assembleDebug` and `testDebugUnitTest` pass. Commit.

### Task 7: Screens

**Files:**
- Create: `ui/Theme.kt`, `ui/HomeScreen.kt`, `ui/HistoryScreen.kt`, `ui/Graph.kt`, `ui/SettingsScreen.kt`, `ui/AppViewModel.kt`; modify `MainActivity.kt`.

Decisions: bottom navigation with Home, History, Settings. Home: range fields with `validateRange` message, Start, in-session text "Session in progress, started h:mm a", hold-to-end (1.5 s) button, Test in 10 seconds (disabled during a session), last result card ("Today: N minutes" or the early-end reveal "You sat X minutes. The timer had picked Y."). History: tabs 7/21/30/60/90 labeled 1 week, 3 weeks, 1 month, 2 months, 3 months; stats; "Up N min/day vs the previous <period>" or "Down"; Canvas bar graph plus trend line; cycle list; recent sessions list showing picked vs actual. Settings: ringtone picker via `RingtoneManager.ACTION_RINGTONE_PICKER` (TYPE_ALARM), theme selector, setup check rows with tick or button (notifications runtime request, `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`, `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), export and import via `CreateDocument`/`OpenDocument` with import confirmation dialog.

- [ ] **Step 1:** Implement; build passes. Commit.

### Task 8: Release build, docs, Brain pointer

- [ ] **Step 1:** Generate the release keystore in a private folder outside the repository (folder 700, files 600) with a random password stored in a 600 properties file beside it; `app/build.gradle.kts` reads it via a path from `~/.gradle/gradle.properties` or an environment variable, and the build still works without it for debug.
- [ ] **Step 2:** `./gradlew testDebugUnitTest assembleRelease` passes; `apksigner verify --print-certs` succeeds; record APK size.
- [ ] **Step 3:** Run on an emulator if one can be created; otherwise record that device testing is pending.
- [ ] **Step 4:** Write `README.md` (purpose, build, install, key location, code map, real-phone checklist). Copy the APK to an owner-visible folder. Commit and push.
- [ ] **Step 5:** Brain: branch in `~/git/claude-memory`, add the project line to `shared/projects/README.md`, run the validator, open a PR.

# CLAUDE.md

Guidance for Claude Code (and future me) working in this repo. User-facing docs are in `README.md`.

## What this is

**Habit Nudge**: a personal, never-published Android app to quit doomscrolling and build habits with reminders that are hard to ignore. It runs on one phone, a **Huawei Y7p (ART-L29), Android 10 / API 29, EMUI 10.1, arm64, ~4 GB RAM, 720×1560, no Google Play Services**, and is sideloaded over USB with adb.

GitHub: https://github.com/assil-bouzaine/Habit-nudge (public). Commit identity for this repo: `assil-bouzaine <bouzaineassil015@gmail.com>`.

## Hard constraints (from the user, never relax without asking)

- **No Google Play Services / Firebase / FCM.** AndroidX/Jetpack is fine.
- **No INTERNET permission, no server, no accounts.** The manifest strips `INTERNET`, and the Gradle task `verify<Variant>NoInternet` fails the build if any library adds it back.
- **No Android Studio and no emulator.** Use the CLI toolchain only and test on the real phone.
- **Free tools and libraries only.**
- **Keep it light:** it's a low-end phone.
- **The app-open nudge must never *block* an app.** It always offers a way to stay ("Stay anyway"), though that button may be delayed by a countdown.
- **Ask before taking `adb screencap` screenshots.** It's a personal phone, and a screenshot once captured a private video call.

## Build, install, test

```powershell
& ".\build.ps1"            # assembleRelease + adb install -r
& ".\build.ps1" -NoInstall # build only; log in build\build.log
```

- **Toolchain:** Temurin JDK 17 at `%USERPROFILE%\Android\jdk17`, SDK at `%USERPROFILE%\Android\sdk` (platform-tools, `platforms;android-35`, `build-tools;35.0.0`).
- **On Linux (current machine):** JDK 17 from pacman, SDK at `~/Android/sdk` — `export ANDROID_HOME=~/Android/sdk`, add `platform-tools` to PATH, then `./gradlew assembleRelease` and `adb install -r app/build/outputs/apk/release/app-release.apk`. Signing still comes from the git-ignored `keystore.properties`; keys at `~/Android/keys/habitnudge.jks`.
- **Pinned versions:** AGP 8.7.3, Gradle 8.11.1 (wrapper), Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.12.01, Room 2.6.1.
- **SDK levels:** `minSdk 28`, `targetSdk 29` (deliberate: the older rules for exact alarms, notifications and background activity starts), `compileSdk 35`.
- **Signing:** release builds are minified (R8) and signed from git-ignored `keystore.properties`. The key is at `%USERPROFILE%\Android\keys\habitnudge.jks`, outside the repo. Losing it means uninstalling, and so losing data, to update.
- **No unit tests:** verification happens on the phone, as follows.
  - **Health screen:** "Test reminder in 1 minute" at each strictness level.
  - **Crashes:** `adb logcat -d -b crash`.
  - **Alarms:** `adb shell dumpsys alarm | grep habitnudge`.
  - **Reliability log:** `adb shell cat /sdcard/Android/data/me.habitnudge/files/reliability.log`.
  - **Deep Doze test:** `adb shell dumpsys battery unplug; adb shell dumpsys deviceidle force-idle`, then `unforce` / `battery reset` to undo.
  - **Accessibility service config:** `adb shell dumpsys accessibility | grep -i "Bound services"`.

### Windows / PowerShell 5.1 gotchas (all hit for real)

- **Gradle and stderr:** don't redirect Gradle's stderr in PowerShell, because each line gets wrapped in an error record. `build.ps1` uses `Start-Process` with `-RedirectStandardOutput/-Error`.
- **`cmd /c` quoting:** it's unreliable with the space in the folder name (`Habit Nudge`). Don't use it.
- **Commit messages:** write the message to a scratchpad file and run `git commit -F <file>`. Inline here-strings with double quotes get split into pathspecs.
- **Locked `classes.dex`:** "The process cannot access the file … classes.dex" happens intermittently (antivirus). `build.ps1` retries once.
- **`sdkmanager`:** command-line tools newer than build 13114758 hand it to an "Android CLI" whose bundled JRE gets blocked by Smart App Control. Use cmdline-tools 13114758, and feed license "y"s through a file and cmd stdin.

## Architecture

Single module `app`, package `me.habitnudge`, Kotlin + Jetpack Compose (Material 3), Room, SharedPreferences.

```
HabitApp.kt          Application: db, prefs, app-wide coroutine scope, creates notification channels
MainActivity.kt      Edge-to-edge Compose host; seeds defaults; Engine.onAppStart; opens Reminders from the plan-tomorrow reminder
data/                Entities, DAOs, AppDatabase (auto-migrations), Prefs, Seed, NoteAuth (notes PIN), DiagLog (reliability log)
schedule/            Engine, Occurrences, receivers (alarm, boot/time, Done), RescheduleActivity
notify/Notifier.kt   Channels (gentle_v1, sticky_v1, nag_v1, takeover_v1, nudge_v1) and all notification builders
takeover/            TakeoverActivity (full-screen card), Takeover helpers, AlarmSound
nudge/               NudgeService (accessibility), NudgeCard (overlay views), Nudges (messages), Stats + Bedtime
ui/                  Screens: Reminders (Plan + Recurring behind a segmented control), Nudge, Stats, Notes, Setup (HealthScreen, reached via the gear in every ScreenHeader); Theme, Components, Widgets, RescheduleDialog
```

### Scheduling (`schedule/Engine.kt`)

- **One alarm only:** the Engine keeps exactly one `AlarmManager` alarm, for the earliest of the next occurrence (planned reminder, recurring slot, test) or the next repeat nag / deferred Takeover retry.
- **When it fires** (`onAlarm`), it:
  1. handles occurrences in `(lastProcessedAt, now]`, firing anything at most 30 minutes late and dropping anything older
  2. handles due nags and escalations
  3. re-posts active alerts whose notifications vanished
  4. sets the next alarm
  5. warns once a day if the nudge service is off
- **Alarm type:** Nagging/Takeover use `setAlarmClock` (immune to Doze); everything else uses `setExactAndAllowWhileIdle`.
- **Re-arming:** on boot, app update, time and time-zone change (`SystemEventReceiver`), and on every app start.
- **Occurrence keys** are stable (`p:<id>`, `r:<id>:<epochDay>:<minute>`, `test:<millis>`), so nothing fires twice.
- **Active alerts:** Sticky/Nagging/Takeover become an `ActiveAlert` row until Done. For Takeovers, `nextNagAt` doubles as the "retry after the call" time.
- **Concurrency:** all Engine entry points are serialized by a `Mutex`.

### Strictness levels

| Level | How it alerts | Done |
|---|---|---|
| Gentle | Normal notification on `gentle_v1` | none |
| Sticky | Ongoing heads-up | Done action |
| Nagging | Re-posted every N min on `nag_v1` (USAGE_ALARM, rings on vibrate) | Done action; becomes a Takeover after N ignored nags |
| Takeover | Full-screen-intent notification + direct `startActivity`, alarm tone and vibration for at most 2 min | Done after an optional countdown |

**Takeover details:**
- **Launching it:** the activity can start from the background because the app holds "display over other apps" (an Android 10 exemption).
- **Sound:** the tone is started by the Engine, not the activity, because EMUI keeps the card *paused* over the lock screen.
- **Leaving it:** Back is blocked, and Home or recents relaunches it.
- **Calls:** during a call (audio mode IN_CALL / IN_COMMUNICATION / RINGTONE) it is deferred and retried every minute.

**Reschedule ("remind me later")** is on every reminder notification and on the Takeover card. On the card it's locked by the same countdown as Done. Choosing a time closes the alert and adds a `PlannedReminder` at the new time, with the same message, strictness and opens-planner flag (`Engine.rescheduleReminder`).

### App-open nudge (`nudge/NudgeService.kt`)

- **What it listens to:** an accessibility service using window-state, content-changed, scrolled, clicked and focused events, **package name only**, with `canRetrieveWindowContent=false`.
  - Content, scroll, click and focus events are needed because **EMUI sends no window-state event when returning to an app from recents**.
  - The event types are also set at runtime in `onServiceConnected`, because the system kept a stale copy of the XML config across an update.
- **"Opening" an app** means arriving from home, from a locked screen, or from another app after more than 30 seconds.
  - **Transient surfaces** (the shade = `com.android.systemui`, `android`, the EMUI share sheet `com.huawei.android.internal.app`, keyboards, our own package) don't count as leaving.
  - **Home or launcher packages** end the session.
- **Every open:** a centered `NudgeCard` (a `TYPE_ACCESSIBILITY_OVERLAY` window with a light dim that passes touches through), or a notification banner.
- **Check-ins:** "Still in X?" every N minutes while you stay.
- **Leaving** removes the card and the nudge notifications immediately.
- **Bedtime mode** (default 23:00–06:00):
  - every open gets a full-screen dark card that doesn't fade
  - "Stay anyway" is locked for 15 seconds; "Get me out" works at once
  - check-ins every 5 minutes as that same card
- **Stats** recorded per app per day: opens, Get me out taps, Stay taps, check-ins, foreground time, and that day's limit.

### Notes (`ui/NotesScreen.kt`, `data/NoteAuth.kt`)

- **Two kinds:** regular notes (always visible) and secret notes (PIN-protected). Only regular notes get reminders — the editor hides the reminder options for secrets.
- **PIN:** SHA-256 hash in `Prefs.notesAuthPin`, stored by `NoteAuth`; **no recovery** (losing it hides the secrets forever). An unlock lasts **60 seconds** (`NoteAuth.SESSION_DURATION_MS`), then secrets hide on their own.
- **Hiding:** the lock glyph in the Notes header switches views — locked shows regular notes only, unlocked shows **secret notes alone** (never mixed). While unlocked, "+" defaults to a secret note. There is deliberately no "Secret Notes" section that reveals private notes exist.
- **Reminders:** optional per regular note — every N days at a set time, any strictness (default Gentle), with Pause/Resume on the card (`ReminderConfig.isPaused`). Occurrence key `note:<id>:<epochDay>`; `Occurrences.generateNoteReminders` creates the window and `Engine.advanceNoteReminder` fires one and rolls `nextReminderEpochDay` forward. Deleting the note removes its reminder.
- **List vs editor:** the list clamps content to one line with an ellipsis; the editor shows it in full.

### Data (Room, `app/schemas/` exported)

| Version | Change |
|---|---|
| v1 | planned_reminder, recurring_rule, active_alert |
| v2 | nudge_app, nudge_message |
| v3 | nudge_app.cooldownMin → checkInMin (rename + set 15) |
| v4 | app_day_stat |
| v5 | recurring_rule.daysMask (bit 0 = Mon … bit 6 = Sun, 127 = every day), app_day_stat.limitMin, planned_reminder.opensPlanner |
| v6 | note (content, isSecret, timestamps, optional embedded reminder_config: intervalDays, timeOfDay, isPaused, nextReminderEpochDay, strictness) |

**Always add a migration**, normally an `AutoMigration`. Never use destructive migration: the user's data lives only on the phone. Seeding happens once and is tracked by `Prefs` flags (`seeded`, `seededNudge`, `seededRuthless`).

**What's pruned:** planned reminders older than 30 days and stats older than 90 days, on app start.

### UI conventions

- **Colours:** brand blue `#1877F2` (`BrandBlue` in `ui/Theme.kt`), with light and dark schemes.
- **Shared components:** white `AppCard`s on a tinted background, `ScreenHeader` (title-line-aligned icons: `leading` slot e.g. a back arrow, `trailing` slot e.g. the Notes lock, plus the Setup gear with its red dot unless `showGear = false`), `Pill`, `StrictnessPill`, `IconBadge`, `SectionLabel`, `EmptyState` (`ui/Components.kt`).
- **Strictness colours:** Gentle green, Sticky blue, Nagging amber, Takeover red.
- **Icons:** only the `material-icons-core` set is available (no extended icons), so emoji are used where no core icon fits (🌙 🔥 🏆).
- **Status never by colour alone:** charts follow the dataviz rules, with a glyph or legend for every status colour.
- **Writes** go through `app.scope` (not the composable scope), followed by `Engine.reschedule(app)` when reminders change.

## Decisions log

**Chosen as defaults on 2026-10-04:**
- Takeover and Nagging ring on the alarm stream; Gentle and Sticky follow the ringer. The phone is usually on vibrate.
- Missed reminders fire if at most 30 minutes late, otherwise they're dropped.
- Takeover waits for calls to end.
- Compose, Kotlin, Room.

**Later changes, all at the user's request:**
- **Snooze:** originally "no snooze, only Done". Now **Reschedule** exists, locked by the Takeover countdown.
- **Nudge cooldown:** the "10-minute per-app cooldown" was replaced by "nudge on every real open, plus Still-here check-ins".
- **Message tone:** gentle messages were replaced by ruthless ones (custom messages kept). The card says "REALITY CHECK" with **Stay anyway** (quiet) and **Get me out** (bold).
- **History:** "no history in v1" was replaced by the Stats tab.
- **Notes:** secret notes exist but are **hidden** — no visible "Secret Notes" section; a lock glyph in the header switches between the regular list and the secrets-alone view, re-locking after 60 s. Secrets can't have reminders. No PIN recovery.
- **Navigation:** 6 bottom tabs → **4** (Reminders merges Plan + Recurring behind a segmented control; Setup lives behind a gear in every `ScreenHeader`).

## History (what was built, in order)

1. **Toolchain + skeleton:** CLI-only build, a signed release APK of about 1 MB, the no-INTERNET check.
2. **Engine + Gentle + Setup screen:** single-alarm scheduler, boot and time re-arming, health checks with deep links (including Huawei App launch), test button.
3. **Sticky / Nagging / escalation flag.**
4. **Takeover:** full-screen card over the lock screen, alarm tone (moved to the Engine after the EMUI paused-activity bug), call deferral, escalation.
5. **Recurring rules UI** with seeded defaults (Drink water every 90 minutes 09:00–22:00; Plan tomorrow at 21:00).
6. **Planner:** per-day list, copy the previous day (add or replace), past-time validation.
7. **App-open nudge:** accessibility service, card overlay, still-here check-ins, per-app style.
8. **Reliability:** rolling reliability log, once-a-day service-off warning, Setup red dot, launcher icon, v1.0. Under forced deep Doze, Gentle and Takeover both fired 0 s late.
9. **Blue Material 3 redesign;** multi-select delete in the planner; ruthless messages; the fix for returning from recents.
10. **Bedtime mode + Stats tab** (streak, 7-day chart, per-app today).
11. **Reschedule + planner round:**
    - Reschedule on reminders and the Takeover card
    - days of the week for recurring rules
    - calendar date picker on the Plan tab
    - Stats redesign: current, best and success tiles; a 35-day success calendar; red over-limit bars; per-day limit history
12. **Notes feature:** regular notes with optional every-N-days reminders (any strictness, pause/resume) and hidden PIN-protected secret notes; Room v6, `NoteAuth`, occurrence keys `note:<id>:<epochDay>`.
13. **This round:**
    - Notes polish: one-line note rows, Pause/Resume as a text button beside the bin, secrets shown alone after unlock (60 s session)
    - Navigation: 6 tabs → 4; Plan + Recurring merged behind a segmented control (`RemindersScreen`); Setup behind a gear in every header (`LocalSetup`, `showSetup`, back arrow on the left)
    - `ScreenHeader` gained `leading`/`trailing` slots with title-line alignment

**Not yet done:** the reboot test (a Sticky surviving a restart) was never run on the phone, and there's been no 24-hour reliability run with the log pulled afterwards.

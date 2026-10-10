# CLAUDE.md

Guidance for Claude Code working in this repo. User-facing docs are in `README.md`.

## What this is

**Habit Nudge**: a personal, never-published Android app to quit doomscrolling and build habits with reminders that are hard to ignore. It runs on one phone, a **Huawei Y7p (ART-L29), Android 10 / API 29, EMUI 10.1, arm64, ~4 GB RAM, 720×1560 (2× density, so 360 dp wide), no Google Play Services**, sideloaded over USB with adb.

GitHub: https://github.com/assil-bouzaine/Habit-nudge (public). Work on `dev`, merge to `main` when the user asks. Commit identity: `assil-bouzaine <bouzaineassil015@gmail.com>`.

## Hard constraints (from the user; never relax without asking)

- **No Google Play Services / Firebase / FCM.** AndroidX/Jetpack is fine.
- **No INTERNET permission, no server, no accounts.** The manifest strips `INTERNET`, and the Gradle task `verify<Variant>NoInternet` fails the build if a library adds it back.
- **No Android Studio, no emulator.** CLI toolchain only; test on the real phone.
- **Free tools and libraries only.** **Keep it light:** it's a low-end phone.
- **The app-open nudge must never *block* an app.** "Stay anyway" is always offered, though it may be delayed by a countdown.
- **Ask before taking `adb screencap` screenshots.** Screenshots have twice captured a private video call (the second time on 2026-10-10, a floating call window, *after* permission was given). Permission covers the moment it was given, not a session. Before each batch check for a call or floating window (`adb shell dumpsys audio | grep -i mode`, `adb shell dumpsys window | grep -i pip`); if in doubt, ask again. If one gets captured anyway: stop, delete the files, say so.

## Build and install

Current machine is **Linux** (zsh): JDK 17 from pacman, SDK at `~/Android/sdk` (platform-tools, `platforms;android-35`, `build-tools;35.0.0`).

```sh
export ANDROID_HOME=~/Android/sdk          # platform-tools on PATH
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk   # -r keeps the data
```

- **Pinned versions:** AGP 8.7.3, Gradle 8.11.1 (wrapper), Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.12.01 (foundation 1.7), Room 2.6.1, profileinstaller 1.4.1.
- **SDK levels:** `minSdk 28`, `targetSdk 29` (deliberate: the older rules for exact alarms, notifications and background activity starts), `compileSdk 35`.
- **Release build:** R8-minified, resources shrunk, ships the Compose baseline profile (installed by `profileinstaller`, since there's no Play Store to do it). Signed from the git-ignored `keystore.properties`; the key is `~/Android/keys/habitnudge.jks` (Windows: `%USERPROFILE%\Android\keys\`). **Losing the key means uninstalling, and so losing all data, to update.**
- **Commit messages:** write them to a scratchpad file and `git commit -F <file>`.
- **Check the APK:** `~/Android/sdk/build-tools/35.0.0/aapt2 dump permissions <apk>` (name the exact build-tools dir; a glob matches two versions).

<details><summary>Windows / PowerShell 5.1 (the previous machine; all hit for real)</summary>

- `& ".\build.ps1"` (build + install) or `-NoInstall` (log in `build\build.log`). JDK at `%USERPROFILE%\Android\jdk17`, SDK at `%USERPROFILE%\Android\sdk`.
- Don't redirect Gradle's stderr in PowerShell (each line becomes an error record); `build.ps1` uses `Start-Process -RedirectStandardOutput/-Error`.
- `cmd /c` quoting breaks on the space in the folder name; don't use it. Inline double-quoted here-strings for commit messages get split into pathspecs.
- "The process cannot access the file … classes.dex" happens intermittently (antivirus); `build.ps1` retries once.
- cmdline-tools newer than build 13114758 hand `sdkmanager` to an "Android CLI" whose JRE Smart App Control blocks. Use 13114758; feed license "y"s through a file and cmd stdin.
</details>

## Testing on the phone

There are no unit tests; everything is verified on the device.

- **Crashes:** `adb logcat -c -b crash` before, `adb logcat -d -b crash` after.
- **Alarms:** `adb shell dumpsys alarm | grep habitnudge` (`when` is epoch ms; `date -d @<seconds>`).
- **Reliability log:** `adb shell cat /sdcard/Android/data/me.habitnudge/files/reliability.log`. Logs alarm delays, app updates, and Takeover stages (`note fired`, `takeover launched/deferred`, `takeover card created`).
- **Setup screen:** "Test reminder in 1 minute" at each strictness level; "Takeover looks" previews both cards silently.
- **Deep Doze:** `adb shell dumpsys battery unplug; adb shell dumpsys deviceidle force-idle`, then `unforce` / `battery reset`.
- **Accessibility config:** `adb shell dumpsys accessibility | grep -i "Bound services"`.
- **Driving the UI without screenshots:** `adb shell uiautomator dump /sdcard/ui.xml` then grep `text=` / `content-desc=` and `bounds`; tap the centre with `adb shell input tap x y`. Delete `/sdcard/ui.xml` afterwards.
  - Bottom tabs sit at y≈1470 (x 83 / 267 / 451 / 635); the New FAB is at about (587, 1311); an editor's ✕ is at about (56, 133). Labels in the bottom bar dump as `[0,0][0,0]`, so tap them by position.
  - zsh doesn't word-split variables: use `set -- ${=b}`, not `set -- $b`. A glob that matches nothing aborts the whole command line.
  - Wait ~2 s after `am start` before tapping. **Never press Back to close a dialog** in a test: it can leave the app and the next taps land in the launcher. Close with the dialog's own button, and don't save test data.

## Architecture

Single module `app`, package `me.habitnudge`, Kotlin + Jetpack Compose (Material 3), Room, SharedPreferences.

```
HabitApp.kt          Application: db, prefs, app-wide coroutine scope, creates notification channels
MainActivity.kt      Compose host; seeds defaults; Engine.onAppStart; opens Reminders from the plan-tomorrow reminder
data/                Entities, DAOs, AppDatabase (auto-migrations), Prefs, Seed, NoteAuth (notes PIN), DiagLog (reliability log)
schedule/            Engine, Occurrences, receivers (alarm, boot/time, Done), RescheduleActivity
notify/Notifier.kt   Channels (gentle_v1, sticky_v1, nag_<id from Prefs>, takeover_v1, nudge_v1) and all notification builders
takeover/            TakeoverActivity (full-screen card), Takeover helpers, AlarmSound
nudge/               NudgeService (accessibility), NudgeCard (overlay views), Nudges (messages), Stats + Bedtime
ui/                  Tabs: Reminders (Day plan + Recurring), Nudge, Stats, Notes; Setup (HealthScreen) behind the gear.
                     Theme, Components, Editor (full-screen editors), Widgets (time wheels, pickers, app icons), Glyphs, RescheduleDialog
```

### Scheduling (`schedule/Engine.kt`)

- **One alarm only:** the earliest of the next occurrence (planned, recurring slot, note, test) or the next repeat nag / deferred Takeover retry. The next occurrence is searched a day at a time up to 8 days ahead (`firstOccurrenceAfter`).
- **`onAlarm`:** (1) fire occurrences in `(lastProcessedAt, now]` at most 30 min late, drop older ones; (2) due nags and escalations; (3) re-post active alerts whose notifications vanished; (4) set the next alarm; (5) warn once a day if the nudge service is off.
- **Alarm type:** Nagging/Takeover use `setAlarmClock` (immune to Doze); the rest use `setExactAndAllowWhileIdle`.
- **Re-arming:** on boot, app update, time / time-zone change (`SystemEventReceiver`), and every app start.
- **Occurrence keys** are stable, so nothing fires twice: `p:<id>`, `r:<id>:<epochDay>:<minute>`, `note:<id>:<epochDay>[:<minute>]`, `test:<millis>`.
- **Active alerts:** Sticky/Nagging/Takeover become an `ActiveAlert` row until Done. For Takeovers, `nextNagAt` doubles as the "retry after the call" time.
- **Concurrency:** every Engine entry point holds one `Mutex`.
- **Master pause** (`Prefs.alertsPaused`, a latch, not timed): `Engine.setPaused` stops the tone and cancels all notifications; resume re-posts open alerts. While paused, `onAlarm` advances the window without firing (missed reminders are dropped; due nags stay due and fire on resume), `onAppStart`/`deferTakeovers` don't re-post, and the service-off warning is skipped. NudgeService keeps recording stats but shows nothing. A Takeover already on screen stays until Done. Test/preview buttons toast instead.
- **Reschedule ("Remind me later")** is on every reminder notification and on the Takeover card (locked by the Done countdown there). It closes the alert and adds a `PlannedReminder` with the same message, strictness and opens-planner flag (`Engine.rescheduleReminder`).

### Strictness levels

| Level | How it alerts | Done |
|---|---|---|
| Gentle | Normal notification on `gentle_v1` (follows the ringer) | none |
| Sticky | Ongoing heads-up (follows the ringer) | Done action |
| Nagging | Re-posted every N min on the Nagging channel: alarm stream, so it sounds on vibrate, with the chosen alarm sound | Done; becomes a Takeover after N ignored nags |
| Takeover | Full-screen-intent notification + direct `startActivity`; alarm tone and vibration for at most 2 min | Done after an optional countdown |

**Takeover:**
- **Launching:** works from the background because the app holds "display over other apps" (an Android 10 exemption).
- **Tone:** started by the Engine, not the activity, because EMUI keeps the card *paused* over the lock screen.
- **Alarm sound:** picked in Setup → Sound with the phone's ringtone picker, stored as `Prefs.alarmToneUri` (null = phone default). Takeover plays it, falling back to the phone's alarm. Nagging's channel uses it too. A channel's sound is fixed, so `Notifier.setAlarmTone` deletes the Nagging channel and creates a new one under a fresh id (`Prefs.nagChannelId`, default `nag_v1`). **Always use `Notifier.nagChannel(context)`, never a hard-coded id.**
- **Leaving:** Back is blocked; Home or recents relaunches it. **Calls** (audio mode IN_CALL / IN_COMMUNICATION / RINGTONE) defer it, retried every minute.
- **Two looks, told apart at a glance** (`AlarmTakeover` / `NoteTakeover`, chosen by the `note:` key):
  - plan/recurring = **alarm**: red gradient, shaking alarm glyph, huge thin clock time, bold message; a round Done whose ring fills during the countdown, then pulses outward like an incoming call
  - notes = **sticky note**: cream desk, a tilted taped yellow note that drops in and settles, serif text; a long pill Done that fills amber during the countdown and turns ink-dark when unlocked
- **Preview:** `TakeoverActivity.preview` (Setup → Takeover looks) shows either card with a sample alert: no tone, no database row, Done/Remind me later just close it, and a real Takeover replaces it.

### App-open nudge (`nudge/NudgeService.kt`)

- **Listens to** window-state, content-changed, scrolled, clicked and focused events, **package name only** (`canRetrieveWindowContent=false`). The extra event types are needed because **EMUI sends no window-state event when returning from recents**. They're also set at runtime in `onServiceConnected`, because the system kept a stale XML config across an update. The handler returns immediately for the current app; home and keyboard package lists are cached.
- **An "open"** is arriving from home, from the lock screen, or from another app after more than 30 s. Transient surfaces (`com.android.systemui`, `android`, the EMUI share sheet `com.huawei.android.internal.app`, keyboards, our own package) don't count as leaving; home/launcher packages end the session.
- **Every open:** a centred `NudgeCard` (`TYPE_ACCESSIBILITY_OVERLAY`, light dim, touches pass through) or a notification banner. **Check-ins:** "Still in X?" every N min. Leaving removes the card and banners at once.
- **Bedtime mode** (default 23:00–06:00): a full-screen dark card on every open that doesn't fade; "Stay anyway" locked for 15 s, "Get me out" immediate; check-ins every 5 min as that card.
- **Stats** per app per day: opens, Get me out, Stay, check-ins, foreground time, that day's limit. **Removing a watched app drops it from every stat** (`Stats.summary` and `StatsScreen` filter by the watched set); the rows stay, so re-adding brings its history back. The Stats screen recomputes the summary only when a day's whole-minute total, the watched set or the limit changes.

### Notes (`ui/NotesScreen.kt`, `data/NoteAuth.kt`)

- **Kinds:** regular, and private (`isSecret`, shown as "Private"). Only regular notes get reminders.
- **Layout:** two-column staggered grid of `NoteTile` cards: first line bold as the title (3 lines max), up to 7 body lines, then the reminder (bell + "Daily · 9:00 AM", or bell-off "Paused") and the relative time. Long-press for Pause/Resume reminder and Delete; there are no per-card buttons. The full-screen editor shows the whole text.
- **Filters:** **All · Reminders** (regular notes). A **Private** pill exists but is **invisible until unlocked**.
- **Unlocking (the "knock"):** New → type the PIN as the note's entire text → Save. Nothing is saved; Private appears and opens. It locks, and the pill disappears, on tapping All/Reminders or Lock, after 60 s (`PRIVATE_OPEN_MS`), or when the app goes to the background. A wrong code just saves as a normal note, so nothing hints that Private exists. Side effect: a new note whose whole text equals the PIN can't be saved as a regular note.
- **Private notes** are made with New while Private is open. The editor's Regular/Private switch only shows then, or before any PIN exists (choosing Private sets the PIN). **PIN:** SHA-256 in `Prefs.notesAuthPin`; **no recovery**.
- **Reminders:** every N days, once at a set time, or N times a day **spread evenly** over a From–Until window with both ends included (`Occurrences.evenSlots`: 7:00–22:00 × 5 → 7:00, 10:45, 14:30, 18:15, 22:00; the editor previews them). Any strictness and its options; `ReminderConfig.isPaused`. `Engine.advanceNoteReminder` rolls `nextReminderEpochDay` forward after the day's last slot. A past day rolls forward on the next schedule computation, and the editor starts new reminders on the next *future* slot (a past slot would be dropped as late and never start). Deleting a note removes its reminder.

### Data (Room, schemas exported to `app/schemas/`)

| Version | Change |
|---|---|
| v1 | planned_reminder, recurring_rule, active_alert |
| v2 | nudge_app, nudge_message |
| v3 | nudge_app.cooldownMin → checkInMin (rename + set 15) |
| v4 | app_day_stat |
| v5 | recurring_rule.daysMask (bit 0 = Mon … bit 6 = Sun, 127 = every day), app_day_stat.limitMin, planned_reminder.opensPlanner |
| v6 | note (content, isSecret, timestamps, optional embedded reminder_config: intervalDays, timeOfDay, isPaused, nextReminderEpochDay, style) |
| v7 | reminder_config gains timesPerDay, windowStartMin, windowEndMin |
| v8 | data-only: clear v7's backfilled window columns from reminder-less notes (they broke Room's all-null check for the optional embed and crashed every note read) |

- **Always add a migration**, normally an `AutoMigration`. **Never destructive:** the user's data lives only on the phone.
- **Seeding** happens once, tracked by `Prefs` flags (`seeded`, `seededNudge`, `seededRuthless`).
- **Pruned on app start:** planned reminders older than 30 days, stats older than 90 days.
- **Reliability log** (`DiagLog`) appends a line per event and trims to the newest 400 once the file passes ~48 KB.

### UI conventions

- **Colours:** brand blue `#1877F2` (`BrandBlue`, `ui/Theme.kt`), light and dark schemes. Strictness: Gentle green, Sticky blue, Nagging amber, Takeover red, always with a text label too.
- **Components** (`ui/Components.kt`):
  - `ScreenHeader`: headline title + one-line subtitle; `leading` slot (back arrow), `trailing` slot (e.g. the Notes Lock button), then the pause bell and the Setup gear (red dot on problems; `showGear = false` hides it)
  - `ListRow` for lists, `SectionCard` for settings groups, `SectionLabel`, `EmptyState` (icon + title + body)
  - `SegmentedControl`: the only segmented/radio-style switch (screen switches, AM/PM, note kind, strictness). The chosen option sits on the lighter tone in both themes.
- **Editors:** new/edit reminder, recurring rule and note use the full-screen `EditorScreen` (`ui/Editor.kt`): ✕ / title / Save, a big borderless `MessageField`, then `TimeRow`, `StepperRow` (−/+), `SwitchRow` under `FormSection` labels. No number text fields in editors.
- **Strictness picking:** always `StrictnessPicker` (`ui/Widgets.kt`): a 4-way `SegmentedControl` with `description()` underneath.
- **Time:** one 12-hour dialog with three snapping scroll wheels; hour and minute loop, AM/PM doesn't (`DigitalTimeDialog` + `Wheel`). Opened via `TimeRow` in editors or `TimeButton` in settings cards. Times are shown with `formatMinute` (locale 12-hour), never hand-formatted "09:00".
- **Plan days:** a horizontal strip of day chips from today (two weeks; more via the calendar button).
- **Buttons:** at most one filled primary action per screen; previews and secondary actions are outlined or text. Every list tab has the same extended FAB, "New".
- **Master pause:** bell icon in every header (`LocalPause`), red bell-off while paused, plus a slim red "Alerts paused — nothing will ring" banner with Resume (`PausedBanner`).
- **Icons:** only `material-icons-core`. Missing ones are drawn from Material path data in `ui/Glyphs.kt`; add new ones there. **No emoji in the UI.**
- **App icons:** `AppIconImage` / `rememberAppIcon` (`ui/Widgets.kt`) decode off the main thread and cache for the process; never call `toBitmap` in composition.
- **Charts:** follow the dataviz rules; status never by colour alone (glyph or matching legend for every status).
- **Writes** go through `app.scope`, not the composable scope, followed by `Engine.reschedule(app)` when reminders change.

## Decisions

Defaults chosen 2026-10-04: Takeover and Nagging ring on the alarm stream, Gentle and Sticky follow the ringer (the phone is usually on vibrate); missed reminders fire if at most 30 min late, else dropped; Takeover waits for calls; Compose + Kotlin + Room.

Changed later at the user's request (was → now):
- **Snooze:** only Done → **Remind me later**, locked by the Takeover countdown.
- **Nudge:** 10-minute per-app cooldown → a nudge on every real open, plus Still-here check-ins.
- **Messages:** gentle → ruthless (custom ones kept). The card says "REALITY CHECK" with **Stay anyway** (quiet) and **Get me out** (bold).
- **History:** none in v1 → the Stats tab.
- **Navigation:** 6 bottom tabs → 4 (Plan + Recurring under Reminders; Setup behind the gear).
- **Look (2026-10-10):** generic Material + emoji → custom glyphs, one segmented control, full-screen editors, the red-alarm vs sticky-note Takeovers.
- **Several-times note reminders (2026-10-10):** random times in the window → evenly spaced.
- **Private notes (2026-10-10):** hidden behind a header lock glyph with a 60 s session → briefly a visible Private filter → the **knock** (invisible until a new note with exactly the PIN is saved; closes on going back or after 1 min).

## History

1. CLI toolchain, ~1 MB signed release APK, no-INTERNET check.
2. Single-alarm Engine, Gentle, Setup/health checks with deep links (incl. Huawei App launch), test button.
3. Sticky, Nagging, escalation.
4. Takeover over the lock screen; tone moved to the Engine after the EMUI paused-activity bug; call deferral.
5. Recurring rules with seeded defaults (water every 90 min 09:00–22:00; plan tomorrow at 21:00).
6. Planner: per-day list, copy previous day, past-time validation.
7. App-open nudge: accessibility service, overlay card, check-ins, per-app style.
8. Reliability log, service-off warning, Setup red dot, launcher icon, v1.0 (deep Doze: Gentle and Takeover fired 0 s late).
9. Blue Material 3 redesign, planner multi-select delete, ruthless messages, recents fix.
10. Bedtime mode and the Stats tab.
11. Reschedule, weekdays for recurring rules, calendar date picker, Stats redesign (streak tiles, 35-day calendar, over-limit bars).
12. Notes with every-N-days reminders and PIN-protected secret notes (Room v6).
13. Notes polish, 6 → 4 tabs, Setup behind the gear, `ScreenHeader` slots.
14. UI de-slop: glyphs instead of emoji, one segmented control and strictness picker, day-chip strip, the two Takeover looks with previews.
15. Full-screen editors, scroll-wheel time picker, custom alarm sound, evenly spaced note reminders.
16. Notes grid with filters and long-press menu; Private opened by the PIN knock.
17. Performance pass: off-main-thread cached app icons, day-by-day next-alarm search, append-only reliability log, fewer Stats recomputes, profileinstaller, unused-code cleanup.

**Not yet verified on the phone:** a Sticky surviving a reboot; a 24-hour reliability run with the log pulled; the custom alarm sound end to end; the note-style Takeover; the PIN knock.

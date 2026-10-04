# Habit Nudge

A small, offline Android app to quit doomscrolling and build better habits, using reminders that are hard to ignore.

Built for personal use on a Huawei Y7p (EMUI 10, Android 10, no Google Play Services). It is not published on any store: you build it on a PC and install it over USB.

## Why it exists

Ordinary reminders are easy to swipe away, and blocker apps get uninstalled the first time they annoy you. Habit Nudge sits in between:

- **Reminders you choose how strict to make**, from a normal notification up to a full-screen alarm that only closes when you tap Done.
- **A reality check when you open apps you want to use less** (Instagram, TikTok, …). It never blocks you, but it makes every open a conscious choice.
- **Honest numbers** on how often you open those apps and how long you stay.

Everything runs on the phone. There are no accounts, no server and no ads, and the app does not even have the internet permission.

## Features

### Day planner
- Each evening, plan tomorrow's reminders: a time, a message and a strictness level for each.
- Copy the previous day's plan with one tap (choose **Add** or **Replace**), then edit it.
- Long-press to select several reminders and delete them together.
- Opens on *today* before 17:00 and on *tomorrow* after.

### Recurring reminders
- Repeat during the day (for example "Drink water" every 90 minutes from 09:00 to 22:00), or once a day.
- Starts with two defaults: *Drink water* and an evening *Plan tomorrow's reminders*. Tapping the second one opens tomorrow's plan.

### Strictness levels (per reminder)
| Level | What happens |
|---|---|
| **Gentle** | Normal notification with sound. |
| **Sticky** | Pop-up notification that can't be swiped away or cleared until you tap **Done**. |
| **Nagging** | Like Sticky, but alerts again every N minutes until Done. Plays on the alarm volume, so it sounds even on vibrate. Can turn into a Takeover after N ignored alerts. |
| **Takeover** | Full-screen card on top of whatever app you're in. Wakes the phone like an alarm, rings the alarm tone (up to 2 minutes), and blocks Back. Optionally, Done unlocks only after a countdown. If you're in a call, it waits until the call ends. |

### App-open nudge
- Pick apps from your installed apps. Every time you **open** one, a card appears in the middle of the screen with a blunt message, for example *"{app} again? You said you'd stop. Prove it."*
- **Get me out** goes to the home screen. **Stay anyway** closes the card.
- "Opening" means arriving from the home screen, a locked screen, or another app after more than 30 seconds. The notification shade, keyboard and share sheets don't count as leaving.
- While you stay, a **"Still in Instagram?"** check-in arrives every N minutes (per app, default 15).
- You can edit the messages (shown in turn; `{app}` becomes the app's name), choose a card or a notification banner per app, and set how long the card stays.
- **Bedtime mode** (default 23:00–06:00): a full-screen dark card on every open, with *Stay anyway* locked for 15 seconds, and check-ins every 5 minutes as that same card.

### Stats
- For each watched app and day: opens, time spent, *Get me out* and *Stay anyway* taps.
- A streak of days with total watched-app time under a daily limit (default 60 minutes).
- A 7-day bar chart with the limit line. Data is kept for 90 days, on the phone only.

### Setup & health
- Shows whether everything the app depends on is switched on, each with a button to the right settings page: notifications and their categories, display over other apps, the accessibility service, battery optimization, Huawei's *App launch*, alarm volume.
- **Test reminder in 1 minute** at any strictness level, so you can lock the phone and confirm it fires.
- A **reliability log** of when each alarm was due and when it actually fired. Entries more than a minute late are flagged LATE.

## Privacy

- **No internet:** the manifest strips the `INTERNET` permission, and the build fails if any library adds it back.
- **What the accessibility service sees:** it only uses the package name of the app in front (for example `com.instagram.android`). `canRetrieveWindowContent` is off, so it cannot read what's on your screen.
- **Where data lives:** everything (reminders, settings, stats) stays in the app's private storage on the phone.

## Requirements

**Phone**
- Android 9 or newer (minSdk 28). It was built and tested on Android 10 / EMUI 10.1, and Google Play Services are not needed.

**PC (Windows), command-line toolchain only; no Android Studio needed**
- [Temurin JDK 17](https://adoptium.net/)
- Android SDK [command-line tools](https://developer.android.com/studio#command-line-tools-only), plus `platform-tools`, `platforms;android-35` and `build-tools;35.0.0`
- The Gradle wrapper is included (Gradle 8.11.1), so you don't install Gradle yourself.

Pinned versions: AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, Room 2.6.1.

## Setup on a new PC

1. **Install the JDK and SDK** somewhere without spaces in the path, for example `%USERPROFILE%\Android\jdk17` and `%USERPROFILE%\Android\sdk`. Then set the user environment variables:
   ```
   JAVA_HOME    = %USERPROFILE%\Android\jdk17
   ANDROID_HOME = %USERPROFILE%\Android\sdk
   Path        += %JAVA_HOME%\bin; %ANDROID_HOME%\platform-tools; %ANDROID_HOME%\cmdline-tools\latest\bin
   ```

2. **Install the SDK packages** (from a fresh terminal):
   ```
   sdkmanager --licenses
   sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
   ```
   > Command-line tools newer than build `13114758` hand `sdkmanager` over to a new "Android CLI" that downloads its own Java. On Windows with Smart App Control, that Java can get blocked. If `sdkmanager` fails with an *Application Control policy* error, use [`commandlinetools-win-13114758_latest.zip`](https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip) instead.

3. **Point Gradle at the SDK.** Create `local.properties` in the project root (it is git-ignored):
   ```
   sdk.dir=C\:\\Users\\<you>\\Android\\sdk
   ```

4. **Create a signing key** (once). Release builds are signed with your own key:
   ```
   keytool -genkeypair -keystore %USERPROFILE%\Android\keys\habitnudge.jks -storetype PKCS12 ^
     -alias habitnudge -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=Habit Nudge personal"
   ```
   Then create `keystore.properties` in the project root (git-ignored):
   ```
   storeFile=C:/Users/<you>/Android/keys/habitnudge.jks
   storePassword=<password>
   keyAlias=habitnudge
   keyPassword=<password>
   ```
   > **Back up the `.jks` file and its password.** Android only accepts an update signed with the same key. If you lose it, you must uninstall the app, which deletes its data, before installing a new build.

## Build and install

1. **Prepare the phone (once):**
   - Settings → About phone → tap **Build number** 7 times.
   - Settings → System → Developer options → turn on **USB debugging**. On Huawei, also turn on **Allow ADB debugging in charge only mode**.
   - Plug in the USB cable, choose **Transfer files**, and accept the *Allow USB debugging?* prompt.
   - Check that `adb devices` lists the phone.

2. **Build and install in one step:**
   ```powershell
   & ".\build.ps1"            # builds the release APK and installs it over adb
   & ".\build.ps1" -NoInstall # build only
   ```
   The APK ends up at `app\build\outputs\apk\release\app-release.apk` (about 2 MB), and the full build log at `build\build.log`.

3. **Or do it by hand:**
   ```
   gradlew.bat assembleRelease
   adb install -r app\build\outputs\apk\release\app-release.apk
   ```

## First run on the phone

Open the app, go to the **Setup** tab, and fix every card marked with a warning:

1. **Notifications**, and keep the Sticky, Nagging and Takeover categories set to pop up.
2. **Banners and lock screen notifications** (EMUI). The app can't read these settings, so tick *Done* once you've set them.
3. **Display over other apps**, needed for Takeover.
4. **Battery optimization off.**
5. **Huawei App launch**: find Habit Nudge, switch off *Manage automatically*, and turn on Auto-launch, Secondary launch and Run in background. Tick *Done*. Without this, EMUI kills the app in the background and reminders stop.
6. **Accessibility → Habit Nudge app-open nudge** on, for the app-open nudge.
7. **Alarm volume** above zero, because Nagging and Takeover use it.

Then use **Test reminder in 1 minute** and lock the phone to confirm reminders arrive.

## Troubleshooting

| Problem | Fix |
|---|---|
| Reminders stop or arrive late | Re-check the Setup tab, especially *App launch* and *Battery optimization*. Look at the reliability log on the Setup tab. |
| App-open nudges stopped | EMUI sometimes switches the accessibility service off. The Setup tab shows a red dot, and the app posts a once-a-day warning. Turn it back on in Accessibility settings. |
| Build fails with *"The process cannot access the file … classes.dex"* | Windows (usually antivirus) still has the previous build's file open. `build.ps1` already retries once; if it keeps happening, run it again. |
| `adb devices` is empty | Re-plug the cable, choose *Transfer files*, accept the debugging prompt. On some PCs, Huawei's USB driver from HiSuite is needed. |
| Want to inspect the reliability log on the PC | `adb pull /sdcard/Android/data/me.habitnudge/files/reliability.log` |

## Project layout

```
app/src/main/java/me/habitnudge/
├── HabitApp.kt, MainActivity.kt
├── data/       Room database (entities, DAOs, migrations), preferences, seed data, reliability log
├── schedule/   Engine: one exact alarm for the next due item; boot/time-change/Done receivers
├── notify/     Notification channels and builders
├── takeover/   Full-screen Takeover activity and alarm sound
├── nudge/      Accessibility service, nudge card overlay, bedtime mode, stats recording
└── ui/         Compose screens (Plan, Recurring, Nudge, Stats, Setup), theme, shared components
app/schemas/    Exported Room schemas (used for automatic migrations)
build.ps1       Build + install helper
```

**How scheduling works:** the app keeps a single exact alarm set for the next thing that's due, whether that's a planned reminder, a recurring slot or a repeat nag. When the alarm fires, the app handles everything due since the last run, fires anything less than 30 minutes late, drops anything older, and sets the next alarm. Nagging and Takeover use `setAlarmClock` so Doze can't delay them. The alarm is re-armed after a reboot, an app update, a clock or time-zone change, and whenever the app is opened.

The app targets Android 10 (API 29) on purpose. It only runs on one sideloaded phone, and this keeps the older, simpler rules for exact alarms, notifications and background activity starts.

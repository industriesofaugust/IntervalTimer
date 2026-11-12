# Dialed — notes for Claude

Workout interval timer: a phone app and a Wear OS watch app that stay in step.
The app is called "Dialed" (renamed from "Interval Timer" on 2026-11-10); the
folder, the GitHub repo and the package keep the old name on purpose.
Package `com.august.intervaltimer` (both apps). Kotlin, Compose, Room,
DataStore, Wear Compose Material 3, Health Services, Health Connect.

**Start here:** `docs/DESIGN.md` is the design source of truth,
`docs/RELEASE.md` is how a build reaches Google Play and what is left before
the first submission, and `store/` holds the Play listing text, privacy
policy, console answers and graphics.

## Where things are

- `core` — plain Kotlin shared by both apps: `timer/TimerEngine.kt` (the
  countdown), `model/` (intervals, template edits, starters), `sync/` (wire
  formats for templates, results and the live link), `design/`, `health/`.
- `app` — the phone. `workout/PhoneWorkout.kt` owns the running workout for
  the whole process; `workout/PhoneWorkoutService.kt` is the foreground
  service and notification that keep it alive; `workout/CuePlayer.kt` plays
  beeps, vibration and the optional voice. `data/PhoneLiveLink.kt` is the
  phone's end of the live link. Screens are in `ui/`.
- `wear` — the watch. `workout/WorkoutSession.kt` and `WorkoutService.kt` are
  the watch's equivalents; `metrics/WorkoutMetricsRecorder.kt` reads sensors.

## Rules for this repo

- Commit as `August <industriesofaugust@gmail.com>`. Keep the owner's
  personal name, personal email and device serials out of anything committed.
  Commit with `git commit -F <file>` (quotes break `-m`).
- Remote: `github.com/industriesofaugust/IntervalTimer` (private), branch
  `main`. Commit, push, tag and install only when asked.
- Never commit `keystore.properties` or `*.jks` (both git-ignored).
- **Never test deletes, or anything else destructive, on the owner's real
  data.** On 2026-10-16 a swipe-to-delete test on the real phone removed 17
  workouts from the app and from Health Connect. Use the emulators, or the
  debug build, which is a separate app (`.test`) with its own empty data.
- **Never send taps or keys to the real phone unless Dialed is the app
  in front**, and prefer the emulators. On 2026-11-10 taps meant for the app
  landed in another app the owner was using. Never send `KEYCODE_BACK` to it.
- **Never use `adb shell monkey` on the real phone or watch, not even to open
  the app.** It switches auto-rotate on as a side effect. On 2026-11-10 that
  left the owner's watch flipping its display upside down when raised (the
  watch is worn crown-left, which needs the rotation locked at 180°). Open the
  app with `adb shell am start -n com.august.intervaltimer/.MainActivity`,
  which changes nothing. The undo is
  `adb shell cmd window user-rotation lock 2` on the watch (0 on a watch worn
  crown-right).
- Ask before deleting data, granting permissions or anything destructive.
  Pause device automation during calls. Stay out of the notification shade
  and other private screens on the real phone.
- The owner is a beginner developer: explain plainly and keep code simple,
  with comments that say why.

## Build and test

- `JAVA_HOME` = `C:\Program Files\Android\Android Studio\jbr`, then
  `.\gradlew.bat assembleRelease testDebugUnitTest :app:lintRelease :wear:lintRelease`.
- `.\scripts\install.ps1` builds the optimised apps and installs them on
  **every** connected device, emulators included. `.\scripts\bundle.ps1`
  builds the signed Play bundles.
- Toolchain is pinned by the owner's Android Studio (2025.1.2): AGP 8.12.3,
  Gradle 8.13, Kotlin 2.2.21, compile SDK 36. Phone targets 36, watch 35
  (Play's minimums since August 2026). Wear Compose 1.7 and Health Connect
  alphas need compile SDK 37, so they wait for a Studio update.
- Parallel Gradle runs leave daemons locking `core/.../classes.jar`; run
  `gradlew --stop`, but never while another build is running.

## Devices

- Emulators: `Medium_Phone_API_36.0` and `Pixel_Watch` (API 37). They are
  paired, so a phone workout links to the watch emulator and gets synthetic
  heart rate. `adb shell uiautomator dump` reads Compose text reliably; it
  fails while a notification countdown is on screen (use a screenshot).
- Real devices: a Pixel phone over USB and a Pixel Watch 2 over Wi-Fi adb.
  The watch drops adb often and sometimes needs pairing again with a code.
  Its heart-rate sensor is probably faulty hardware, so a missing heart rate
  in a test may be the watch, not the app.

## Open items

- Everything in "Before the first submission" in `docs/RELEASE.md`.
- Not yet verified on real devices: the background workout and notification,
  voice cues, heart-rate zones and TIME IN ZONES on the detail page, and a
  delete carrying over to Health Connect while watching it happen.
- Known and accepted: Health Connect can end up with a duplicate if the
  phone's fallback copy was written before the watch's result arrives; the
  watch has no LOST state.
- Ideas not started: a Wear OS tile to start the last workout, a home-screen
  widget, sharing a workout summary, Strava (deferred by the owner).

# Interval Timer

A workout interval timer for Android (phone) with a Wear OS companion app planned.
Phone = build and review sessions; watch = run them.

## Current status: v0.11

Three modules:
- `app` — the phone app, three pages behind a bottom bar:
  Home (template library: hold and drag to reorder, drag onto the trash bar
  to delete; quick session; per-interval editor with spinning minute/second
  wheels for exact durations), Activity (this-week/all-time stats, full
  history, per-workout heart-rate chart), Settings (watch cue controls for
  vibration and sound with a "Test on watch" button, phone sound, Health
  Connect link, theme). Room database is the source of truth.
- `wear` — the watch app: home screen with a New builder (rounds, work,
  rest), Recents (last two workouts started), and Starters and Custom lists.
  Tap a workout for a full preview before starting. Settings hold vibration
  and sound levels (1–5, with live previews) and a screen-on toggle. The
  timer shows a progress ring with pause, skip, and an end button that
  records the intervals completed. Heart rate, steps, and calories come from
  Health Services.
- `core` — shared timer engine, interval model, and sync wire formats.
  The engine emits cue events; each device answers in its own voice.

Design language everywhere: coral = work, teal = rest, amber = get ready.

## Roadmap

- [x] Phase 1 — phone quick-build timer
- [x] Phase 2 — per-interval editing + templates saved in Room
- [x] Phase 3 — Wear OS timer with vibration cues
- [x] Phase 4 — phone→watch template sync (Wearable Data Layer)
- [x] Phase 5 — heart rate/steps/calories via Health Services + results→phone
- [x] Phase 6 — history, HR chart, Health Connect
- [x] v0.7 — watch settings/preview/create + stronger haptics; phone edit
  mode + three-page layout with bottom navigation
- [x] v0.11 — automated tests (timer engine, sync formats, database
  upgrades) and library updates
- [x] v0.10 — phone workouts saved to history, end-early summary on the
  phone, drag/duplicate in the editor, richer watch timer, exact times on
  the watch
- [x] v0.9 — watch timer runs in a foreground service (keeps counting and
  cueing with the screen off), watch-face icon to return to a workout,
  dim ambient screen; package renamed to com.august.intervaltimer; seven
  review bugs fixed
- [x] v0.8 — precise time wheels, editor keyboard fix, drag-to-reorder and
  trash, watch home redesign, watch level-bar settings, end-early button,
  phone-side watch cue controls

Possible next steps (see `IntervalTimer-Improvement-Plan.md` in the Claude
Projects folder): bug fixes from the code review, watch foreground service
with Ongoing Activity and ambient mode, saving phone workouts to history,
tests.

## Tests

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest
```

Run this before installing a new build. The core tests drive the real timer
engine on a fake clock; the app tests open old database versions with the
current code. Keep the Android Gradle Plugin at 8.12.x until Android Studio
is updated, or Studio will refuse to sync the project.

## How to run

Option A — Android Studio (easiest):
1. Open Android Studio → Open → select this folder.
2. Let Gradle sync finish (first time takes a few minutes).
3. Pick a device (emulator or your Pixel with USB/wireless debugging on) and press Run ▶.

Option B — command line:
```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat installDebug   # builds and installs to a connected device
```

## History, chart, and Health Connect (Phase 6)

The watch now records the full heart-rate curve (thinned to ≤600 points) and
sends it with each result. On the phone: "See all" on the home screen opens
History; tapping a workout opens a detail screen with full stats and a
Canvas-drawn heart-rate chart. The History screen offers a one-tap Health
Connect link — once granted, every workout (past and future) is written as an
exercise session with heart rate, steps, and calories, visible to any app the
user allows. Results with no heart data show a plain "no heart rate" note.

## Health metrics (Phase 5)

During a workout the watch records heart rate, steps, and calories through
Health Services (`ExerciseClient`), which batches sensor reads efficiently.
The first workout asks for the Body sensors and Physical activity permissions —
denying them still lets the timer run, just without metrics. Live BPM shows on
the timer; avg/max HR, steps, and calories appear on the finish screen. Each
result is sent to the phone as a data item; a manifest-registered
`ResultListenerService` stores it in Room (even if the phone app is closed)
and it appears under "Recent workouts" on the home screen.

## How template sync works (Phase 4)

The phone app publishes the template list to the Wearable Data Layer (a small
synced storage that Google Play services keeps identical on both devices) as
JSON whenever the Room database changes. The watch reads it on launch and
listens for live updates — templates appear under "Your templates" on the
watch, above the built-in starter workouts. Data items persist, so the watch
picks up the latest list the next time it connects even if it was off during
the edit. Requires both apps installed and the devices paired.

To test without real hardware: pair a phone emulator with a watch emulator
(Device Manager → ⋮ next to the Wear OS device → Pair Wearable), install the
`app` config on the phone and `wear` on the watch, then save a template on
the phone.

## Running the watch app

Wear OS emulator (one-time setup): Android Studio → Device Manager (phone+watch
icon in the right sidebar) → "+" → Create Virtual Device → Wear OS → "Wear OS
Large Round" → pick the recommended system image (it downloads ~1.3 GB) →
Finish. Then choose the `wear` run configuration, select the watch device, Run.

Real Pixel Watch 2: enable Developer options on the watch (Settings → System →
About → tap Build number 7×), turn on wireless debugging, pair via
`adb pair <ip:port>` — then install the `wear` configuration to it.

## Project structure

```
core/src/main/java/com/august/intervaltimer/
  model/Interval.kt        # Interval data class + quick-build session generator
  timer/TimerViewModel.kt  # shared countdown engine: ticking, cue events, pause/skip
  timer/SoundPlayer.kt     # beep cues via ToneGenerator (used by the phone)

app/src/main/java/com/august/intervaltimer/
  MainActivity.kt          # phone entry point + navigation graph
  TimerApp.kt              # Application class: database + repository singletons
  data/                    # Room entities, DAO, database, repository
  ui/LibraryScreen.kt      # home: template library, drag-to-reorder and trash
  ui/EditorScreen.kt       # per-interval template editor
  ui/DurationPickerDialog.kt # minute/second wheels for exact durations
  ui/QuickBuildScreen.kt   # rounds/work/rest steppers
  ui/TimerScreen.kt        # progress ring, controls, finished summary
  ui/ActivityScreen.kt     # stats and workout history
  ui/PhoneSettingsScreen.kt # watch cues, phone sound, Health Connect, theme
  ui/theme/                # colors, typography, Material 3 theme

wear/src/main/java/com/august/intervaltimer/
  MainActivity.kt          # watch entry point + swipe-dismissable navigation
  haptics/HapticsPlayer.kt # vibration patterns, scaled by level (1–5)
  settings/WatchSettings.kt # DataStore: cue levels, screen-on, recents
  sync/PhoneListenerService.kt # applies phone-sent settings, runs test sequence
  ui/TemplateListScreen.kt # home: New, Settings, Recents, Starters, Custom
  ui/TemplatePickScreens.kt # Starters and Custom lists
  ui/PreviewScreen.kt      # full interval list before starting
  ui/CreateScreen.kt       # quick builder (rounds, work, rest)
  ui/SettingsScreen.kt     # vibration and sound level bars, screen-on
  ui/WearTimerScreen.kt    # round-screen timer: ring, countdown, end button
  ui/WearTheme.kt          # watch color scheme (always dark)
```

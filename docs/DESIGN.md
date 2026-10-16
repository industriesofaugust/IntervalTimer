# Design: the "Dial" style

Agreed with August in a design workshop on 2026-10-12/13. This is the
reference for how both apps should look and behave. "Minimal" is the earlier
look, kept as a switchable style; Dial is the default.

## Principles

- Focus on completing the current interval. Fewer distractions beats more data.
- Stopwatch-instrument feel: a segmented ring, tick marks, monospaced numerals,
  capital labels.
- Coral = work, teal = rest, amber = get ready.

## Session ring and strip

- One arc per interval, sized by duration.
- The ring **lights up as you go**: finished intervals are lit in their color,
  the current one fills clockwise, what is still to come is grey (work a
  slightly lighter grey than rest, so the remaining shape is visible).
- Ended early: unfinished intervals stay grey.
- Tick marks stay, on phone and watch.
- The same session as a flat coral/teal bar (the "strip") appears on template
  cards, the editor, history cards, and the workout detail page.

## Text

- Capitals for labels and for the exercise name **on the timer**
  (`WORK`, `SQUATS`, `NEXT REST 0:20`). Template names are normal case
  everywhere else (`Leg day`).
- No leading zeros on counts (`5 INTERVALS`). The timer label is just `WORK` or `REST`, with no count, on phone and watch, to keep attention on the present.
- Countdown shows bare seconds by default, even over a minute (1:30 is `90`).
  A setting switches it to `1:30`. All other durations use `0:24`.
- JetBrains Mono for numerals and labels, Space Grotesk for titles.

## Timer screens

- Work: label, countdown, exercise name, then a quiet `NEXT REST 0:20` line.
  `LAST ONE` when nothing follows.
- Rest and get-ready: label, countdown, then `UP NEXT`, the next exercise in
  its own color, and its length.
- Finished: the ring, total time, intervals done, heart rate, steps, calories.
- Phone: no elapsed/remaining row. A heart-rate and calories row will appear
  there once the watch live link exists.

## Watch controls

- Clean screen. Tap anywhere to show pause, skip, and end for a few seconds.
- End is press-and-hold.
- Swiping out of the timer is disabled during a workout. The crown still goes
  to the watch face, and the workout keeps running.
- New workout keeps three rows, each with its own − and +.

## Phone screens

- Home: wordmark, quick session, template cards with strip and
  `5 INTERVALS · 2:40`. Text-only bottom bar.
- Editor: see "Template builder" below (replaced the two-row cards in v0.13).
- Activity: first tile cycles `TODAY` / `THIS WEEK` / `THIS MONTH` and drives
  the bar chart. Second tile is `DAYS OFF`: days since the last workout, with
  its date (`--` when there is no history). History always lists everything.
- Workout detail, in this order: title and date, strip, six tiles (time, work,
  done, avg ♥, max ♥, kcal), heart-rate chart, steps, delete. The chart has
  solid dark coral/teal bands per interval with a bright cap, a dotted average
  line, and a labelled max point.
- Settings: style picker, countdown format, watch cues as five-step bars,
  test on watch, phone sound, Health Connect, theme.

## Phone navigation and spacing (v0.13)

- Home, Activity and Settings are three pages you can swipe between. The
  bottom bar still works and follows the swipe. Back from Activity or
  Settings goes to Home first.
- Page titles sit just under the status bar (about 12dp gap), not 2 bars lower.

## Template builder (v0.13)

Goal: a typical workout takes seconds to set up; per-interval detail is
there when wanted, never required. Templates are still a flat list.

- Pinned header: back, template name (placeholder is a generated name such as
  `8× 0:40/0:20`, used if left blank), `SAVE` always visible, even with the
  keyboard up. Under it the live strip and `15 INTERVALS · 7:40`.
- New templates open already filled with 8 rounds of 0:40 work / 0:20 rest,
  with starter chips `TABATA` `HIIT` `STRENGTH` until the list is edited.
- Pattern tiles: `ROUNDS`, `WORK`, `REST`, each with − and + (hold to
  repeat) and, for times, tap the value for the wheels. They act on the whole
  list: rounds adds or removes rest+work pairs at the end; WORK/REST set
  every interval of that type (`MIXED` when they differ).
- Interval rows are one line (~52dp): coloured edge, number, editable name,
  duration, drag handle. Keyboard Next jumps to the next work interval's
  name, so a circuit is named in one typing run.
- Tap a row to open its details below it (one at a time): work/rest, − time
  +, duplicate, delete.
- Drag the handle to reorder; the list scrolls by itself near the top and
  bottom edges.
- Footer: `+ WORK`, `+ REST`, `REPEAT ALL` (appends another copy of the
  whole list after a rest).
- Bottom bar: total and `SAVE & START`.
- Names that were generated (`Work 3 of 8`, `Rest`) renumber themselves when
  the structure changes; names you typed are kept.

## Watch, version 2 (v0.13)

Built with Wear Compose Material 3. One look for every menu screen; the
Style setting (Dial / Minimal) only changes the timer face.

- Black background (OLED), colour from the palette on surfaces: coral
  primary, teal secondary, amber tertiary, warm dark containers.
- One button family: full-width buttons 52dp high, round icon buttons 48dp
  (pause 60dp), the main action of a screen is an edge button hugging the
  bottom of the round screen. Labels are capitals in JetBrains Mono; names
  are normal case in Space Grotesk.
- The clock (TimeText) at the top of every screen except the timer.
- Home: Resume (only while a workout runs), the last two workouts as cards
  (name, `15 INTERVALS · 3:50`, strip), then `WORKOUTS`, `NEW`, `SETTINGS`.
- Workouts: one list, `MINE` (from the phone) then `STARTERS`.
- Preview: the ring with name, count, total and a `START` button inside it
  (tappable straight away), then the interval list, with `START` again as
  the edge button at the end.
- New: three rows (rounds, work, rest) with − and +, tap a time for a
  minutes:seconds picker, total, a `SAVE TO PHONE` switch, `START` as the
  edge button.
- Settings: Vibrate and Sound switches, each with a 5-step slider when on;
  Keep screen on; Countdown format; Timer face.
- Timer: unchanged layout (see Timer screens and Watch controls). The
  controls fade in and out; End is press-and-hold in both faces.
- Done on the finish screen goes back to Home.
- Screens appear with no pop-in: settings and templates are loaded before
  the first frame and kept in memory.

## Live link (v0.14)

Starting a workout on the phone also runs it on the watch.

- Any phone start (template play, quick session, SAVE & START) asks the
  watch to join. A setting on the phone, `USE WATCH` (default on), turns
  this off.
- The watch runs the same session in step with the phone: its own timer,
  sensors recording, and its cues (vibration/sound per the watch settings).
  It shows as an ongoing workout; opening the app shows the normal timer.
- Pause, resume, skip and end work from either device and apply to both.
- Under the phone ring: a quiet row `♥ 128 · 45 KCAL`, updated every few
  seconds while the watch sends data; hidden when there is none.
- One history entry per workout: the watch's result (with the heart-rate
  chart) when the watch joined, otherwise the phone's own as before.
- If the watch is out of reach, busy with its own workout, or drops out,
  the phone carries on alone exactly as before.
Link experience (v0.15):

- Phone timer tag beside the session name: `WATCH` (lit) / `CONNECTING`
  (pulsing) / `NO WATCH` (tap to retry) / `LOST` (no data for 20 s; numbers
  fade). Hidden when `USE WATCH` is off.
- Home and Quick session show `WATCH READY` / `NO WATCH` before starting.
- `WHEN WATCH JOINS` setting for the phone's own cues: `QUIET` (default) /
  `SOUND ONLY` / `BOTH`; only applies while the tag says `WATCH`.
- Watch-started workouts never pull the phone in by themselves. The watch's
  link chip in the controls overlay (`SYNC PHONE` → `SENT…` → `PHONE ●`;
  on the Minimal face the phone glyph itself) asks the phone, which shows
  "Workout on your watch — tap to follow" (or a `WORKOUT ON WATCH · FOLLOW`
  banner on Home). Ending the watch workout first withdraws the request.

Turning sync on and off (v0.16):

- Settings `WATCH LINK`: `AUTO` (watch joins every phone workout) /
  `MANUAL` (workouts start unlinked; tag shows `LINK WATCH`, tap to link) /
  `OFF` (no tag, no link). Replaces `USE WATCH` (on -> AUTO, off -> OFF).
- During a workout either device can stop syncing: tap the phone tag or
  the watch chip once (it reads `STOP SYNC?` for 3 s), tap again to confirm.
- The device the workout was started on keeps it and saves it. The other
  device steps out: it stops its copy without saving (the watch returns to
  its home screen, the phone timer closes).
- Sync can be turned back on from the device that owns the workout (phone
  tag `LINK WATCH`, watch chip `SYNC PHONE`); the other device rejoins at
  the current position.

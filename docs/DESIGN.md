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
  (`WORK 3/5`, `SQUATS`, `NEXT REST 0:20`). Template names are normal case
  everywhere else (`Leg day`).
- No leading zeros on counts (`5 INTERVALS`, `WORK 3/5`).
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
- Editor: live strip under the name; two-row interval cards with a colored
  edge; colored `+ WORK` / `+ REST`.
- Activity: first tile cycles `TODAY` / `THIS WEEK` / `THIS MONTH` and drives
  the bar chart. Second tile is `DAYS OFF`: days since the last workout, with
  its date (`--` when there is no history). History always lists everything.
- Workout detail, in this order: title and date, strip, six tiles (time, work,
  done, avg ♥, max ♥, kcal), heart-rate chart, steps, delete. The chart has
  solid dark coral/teal bands per interval with a bright cap, a dotted average
  line, and a labelled max point.
- Settings: style picker, countdown format, watch cues as five-step bars,
  test on watch, phone sound, Health Connect, theme.

## Later

- Live link: starting a workout on the phone also starts sensor recording on
  the watch, which vibrates the cues and streams heart rate and calories back.

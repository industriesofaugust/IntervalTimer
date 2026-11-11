# Releasing to Google Play

How a build gets from this folder to the Play Store, and what is still to do
before the first one. The text to paste into the Play Console is in `store/`.

## The two kinds of build

| | For your own devices | For Google Play |
|---|---|---|
| Command | `.\scripts\install.ps1` | `.\scripts\bundle.ps1` |
| Signed with | the debug key on this PC | the upload key |
| Output | installed straight onto the phone and watch | `app-release.aab` and `wear-release.aab` |
| Installs over the other kind? | No | No |

Android only installs an update over an app signed with the same key. So a
phone or watch that has the "own devices" build must have it uninstalled
before the Play build can go on (and the other way round). Uninstalling
removes that device's templates and history; workouts already written to
Health Connect stay there.

There is also the **debug build**, which Android Studio's Run button installs.
It is a separate app called "Interval Timer Test" with its own empty data, so
it can sit next to the real one and nothing done in it touches real workouts.

## The upload key

- Key: `%USERPROFILE%\keystores\intervaltimer-upload.jks`
- Its password: in `keystore.properties` in this folder. Git ignores that
  file, so it is on this PC only.
- Made on 2026-11-10 by `scripts\new-upload-key.ps1`. Never run that again.

**Back up both files somewhere off this PC.** If they are lost, Google can
reset the upload key, but it takes a support request and a few days.

When the first bundle is uploaded, accept **Play App Signing**. Google then
holds the real signing key and this one is only used to prove an upload is
yours.

## Version numbers

Both apps read their version from `gradle.properties`:

```
appVersionName=1.0.0
appVersionNumber=18
```

- `appVersionNumber` must go up by one for **every** upload, even a fix.
- `appVersionName` is what people see; change it when the release is one they
  should notice.
- Play needs a different code on every file. The phone build gets
  `appVersionNumber × 10` and the watch build that plus 1 (18 → 180 and 181).
  Nothing to do by hand.

## Making a release

1. Raise `appVersionNumber` (and `appVersionName` if wanted) in `gradle.properties`.
2. `.\scripts\bundle.ps1`. It runs the tests first and stops if one fails.
3. In the Play Console, upload `app\build\outputs\bundle\release\app-release.aab`
   to the phone track and `wear\build\outputs\bundle\release\wear-release.aab`
   to the Wear OS track of the same release.
4. Paste the release notes from `store/listing.md`.
5. Commit, then tag: `git tag v1.0.0` and `git push origin v1.0.0`.

## Before the first submission

Things only the account owner can do. `store/play-console-answers.md` has the
wording for each form.

- [ ] Back up the upload key and `keystore.properties`.
- [ ] Publish the privacy policy (`store/privacy-policy.md`) at a public web
      address and put the address in the listing and the console.
- [ ] Create the app in the Play Console (free, Health & Fitness).
- [ ] Store listing: text from `store/listing.md`, graphics from
      `store/graphics/`.
- [ ] App content forms: privacy policy, ads, app access, content rating,
      target audience, data safety, health apps, Health Connect permissions,
      foreground services (needs short screen recordings).
- [ ] Add the Wear OS form factor and upload a Wear OS screenshot.
- [ ] Internal testing first: upload both bundles, add your own account as a
      tester, install on the phone and the watch, and run a real workout.
- [ ] Closed testing: 12 testers opted in for 14 days in a row, then apply
      for production access.

## Checked before this release, and not

Checked on emulators (phone API 36, watch API 37) with the optimised builds,
2026-11-10: first run, starters, quick session, template builder, a workout
running with the app closed (notification with pause, skip and end), end
confirmation, history, swipe-to-delete with undo, the watch app targeting
API 35 (permissions, workout, heart rate, finish), and a phone workout linked
to the watch.

Checked on the real Pixel phone and Pixel Watch 2 the same day, updated in
place from 0.17: templates, history and settings kept; a watch-only workout
running its full length with the watch screen off, arriving in the phone's
history and in Health Connect; a phone workout linked to the watch, running to
its end with the app in the background, saved once. This found and fixed one
bug the emulator did not show (the "Workout complete" notification stayed
pinned).

Not checked yet: the cues, the voice and the music ducking by ear; the live
status-bar chip on Android 16; swipe-to-delete on the real phone (on purpose:
never on real data); and the items open in `CLAUDE.md` (heart-rate zones
chart, a Health Connect delete while watching).

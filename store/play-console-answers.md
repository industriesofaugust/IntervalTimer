# Play Console answers

Prepared answers for the forms under Play Console → Policy → App content,
based on what the app actually does (checked against the release build on
2026-11-10). The wording of Google's questions changes; match by meaning.

This app has more to declare than a simple one, because it has a watch app,
uses health data and runs during workouts with the screen off. Each of those
has its own form below.

## Facts the answers rest on

- No account, no server, no ads, no analytics, no crash reporting SDK.
- Neither release APK requests the `INTERNET` permission (checked with
  `aapt2 dump permissions`).
- All data (templates, workout history, settings) is stored in the apps'
  private storage on the phone and watch.
- Phone and watch sync through Google Play services' Wear OS data layer. The
  data goes between the user's own devices; the developer never receives it.
- `android:allowBackup="true"`: Android's own cloud backup may copy app data to
  the user's Google account. Google's guidance treats this as not being
  collection by the developer.
- Phone permissions: notifications, vibrate, wake lock, foreground service
  (media playback), and five Health Connect permissions (below).
- Watch permissions: body sensors, activity recognition, notifications,
  vibrate, wake lock, foreground service (health and special use).

## Privacy policy

Not published yet. The text is in `store/privacy-policy.md`. It needs a public
web address before the app can be submitted, because the app uses health
permissions. The publisher's other app, James Allen Daily (a separate,
unrelated app), has its policy published from a public GitHub repo
(`industriesofaugust/daily-james-allen-privacy`). The same approach works
here, but in a **new** public repo of its own with `store/privacy-policy.html`
as `index.html`. Do not add this policy to the James Allen repo or reuse its
address: each app needs its own page. Put the address here and in
`listing.md` once it exists.

The same text is also shown inside the app (Settings → Privacy policy, and
from Health Connect's permission screen). If `privacy-policy.md` changes, keep
`PrivacyPolicyActivity.kt` and the published page in step with it.

## App access

"All functionality is available without special access." No login.

Note for the reviewer (there is a box for this): heart rate, steps and
calories need a Wear OS watch with the watch app installed; everything else
works on a phone alone.

## Ads

"No, my app does not contain ads."

## Content rating questionnaire

- Category: All Other App Types (or Utility/Health if offered).
- Violence, sexuality, language, controlled substances, gambling: No to all.
- User-generated content shared with others: No.
- Shares the user's location: No.
- Allows purchases: No.

Expected result: Everyone / PEGI 3.

## Target audience and content

- Target age groups: 18 and over (simplest; avoids the Families policy
  requirements).
- Appeals to children: No.

## Data safety

"Collected" in this form means sent off the device to the developer or a
third party. This app sends nothing to either.

- Does your app collect or share any of the required user data types? **No.**
- Is all of the user data collected by your app encrypted in transit? Not
  applicable.
- Do you provide a way for users to request that their data is deleted? Not
  applicable (nothing is collected). Users delete workouts in the app, or all
  data by uninstalling.

Resulting label: "No data collected. No data shared with third parties."

If a reviewer questions this because of the health permissions: health data
is processed only on the user's devices and written to Health Connect on the
same phone, at the user's request. That is on-device processing, which the
form does not count as collection.

## Health apps declaration

Play Console → App content → Health apps.

- Is this a health app? Yes.
- Features: Activity and fitness (exercise or workout tracking). Nothing
  medical: no diagnosis, no treatment, no clinical decision support.
- Not a regulated medical device.

## Health Connect permissions

Play asks why each health data type is needed. Suggested wording:

| Data type | Access | Why |
|---|---|---|
| Exercise | Write | Saves each finished interval workout as an exercise session with its work, rest and pause segments, so it appears in the user's other health apps. |
| Heart rate | Write | Saves the heart-rate samples the Wear OS watch app measured during the workout, alongside that exercise session. |
| Steps | Write | Saves the steps the watch counted during the workout. |
| Total calories burned | Write | Saves the calories the watch estimated for the workout. |
| Resting heart rate | Read | Reads the most recent value only, to draw heart-rate zones on the user's workout chart. It is shown to the user and not stored or sent anywhere. |

Linking is optional and off until the user taps Link in Settings. Deleting a
workout in the app deletes its Health Connect records.

## Foreground service declarations

Play Console → App content → Foreground service permissions. Each type needs a
short description and a link to a video showing it in use (a screen recording
uploaded to YouTube as unlisted, or a Drive link, is enough).

**Phone: media playback**

- What it does: plays the workout's audio cues (countdown beeps, the tones
  for work and rest, and optional spoken exercise names) while the screen is
  off or another app, such as a music player, is in front.
- Started only when the user presses start on a workout. A notification with
  the countdown and Pause, Skip and End buttons shows the whole time. It stops
  when the workout ends.
- If it could not run: the countdown would freeze seconds after the screen
  turned off, and the user would miss every change of interval.
- Video to record: start a workout, press the home button, show the
  notification counting down and a cue being heard, pull down the
  notification and press Pause, then End.

**Watch: health**

- What it does: runs the workout timer on the watch and records heart rate,
  steps and calories with Health Services while the watch screen is off.
- Started only when the user starts a workout on the watch, or starts one on
  the phone with the watch link on. An ongoing-activity icon shows on the
  watch face. It stops when the workout ends.
- Video to record: start a workout on the watch, let the screen dim, show the
  ongoing icon on the watch face, return to the app, end the workout.

**Watch: special use**

- Subtype text in the manifest: "Interval workout timer that must keep
  counting and cueing with the screen off".
- Used only as a fallback when the user has refused the sensor permissions,
  so the "health" type is not allowed. The timer then still has to count and
  vibrate with the screen off, without recording anything.
- If Play pushes back on this one: it can be removed, at the cost that the
  watch timer would need the activity permission to run with the screen off.

## Other declarations

- News app: No.
- Financial features: None.
- Government app: No.
- Advertising ID: The app does not use an advertising ID.

## Wear OS

The watch app is part of the same Play listing, not a separate app.

1. Play Console → Test and release → Advanced settings → Form factors → add
   **Wear OS** and accept the Wear OS review terms.
2. Wear OS gets its own release tracks. Upload `wear-release.aab` to the Wear
   OS track and `app-release.aab` to the phone track, for the same kind of
   release (internal, closed, production).
3. Add at least one Wear OS screenshot (384×384) in the store listing.
4. The watch build is reviewed against the Wear OS quality guidelines, which
   can take longer than the phone review.

## Store settings

- Category: Health & Fitness.
- Contact email: industriesofaugust@gmail.com.
- Countries: all, unless there is a reason to restrict.
- Price: Free. (A free app can never be changed to paid later.)

## AI asset declaration (store listing)

Label as created with AI: the feature graphic and the 512 icon export (both
produced with an AI assistant). Not labelled: the phone and watch
screenshots, which are captures of the running app.

## Upload

- Build: `.\scripts\bundle.ps1` →
  `app\build\outputs\bundle\release\app-release.aab` and
  `wear\build\outputs\bundle\release\wear-release.aab`.
- Accept Play App Signing when prompted. The keystore in
  `%USERPROFILE%\keystores\` is then the *upload* key.
- Raise `appVersionNumber` in `gradle.properties` by one for every upload
  after the first.

## Testing track

New personal developer accounts must run a closed test with at least 12
testers opted in for 14 days in a row before production access can be
requested, and that applies to each new app. Internal testing (up to 100
testers, no review wait) is available straight away and is the quickest way
to get the Play build on your own phone and watch.

Installing the Play build on your own devices: it is signed with a different
key from the builds `scripts\install.ps1` puts on them, so Android will not
install one over the other. Uninstall the old one first, on both the phone
and the watch (this removes its data; workouts already in Health Connect
stay there).

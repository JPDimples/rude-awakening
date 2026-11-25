# Rude Awakening

A native Kotlin Android alarm clock that yells insults until you shake it awake.

No Python, no Flutter — this is the native version. It uses Android's own
`AlarmManager` for exact alarms, built-in `TextToSpeech` to yell the insults,
and the accelerometer to count shakes. No internet, no login, no account.

## What it does

- **Home screen (industrial, v1.5):** dark Midnight background by
  default, amber hazard stripe under the title, monospace clock and
  alarm times, sharp-cornered steel cards with an amber ON / dark OFF
  button (no more Switch), amber primary buttons. "All alarms are off" /
  "2 alarms set" status line. **+ Add alarm** for as many alarms as you
  want; tap a card to change its time and repeat days; ON/OFF toggles
  it; ✕ deletes it.
- **Sound (v1.4, 10 clips since v1.6): HEAVY METAL, no voices.** The
  alarm plays 30-second heavy metal clips instead of a spoken voice. The
  insults still show on screen and rotate every 15 seconds — they just
  aren't spoken any more. Clips play on the **alarm audio stream**
  (v1.6), so they ring on the alarm volume even with media muted.
  **Test metal sound** on the home screen plays the saved clip for
  10 seconds.
- **Customize (v1.4):** the Customize screen picks the metal clip (tap to
  hear it, or Shuffle all), renames the app (the name shown at the top of
  the app — the name under the home-screen icon stays "Rude Awakening",
  Android sets that at install), and changes the background: Pastel,
  Sunrise, Ocean, Midnight, Blood, Forest, Ember, Steel, Toxic (9 presets
  since v1.6), or a photo from the gallery (text goes light or dark to
  match). Shakes-to-stop and snooze length are here too.
- **Set alarm:** pick the time, pick the repeat days, it schedules
  immediately and says what happened on screen (or exactly why it
  failed, e.g. exact-alarm permission not granted).
- **TEST ALARM NOW button:** fires the alarm immediately so you can test
  shake-to-stop without waiting until morning.
- **Alarm screen:** light pastel, huge insult text, shake progress bar,
  `SHAKES: n / 20`.
- **Insults:** 91 insults supplied by John, used exactly as written. They are
  dealt from a shuffled deck — no repeats until all 91 have been used — and a
  new one appears every 15 seconds while the alarm is ringing, spoken aloud
  by the phone's text-to-speech voice.
- **Shake to stop: 20 shakes.** At 20 the alarm stops and says
  "FINALLY. You're up." — and the alarm re-arms for tomorrow.
- **Snooze: 5 minutes, +5 shakes.** Snoozing makes the next round harder:
  25 shakes, then 30, capped at 40.
- **After reboot:** if the alarm was enabled, it re-arms itself.

## Permissions to grant (first run)

- Exact alarm — Android 12+ asks for this. If the app says "NOT GRANTED",
  tap Set alarm and it opens the system settings for it.
- Notifications — Android 13+ asks on first open. Needed for the alarm
  foreground service.
- Battery optimisation off for the app helps the alarm fire on time.

## Test it on the phone (Galaxy S25+)

1. Install the APK (allow "Install unknown apps" for the app you open it from).
2. Open Rude Awakening, grant notifications.
3. Tap **TEST ALARM NOW**.
4. Shake the phone 20 times. Watch the counter climb to 20 — then silence.
5. Try Snooze once: next round needs 25 shakes.

Do not rely on it as your only alarm for anything important until you have
tested a real overnight firing.

## Build

Local (same pattern as the TapTalk manual build on this VM):

```bash
./manual-build.sh
# -> rude-awakening-native-v7.apk (debug-signed, apksigner verified)
```

GitHub Actions (`.github/workflows/build-apk.yml`) runs the same script:
it installs JDK 17, the Android SDK (platform 34, build-tools 34.0.0) and
Kotlin 1.9.24, then runs `./manual-build.sh` and uploads the APK.

## History

- **v1.7 (2026-11-25): fix cut-off alarm card, scrollable sound list, pill
  3D buttons, digital alarm clock.** John sent screenshots: the alarm
  card was cut off (the ON button sliced in half, days text missing) and
  the sound list was cut off mid-row. Root cause: the alarm list lived in
  a small weighted ScrollView viewport that couldn't fit one card at his
  font size, and the sound list was a fixed 220dp ListView trapped inside
  the page's ScrollView. Fixes: the whole home screen is now one
  ScrollView (the page scrolls, nothing is trapped in a small viewport),
  alarm cards are two rows (time + ON/✕ on top, days underneath) at 32sp
  with a 96dp minimum height, and the sound list is plain rows inside the
  Customize page's ScrollView, so all 10 clips scroll with the page.
  Buttons are now pill-shaped and 3D (layer-list: shadow strip, darker
  edge, amber/steel face, 28dp radius). The ringing screen has a live
  digital clock — the current time in big monospace amber with ticking
  seconds above WAKE UP!.
- **v1.6 (2026-11-25): 10 metal clips, 3 new backgrounds, alarm stream.**
  5 more royalty-free metal clips (Metal Child, So Metal It Hurts, True
  Metal Awaits, Death Metal Tango, Long Live Heavy Metal — CC BY / CC
  BY-SA, Jamendo via Openverse, trimmed to 30s). 3 new backgrounds:
  Ember, Steel, Toxic (9 presets total). Clips now play on the alarm
  audio stream, so they ring on the alarm volume even with media muted.
  Note: the shake buzz was already in the app since v1.0 (50ms vibrate
  on every shake, VIBRATE permission in the manifest) — not a v1.6
  addition. Compose migration: John approved it, but the background
  build work was stopped/removed before producing anything — do NOT
  re-attempt it.
- **v1.5 (2026-11-25): Customize bug fix + industrial redesign.** The
  v1.4 background change did nothing: `BackgroundHelper.apply()` set the
  background on the activity's content parent, but every screen's own
  root layout painted its pastel gradient over it, hiding the change.
  Fixed by giving each screen's root layout an id (`rootLayout`) and
  applying the background to that view directly — presets and gallery
  photos now actually show. (Cancelling the photo text-choice dialog
  also applies the photo now.) Design tightened to modern industrial:
  dark Midnight default background, amber accent, hazard-stripe bars,
  monospace times, sharp 4dp corners, steel cards with 1dp borders,
  amber primary buttons, ON/OFF buttons instead of Switches.
- **v1.4 (2026-11-25): heavy metal instead of voices + Customize.**
  John hated all the voices, so text-to-speech is gone entirely. The
  alarm now plays one of 5 royalty-free heavy metal clips (30 seconds
  each, bundled in the app) — pick one in Customize or Shuffle all.
  Customize also renames the app and changes the background (6 presets
  or a gallery photo, with light/dark text to match). Pixabay was
  blocked from this machine (403), so the clips came from Openverse /
  Freesound / Jamendo instead. Credits: “Heavy Metal Riffs – Monolith”
  by Tri-Tachyon (CC BY), “Cyberpunk Metal” by Bertsz (CC0), “Hard Rock
  Loop” by BaDoink (CC0), “Heavy Metal” by Rínon Nínqueon (CC BY-SA),
  “Metal Hunters” by Gangrel (CC BY) — all trimmed to 30s.
- **v1.3 (2026-11-25): multiple alarms + Settings.** Home screen now lists
  alarm cards (Samsung Clock style) — tap **+ Add alarm**, tap a card to
  change its time and repeat days (Sun–Sat checkboxes, per alarm), switch
  toggles it on/off, ✕ deletes it. One-time alarms switch themselves off
  after firing; repeating alarms re-arm for their next day. **Settings**
  screen lists every installed English voice — tap one to hear an insult
  in it and save it (the v1.1 male-voice guess was wrong on Samsung, whose
  voices don't say "male" in their names, so guessing is gone — John
  picks). Settings also has shakes-to-stop (10–40) and snooze length
  (5/10/15 min). Old v1.x single alarm migrates to one every-day alarm.
- **v1.2 crash fix (2026-11-25):** v1.1 would not open at all. Cause: the
  pastel background file used a bare `<gradient>` as its root element.
  That compiles fine in aapt2 but Android cannot inflate it as a drawable,
  and since it was also the theme's `windowBackground` the app crashed
  before any screen appeared. Fixed by wrapping the gradient in a
  `<shape>`. Lesson: drawable files must have a `<shape>` (or selector /
  layer-list) root — aapt2 will not catch a bad root tag. The GitHub
  Actions build was also fixed: `$HOME` in the workflow's `env:` mapping
  is passed literally, so the build script looked for a folder literally
  named `$HOME` (exit 127) — the paths are now exported in the shell.
- `flet-v1` branch: the earlier Python + Flet version, preserved as-is,
  including its 91 pre-generated voice MP3s. The native version replaces
  those with live Android TextToSpeech.
- Package: `com.johnparnham.rudeawakening`, compile/target SDK 34, min SDK 26.

# Rude Awakening

An alarm clock that yells insults until you shake it awake. Python + Flet,
built as an Android APK with GitHub Actions. Debug build only — no Play
Store, no signing.

## What it does (v1)

- **Home screen:** big live clock, alarm time picker, set/cancel alarm.
  The alarm time is saved (SharedPreferences) so it survives a restart.
- **TEST ALARM NOW button:** fires the alarm immediately so you can test
  shake-to-stop without waiting until morning.
- **Alarm screen:** full-screen dark, huge insult text, shake progress bar,
  `SHAKES: n / 20`.
- **Insults:** 91 insults supplied by John, used exactly as written. They are
  dealt from a shuffled deck — no repeats until all 91 have been used — and a
  new one appears every 15 seconds while the alarm is ringing.
- **Voice:** each insult was pre-generated as an MP3 with edge-tts
  (`en-US-GuyNeural`, +15% rate, 91/91 clips in `src/assets/voice/`), played
  through Flet Audio, over a looping alarm tone (`src/assets/alarm.wav`).
- **Shake to stop: 20 shakes.** Each shake detected by Flet's ShakeDetector
  counts as 1. At 20 the alarm stops and the home screen says
  "FINALLY. You're up." — and the alarm re-arms for tomorrow.
- **Snooze: 5 minutes, +5 shakes.** Snoozing makes the next round harder:
  25 shakes, then 30, capped at 40.
- **Wakelock:** the screen is kept awake while the alarm is ringing.

## The honest limit — read this

**V1 is a foreground alarm.** It fires reliably while the app is left open.
Android Doze can delay or kill background apps, so v1 is NOT guaranteed to
fire with the app fully closed. For v1, leave the app open overnight (and
turn off battery optimization for it if Android offers that).

A native background-service version is the v2 follow-up if v1 proves
unreliable overnight. Do not rely on v1 as your only alarm for anything
important until you have tested a real overnight firing.

## Test it on the phone (Galaxy S25+)

1. Install the APK (tap the file, allow "Install unknown apps" for the app
   you open it from, then Install).
2. Open **Rude Awakening**.
3. Tap **TEST ALARM NOW** first — do not wait for morning to find out
   whether shake works.
4. It should start yelling an insult out loud. **Shake the phone 20 times.**
   Watch the counter climb to `SHAKES: 20 / 20` — the alarm stops.
5. Try **SNOOZE**: it stops, waits 5 minutes, then fires again needing
   25 shakes.
6. Then set a real alarm time a few minutes ahead, leave the app OPEN, and
   check it fires.

## Rebuild

Push to `main` and GitHub Actions builds the APK (first build downloads the
Flutter SDK and takes ~15 minutes):

```bash
git push origin main
gh run list --repo JPDimples/rude-awakening
gh run download <run-id> --repo JPDimples/rude-awakening
```

Local checks (no phone needed):

```bash
python3 -m py_compile src/main.py src/alarm_logic.py
python3 tests/test_logic.py
```

Regenerate the voice clips (needs internet — edge-tts talks to Microsoft):

```bash
pip install edge-tts
python tools/generate_voice.py
```

## Files

- `src/main.py` — the Flet app (home screen, alarm screen, shake, audio).
- `src/alarm_logic.py` — pure logic: insult deck, shake counter, scheduling.
  No Flet imports, covered by `tests/test_logic.py`.
- `src/assets/insults.json` — the 91 insults, exactly as supplied.
- `src/assets/voice/*.mp3` — 91 pre-generated spoken insults + manifest.
- `src/assets/alarm.wav` — looping alarm tone.
- `tools/generate_voice.py` — regenerates the voice clips.
- `.github/workflows/build-apk.yml` — GitHub Actions APK build.

## Notes on the Flet version

Built against Flet **1.1.3**. Two things changed from older Flet guides:

- `page.client_storage` no longer exists — persistence uses the
  `SharedPreferences` service instead.
- `Audio` lives in the separate `flet-audio` package (`import flet_audio`),
  and `ShakeDetector` / `Wakelock` / `Audio` are all added to
  `page.services` (not `page.overlay`).

# Rude Awakening

A native Kotlin Android alarm clock that yells insults until you shake it awake.

No Python, no Flutter — this is the native version. It uses Android's own
`AlarmManager` for exact alarms, built-in `TextToSpeech` to yell the insults,
and the accelerometer to count shakes. No internet, no login, no account.

## What it does

- **Home screen:** live clock, alarm time picker, set / cancel alarm.
- **TEST ALARM NOW button:** fires the alarm immediately so you can test
  shake-to-stop without waiting until morning.
- **Alarm screen:** full-screen dark, huge insult text, shake progress bar,
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
# -> rude-awakening-native-v1.apk (debug-signed, apksigner verified)
```

GitHub Actions (`.github/workflows/build-apk.yml`) runs the same script:
it installs JDK 17, the Android SDK (platform 34, build-tools 34.0.0) and
Kotlin 1.9.24, then runs `./manual-build.sh` and uploads the APK.

## History

- `flet-v1` branch: the earlier Python + Flet version, preserved as-is,
  including its 91 pre-generated voice MP3s. The native version replaces
  those with live Android TextToSpeech.
- Package: `com.johnparnham.rudeawakening`, compile/target SDK 34, min SDK 26.

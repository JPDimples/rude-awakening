"""Logic tests for Rude Awakening — run with: python3 tests/test_logic.py

No phone, screen, or Flet needed: this only exercises src/alarm_logic.py
plus the on-disk assets (insults + generated voice clips).
"""
import json
import os
import sys
from datetime import datetime, time as dtime

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))

from alarm_logic import (  # noqa: E402
    BASE_SHAKES,
    MAX_SHAKES,
    InsultDeck,
    ShakeCounter,
    compute_next_fire,
    is_due,
    load_insults,
)

ROOT = os.path.join(os.path.dirname(__file__), "..")


def check(name, cond):
    print(("PASS" if cond else "FAIL"), "-", name)
    assert cond, name


def main():
    insults, path = load_insults()
    check("insults.json found", path is not None)
    check("91 insults loaded", len(insults) == 91)
    check("91 unique insults", len(set(insults)) == 91)
    check("insults not censored (first insult intact)",
          insults[0] == "Get your lazy ass up; the bed isn’t going to fuck you.")

    # Deck: no repeats until exhausted, then reshuffles
    deck = InsultDeck(len(insults))
    drawn = [deck.next() for _ in range(91)]
    check("deck deals all 91 without repeats", len(set(drawn)) == 91)
    check("deck reshuffles after exhaustion", deck.next() in range(91))

    # Shake counter: 20 to stop
    sc = ShakeCounter()
    check("starts at 20 required", sc.required == BASE_SHAKES == 20)
    for _ in range(19):
        check("not reached before 20 shakes", not sc.add_shake())
    check("reached exactly on shake 20", sc.add_shake())
    check("progress is 1.0 at target", sc.progress == 1.0)

    # Snooze: +5 each time, cap 40
    sc = ShakeCounter()
    check("snooze 1 -> 25", sc.snooze() == 25)
    check("snooze 2 -> 30", sc.snooze() == 30)
    check("snooze 3 -> 35", sc.snooze() == 35)
    check("snooze 4 -> 40", sc.snooze() == 40)
    check("snooze 5 -> still 40 (cap)", sc.snooze() == MAX_SHAKES == 40)
    check("snooze resets shake count", sc.done == 0)
    sc.reset()
    check("reset restores 20", sc.required == 20 and sc.done == 0)

    # Scheduling: future time today, past time tomorrow
    now = datetime(2026, 11, 25, 6, 0)
    check("07:00 alarm at 06:00 fires today",
          compute_next_fire(dtime(7, 0), now) == datetime(2026, 11, 25, 7, 0))
    now = datetime(2026, 11, 25, 8, 0)
    check("07:00 alarm at 08:00 fires tomorrow",
          compute_next_fire(dtime(7, 0), now) == datetime(2026, 11, 26, 7, 0))
    now = datetime(2026, 11, 25, 7, 0)
    check("07:00 alarm at exactly 07:00 fires tomorrow (no instant fire)",
          compute_next_fire(dtime(7, 0), now) == datetime(2026, 11, 26, 7, 0))
    check("is_due true at/after fire time",
          is_due(datetime(2026, 11, 25, 7, 0), datetime(2026, 11, 25, 7, 0, 1)))
    check("is_due false before fire time",
          not is_due(datetime(2026, 11, 25, 7, 0), datetime(2026, 11, 25, 6, 59)))

    # Voice clips: one per insult
    voice_dir = os.path.join(ROOT, "src", "assets", "voice")
    mp3s = [f for f in os.listdir(voice_dir) if f.endswith(".mp3")]
    check("91 voice MP3s generated", len(mp3s) == 91)
    check("all voice MP3s non-empty",
          all(os.path.getsize(os.path.join(voice_dir, f)) > 1000 for f in mp3s))
    with open(os.path.join(voice_dir, "manifest.json")) as f:
        manifest = json.load(f)
    check("voice manifest says 91/91", manifest["generated"] == 91)
    check("voice is GuyNeural +15%",
          manifest["voice"] == "en-US-GuyNeural" and manifest["rate"] == "+15%")

    # Alarm tone exists
    tone = os.path.join(ROOT, "src", "assets", "alarm.wav")
    check("alarm.wav exists and non-empty",
          os.path.isfile(tone) and os.path.getsize(tone) > 1000)

    print("\nALL LOGIC TESTS PASSED")


if __name__ == "__main__":
    main()

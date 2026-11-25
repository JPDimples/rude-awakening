"""Pure logic for Rude Awakening — no Flet imports, so it can be unit-tested
on any machine without a phone, a screen, or an accelerometer.

Rules (locked for v1):
- 20 shakes to stop the alarm.
- Snooze = 5 minutes, and +5 shakes required next time (25, then 30, cap 40).
- Insults are dealt from a shuffled deck: no repeats until the list is
  exhausted, then the deck is reshuffled.
- The alarm fires when now >= next scheduled fire time. When an alarm time
  is set for a time that already passed today, it is scheduled for tomorrow.
"""
import json
import random
from datetime import datetime, time as dtime, timedelta
from pathlib import Path

BASE_SHAKES = 20
SNOOZE_SHAKES_BONUS = 5
MAX_SHAKES = 40
SNOOZE_MINUTES = 5


def find_insults_file():
    """Locate insults.json in the layouts Flet uses (dev and packaged)."""
    here = Path(__file__).resolve().parent
    candidates = [
        here / "assets" / "insults.json",  # src/assets (Flet layout)
        here.parent / "assets" / "insults.json",  # project-root assets
        Path.cwd() / "assets" / "insults.json",
        Path.cwd() / "src" / "assets" / "insults.json",
    ]
    for path in candidates:
        if path.is_file():
            return path
    return None


def load_insults():
    """Load the insult list EXACTLY as supplied — no editing, no extras."""
    path = find_insults_file()
    if path is None:
        return [], None
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    return list(data["insults"]), path


def voice_file_for(assets_dir, index):
    """Path of the pre-generated voice clip for insult `index`, if it exists."""
    candidate = Path(assets_dir) / "voice" / f"{index:03d}.mp3"
    return candidate if candidate.is_file() and candidate.stat().st_size > 1000 else None


class InsultDeck:
    """Shuffled deck of insult indices — no repeats until exhausted."""

    def __init__(self, count, rng=None):
        self.count = count
        self._rng = rng or random.Random()
        self._deck = []
        self._reshuffle()

    def _reshuffle(self):
        self._deck = list(range(self.count))
        self._rng.shuffle(self._deck)

    def next(self):
        """Return the next insult index, reshuffling when the deck is empty."""
        if not self._deck:
            self._reshuffle()
        return self._deck.pop()

    def remaining(self):
        return len(self._deck)


class ShakeCounter:
    """Counts shakes toward silencing the alarm."""

    def __init__(self):
        self.required = BASE_SHAKES
        self.done = 0

    def add_shake(self):
        """Record one shake. Returns True when the target is reached."""
        self.done += 1
        return self.done >= self.required

    def snooze(self):
        """Snooze penalty: +5 shakes next time, capped at 40."""
        self.required = min(MAX_SHAKES, self.required + SNOOZE_SHAKES_BONUS)
        self.done = 0
        return self.required

    def reset(self):
        self.required = BASE_SHAKES
        self.done = 0

    @property
    def progress(self):
        if self.required <= 0:
            return 1.0
        return min(1.0, self.done / self.required)


def compute_next_fire(alarm_time, now):
    """Next datetime for `alarm_time` at/after `now`.

    If the time already passed today, schedule for tomorrow — otherwise
    setting an alarm at 20:00 for 07:00 would fire immediately.
    """
    target = datetime.combine(now.date(), alarm_time)
    if target <= now:
        target += timedelta(days=1)
    return target


def is_due(next_fire, now):
    return next_fire is not None and now >= next_fire

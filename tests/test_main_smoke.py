"""Headless smoke test: actually run main() against a stub Page.

No phone, display, or Flet client needed. The stub records what main()
builds and lets the 1-second clock loop tick a few times. Service calls
(SharedPreferences etc.) have no live client here, so they raise inside
main()'s try/except paths — exactly as designed.

Run: python3 tests/test_main_smoke.py
"""
import asyncio
import os
import re
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))

import main as app_main  # noqa: E402


class FakePage:
    def __init__(self):
        self.title = None
        self.theme_mode = None
        self.bgcolor = None
        self.horizontal_alignment = None
        self.services = []
        self.controls = []
        self.updates = 0

    def add(self, *controls):
        self.controls.extend(controls)

    def update(self):
        self.updates += 1

    def show_dialog(self, dialog):
        pass


async def run():
    page = FakePage()
    # In Flet, main() sets up the page and RETURNS — the framework keeps the
    # app (and its background tasks) alive. So main() completing is normal.
    await asyncio.wait_for(asyncio.create_task(app_main.main(page)), timeout=10)
    # Background loops (clock etc.) keep running on this loop after main returns.
    await asyncio.sleep(2.5)

    assert len(page.controls) == 2, f"expected home+alarm views, got {len(page.controls)}"
    home, alarm = page.controls
    assert home.visible is True, "home view should be visible at startup"
    assert alarm.visible is False, "alarm view should be hidden at startup"

    # find the clock text (size 64) and check it ticked
    clock = next(c for c in home.controls if getattr(c, "size", None) == 64)
    assert re.match(r"^\d{2}:\d{2}:\d{2}$", clock.value), f"clock not ticking: {clock.value!r}"
    assert page.updates >= 2, "clock loop did not call page.update()"

    # 4 services registered: prefs, wakelock, voice audio, tone audio, shake
    names = [type(s).__name__ for s in page.services]
    assert names == ["SharedPreferences", "Wakelock", "Audio", "Audio", "ShakeDetector"], names

    # alarm status + insult info present on home
    texts = [getattr(c, "value", "") for c in home.controls if hasattr(c, "value")]
    assert any("No alarm set" in t for t in texts), texts
    assert any("91 insults loaded" in t for t in texts), texts
    assert any("foreground alarm" in t for t in texts), texts

    print("SMOKE TEST PASSED: main() boots, clock ticks, home/alarm views correct,")
    print(f"  services registered: {names}, clock shows {clock.value}")


if __name__ == "__main__":
    asyncio.run(run())

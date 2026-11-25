"""Rude Awakening v1 — an alarm clock that yells insults until you shake it awake.

MVP (locked):
- Home: big clock, alarm time picker, set/cancel, TEST ALARM NOW button.
- Alarm screen: huge insult text, shake progress, shakes remaining.
- Shake to stop: 20 shakes (each ShakeDetector event = 1 shake).
- Snooze: 5 minutes, +5 shakes required next time (25, 30, ... cap 40).
- Insults: 91 supplied by John, shown in a shuffled deck (no repeats until
  exhausted), new insult every 15 seconds. Spoken via pre-generated
  edge-tts MP3s (en-US-GuyNeural, +15%) with a looping alarm tone underneath.
- Persistence: alarm time saved in SharedPreferences (Flet 1.1.3 — the old
  page.client_storage API no longer exists in this version).

HONEST LIMIT (v1): this is a FOREGROUND alarm. It fires reliably while the
app is left open. Android Doze can delay or kill background apps, so it is
NOT guaranteed to fire with the app fully closed — that needs a native
background service (v2). Leave the app open overnight for v1.
"""
import asyncio
from datetime import datetime, time as dtime, timedelta
from pathlib import Path

import flet as ft
import flet_audio as fta

from alarm_logic import (
    InsultDeck,
    ShakeCounter,
    compute_next_fire,
    is_due,
    load_insults,
)

ALARM_CHECK_INTERVAL_SECONDS = 20
INSULT_INTERVAL_SECONDS = 15
SNOOZE_MINUTES = 5


def _assets_dir():
    here = Path(__file__).resolve().parent
    for candidate in (here / "assets", here.parent / "assets"):
        if (candidate / "insults.json").is_file():
            return candidate
    return here / "assets"


async def main(page: ft.Page):
    page.title = "Rude Awakening"
    page.theme_mode = ft.ThemeMode.DARK
    page.bgcolor = ft.Colors.BLACK
    page.horizontal_alignment = ft.CrossAxisAlignment.CENTER

    insults, insults_path = load_insults()
    assets_dir = _assets_dir()
    voice_dir = assets_dir / "voice"
    voice_available = [
        (voice_dir / f"{i:03d}.mp3").is_file()
        and (voice_dir / f"{i:03d}.mp3").stat().st_size > 1000
        for i in range(len(insults))
    ]
    voice_count = sum(voice_available)
    print(f"[rude-awakening] insults: {len(insults)} from {insults_path}")
    print(f"[rude-awakening] voice clips available: {voice_count}/{len(insults)}")

    # ---- state ------------------------------------------------------------
    alarm_time = dtime(7, 0)
    alarm_enabled = False
    next_fire = None  # datetime | None
    ringing = False
    shakes = ShakeCounter()
    deck = InsultDeck(len(insults)) if insults else None
    current_insult_idx = None
    insult_task = None
    wakelock_ok = False

    # ---- services ---------------------------------------------------------
    prefs = ft.SharedPreferences()
    wakelock = ft.Wakelock()
    voice_audio = fta.Audio(
        src="voice/000.mp3",
        autoplay=False,
        volume=1.0,
        release_mode=fta.ReleaseMode.STOP,
    )
    tone_audio = fta.Audio(
        src="alarm.wav",
        autoplay=False,
        volume=0.6,
        release_mode=fta.ReleaseMode.LOOP,
    )
    page.services.append(prefs)
    page.services.append(wakelock)
    page.services.append(voice_audio)
    page.services.append(tone_audio)

    # ---- home view --------------------------------------------------------
    clock_text = ft.Text(" --:--:-- ", size=64, weight=ft.FontWeight.BOLD)
    alarm_status = ft.Text("No alarm set", size=22)
    info_text = ft.Text(
        f"{len(insults)} insults loaded"
        + (f" • {voice_count} spoken clips" if voice_count else " • VOICE MISSING — text + tone only"),
        size=14,
        color=ft.Colors.GREY_400,
    )
    done_text = ft.Text("", size=20, color=ft.Colors.GREEN_400)
    warn_text = ft.Text(
        "V1 is a foreground alarm: leave this app OPEN overnight or it may "
        "not fire. Android Doze can kill background apps.",
        size=13,
        color=ft.Colors.AMBER_400,
        text_align=ft.TextAlign.CENTER,
    )

    # ---- alarm view -------------------------------------------------------
    insult_text = ft.Text(
        "WAKE UP!",
        size=30,
        weight=ft.FontWeight.BOLD,
        color=ft.Colors.WHITE,
        text_align=ft.TextAlign.CENTER,
    )
    shake_text = ft.Text("SHAKES: 0 / 20", size=30, weight=ft.FontWeight.BOLD, color=ft.Colors.RED)
    shake_bar = ft.ProgressBar(value=0.0, width=320)
    snooze_btn = ft.Button("SNOOZE 5 MIN (+5 shakes next time)")

    home_view = ft.Column(
        [
            ft.Text("RUDE AWAKENING", size=28, weight=ft.FontWeight.BOLD, color=ft.Colors.RED),
            ft.Text("An alarm clock that insults you awake.", size=15, color=ft.Colors.GREY_400),
            clock_text,
            alarm_status,
            ft.Button("Pick alarm time", on_click=lambda e: page.show_dialog(time_picker)),
            ft.Button("Cancel alarm", on_click=lambda e: asyncio.create_task(cancel_alarm())),
            ft.Button("TEST ALARM NOW", on_click=lambda e: asyncio.create_task(fire_alarm(test=True))),
            done_text,
            info_text,
            warn_text,
        ],
        horizontal_alignment=ft.CrossAxisAlignment.CENTER,
        spacing=16,
    )
    alarm_view = ft.Column(
        [
            ft.Text("WAKE UP!", size=56, weight=ft.FontWeight.BOLD, color=ft.Colors.RED),
            insult_text,
            shake_text,
            shake_bar,
            ft.Text("SHAKE THE PHONE!", size=22, weight=ft.FontWeight.BOLD),
            snooze_btn,
            ft.Text("No off button. Shake it or suffer.", size=14, color=ft.Colors.GREY_400),
        ],
        horizontal_alignment=ft.CrossAxisAlignment.CENTER,
        spacing=22,
        visible=False,
    )
    page.add(home_view, alarm_view)

    # ---- helpers ----------------------------------------------------------
    def refresh_home():
        if alarm_enabled and next_fire is not None:
            alarm_status.value = (
                f"Alarm: {alarm_time.strftime('%H:%M')} — ON\n"
                f"Next: {next_fire.strftime('%a %H:%M')} • {shakes.required} shakes to stop"
            )
        else:
            alarm_status.value = "No alarm set"
        page.update()

    def refresh_alarm_ui():
        shake_text.value = f"SHAKES: {shakes.done} / {shakes.required}"
        shake_bar.value = shakes.progress
        snooze_btn.text = (
            f"SNOOZE 5 MIN (+5 shakes — {min(40, shakes.required + 5)} next time)"
        )
        page.update()

    async def save_alarm():
        try:
            await prefs.set("alarm_time", alarm_time.strftime("%H:%M"))
            await prefs.set("alarm_enabled", True)
        except Exception as e:  # noqa: BLE001 - prefs may be unavailable on desktop
            print(f"[rude-awakening] prefs save failed (non-fatal): {e}")

    async def clear_saved_alarm():
        try:
            await prefs.remove("alarm_time")
            await prefs.set("alarm_enabled", False)
        except Exception as e:  # noqa: BLE001
            print(f"[rude-awakening] prefs clear failed (non-fatal): {e}")

    async def set_alarm(t):
        nonlocal alarm_time, alarm_enabled, next_fire
        alarm_time = t
        alarm_enabled = True
        next_fire = compute_next_fire(alarm_time, datetime.now())
        shakes.reset()
        await save_alarm()
        refresh_home()

    async def cancel_alarm():
        nonlocal alarm_enabled, next_fire
        alarm_enabled = False
        next_fire = None
        await clear_saved_alarm()
        refresh_home()

    async def play_current_insult():
        """Show + speak the next insult from the deck."""
        nonlocal current_insult_idx
        if not insults or deck is None:
            insult_text.value = "GET UP. (insults file missing)"
            page.update()
            return
        current_insult_idx = deck.next()
        insult_text.value = insults[current_insult_idx]
        page.update()
        if voice_available[current_insult_idx]:
            try:
                voice_audio.src = f"voice/{current_insult_idx:03d}.mp3"
                voice_audio.update()
                await voice_audio.play()
            except Exception as e:  # noqa: BLE001 - voice must never kill the alarm
                print(f"[rude-awakening] voice play failed: {e}")

    async def insult_loop():
        try:
            while ringing:
                await play_current_insult()
                for _ in range(INSULT_INTERVAL_SECONDS):
                    if not ringing:
                        return
                    await asyncio.sleep(1)
        except asyncio.CancelledError:
            pass

    async def fire_alarm(test=False):
        nonlocal ringing, insult_task, wakelock_ok
        if ringing:
            return
        if not insults:
            insult_text.value = "GET UP. (insults file missing — reinstall the app)"
        ringing = True
        shakes.done = 0
        home_view.visible = False
        alarm_view.visible = True
        refresh_alarm_ui()
        try:
            await wakelock.enable()
            wakelock_ok = True
        except Exception as e:  # noqa: BLE001
            wakelock_ok = False
            print(f"[rude-awakening] wakelock failed: {e}")
        try:
            await tone_audio.play()
        except Exception as e:  # noqa: BLE001
            print(f"[rude-awakening] alarm tone failed: {e}")
        print(f"[rude-awakening] ALARM FIRING (test={test}) — {shakes.required} shakes required")
        insult_task = asyncio.create_task(insult_loop())

    async def stop_alarm():
        nonlocal ringing, next_fire, insult_task
        ringing = False
        if insult_task is not None:
            insult_task.cancel()
            insult_task = None
        try:
            await tone_audio.pause()
        except Exception:  # noqa: BLE001
            pass
        try:
            await voice_audio.pause()
        except Exception:  # noqa: BLE001
            pass
        try:
            await wakelock.disable()
        except Exception:  # noqa: BLE001
            pass
        alarm_view.visible = False
        home_view.visible = True
        # Re-arm for tomorrow at the same time; shakes reset to 20.
        if alarm_enabled:
            next_fire = compute_next_fire(alarm_time, datetime.now())
        shakes.reset()
        done_text.value = "FINALLY. You're up."
        refresh_home()

    async def snooze_alarm():
        nonlocal next_fire
        if not ringing:
            return
        ringing = False
        if insult_task is not None:
            insult_task.cancel()
            insult_task = None
        try:
            await tone_audio.pause()
            await voice_audio.pause()
            await wakelock.disable()
        except Exception:  # noqa: BLE001
            pass
        new_required = shakes.snooze()
        next_fire = datetime.now() + timedelta(minutes=SNOOZE_MINUTES)
        alarm_view.visible = False
        home_view.visible = True
        done_text.value = (
            f"Snoozed 5 min. Next time: {new_required} shakes. Coward."
        )
        print(f"[rude-awakening] snoozed — next fire {next_fire}, {new_required} shakes required")
        refresh_home()

    async def on_shake(e):
        if not ringing:
            return
        reached = shakes.add_shake()
        refresh_alarm_ui()
        if reached:
            await stop_alarm()

    snooze_btn.on_click = lambda e: asyncio.create_task(snooze_alarm())

    shake_detector = ft.ShakeDetector(
        minimum_shake_count=1,
        shake_slop_time_ms=300,
        shake_count_reset_time_ms=1000,
        shake_threshold_gravity=2.7,
        on_shake=on_shake,
    )
    page.services.append(shake_detector)

    # ---- time picker ------------------------------------------------------
    async def on_time_picked(e):
        await set_alarm(time_picker.value)

    time_picker = ft.TimePicker(
        value=dtime(7, 0),
        confirm_text="Set alarm",
        help_text="Pick your wake-up time",
        on_change=on_time_picked,
    )

    # ---- background loops -------------------------------------------------
    async def clock_loop():
        while True:
            clock_text.value = datetime.now().strftime("%H:%M:%S")
            if home_view.visible:
                page.update()
            await asyncio.sleep(1)

    async def alarm_check_loop():
        while True:
            await asyncio.sleep(ALARM_CHECK_INTERVAL_SECONDS)
            if alarm_enabled and not ringing and is_due(next_fire, datetime.now()):
                await fire_alarm()

    asyncio.create_task(clock_loop())
    asyncio.create_task(alarm_check_loop())

    # ---- restore saved alarm ----------------------------------------------
    try:
        saved = await prefs.get("alarm_time")
        enabled = await prefs.get("alarm_enabled")
        if saved and enabled:
            hh, mm = (int(x) for x in str(saved).split(":"))
            alarm_time = dtime(hh, mm)
            time_picker.value = alarm_time
            alarm_enabled = True
            next_fire = compute_next_fire(alarm_time, datetime.now())
            print(f"[rude-awakening] restored alarm {saved}, next fire {next_fire}")
    except Exception as e:  # noqa: BLE001 - fresh install / desktop has no saved alarm
        print(f"[rude-awakening] no saved alarm to restore: {e}")

    refresh_home()


if __name__ == "__main__":
    ft.run(main)

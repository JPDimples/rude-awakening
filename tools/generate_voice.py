"""Generate one MP3 per insult with edge-tts (en-US-GuyNeural, +15% rate).

Usage (from repo root):
    .venv/bin/python tools/generate_voice.py [--limit N] [--voice VOICE]

Skips files that already exist and are non-empty. Retries each insult up
to 3 times — the Bing speech endpoint times out on first connection
sometimes. Writes assets/voice/manifest.json mapping index -> filename.
"""
import argparse
import asyncio
import json
import os
import sys

import edge_tts

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS_DIR = os.path.join(ROOT, "src", "assets")
VOICE_DIR = os.path.join(ASSETS_DIR, "voice")


async def synth_one(sem, idx, text, voice, rate):
    path = os.path.join(VOICE_DIR, f"{idx:03d}.mp3")
    if os.path.exists(path) and os.path.getsize(path) > 1000:
        print(f"[{idx:03d}] skip (exists)", flush=True)
        return True
    async with sem:
        for attempt in range(1, 4):
            try:
                comm = edge_tts.Communicate(text, voice, rate=rate)
                await asyncio.wait_for(comm.save(path), timeout=60)
                if os.path.getsize(path) > 1000:
                    print(f"[{idx:03d}] ok ({os.path.getsize(path)} bytes)", flush=True)
                    return True
            except Exception as e:  # noqa: BLE001 - retry any failure
                print(f"[{idx:03d}] attempt {attempt} failed: {e}", flush=True)
                await asyncio.sleep(2 * attempt)
                if os.path.exists(path):
                    os.remove(path)
        print(f"[{idx:03d}] FAILED after 3 attempts", flush=True)
        return False


async def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--voice", default="en-US-GuyNeural")
    ap.add_argument("--rate", default="+15%")
    args = ap.parse_args()

    with open(os.path.join(ASSETS_DIR, "insults.json")) as f:
        insults = json.load(f)["insults"]
    if args.limit:
        insults = insults[: args.limit]
    os.makedirs(VOICE_DIR, exist_ok=True)

    sem = asyncio.Semaphore(3)
    results = await asyncio.gather(
        *(synth_one(sem, i, t, args.voice, args.rate) for i, t in enumerate(insults))
    )
    ok = sum(1 for r in results if r)
    manifest = {
        "voice": args.voice,
        "rate": args.rate,
        "count": len(insults),
        "generated": ok,
        "files": {i: f"{i:03d}.mp3" for i, r in enumerate(results) if r},
    }
    with open(os.path.join(VOICE_DIR, "manifest.json"), "w") as f:
        json.dump(manifest, f, indent=2)
    print(f"DONE: {ok}/{len(insults)} generated")
    return 0 if ok == len(insults) else 1


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))

#!/usr/bin/env python3
# SPDX-License-Identifier: BSD-2-Clause
"""Rebuild the original Runewarden sound set with Python's standard library only.

No sound recordings, instruments or game assets are sampled. All waveforms and note
arrangements below are original synthesis instructions dedicated to CC0 1.0.
"""

from pathlib import Path
import hashlib
import math
import struct
import wave

RATE = 22_050
ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / "src/main/resources/com/runewarden/sounds"

# Start time, frequency, decay duration and gain. Brief modal chimes grow into a
# warmer harmonic flourish as rarity increases, without imitating a game melody.
SOUNDS = {
    "white-chime": (0.280, 0.38, [(0, 1046.502, 0.260, 1)]),
    "green-chime": (0.430, 0.42, [(0, 659.255, 0.310, .85), (.095, 783.991, .310, 1)]),
    "blue-chime": (0.650, 0.46, [(0, 659.255, .380, .72), (.105, 987.767, .420, .83), (.215, 1318.510, .410, 1)]),
    "purple-sting": (0.950, 0.50, [(0, 440, .550, .65), (.085, 659.255, .550, .72), (.180, 880, .600, .88), (.310, 1174.659, .610, 1)]),
    "gold-fanfare": (1.450, 0.56, [(0, 587.330, .550, .58), (.100, 739.989, .600, .60), (.205, 880, .680, .70),
                                    (.325, 1174.659, .950, .83), (.325, 293.665, 1.060, .45), (.445, 1760, .900, .55)]),
    "quiet-tick": (0.180, 0.12, [(0, 880, .150, 1)]),
}


def bell(frequency: float, elapsed: float, duration: float) -> float:
    if elapsed < 0 or elapsed >= duration:
        return 0.0
    attack = min(1.0, elapsed / .006)
    release = min(1.0, (duration - elapsed) / .025)
    envelope = attack * attack * release * math.exp(-5.0 * elapsed / duration)
    # A soft fundamental with two inharmonic overtones gives a small glass/metal
    # character. Every sound begins and ends at zero to avoid an audible click.
    angle = 2 * math.pi * frequency * elapsed
    return envelope * (.76 * math.sin(angle) + .17 * math.sin(angle * 2.003) + .07 * math.sin(angle * 3.997))


def synthesize(duration: float, peak: float, notes: list) -> bytes:
    frames = round(duration * RATE)
    samples = []
    for frame in range(frames):
        now = frame / RATE
        sample = 0.0
        for start, frequency, decay, gain in notes:
            sample += gain * (bell(frequency, now - start, decay)
                              + .10 * bell(frequency, now - start - .072, decay * .65))
        # A final fade ensures the tail reaches zero even for the delayed overtone.
        sample *= min(1.0, max(0.0, (duration - now) / .035))
        samples.append(sample)
    maximum = max(abs(value) for value in samples) or 1
    return b"".join(struct.pack("<h", round(value / maximum * peak * 32767)) for value in samples)


def main() -> None:
    DESTINATION.mkdir(parents=True, exist_ok=True)
    total = 0
    for name, (duration, peak, notes) in SOUNDS.items():
        path = DESTINATION / f"{name}.wav"
        with wave.open(str(path), "wb") as output:
            output.setnchannels(1)
            output.setsampwidth(2)
            output.setframerate(RATE)
            output.writeframes(synthesize(duration, peak, notes))
        contents = path.read_bytes()
        total += len(contents)
        print(f"{path.name}: {len(contents):,} bytes, SHA-256 {hashlib.sha256(contents).hexdigest()}")
    print(f"Total: {total:,} bytes")


if __name__ == "__main__":
    main()

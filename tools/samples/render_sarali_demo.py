"""Render Sarali Varisai #1 from the bundled samples, the way the app plays them.

Reads audio/harmonium/<midi>.flac (exact 12-TET, A4 = 440 Hz) and applies the
same rule as HarmoniumBank.playbackFor() in the shared module: nearest
semitone sample, played at rate = target / sample frequency. Useful for
listening checks after re-running prepare_harmonium.py.

Usage:
    uv run python render_sarali_demo.py [--sa-midi 49 --sa-midi 57]
"""

import argparse
from pathlib import Path

import numpy as np
import parselmouth
import soundfile as sf
import soxr

HERE = Path(__file__).parent
AUDIO = HERE.parent.parent / "audio" / "harmonium"
OUT = HERE / "build"
SR = 44_100

# Mayamalavagowla (raga of Sarali Varisai): Swara ratios as in Swara.kt.
RATIOS = {"S": 1, "R1": 16 / 15, "G3": 5 / 4, "M1": 4 / 3, "P": 3 / 2, "D1": 8 / 5, "N3": 15 / 8, "S'": 2}
SARALI_1 = ["S", "R1", "G3", "M1", "P", "D1", "N3", "S'", "S'", "N3", "D1", "P", "M1", "G3", "R1", "S"]

NOTE_S = 0.8  # one swara per beat at 75 bpm
XFADE_S = 0.03


def midi_to_hz(m: float) -> float:
    return 440.0 * 2 ** ((m - 69) / 12)


def playback_for(hz: float) -> tuple[int, float]:
    """Mirror of HarmoniumBank.playbackFor(): (midi, rate)."""
    midi = int(round(69 + 12 * np.log2(hz / 440)))
    if not 36 <= midi <= 72:
        raise ValueError(f"{hz:.2f} Hz outside the bank")
    return midi, hz / midi_to_hz(midi)


def render_note(hz: float) -> np.ndarray:
    midi, rate = playback_for(hz)
    x, sr = sf.read(AUDIO / f"{midi}.flac", dtype="float64")
    y = soxr.resample(x, sr * rate, SR)  # resample-as-varispeed, like SoundPool rate
    n = int((NOTE_S + XFADE_S) * SR)
    y = np.pad(y, (0, max(0, n - len(y))))[:n]
    fade = int(XFADE_S * SR)
    y[:fade] *= np.linspace(0, 1, fade)
    y[-fade:] *= np.linspace(1, 0, fade)
    return y


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sa-midi", type=int, action="append", help="default: 49 (C#3) and 57 (A3)")
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    for sa_midi in args.sa_midi or [49, 57]:
        sa = midi_to_hz(sa_midi)
        step = int(NOTE_S * SR)
        audio = np.zeros(step * len(SARALI_1) + int(XFADE_S * SR))
        for i, s in enumerate(SARALI_1):
            y = render_note(sa * RATIOS[s])
            audio[i * step : i * step + len(y)] += y
        tail = int(0.25 * SR)
        audio[-tail:] *= np.linspace(1, 0, tail)
        audio = 0.8 * audio / np.max(np.abs(audio))
        path = OUT / f"sarali1_sa{sa_midi}.wav"
        sf.write(path, audio, SR, subtype="PCM_16")

        errs = []
        for i, s in enumerate(SARALI_1):
            seg = audio[int((i * NOTE_S + 0.15) * SR) : int(((i + 1) * NOTE_S - 0.1) * SR)]
            f = parselmouth.Sound(seg, SR).to_pitch_ac(time_step=0.01, pitch_floor=50, pitch_ceiling=1200)
            v = f.selected_array["frequency"]
            errs.append(1200 * np.log2(np.median(v[v > 0]) / (sa * RATIOS[s])))
        print(f"{path.relative_to(HERE)}: Sa {sa:.2f} Hz, per-note error {min(errs):+.2f}..{max(errs):+.2f} cents")


if __name__ == "__main__":
    main()

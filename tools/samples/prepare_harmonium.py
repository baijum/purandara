"""Prepare the bundled harmonium samples from the CC0 Freesound pack.

Source: "Harmonium" pack by cabled_mess (https://freesound.org/people/cabled_mess/packs/29512/),
cut from donyaquick's recording (https://freesound.org/s/330410/). CC0.
Download the pack zip from Freesound (login required) and extract it into
datasets/harmonium/.

For every semitone MIDI_LOW..MIDI_HIGH this writes audio/harmonium/<midi>.flac:
  * pitch corrected to exact 12-TET at A4 = 440 Hz (the instrument is ~+5..+17
    cents sharp), so the app can treat file <midi> as exactly that note;
  * trimmed to start at the attack and cut to CLIP_S with a short end fade;
  * loudness-normalised so all notes play at the same level;
  * 44.1 kHz, 16-bit mono FLAC.

The source file names are unreliable (sharps are unlabeled and duplicated),
so notes are identified by measuring their pitch.

Usage:
    uv run python prepare_harmonium.py [--source datasets/harmonium] [--out ../../audio/harmonium]
"""

import argparse
import sys
from pathlib import Path

import numpy as np
import parselmouth
import soundfile as sf
import soxr

HERE = Path(__file__).parent
MIDI_LOW, MIDI_HIGH = 36, 74  # C2..D5, must match HarmoniumBank in the shared module
SR = 44_100
CLIP_S = 2.5  # covers one swara at 24 bpm; the app fades out earlier for shorter notes
END_FADE_S = 0.08
TARGET_RMS_DBFS = -18.0
PEAK_LIMIT = 0.95
MAX_ERROR_CENTS = 1.0


def midi_to_hz(midi: float) -> float:
    return 440.0 * 2 ** ((midi - 69) / 12)


def median_f0(x: np.ndarray, sr: int) -> float:
    pitch = parselmouth.Sound(x, sr).to_pitch_ac(time_step=0.02, pitch_floor=50, pitch_ceiling=1200)
    f0 = pitch.selected_array["frequency"]
    return float(np.median(f0[f0 > 0]))


def load_mono(path: Path) -> tuple[np.ndarray, int]:
    x, sr = sf.read(path, dtype="float64")
    return (x if x.ndim == 1 else x.mean(axis=1)), sr


def prepare(x: np.ndarray, sr: int, midi: int) -> np.ndarray:
    # Start at the bellows attack: first sample above 2% of the peak, minus 5 ms.
    start = max(0, int(np.argmax(np.abs(x) > 0.02 * np.abs(x).max())) - int(0.005 * sr))
    x = x[start:]
    # Bellows pressure makes pitch drift slightly over a long note, so measure
    # only the portion that ends up in the clip (skipping the attack).
    f0 = median_f0(x[int(0.2 * sr) : int(CLIP_S * 1.05 * sr)], sr)
    # Varispeed retune: playing at `rate` x speed multiplies pitch by `rate`.
    rate = midi_to_hz(midi) / f0
    y = soxr.resample(x, sr * rate, SR, quality="VHQ")
    n = int(CLIP_S * SR)
    if len(y) < n:
        raise ValueError(f"midi {midi}: source too short ({len(y) / SR:.2f}s)")
    y = y[:n].copy()
    fade = int(END_FADE_S * SR)
    y[-fade:] *= np.cos(np.linspace(0, np.pi / 2, fade)) ** 2
    # Normalise on the steady part (skip attack and fade).
    body = y[int(0.2 * SR) : n - fade]
    y *= 10 ** (TARGET_RMS_DBFS / 20) / np.sqrt(np.mean(body**2))
    peak = np.max(np.abs(y))
    if peak > PEAK_LIMIT:
        y *= PEAK_LIMIT / peak
    return y


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--source", type=Path, default=HERE / "datasets" / "harmonium")
    parser.add_argument("--out", type=Path, default=HERE.parent.parent / "audio" / "harmonium")
    args = parser.parse_args()

    bank: dict[int, tuple[Path, float]] = {}
    for path in sorted(args.source.glob("*.wav")):
        x, sr = load_mono(path)
        f0 = median_f0(x, sr)
        midi = int(round(69 + 12 * np.log2(f0 / 440)))
        if midi in bank:
            print(f"error: {path.name} and {bank[midi][0].name} both measure as midi {midi}", file=sys.stderr)
            return 1
        bank[midi] = (path, f0)

    missing = [m for m in range(MIDI_LOW, MIDI_HIGH + 1) if m not in bank]
    if missing:
        print(f"error: no source sample for midi {missing}", file=sys.stderr)
        return 1

    args.out.mkdir(parents=True, exist_ok=True)
    total = 0
    worst = 0.0
    for midi in range(MIDI_LOW, MIDI_HIGH + 1):
        path, f0 = bank[midi]
        x, sr = load_mono(path)
        y = prepare(x, sr, midi)
        out = args.out / f"{midi}.flac"
        sf.write(out, y, SR, format="FLAC", subtype="PCM_16")
        # Verify from the written file.
        z, zsr = sf.read(out)
        err = 1200 * np.log2(median_f0(z[int(0.2 * zsr) : -int(END_FADE_S * zsr)], zsr) / midi_to_hz(midi))
        worst = max(worst, abs(err))
        if abs(err) > MAX_ERROR_CENTS:
            print(f"error: {out.name} is {err:+.2f} cents off", file=sys.stderr)
            return 1
        size = out.stat().st_size
        total += size
        src_cents = 1200 * np.log2(f0 / midi_to_hz(midi))
        print(f"{out.name:>10}  from {path.name:<40} source {src_cents:+6.1f}c -> {err:+5.2f}c  {size / 1024:6.1f} KiB")

    print(f"\n{MIDI_HIGH - MIDI_LOW + 1} files, {total / 1024 / 1024:.2f} MiB, worst pitch error {worst:.2f} cents")
    return 0


if __name__ == "__main__":
    sys.exit(main())

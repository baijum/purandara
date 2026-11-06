"""Prepare the bundled tanpura drone loops from Sankalp Gulati's recording.

Source: "Acoustic Tanpura 10" by sankalp (Sankalp Gulati), recorded at Dhrupad
Sansar, IIT Bombay; tuning pa SA SA sa, Sa ~233 Hz, jivari thread inserted.
https://freesound.org/people/sankalp/sounds/155272/  (CC BY 4.0)
Download the original WAV (login required) into datasets/tanpura/.

For each Sa in SA_MIDIS this writes audio/tanpura/<midi>.flac:
  * pitch-shifted with Rubber Band (tempo and body resonance preserved) so
    its Sa is exact 12-TET at A4 = 440 Hz;
  * cut to three whole plucking cycles (LOOP_START..LOOP_END, found by
    comparing the spectrum before candidate start and end points), with the
    end crossfaded into the audio just before the start, so playing the file
    in a circle has no seam;
  * mono, loudness-normalised, 44.1 kHz 16-bit FLAC.

The app plays the variant nearest the student's Sa and nudges its speed by at
most ±2 semitones (±3 at the very bottom), see TanpuraBank in the shared module.

Requires the `rubberband` command-line tool (brew install rubberband) at
build time only; nothing from it ships in the app.

Usage:
    uv run python prepare_tanpura.py [--source datasets/tanpura/155272__sankalp__acoustic-tanpura-10.wav]
"""

import argparse
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
import soundfile as sf
import soxr

HERE = Path(__file__).parent
SR = 44_100
SA_MIDIS = [46, 50, 54, 58]  # A#2, D3, F#3, A#3; must match TanpuraBank.SA_MIDIS
LOOP_START = 24.70  # seconds in the original: three whole plucking cycles (~6.0 s each)
LOOP_END = 42.85
CROSSFADE_S = 0.25
MARGIN_S = 1.0  # extra audio around the loop given to Rubber Band
TARGET_RMS_DBFS = -18.0
MAX_ERROR_CENTS = 3.0


def midi_to_hz(midi: float) -> float:
    return 440.0 * 2 ** ((midi - 69) / 12)


def measure_sa(x: np.ndarray, near_hz: float) -> float:
    """Sa from the long-term spectrum peak within a semitone of [near_hz]."""
    n = 1 << 18
    spec = np.zeros(n // 2 + 1)
    for i in range(0, len(x) - n, n // 4):
        spec += np.abs(np.fft.rfft(x[i : i + n] * np.hanning(n))) ** 2
    f = np.fft.rfftfreq(n, 1 / SR)
    m = (f > near_hz / 2 ** (1 / 12)) & (f < near_hz * 2 ** (1 / 12))
    k = np.argmax(spec[m])
    # Parabolic interpolation on the log spectrum for sub-bin precision.
    idx = np.nonzero(m)[0][k]
    a, b, c = np.log(spec[idx - 1 : idx + 2])
    shift = 0.5 * (a - c) / (a - 2 * b + c)
    return float(f[idx] + shift * (f[1] - f[0]))


def pitch_shift(x: np.ndarray, semitones: float) -> np.ndarray:
    with tempfile.TemporaryDirectory() as tmp:
        src, dst = Path(tmp) / "in.wav", Path(tmp) / "out.wav"
        sf.write(src, x, SR, subtype="FLOAT")
        # --fine: R3 engine (best quality); -F: preserve formants (the tanpura's body).
        subprocess.run(["rubberband", "--fine", "-F", "-p", f"{semitones:.6f}", "-q", str(src), str(dst)], check=True)
        y, _ = sf.read(dst, dtype="float64")
    return y[: len(x)]


def make_loop(y: np.ndarray, start: int, end: int) -> np.ndarray:
    """Samples [start, end) with the tail blended into the audio before [start]."""
    loop = y[start:end].copy()
    x = int(CROSSFADE_S * SR)
    w = np.sin(np.linspace(0, np.pi / 2, x)) ** 2  # equal-power-ish ramp
    tail = slice(len(loop) - x, len(loop))
    loop[tail] = loop[tail] * (1 - w) + y[start - x : start] * w
    return loop


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--source", type=Path, default=HERE / "datasets" / "tanpura" / "155272__sankalp__acoustic-tanpura-10.wav")
    parser.add_argument("--out", type=Path, default=HERE.parent.parent / "audio" / "tanpura")
    args = parser.parse_args()

    raw, rate = sf.read(args.source, dtype="float64")
    x = soxr.resample(raw.mean(axis=1) if raw.ndim > 1 else raw, rate, SR, quality="VHQ")
    sa = measure_sa(x, 233.0)
    print(f"source Sa {sa:.2f} Hz ({1200 * np.log2(sa / midi_to_hz(58)):+.1f} cents from A#3)")

    margin = int(MARGIN_S * SR)
    seg_start = int(LOOP_START * SR) - margin
    segment = x[seg_start : int(LOOP_END * SR) + margin]
    start, end = margin, margin + int((LOOP_END - LOOP_START) * SR)

    args.out.mkdir(parents=True, exist_ok=True)
    for midi in SA_MIDIS:
        target = midi_to_hz(midi)
        semitones = 12 * np.log2(target / sa)
        loop = make_loop(pitch_shift(segment, semitones), start, end)
        loop *= 10 ** (TARGET_RMS_DBFS / 20) / np.sqrt(np.mean(loop**2))
        peak = np.max(np.abs(loop))
        if peak > 0.95:
            loop *= 0.95 / peak
        out = args.out / f"{midi}.flac"
        sf.write(out, loop, SR, format="FLAC", subtype="PCM_16")
        z, _ = sf.read(out)
        measured = measure_sa(np.tile(z, 2), target)
        err = 1200 * np.log2(measured / target)
        jump = abs(z[0] - z[-1])
        print(f"{out.name}: shift {semitones:+6.2f} st, Sa {measured:7.2f} Hz ({err:+.1f} c), "
              f"{len(z) / SR:.2f} s, wrap step {jump:.4f}, {out.stat().st_size / 1024:.0f} KiB")
        if abs(err) > MAX_ERROR_CENTS:
            print(f"error: {out.name} is {err:+.1f} cents off", file=sys.stderr)
            return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

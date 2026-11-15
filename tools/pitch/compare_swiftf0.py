#!/usr/bin/env python3
"""Compare the bundled SwiftF0 model with v0.3.0 (docs/plan-swiftf0-upgrade.md).

Not part of the build. Set up once, from this directory:

    python3 -m venv .venv && .venv/bin/pip install numpy onnxruntime
    curl -L -o .cache/swift_f0_v0.3.0.onnx \
      https://raw.githubusercontent.com/lars76/swift-f0/v0.3.0/swift_f0/model.onnx

then `.venv/bin/python -I compare_swiftf0.py` (synthetic tracks with exact pitch)
or `... compare_swiftf0.py --audio a.wav --sa 146.83` for a real recording
(convert .m4a with `afconvert -f WAVE -d LEI16 in.m4a out.wav`). Real
recordings have no ground truth: each method is compared with new-offline
(the reference way to run the model) and with old-live, and the spans where
they differ by an octave are listed so they can be listened to.

Every method is scored at the app's pitch-frame times: a 2048-sample window
every 512 samples at the capture rate (48 kHz), the frame being the window's
centre, as PitchPipeline does. Methods:

  old-live      bundled model on each capture window resampled to 16 kHz, its
                middle frame (today's sing-along and Recordings path)
  new-live      v0.3.0 on a short rolling buffer at 16 kHz: 6 frames before,
                the frame, 4 after (176 ms; the frame is 64-80 ms behind the
                newest audio). The model gives low confidence on the first and
                last frame of any input, so the newest frame is never voiced,
                and on real voices fewer than 4 frames after reads junk (about
                2 kHz) on many frames.
  new-live-exact  the reference context, 11 before and 10 after (352 ms; the
                frame is 176 ms behind)
  new-offline   v0.3.0 over the whole track in 30 s chunks with overlap (the
                reference detect), nearest frame
  ...+range     the same with fmin/fmax from Sa: mandra Pa to tara Pa, a
                semitone of margin each side
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
import time
import wave
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
import onnxruntime as ort

HERE = Path(__file__).resolve().parent
OLD_MODEL = HERE.parent.parent / "models" / "swift_f0_model.onnx"
NEW_MODEL = HERE / ".cache" / "swift_f0_v0.3.0.onnx"
NEW_SHA256 = "6385e8c2ebc3872e82c9ff5946228de44cd3be77a750ea53698b7dbfe94b0a22"

CAPTURE_RATE = 48_000
WINDOW = 2048          # PitchPipeline.WINDOW_SIZE
HOP = 512              # PitchPipeline.HOP_SIZE
LIVE_INTERVAL = 5      # NeuralArbitrator.SUPERVISOR_INTERVAL

MODEL_RATE = 16_000
MODEL_HOP = 256
FMIN, FMAX = 46.875, 2093.75
LEFT_FRAMES, LOOKAHEAD_FRAMES, CHUNK_FRAMES = 11, 10, 1875
LIVE_LEFT, LIVE_AHEAD = 6, 4
SILENCE_PEAK = 1e-3
VOICED_CONFIDENCE = 0.5
SEMITONE = 2 ** (1 / 12)

# Mayamalavagowla, just intonation (Swara.ratio, docs/tuning.md).
RATIO = {"S": 1, "R": 16 / 15, "G": 5 / 4, "M": 4 / 3, "P": 3 / 2, "D": 8 / 5, "N": 15 / 8, "S'": 2}
SARALI_1 = ["S", "R", "G", "M", "P", "D", "N", "S'", "S'", "N", "D", "P", "M", "G", "R", "S"]


# --- The app's resampler (shared/.../pitch/AudioResampler.kt) ---------------

def app_downsample(x: np.ndarray, source_rate: int = CAPTURE_RATE) -> np.ndarray:
    """5-tap moving average, then linear interpolation, as AudioResampler."""
    n = len(x)
    if n == 0:
        return np.zeros(0, np.float32)
    if n <= 4:
        filtered = x.astype(np.float32)
    else:
        c = np.concatenate(([0.0], np.cumsum(x, dtype=np.float64)))
        i = np.arange(n)
        lo, hi = np.maximum(i - 2, 0), np.minimum(i + 2, n - 1)
        filtered = ((c[hi + 1] - c[lo]) / (hi - lo + 1)).astype(np.float32)
    ratio = source_rate / MODEL_RATE
    out_n = max(1, int(n / ratio))
    pos = np.arange(out_n) * ratio
    base = np.clip(pos.astype(int), 0, n - 1)
    nxt = np.minimum(base + 1, n - 1)
    frac = (pos - base).astype(np.float32)
    return filtered[base] * (1 - frac) + filtered[nxt] * frac


# --- Models -----------------------------------------------------------------

def session(path: Path) -> ort.InferenceSession:
    options = ort.SessionOptions()
    options.intra_op_num_threads = 1
    options.inter_op_num_threads = 1
    return ort.InferenceSession(str(path), options)


class Timer:
    def __init__(self) -> None:
        self.seconds = 0.0
        self.calls = 0

    def run(self, s: ort.InferenceSession, feeds: dict) -> list:
        t = time.perf_counter()
        out = s.run(None, feeds)
        self.seconds += time.perf_counter() - t
        self.calls += 1
        return out


def old_estimate(s, timer, window48k):
    audio = app_downsample(window48k)
    pitch, conf = timer.run(s, {s.get_inputs()[0].name: audio[None, :]})
    pitch, conf = pitch.reshape(-1), conf.reshape(-1)
    if len(pitch) == 0:
        return 0.0
    i = len(pitch) // 2
    hz, c = float(pitch[i]), float(conf[min(i, len(conf) - 1)])
    return hz if FMIN <= hz <= FMAX and c >= VOICED_CONFIDENCE else 0.0


def new_run(s, timer, audio16k, fmin, fmax):
    pitch, conf = timer.run(s, {
        "audio": audio16k[None, :].astype(np.float32),
        "fmin": np.asarray(fmin, np.float32),
        "fmax": np.asarray(fmax, np.float32),
    })
    pitch, conf = pitch[0].astype(np.float64), conf[0].astype(np.float64)
    n = len(conf)
    hops = audio16k[: n * MODEL_HOP].reshape(n, MODEL_HOP) if len(audio16k) >= MODEL_HOP else audio16k[None, :]
    conf[np.abs(hops).max(axis=1) < SILENCE_PEAK] = 0.0
    return np.where(conf >= VOICED_CONFIDENCE, pitch, 0.0)


def new_offline(s, timer, audio16k, fmin, fmax):
    """The reference SwiftF0.detect: chunks of 1875 frames, 11 left / 10 ahead."""
    n = max(1, len(audio16k) // MODEL_HOP)
    parts = []
    for start in range(0, n, CHUNK_FRAMES):
        end = min(start + CHUNK_FRAMES, n)
        left = max(0, start - LEFT_FRAMES)
        stop = (end + LOOKAHEAD_FRAMES) * MODEL_HOP if end < n else len(audio16k)
        hz = new_run(s, timer, audio16k[left * MODEL_HOP: stop], fmin, fmax)
        parts.append(hz[start - left: end - left])
    return np.concatenate(parts)


def sa_range(sa_hz):
    if sa_hz is None:
        return FMIN, FMAX
    return max(FMIN, sa_hz * 0.75 / SEMITONE), min(FMAX, sa_hz * 3 * SEMITONE)


# --- Methods at the app's frame times -----------------------------------------

def frame_times(n_samples):
    starts = np.arange(0, n_samples - WINDOW + 1, HOP)
    return starts, (starts + WINDOW / 2) / CAPTURE_RATE


def live(s, audio16k, starts, left, ahead, fmin, fmax):
    """The rolling buffer ends with the capture window; read the frame `ahead`
    from its end. Scored at the window centre, as the app would use it."""
    timer = Timer()
    size = (left + 1 + ahead) * MODEL_HOP
    out = []
    for a in starts:
        end16 = int((a + WINDOW) * MODEL_RATE / CAPTURE_RATE)
        hz = new_run(s, timer, audio16k[max(0, end16 - size): end16], fmin, fmax)
        out.append(hz[len(hz) - 1 - ahead] if len(hz) > ahead else 0.0)
    return np.array(out), timer


def run_methods(x48k, sa_hz, old_s, new_s):
    starts, times = frame_times(len(x48k))
    audio16k = app_downsample(x48k)
    results = {}

    timer = Timer()
    results["old-live"] = (np.array([old_estimate(old_s, timer, x48k[a: a + WINDOW]) for a in starts]), timer)

    for suffix, (fmin, fmax) in (("", (FMIN, FMAX)), ("+range", sa_range(sa_hz))):
        if suffix and sa_hz is None:
            continue
        for name, left, ahead in (("new-live", LIVE_LEFT, LIVE_AHEAD), ("new-live-exact", LEFT_FRAMES, LOOKAHEAD_FRAMES)):
            results[name + suffix] = live(new_s, audio16k, starts, left, ahead, fmin, fmax)

        timer_off = Timer()
        track = new_offline(new_s, timer_off, audio16k, fmin, fmax)
        idx = np.clip(np.rint(times * MODEL_RATE / MODEL_HOP).astype(int), 0, len(track) - 1)
        results["new-offline" + suffix] = (track[idx], timer_off)
    return times, results


# --- Synthetic voice ----------------------------------------------------------

@dataclass
class Track:
    name: str
    group: str
    sa_hz: float
    audio: np.ndarray
    f0: np.ndarray            # per sample, 0 = silence
    steady: np.ndarray        # per sample: on a held note, away from changes
    notes: list = field(default_factory=list)


VOWELS = {
    # formant Hz, bandwidth, relative level
    "aa": ((700, 130, 1.0), (1150, 160, 0.6), (2600, 250, 0.25)),
    "oo": ((320, 90, 1.0), (800, 120, 0.4), (2300, 250, 0.1)),
    "ee": ((300, 90, 1.0), (2300, 200, 0.5), (3000, 250, 0.3)),
}


def formant_gain(freq, vowel="aa"):
    """A rough vowel envelope over the 1/k source slope applied by the caller."""
    return 0.15 + sum(a * np.exp(-0.5 * ((freq - f) / bw) ** 2) for f, bw, a in VOWELS[vowel])


def synth_voice(f0, amp, rng, h1_gain=1.0, breath=0.02, jitter_cents=6.0, vowel="aa"):
    n = len(f0)
    drift = np.cumsum(rng.normal(0, 1, n))
    drift = drift - np.convolve(drift, np.ones(4801) / 4801, mode="same")
    wobble = 2 ** ((jitter_cents * np.tanh(drift / 400)) / 1200)
    f = f0 * wobble
    phase = 2 * np.pi * np.cumsum(f) / CAPTURE_RATE
    out = np.zeros(n)
    voiced = f0 > 0
    mean_f = float(np.mean(f0[voiced])) if voiced.any() else 200.0
    for k in range(1, int(7500 / mean_f) + 1):
        gain = formant_gain(k * f, vowel) / k
        if k == 1:
            gain = gain * h1_gain
        gain = np.where(k * f < 7800, gain, 0.0)
        out += gain * np.sin(k * phase + rng.uniform(0, 2 * np.pi))
    out *= voiced
    noise = rng.normal(0, 1, n)
    noise = np.convolve(noise, [0.25, 0.5, 0.25], mode="same")
    out = out / (np.max(np.abs(out)) + 1e-9) + breath * noise * (amp > 0)
    return out * amp


def envelope(n, on, off, ramp=0.03):
    t = np.arange(n) / CAPTURE_RATE
    up = np.clip((t - on) / ramp, 0, 1)
    down = np.clip((off - t) / ramp, 0, 1)
    return np.minimum(up, down)


def sequence(sa, notes, note_s=0.5, glide_s=0.03, lead=0.5, tail=0.5):
    """Notes legato with short glides; returns f0, steady mask, total length."""
    n = int((lead + len(notes) * note_s + tail) * CAPTURE_RATE)
    f0 = np.zeros(n)
    steady = np.zeros(n, bool)
    hz = [sa * RATIO[s] for s in notes]
    for i, h in enumerate(hz):
        a = int((lead + i * note_s) * CAPTURE_RATE)
        b = int((lead + (i + 1) * note_s) * CAPTURE_RATE)
        f0[a:b] = h
        if i > 0 and hz[i - 1] != h:
            g = int(glide_s * CAPTURE_RATE)
            f0[a: a + g] = np.geomspace(hz[i - 1], h, g)
        margin = int(0.06 * CAPTURE_RATE)
        steady[a + margin: b - margin] = True
    end = lead + len(notes) * note_s
    return f0, steady, envelope(n, lead, end), n


def pink(n, rng):
    white = np.fft.rfft(rng.normal(0, 1, n))
    f = np.arange(len(white))
    white[1:] /= np.sqrt(f[1:])
    x = np.fft.irfft(white, n)
    return x / np.std(x)


def synthetic_tracks(seed=7):
    rng = np.random.default_rng(seed)
    tracks = []
    for label, sa in (("A#2", 116.54), ("D3", 146.83), ("F#3", 185.00), ("G#3", 207.65), ("A#3", 233.08), ("C4", 261.63)):
        for vowel in ("aa", "oo", "ee"):
            f0, steady, env, n = sequence(sa, SARALI_1)
            tracks.append(Track(f"sarali {label} {vowel}", "clean", sa,
                                0.3 * synth_voice(f0, env, rng, vowel=vowel), f0 * (env > 0), steady))

    sa = 146.83
    f0, steady, env, n = sequence(sa, SARALI_1)
    tracks.append(Track("breathy, soft", "soft", sa, 0.03 * synth_voice(f0, env, rng, breath=0.35), f0 * (env > 0), steady))
    voice = 0.3 * synth_voice(f0, env, rng)
    room = pink(n, rng) * np.std(voice[env > 0]) / 10 ** (12 / 20)
    hum = 0.01 * np.sin(2 * np.pi * 50 * np.arange(n) / CAPTURE_RATE)
    tracks.append(Track("room noise 12 dB SNR", "noisy", sa, voice + room + hum, f0 * (env > 0), steady))
    for label, sa2 in (("D3", 146.83), ("A#2", 116.54)):
        f0, steady, env, n = sequence(sa2, SARALI_1)
        tracks.append(Track(f"weak fundamental {label}", "overtone", sa2,
                            0.3 * synth_voice(f0, env, rng, h1_gain=0.1), f0 * (env > 0), steady))

    # Kampita on Ri: oscillates R1 <-> G3 at 6 Hz for 1.5 s between steady Sa and Ga.
    sa = 146.83
    lead, n = 0.5, int(4.0 * CAPTURE_RATE)
    t = np.arange(n) / CAPTURE_RATE
    f0 = np.zeros(n)
    steady = np.zeros(n, bool)
    s1, k0, k1, g1 = lead, lead + 0.6, lead + 2.1, lead + 2.7
    f0[(t >= s1) & (t < k0)] = sa
    cents = 1200 * math.log2(16 / 15) + (1200 * math.log2(5 / 4) - 1200 * math.log2(16 / 15)) * 0.5 * (1 - np.cos(2 * np.pi * 6 * (t - k0)))
    sel = (t >= k0) & (t < k1)
    f0[sel] = sa * 2 ** (cents[sel] / 1200)
    f0[(t >= k1) & (t < g1)] = sa * 5 / 4
    steady[(t >= s1 + 0.06) & (t < k0 - 0.06)] = True
    steady[(t >= k1 + 0.06) & (t < g1 - 0.06)] = True
    env = envelope(n, s1, g1)
    tracks.append(Track("kampita on Ri", "gamaka", sa, 0.3 * synth_voice(f0, env, rng), f0 * (env > 0), steady))

    # Jaru: Sa, slide up to Pa over 0.8 s, hold, slide back.
    f0 = np.zeros(n)
    steady = np.zeros(n, bool)
    p = sa * 1.5
    seg = [(0.5, 1.0, sa, sa), (1.0, 1.8, sa, p), (1.8, 2.4, p, p), (2.4, 3.2, p, sa), (3.2, 3.6, sa, sa)]
    for a, b, h0, h1 in seg:
        sel = (t >= a) & (t < b)
        f0[sel] = h0 * (h1 / h0) ** ((t[sel] - a) / (b - a))
        if h0 == h1:
            steady[(t >= a + 0.06) & (t < b - 0.06)] = True
    env = envelope(n, 0.5, 3.6)
    tracks.append(Track("jaru Sa-Pa-Sa", "gamaka", sa, 0.3 * synth_voice(f0, env, rng), f0 * (env > 0), steady))
    return tracks


# --- Scoring --------------------------------------------------------------------

def truth_at(track, times):
    i = np.clip((times * CAPTURE_RATE).astype(int), 0, len(track.f0) - 1)
    return track.f0[i], track.steady[i]


def score(est, f0, steady):
    voiced = f0 > 0
    hit = voiced & (est > 0)
    cents = np.full(len(est), np.nan)
    cents[hit] = 1200 * np.log2(est[hit] / f0[hit])
    a = np.abs(cents)
    octave = hit & ((np.abs(a - 1200) <= 100) | (np.abs(a - 2400) <= 100) | (np.abs(a - 1902) <= 100))
    gross = hit & steady & (a > 50)
    fine = hit & steady & (a <= 50)
    return {
        "voiced": int(voiced.sum()),
        "recall": float(hit.sum() / max(1, voiced.sum())),
        "false_voiced": int(((~voiced) & (est > 0)).sum()),
        "octave": int(octave.sum()),
        "gross_steady": int(gross.sum()),
        "steady": int((voiced & steady).sum()),
        "median_cents": float(np.median(a[fine])) if fine.any() else float("nan"),
    }


def cost_per_minute(timer: Timer, duration_s: float, name: str):
    """Model seconds per minute of audio at the app's cadence."""
    per_call = timer.seconds / max(1, timer.calls)
    if name.startswith("new-offline"):
        return timer.seconds * 60 / duration_s
    frames_per_minute = 60 * CAPTURE_RATE / HOP
    live = per_call * frames_per_minute / LIVE_INTERVAL
    every = per_call * frames_per_minute
    return live, every


# --- Real recordings: agreement only ----------------------------------------

def read_wav(path: Path) -> tuple[np.ndarray, int]:
    with wave.open(str(path)) as w:
        rate, ch, width = w.getframerate(), w.getnchannels(), w.getsampwidth()
        raw = w.readframes(w.getnframes())
    if width != 2:
        sys.exit(f"{path}: expected 16-bit PCM (afconvert -f WAVE -d LEI16)")
    x = np.frombuffer(raw, "<i2").astype(np.float32) / 32768
    return x.reshape(-1, ch).mean(axis=1), rate


def to_capture_rate(x, rate):
    if rate == CAPTURE_RATE:
        return x
    n = int(len(x) * CAPTURE_RATE / rate)
    return np.interp(np.arange(n) * rate / CAPTURE_RATE, np.arange(len(x)), x).astype(np.float32)


def agreement(est, ref):
    """Of the frames voiced in either, the share voiced in both within 50 cents."""
    either = (est > 0) | (ref > 0)
    both = (est > 0) & (ref > 0)
    cents = np.abs(1200 * np.log2(est[both] / ref[both]))
    return {"agree": float((cents <= 50).sum() / max(1, either.sum())),
            "octave": int((np.abs(cents - 1200) <= 100).sum())}


def octave_spans(times, a, b, x):
    both = (a > 0) & (b > 0)
    cents = np.zeros(len(a))
    cents[both] = np.abs(1200 * np.log2(b[both] / a[both]))
    bad = np.where(both & (np.abs(cents - 1200) <= 100))[0]
    if not len(bad):
        return []
    out = []
    for run in np.split(bad, np.where(np.diff(bad) > 3)[0] + 1):
        i0, i1 = run[0], run[-1]
        s0 = int((times[i0] - WINDOW / 2 / CAPTURE_RATE) * CAPTURE_RATE)
        s1 = int((times[i1] + WINDOW / 2 / CAPTURE_RATE) * CAPTURE_RATE)
        out.append((times[i0], times[i1], float(np.median(a[run])), float(np.median(b[run])),
                    float(np.sqrt(np.mean(x[s0:s1] ** 2)))))
    return out


def octave_jumps(est):
    """Voiced-to-voiced steps of about an octave between adjacent frames."""
    a, b = est[:-1], est[1:]
    both = (a > 0) & (b > 0)
    step = np.abs(1200 * np.log2(b[both] / a[both]))
    return int((np.abs(step - 1200) <= 150).sum())


# --- Main ---------------------------------------------------------------------

METHOD_ORDER = ["old-live", "new-live", "new-live-exact", "new-offline",
                "new-live+range", "new-live-exact+range", "new-offline+range"]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--audio", type=Path, nargs="*", help="real recordings (16-bit WAV): agreement only")
    ap.add_argument("--sa", type=float, help="Sa in Hz for --audio (enables the +range methods)")
    ap.add_argument("--write-wavs", type=Path, help="also write the synthetic tracks here")
    ap.add_argument("--json", type=Path, help="write the results as JSON")
    args = ap.parse_args()

    if not NEW_MODEL.exists():
        sys.exit(f"missing {NEW_MODEL}: see the set-up steps at the top of this file")
    if hashlib.sha256(NEW_MODEL.read_bytes()).hexdigest() != NEW_SHA256:
        sys.exit(f"{NEW_MODEL}: checksum mismatch")
    old_s, new_s = session(OLD_MODEL), session(NEW_MODEL)
    report = {}

    if args.audio:
        for path in args.audio:
            x, rate = read_wav(path)
            x = to_capture_rate(x, rate)
            times, results = run_methods(x, args.sa, old_s, new_s)
            print(f"\n{path.name} ({len(x) / CAPTURE_RATE:.1f} s)")
            print(f"  {'method':20} {'voiced':>7} {'agrees with new-offline':>24} {'with old-live':>14} {'octave from old':>16}")
            report[path.name] = {}
            for name in METHOD_ORDER:
                if name not in results:
                    continue
                est = results[name][0]
                ref = agreement(est, results["new-offline"][0])
                old = agreement(est, results["old-live"][0])
                report[path.name][name] = {"voiced": float(np.mean(est > 0)), "vs_offline": ref, "vs_old": old}
                print(f"  {name:20} {np.mean(est > 0):7.0%} {ref['agree']:24.1%} {old['agree']:14.1%} {old['octave']:16d}")
            spans = octave_spans(times, results["old-live"][0], results["new-offline"][0], x)
            for t0, t1, a, b, rms in spans:
                print(f"    octave apart {t0:6.2f}-{t1:5.2f} s: old {a:4.0f} Hz, new-offline {b:4.0f} Hz, rms {rms:.4f}")
    else:
        tracks = synthetic_tracks()
        totals: dict[str, dict] = {}
        costs: dict[str, list] = {}
        for track in tracks:
            if args.write_wavs:
                args.write_wavs.mkdir(parents=True, exist_ok=True)
                pcm = np.clip(track.audio * 32767, -32768, 32767).astype("<i2")
                with wave.open(str(args.write_wavs / f"{track.name.replace(' ', '_').replace(',', '')}.wav"), "wb") as w:
                    w.setnchannels(1), w.setsampwidth(2), w.setframerate(CAPTURE_RATE), w.writeframes(pcm.tobytes())
            times, results = run_methods(track.audio, track.sa_hz, old_s, new_s)
            f0, steady = truth_at(track, times)
            duration = len(track.audio) / CAPTURE_RATE
            print(f"\n{track.name} [{track.group}], Sa {track.sa_hz:.1f} Hz, {duration:.1f} s")
            print(f"  {'method':20} {'recall':>7} {'false+':>7} {'octave':>7} {'gross':>7} {'cents':>6}")
            report[track.name] = {}
            for name in METHOD_ORDER:
                est, timer = results[name]
                s = score(est, f0, steady)
                report[track.name][name] = s
                print(f"  {name:20} {s['recall']:7.1%} {s['false_voiced']:7d} {s['octave']:7d} "
                      f"{s['gross_steady']:4d}/{s['steady']:<4d} {s['median_cents']:5.1f}")
                key = (track.group, name)
                t = totals.setdefault(name, {}).setdefault(track.group, {"octave": 0, "gross": 0, "steady": 0, "voiced": 0, "hit": 0, "false": 0})
                t["octave"] += s["octave"]; t["gross"] += s["gross_steady"]; t["steady"] += s["steady"]
                t["voiced"] += s["voiced"]; t["hit"] += round(s["recall"] * s["voiced"]); t["false"] += s["false_voiced"]
                costs.setdefault(name, []).append(cost_per_minute(timer, duration, name))

        groups = sorted({t.group for t in tracks})
        print("\nSummary: octave errors / gross errors on steady frames (and recall), per group")
        print(f"  {'method':20} " + " ".join(f"{g:>22}" for g in groups))
        for name in METHOD_ORDER:
            cells = []
            for g in groups:
                t = totals[name][g]
                cells.append(f"{t['octave']:4d} / {t['gross']:4d} ({t['hit'] / max(1, t['voiced']):4.0%})")
            print(f"  {name:20} " + " ".join(f"{c:>22}" for c in cells))

        print("\nModel time per minute of audio on this Mac (1 thread)")
        for name in METHOD_ORDER:
            c = costs[name]
            if name.startswith("new-offline"):
                print(f"  {name:20} {np.mean(c):6.2f} s (whole recording)")
            else:
                live = np.mean([x[0] for x in c]); every = np.mean([x[1] for x in c])
                print(f"  {name:20} {live:6.2f} s live (1 in {LIVE_INTERVAL}), {every:6.2f} s on every window")
        report["_summary"] = totals

    if args.json:
        args.json.write_text(json.dumps(report, indent=1))


if __name__ == "__main__":
    main()

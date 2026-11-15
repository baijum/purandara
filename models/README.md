# Models

`swift_f0_v0.3.0.onnx`: SwiftF0 monophonic pitch estimator, v0.3.0, by Lars
Nieradzik (lars76): https://github.com/lars76/swift-f0, tag `v0.3.0`, file
`swift_f0/model.onnx`, unchanged (renamed). MIT licence; credited in
`ATTRIBUTION.md`. Paper: arXiv:2508.18440; training code:
https://github.com/lars76/swift-f0-training.

- Size 135 090 bytes, 14 386 parameters.
- SHA-256 `6385e8c2ebc3872e82c9ff5946228de44cd3be77a750ea53698b7dbfe94b0a22`.
- **Inputs:** `audio` float32 [1, samples] at 16 kHz (−1..1); `fmin`, `fmax`
  float32 scalars (Hz, within 46.875–2093.75): the band to search. A pitch
  outside the band gets confidence 0.
- **Outputs:** `pitch` float64 [1, frames] (Hz) and `confidence` float32
  [1, frames]. Frame *k* is centred on sample *k*·256 (16 ms); voiced at
  confidence ≥ 0.5.
- The first and last frame of any input have low confidence; a frame needs
  the 11 frames before it and 10 after for an exact reading. Digital silence
  reads as random voiced frames, so frames whose hop peaks below 1e-3 get
  confidence 0: the reference does this outside the model, and so does the
  app (`shared/.../pitch/SwiftF0.kt`, with the chunking and the live buffer).

Packaged into both apps: Android assets, iOS bundle folder `models/`. Runs
fully offline via ONNX Runtime (Android 1.32.0, iOS 1.30.0). If it fails to
load, the apps use YIN alone. The choice of this model and how it is run are
in `docs/plan-swiftf0-upgrade.md`.

The model it replaced (an earlier export, 398 KB, SHA-256 `7e2390db…`, from
the ukulele-companion app) is in the git history: `git show
ae14cd3:models/swift_f0_model.onnx`. The comparison tool (`tools/pitch/`)
says how to fetch it.

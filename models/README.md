# Models

`swift_f0_model.onnx`: SwiftF0 monophonic pitch estimator (~95K parameters,
16 kHz input, 46.875–2093.75 Hz), by lars76: https://github.com/lars76/swift-f0
(paper: arXiv:2508.18440). MIT licence. Credited in `ATTRIBUTION.md`.

Copied unchanged from the ukulele-companion app, whose notes give its source
as the swift-f0 package's `model.onnx` (SHA-256
`7e2390db8379cd9e1e2b22828e55b45b57c8559e4c8335678c717dc245c18176`). Packaged into both apps: Android assets, iOS bundle
folder `models/`. Runs fully offline via ONNX Runtime. If it fails to load,
sing-along falls back to YIN alone.

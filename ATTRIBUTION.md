# Attribution

Purandara bundles the following third-party material. Every source is credited
here, including public-domain (CC0) ones that do not legally require it.

## Audio samples

### Harmonium single notes (`audio/harmonium/`)

- **Original recording:** "Harmonium samples – all keys and drones" by
  [donyaquick](https://freesound.org/people/donyaquick/), recorded in the
  Euterpea Studio at Yale University's Department of Computer Science.
  [freesound.org/s/330410](https://freesound.org/s/330410/)
- **Cut into single notes by:** [cabled_mess](https://freesound.org/people/cabled_mess/),
  pack "Harmonium".
  [freesound.org/people/cabled_mess/packs/29512](https://freesound.org/people/cabled_mess/packs/29512/)
- **License:** [Creative Commons 0 (CC0 1.0) Public Domain Dedication](https://creativecommons.org/publicdomain/zero/1.0/)
- **Files used:** the 37 chromatic notes C2–C5.
- **Changes made:** each note was pitch-corrected to exact equal temperament
  at A4 = 440 Hz (the instrument is about 5–17 cents sharp), trimmed to
  2.5 seconds from the attack with a short fade-out, loudness-normalised,
  and converted to 44.1 kHz 16-bit mono FLAC by
  [`tools/samples/prepare_harmonium.py`](tools/samples/prepare_harmonium.py).

Thank you, donyaquick and cabled_mess, for sharing these recordings.

### Tanpura drone (`audio/tanpura/`)

- **Recording:** "Acoustic Tanpura 10" by [Sankalp Gulati](https://freesound.org/people/sankalp/)
  (`sankalp`), recorded at Dhrupad Sansar, IIT Bombay, India, with a Roland
  Edirol R-09HR; tuning pa SA SA sa, Sa ≈ 233 Hz, jivari thread inserted.
  [freesound.org/s/155272](https://freesound.org/s/155272/)
- **License:** [Creative Commons Attribution 4.0 (CC BY 4.0)](https://creativecommons.org/licenses/by/4.0/)
- **Changes made:** converted to mono 44.1 kHz; three plucking cycles
  (24.70–42.85 s) cut into a seamless loop; pitch-shifted with Rubber Band
  (tempo and formants preserved) to exact equal-tempered Sa at A♯2, D3, F♯3
  and A♯3; loudness-normalised; FLAC. See
  [`tools/samples/prepare_tanpura.py`](tools/samples/prepare_tanpura.py).

Thank you, Sankalp Gulati, for recording and sharing this tanpura.

## Machine learning model

### SwiftF0 (`models/swift_f0_model.onnx`)

- **Author:** [lars76](https://github.com/lars76)
- **Source:** [github.com/lars76/swift-f0](https://github.com/lars76/swift-f0);
  paper: Nieradzik, "SwiftF0", [arXiv:2508.18440](https://arxiv.org/abs/2508.18440)
- **License:** [MIT](https://opensource.org/licenses/MIT)
- **Use:** monophonic pitch estimation that checks the YIN detector during
  sing-along (mainly correcting octave errors). Bundled unchanged; runs
  fully offline.

## Runtime libraries

### ONNX Runtime

- **Author:** [Microsoft](https://github.com/microsoft/onnxruntime)
- **License:** [MIT](https://opensource.org/licenses/MIT)
- **Use:** runs the SwiftF0 model. Android: `com.microsoft.onnxruntime:onnxruntime-android`.
  iOS: the ONNX Runtime C xcframework, downloaded at build time by
  `iosApp/setup_onnxruntime.sh` (checksummed).

## Icons

### Material Icons (Android: `ic_check_circle`, `ic_chevron_right`)

- **Author:** [Google](https://github.com/google/material-design-icons)
- **License:** [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- **Use:** the "practised" tick and the "next up" chevron on the Android
  landing page. The path data of the *check_circle* and *chevron_right* icons
  is copied into vector drawables unchanged.

The iOS app uses Apple's SF Symbols from the system; none are bundled.

## Website (`site/`)

### Rozha One (`site/static/fonts/rozha-one-400.woff2`)

- **Author:** Indian Type Foundry
- **License:** [SIL Open Font License 1.1](site/static/fonts/OFL-RozhaOne.txt)
- **Use:** display type on the website, served from the site itself. Latin
  subset as distributed by Google Fonts, unchanged.

### Mukta (`site/static/fonts/mukta-*.woff2`)

- **Author:** Girish Dalvi, Ek Type
- **License:** [SIL Open Font License 1.1](site/static/fonts/OFL-Mukta.txt)
- **Use:** body text on the website (weights 400, 600, 700), served from the
  site itself. Latin subset as distributed by Google Fonts, unchanged.

The website's screenshots are of Purandara itself.

## Code

Pitch detection (`shared/.../pitch/`), the neural supervisors and parts of
the audio capture are ported from the author's own
[Ukulele Companion](https://github.com/baijum/ukulele-companion) (MIT).


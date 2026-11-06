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
- **Files used:** the 39 chromatic notes C2–D5.
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

## Notation

The compositions are traditional; the gitams are by Purandara Dasa
(1484–1564), long in the public domain. The notation is re-encoded in the
app's own format, and every source is credited here. Neither website states
a licence; the authors are to be asked before a store release.

### Varisais (`shared/.../domain/varisai/catalog/` Sarali … Dhattu)

- From the author's own Mayamalavagowla practice workbook.

### Alankarams (`catalog/Alankaram.kt`)

- **Source:** karnatik.com, Beginners' Lesson 6, by Rani:
  [karnatik.com/lesson6.shtml](https://karnatik.com/lesson6.shtml).
- **Changes made:** the site's case-based octaves rewritten in the app's
  notation (lowercase → plain letters, capital `S` → `S′`); its eighth
  alankaram (Sankeerna Eka) left out.

### Gitams (`catalog/Gitam.kt`)

- **Source:** karnatik.com, by Rani:
  [Sri Gananatha](https://karnatik.com/c1806.shtml),
  [Kunda Gaura](https://karnatik.com/c1807.shtml),
  [Kereya Neeranu](https://karnatik.com/c1808.shtml),
  [Padumanabha](https://karnatik.com/c1809.shtml),
  [Analekara](https://karnatik.com/c2415.shtml) (swaras, words, section order).
- **Checked against:** Shivkumar Kalyanaraman's archive,
  [shivkumar.org/music/varnams](https://www.shivkumar.org/music/varnams/index.html)
  (swaras, avarta by avarta, with `tools/notation/crosscheck_gitams.py`), and
  *Sangeeta Bala Padam, Part 1* (English edition, Giri Trading Agency,
  ISBN 81-7950-877-3), Gitam 1, p. 59.
- **Changes made:** Sri Gananatha's refrain and its words follow
  shivkumar.org and the book; words split one syllable per beat in simple
  Latin; one missing upper-octave mark in Kunda Gaura and one word typo in
  Padumanabha corrected. Every change is listed in the file's header.

### Swarajatis (`catalog/Swarajati.kt`)

Rara Venu Gopa Bala:

- **Source:** karnatik.com, by Rani:
  [karnatik.com/c1735.shtml](https://karnatik.com/c1735.shtml) (swaras and
  words; composer not given).
- **Checked against:** Shivkumar Kalyanaraman's archive,
  [raravenu-swarajati.htm](https://www.shivkumar.org/music/varnams/raravenu-swarajati.htm),
  cell by cell with `tools/notation/crosscheck_swarajatis.py`.
- **Changes made:** one octave mark and two holds corrected, words split one
  syllable per cell in simple Latin; listed in the file's header.

Sambasiva Yenave (composer Chinnakrishna Dasar, long in the public domain):

- **Source:** *Sangeeta Bala Padam, Part 1* (English edition, A.S.
  Panchapakesa Iyer, Giri Trading Agency, ISBN 81-7950-877-3), SWARAJATI-2,
  pp. 82–84 (swaras, words, section order; the owner's own copy, photographed
  2026-11-06).
- **Checked against:** Shivkumar Kalyanaraman's archive,
  [sambasiva-swarajati.htm](https://www.shivkumar.org/music/varnams/sambasiva-swarajati.htm)
  (notes courtesy "(Rani) www.karnatic.com"; karnatik.com has the same notes
  in a layout without beat bars or octave marks), and a notation PDF from
  [veenagana.org](http://veenagana.org/wp-content/uploads/2017/12/SAMBASHIVA.pdf)
  (Vasanthi Iyer) — both cell by cell with
  `tools/notation/crosscheck_swarajatis.py`.
- **Changes made:** words split one syllable per cell in simple Latin; the
  book's long syllables written with holds; its raga header's G2/N1 taken as
  a misprint for the standard Khamas G3/N2 (both web sources agree); a
  section-scoped `beat: 5` for the khanda-gati charanam. Every choice between
  the three sources is listed in the file's header.

Kamalajadala (Kalyani, Misra Chapu), Re Re Sri Ramachandra (Arabhi, Tisra
Triputa) and Sri Ramachandra (Bhairavi, Chaturasra Dhruva) — Purandara Dasa
and Paidala Gurumurti Sastri, long in the public domain:

- **Source (single, by the owner's decision — issue #1):** Shivkumar
  Kalyanaraman's archive, adapted from Chitraveena Ravikiran's *Perfecting
  Carnatic Music, Level 1*:
  [kamalajathala-kalyani-geetham](https://www.shivkumar.org/music/varnams/kamalajathala-kalyani-geetham.htm),
  [reresrirama-arabhi-geetham](https://www.shivkumar.org/music/varnams/reresrirama-arabhi-geetham.htm),
  [srirama-bhairavi-geetham](https://www.shivkumar.org/music/varnams/srirama-bhairavi-geetham.htm)
  — each page's PDF, which carries the octave and variant marks its HTML
  loses.
- **Checked with:** `tools/notation/crosscheck_gitams.py` (letter streams
  match the pages exactly; the octaves and variants have no second source and
  follow the PDFs).
- **Changes made:** words lowercased, one syllable per cell; the pages' dash
  holds written as karvai; Kamalajadala's "D P - M" typo corrected to the
  parallel phrase's "D P M"; Re Re's missing hold token supplied; Sri
  Ramachandra's D1 kept as a swara variant of the raga's D2. All listed in
  the file's header.

Kamala Sulochana (Anandabhairavi, Adi) — Purandara Dasa, long in the public
domain:

- **Source (single, by the owner's decision — issue #1):** the "Gurukulam &
  SF" geetham-notes collection (`geetham_notes.pdf`, supplied by the owner;
  pp. 26–28, swaras and words), read together with a staff-and-sargam
  rendering of the same piece also supplied by the owner.
- **Checked against:** [vignanam.org](https://vignanam.org/english/carnatic-music-geethams-kamala-sulochana.html)
  for the sahityam; no online notation source exists, so
  `tools/notation/crosscheck_gitams.py` skips this piece (noted there).
- **Changes made:** the collection's 16-swarasthana raga names read as the
  modern G2/N2; avarta 12's final Sa written tara (the staff rendering dots
  it, and only that joins the next phrase without a leap); two spots where
  the staff rendering sings mandra Ni against the collection's plain Ni are
  left as the collection has them, for the teacher. All listed in the file's
  header.

## Code

Pitch detection (`shared/.../pitch/`), the neural supervisors and parts of
the audio capture are ported from the author's own
[Ukulele Companion](https://github.com/baijum/ukulele-companion) (MIT).
The suladi tala model (`Tala.suladi`) and the clap / finger / wave click
tones follow the author's own ThaaLam app.


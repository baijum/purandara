# Resources for Purandara

A working bibliography of the websites, books, recordings and datasets that
Purandara's notation, theory, tuning and audio work can be checked against.
It is a **development reference, not an app dependency**: nothing here is
fetched at runtime, and every feature still has to work with the radio off.

Two rules from [`AGENTS.md`](../AGENTS.md) shape the list:

- **Two independent sources must agree** before a piece of notation goes in.
  Where they differ, keep the primary and record the variant (or ask the
  owner, who decides pathantarams).
- **Credit every source** used for a bundled asset in
  [`ATTRIBUTION.md`](../ATTRIBUTION.md), in the same change.

### Articulation and breathing

Research checked 9–10 November 2026 for
[articulation and recovery practice](plan-articulation-breathing.md). These
are design references, not imported recordings or notation:

- [Karnatik: introductory singing](https://www.karnatik.com/beginner1.shtml)
  describes continuous phrases and sustained karvai;
  [practice guidance](https://www.karnatik.com/practice.shtml) emphasizes
  breath economy and avoiding a breath in the middle of a sahitya word.
- [Shivkumar: sarali](https://www.shivkumar.org/music/basics/sarali-varisai.htm)
  keeps the tala while increasing note density;
  [janta](https://www.shivkumar.org/music/basics/janta-varisai.htm) describes
  spuritham, not a prescribed silent gap. These lessons credit overlapping
  Karnatik/Ravikiran material; do not count them as independent corroboration.
- [Acharyanet: practical exercises](https://www.acharyanet.com/practical-exercises-in-carnatic-music/)
  distinguishes sustained-note preparation from timed exercises. Its long
  duration targets are teaching suggestions, not universal requirements.
- [Saamaveda Music Academy: beginner modules](https://www.saamavedamusicacademy.in/Free-Basic-Carnatic-Music.php)
  explicitly uses a repeat-after-me format, including varisais, alankaras
  and geethams. This establishes a teaching format, not comparative efficacy
  or appropriate boundaries for every composition.
- [NIDCD: taking care of your voice](https://www.nidcd.nih.gov/health/taking-care-your-voice)
  advises against singing when hoarse or tired. General voice-health guidance
  is not a Carnatic breath map.
- [Ableton: arpeggiator gate](https://www.ableton.com/en/live-manual/12/live-midi-effect-reference/#arpeggiator)
  distinguishes sounding duration from note-onset spacing. It is an audio
  implementation reference, not a Carnatic articulation rule.
- Repp (1995), [*Acoustics, perception, and production of legato articulation
  on a digital piano*](https://doi.org/10.1121/1.413065): perceived legato
  depends on duration, register, interval and listener. This is not a threshold
  study of repeated harmonium notes.
- Bresin & Battel (2000), [*Articulation Strategies in Expressive Piano
  Performance*](https://doi.org/10.1076/jnmr.29.3.211.3092): observed piano
  articulation relative to inter-onset intervals; do not import its measured
  percentages as Carnatic defaults.

None establishes a universal harmonium gap, recovery duration or automatic
breath point at every printed line or tala boundary. The Light preset and
recovery settings are product choices requiring listening/device checks;
phrase annotations require explicit musical review.

Last checked: **7 November 2026**, including the closed issues #1–#7 and their
comments. Links rot; re-check them before a release.

---

## 1. Which source for which job

| Job | Start here | Then check |
|---|---|---|
| Notate a gitam | karnatik.com c-number page | shivkumar.org page + *Sangeeta Bala Padam*; NIOS or Gurukulam & SF for the early ones |
| Notate a swarajati | *Sangeeta Bala Padam* or NIOS | shivkumar.org page + veenagana.org PDF |
| Notate a varnam | shivkumar.org PDF (the four in the app) | *Sangeeta Bala Padam* Part 2, rosemuralikrishnan, patantara, carnaticpedia |
| Confirm a raga's scale and variants | karnatik.com raga page | Raga Surabhi, *Raganidhi*, *Ragas in Carnatic Music*, NIOS ragalakshanas |
| Check a varnam's form and speeds | Wikipedia: Varnam; NIOS pp. 69–72 | Anuradha Mahesh, Morris, Sreenivasaraos |
| Confirm tala angas and jati | karnatik.com tala table | *South Indian Music*, the author's ThaaLam model |
| Decide a just-intonation ratio | Wikipedia: just intonation | 22shruti.com, Helmholtz |
| Hear a piece | Shivkumar's class recordings | Sangeethapriya, Music Academy archives |
| Test pitch or tonic code | YIN paper, Saraga annotations | librosa, aubio, Carnatic Varnam Dataset |
| Check accessibility | WCAG 2.2 | Android and Apple platform docs |
| Check a word or transliteration | *Sangeeta Bala Padam* / Vignanam | ISO 15919, IAST |

---

## 2. Notation and repertoire

### 2.1 karnatik.com (Rani)

The "Royal Carpet: karnATik" site is the project's **primary** notation
source. It is a large, free teaching archive: lessons, geetams, swarajatis,
varnams, krithis, raga pages, tala tables, a glossary and a notation key.
The notation marks octaves by case (capital = upper, trailing dot = lower),
which is why every import has to be re-encoded into the app's grammar
([`docs/plan.md`](plan.md) 3.2).

- Home: <https://karnatik.com/>
- Beginner lessons (including the alankarams in lesson 6):
  <https://karnatik.com/lesson6.shtml>
- Geetams index: <https://karnatik.com/geetams.shtml>
- Ragas: <https://karnatik.com/ragas.shtml>
- Tala table: <https://karnatik.com/taalatable.shtml>
- Notation and symbols: <https://karnatik.com/symbols.shtml>
- Glossary: <https://karnatik.com/glossary.shtml>
- Composers: <https://karnatik.com/composers.shtml>
- The site's own 400-book bibliography: <https://karnatik.com/booklist.shtml>

Pieces used so far:

- Alankarams: [lesson 6](https://karnatik.com/lesson6.shtml)
- Sri Gananatha [c1806](https://karnatik.com/c1806.shtml),
  Kunda Gaura [c1807](https://karnatik.com/c1807.shtml),
  Kereya Neeranu [c1808](https://karnatik.com/c1808.shtml),
  Padumanabha [c1809](https://karnatik.com/c1809.shtml),
  Analekara [c2415](https://karnatik.com/c2415.shtml)
- Rara Venu Gopa Bala [c1735](https://karnatik.com/c1735.shtml)
- Kamala Jadala [c3550](https://karnatik.com/c3550.shtml) — lyrics and the
  tala label (`tishra triputa`); its notation link points to shivkumar.org,
  so the two are not independent for that piece.

The composer pages are useful for the public-domain check, for example
[Chinnakrishna Dasar (co1112)](https://www.karnatik.com/co1112.shtml),
which identifies him as A.K. Chinnakrishna Naidu, clarinettist, and links
the Sambasiva swarajati (`c2421`).

**Caveats.** Many pages were last updated in 2009 and the site states no
licence. Treat it as a reference and a teaching version, not a critical
edition; ask the author before a store release (already noted in
`ATTRIBUTION.md`). Its raga pages sometimes give a single "standard" scale
where a piece uses a variant, so the variant must be checked separately.
For the four varnams it has lyrics only (or points to shivkumar.org's
notation), which is why it cannot serve as their second source.

### 2.2 Shivkumar Kalyanaraman's archive

A very large krithi archive with notation in HTML, Word and PDF, plus class
and lesson recordings. The "basics" section covers sarali, janta, alankarams,
geethams and varnams. It is the project's main **cross-check** for gitams and
swarajatis, the source of the three geethams adapted from Chitraveena
Ravikiran's *Perfecting Carnatic Music*, and the primary source for the four
varnams.

- Archive home: <https://www.shivkumar.org/music/>
- Basics, geethams, swarajatis, varnams:
  <https://www.shivkumar.org/music/varnams/index.html>
- Manodharma (alapana, neraval, swarakalpana):
  <https://www.shivkumar.org/music/manodharma/index.html>
- The class recordings are also on
  [Spotify](https://open.spotify.com/show/58TvKiAEXDYGj9fl2FDHbi) and
  [Apple Podcasts](https://podcasts.apple.com/in/podcast/south-indian-classical-carnatic-music-archive-classes/id1546593201).

**Caveats.** The HTML pages often lose the octave and variant marks; the PDF
on the same page usually keeps them. In this archive a capital letter can
mark *length* (two cells) rather than octave, and `;` can mark a two-cell
hold. Its varnam PDFs also carry a hooked-comma rest mark that the HTML
loses; that mark is why the varnam encodings use `_` in places. No licence
is stated; the pages credit their own notation sources, and those credits
should be carried into `ATTRIBUTION.md`. Use the class recordings to hear a
phrase, not as a notation source.

The four varnams' letter streams match this archive's HTML exactly (312,
317, 346 and 444 letters, via `tools/notation/crosscheck_varnams.py`), but
that is an extraction check, not a second source: the HTML and the PDF are
the same source.

### 2.3 *Sangeeta Bala Padam* (also published as *Ganamrutha Bodhini*)

A.S. Panchapakesa Iyer, English edition, Giri Trading Agency,
ISBN 81-7950-877-3. The project's print primary source for the Sambasiva
Yenave swarajati (SWARAJATI-2, pp. 82–84) and a cross-check for the first
gitam (Gitam 1, p. 59).

- Used for: Sambasiva Yenave (swaras, words, section order); Sri Gananatha
  (refrain and words).
- **Part 2** is the varnam volume: Giri Trading, ISBN 978-81-7950-528-1,
  Adi and Ata tala varnams, one Pancha Nadai varnam and some Pada varnams.
  Its contents page would settle whether the four varnams in the app are
  covered; that check is still open. Available from Giri Trading:
  <https://giri.in>, <https://giriusa.com>.
- Related volumes by the same author: *Ganamrutha Varnamalika* (varnams) and
  *Purandara Ganamrutam* (Purandara Dasa compositions with notation).
- A scan of Part 1 is on
  [Scribd](https://www.scribd.com/document/525450137/109680573-Sangeetha-Baala-Paadam-1);
  it is a re-upload with no contents listing, useful only for a quick look.
- **Caveats.** Print only, and editions and page numbers vary. Record the
  edition, page and date photographed in the notation file's header. The
  book's raga headers occasionally carry misprints, but the Khamas G2/N1
  header is **not** one of them: the book and the NIOS textbook both print
  the G2/N1 (bhashanga) Khamas, while karnatik.com and shivkumar.org print
  the common G3/N2. That is a real two-tradition difference; the app encodes
  G3/N2, and issue #1 records the choice as a teacher question.
  (`ATTRIBUTION.md` and `Swarajati.kt` still treat the header as a misprint;
  that wording predates the NIOS check and should be updated.) The dots over
  some Ṡ/Ṙ in the book could not be decoded from the photos; they do not
  change the Sambasiva pitch sequence.

### 2.4 The Gurukulam & SF geetham collection

A graded, 89-page geetham collection, published as
`geetham_notes.pdf` by Gurukulam & SF:
<https://gurukulamacademy.in/geetham_notes.pdf>. This is the "Gurukulam &
SF" collection the owner supplied for Kamala Sulochana; the public copy
makes it a proper source rather than a private photograph.

Its contents page lists three groups:

- **Pillari geetas:** Sree Ganapati Praarthana (Malahari, p. 8), Kunda Gaura
  (p. 10), Kereya Neeranu (p. 12), Padumanabha (p. 14), Bhaya Samaya
  (Dasaakshi, p. 17), Aanalekara (Suddha Saveri, p. 19).
- **Samchari geetas:** Janakasuthaa (Saveri, p. 22), Sree Raama Chandra
  (Bhairavi, p. 24), Kamalasulochana (Anandabhairavi, p. 26), Varaveenaa
  (Mohanam, p. 29), Mandaradharaare (Kambhoji, p. 31), Govindaachyuta
  (Shankarabharanam, p. 33), Sarasvati (Bagada, p. 35), Kamalajadala
  (Kalyani, p. 37).
- **Ghana raga panchaka geetas:** Amareekabaree (Nata, p. 40), Sree Raama
  Chandra (Gowla, p. 42), Rae Rae Sree Raama (Arabhi, p. 44), Meenaakshee
  Jaya Kaamaakshee (Sree, p. 46), Vande Madhavam (Varali, p. 49).

It is the primary for Kamala Sulochana (pp. 26–28), a second source for
Analekara (pp. 19–20), a third source for Vara Veena (pp. 29–30), and a
source of variants for Kamalajadala (p. 37), Re Re Sri Ramachandra (p. 44)
and Sri Ramachandra (p. 24). It also contains the next geethams the app
might take up (Mandaradharaare, Govindaachyuta, Sarasvati and the Ghana raga
set), which makes it the obvious next repertoire source.

**Caveats.** It is a typeset teaching collection, not a critical edition,
and it states no licence. It sometimes writes a piece an octave higher or
in a different tala from the chosen source (Kamalajadala is Tisra Triputa
and an octave up; Re Re is Chaturasra Rupaka), so compare it cell by cell
and keep the chosen reading in the notation header. The collection's
Kamala Sulochana pages are the owner's primary; a separate staff-and-sargam
sheet for the same piece was supplied privately and is not online.

### 2.5 The NIOS Carnatic Music textbook

*Carnatic Music: Secondary Level Course (Practical)*, National Institute of
Open Schooling (an autonomous organisation under the Ministry of Human
Resource Development, Government of India), free PDF:
<https://www.nios.ac.in/media/documents/Carnatic_Music_243/Carnatic%20Music%20Book2%20(Complete).pdf>.
It is a 101-page print textbook with full notation and tala markers, and it
has become one of the most useful independent checks in the project.

- Analekara (PDF p. 59): `P , P D D D P` on both occurrences. Its pitch
  reading (`P , P`) agrees with the teacher's decision; the app keeps
  karnatik.com's karvai for the D, as the notation header explains.
- Vara Veena (PDF p. 60): full notation, Rupaka.
- Sambasiva Yenave (PDF pp. 64–68; printed pp. 59–63): the complete
  swarajati, including the khanda-gati charanam — a third print source.
  Its Khamas ragalakshana prints `S M1 G2 M1 P D2 N1 S`, the G2/N1
  tradition (see 2.3).
- Jalajaksha (PDF pp. 70–72): pallavi, anupallavi, muktayi swaram and
  charanam, in Hamsadhwani/Adi.
- It also describes the varnam form and its purvanga/uttaranga sections
  (PDF pp. 69–72), which is useful when checking the app's sung order.

**Caveats.** It is a government textbook, so check its terms before
redistributing text or scans; the notation itself is being used as a
reference, not copied. It has its own slips: the Analekara heading says
"Sudha Dhanyasi" although the notation is Suddha Saveri, and its Khamas
G2/N1 belongs to the other tradition. Use it as a print witness, not as a
final authority.

### 2.6 *Perfecting Carnatic Music, Level 1* — Chitraveena N. Ravikiran

A graded course by Chitraveena N. Ravikiran. Shivkumar's pages for
Kamalajadala (Kalyani), Re Re Sri Ramachandra (Arabhi) and Sri Ramachandra
(Bhairavi) are adaptations of this book, so it is the second source behind
those three geethams. Useful for the order of the early repertoire and for
Ravikiran's notation conventions. The book itself is copyrighted: check
against it, do not copy its pages into the repository.

### 2.7 veenagana.org

A small site with notation PDFs, used for the Sambasiva Yenave cross-check
(Vasanthi Iyer's PDF). Direct PDF links are fragile; keep the source URL and
the date accessed in the notation header.

- <http://veenagana.org/>
- Sambasiva PDF:
  <http://veenagana.org/wp-content/uploads/2017/12/SAMBASHIVA.pdf>

### 2.8 Vignanam

Telugu devotional lyrics with meanings, used to check the sahityam of Kamala
Sulochana. It gives words, not notation, so it cannot confirm a tune.

- <https://vignanam.org/>
- Kamala Sulochana:
  <https://vignanam.org/english/carnatic-music-geethams-kamala-sulochana.html>

### 2.9 Rasikas.org

A long-running forum and sahitya wiki. Good for asking about a pathantaram,
finding a lec-dem reference, or seeing how other singers render a phrase.
User-generated, so it is a pointer to a source, never a source on its own.

- Portal: <https://www.rasikas.org/portal/>
- Forums: <https://www.rasikas.org/forums/>

### 2.10 The owner's own materials

- The Mayamalavagowla practice workbook
  (`~/baijum/mayamalavagowla/notes.md`) is the primary for the sarali,
  madhya sthayi, janta, vakra janta and dhattu varisais.
- The Gurukulam & SF collection is now public (see 2.4); the separate
  staff-and-sargam sheet for Kamala Sulochana was supplied privately and is
  not online.
- The workbook was also the outline for the website's Learn section
  (`docs/plan-learn.md`): it suggested the topics and their order, but no
  text was taken from it and the site does not name it. Every claim on the
  Learn pages was checked against two of the public sources below instead.
- The workbook and the staff sheet are not public. Their provenance is
  recorded in the notation headers; where an online second source exists it
  is used, and where none exists the gap is recorded rather than filled
  from memory.

### 2.11 Sources used for one-off checks

These are the pages and PDFs that settled a single cell or supplied a
manual second look. They are not systematic sources; use them the way the
issues did, and record the result in the notation header.

| Source | Covers | Notes |
|---|---|---|
| [eviolinguru.com Analekara sheet](https://www.eviolinguru.com/uploads/7/3/5/7/735729/_analekara.pdf) | Analekara, Geetham 6 | One-page PDF; gives `P , P \| D D \| D P` both times. |
| [meanjalisudheer.com Analekara image](https://meanjalisudheer.com/wp-content/uploads/2022/05/Aanalekara-Swaram-Sahityam-e1651836354204.png) | Analekara | Swaram-sahityam image; same reading. |
| [Carnaticpedia Analekara PDF](https://carnaticpedia.s3.us-west-2.amazonaws.com/Analekara_suddha_s_Averi_tripu_Ta_purandarad_Asa_4417757afa.pdf) | Analekara | Full two-page notation; same reading. |
| [Carnaticpedia Sami Ninne Kori](https://carnaticpedia.com/notations/20/sami_ninne_kori) | Sami Ninne Kori | JavaScript-rendered; needs a browser check. |
| [rosemuralikrishnan.com Ninnukori](https://www.rosemuralikrishnan.com/1-ninnukori-varnam--mohanam.html) | Ninnukori | Dr. Rose Muralikrishnan's full notation; personal use only. Its pallavi opening matches cell for cell. |
| [patantara.com Evvari Bodhana](https://patantara.com/notations/w6fDsmvCkT) | Evvari Bodhana | CC BY-NC-SA, rendered as images; manual compare still open. |
| [musicmaster.in Kamala Sulochana](https://musicmaster.in/blog/kamala-sulochana-geetham/) | Kamala Sulochana | Lyrics, scale and a notation embed; no independent notation. |
| [lemez.net Kamala Jadala documentation](https://www.lemez.net/yoga-nadopasana/assets/pdf/KamalaJadalaDocumentation.pdf) | Kamala Jadala | A 22-page student paper; its bibliography cites shivkumar and karnatik, so it is derived, not independent. |
| [Scribd Sangeeta Bala Padam Part 1](https://www.scribd.com/document/525450137/109680573-Sangeetha-Baala-Paadam-1) | Sangeeta Bala Padam, Part 1 | A re-upload with no contents listing; quick look only. |

### 2.12 What came from where

| Piece | Primary | Cross-check |
|---|---|---|
| Sarali, Madhya Sthayi, Janta, Vakra Janta, Dhattu | owner's workbook | — (traditional sequences; the workbook is the record) |
| Alankarams | karnatik lesson 6 | — |
| Sri Gananatha, Kunda Gaura, Kereya Neeranu, Padumanabha | karnatik c1806–c1809 | shivkumar.org + *Sangeeta Bala Padam* |
| Analekara | karnatik c2415 | shivkumar.org, NIOS p. 59, Gurukulam pp. 19–20, eviolinguru, meanjalisudheer, Carnaticpedia; teacher settled `P , P` |
| Rara Venu Gopa Bala | karnatik c1735 | shivkumar.org |
| Sambasiva Yenave | *Sangeeta Bala Padam* | shivkumar.org + veenagana PDF + NIOS pp. 64–68 (third print source; not yet cell by cell) |
| Kamalajadala, Re Re Sri Ramachandra, Sri Ramachandra | shivkumar.org PDFs (after Ravikiran) | single source by owner's decision, issue #1; Gurukulam pp. 37/44/24 give variants |
| Kamala Sulochana | Gurukulam & SF pp. 26–28 | staff-and-sargam sheet (private); Vignanam (sahityam); musicmaster (lyrics/scale) |
| Vara Veena | shivkumar.org PDF | NIOS p. 60 + Gurukulam pp. 29–30; sourced, not yet encoded |
| Jalajaksha | shivkumar.org PDF | NIOS pp. 70–72; encoded |
| Ninnukori | shivkumar.org PDF | rosemuralikrishnan.com (personal use); hand-checked opening |
| Evvari Bodhana | shivkumar.org PDF | patantara.com (manual check open) |
| Sami Ninne Kori | shivkumar.org PDF | carnaticpedia.com (manual check open) |

The varnams are built but still effectively single-source: the HTML/PDF
comparison validates the transcription, not an independent notation. The
second-source checks above are the work that would change that.

### 2.13 Source differences recorded in the issues

These are the disagreements that matter for playback, with where each was
resolved. The notation file headers remain the authority; this table is a
map to them.

| Piece | Question | What the sources say | Decision |
|---|---|---|---|
| Analekara | Third cell of "ta-la di-nam" | karnatik.com `M`; shivkumar.org, NIOS, Gurukulam, eviolinguru, meanjalisudheer and Carnaticpedia `P` | Teacher settled `P`; the hold stays karnatik's karvai (`P , P \| D , \| D P`). |
| Khamas (Sambasiva) | G2/N1 or G3/N2 | *Sangeeta Bala Padam* and NIOS print G2/N1; karnatik.com and shivkumar.org print G3/N2 | App encodes G3/N2; issue #1 records the two-tradition difference as a teacher question. |
| Kamalajadala | Tala | karnatik.com and Gurukulam: Tisra Triputa; shivkumar.org: Misra Chapu (the same 7-akshara cycle) | Encoded Tisra Triputa; chapu bars noted as a variant. |
| Re Re Sri Ramachandra | Tala | shivkumar.org: Tisra Triputa; Gurukulam: Chaturasra Rupaka | Encoded Tisra Triputa; same letters. |
| Sri Ramachandra | Dhaivata | shivkumar.org: D1 variant; Gurukulam: plain D2 | Encoded D1; variant noted. |
| Sambasiva | Charanam 4, avarta 1, cell 15 | Book `G`; both webs `M` | Encoded the book's `G`; both variants listed. |
| Sambasiva | Pallavi word placement | Book: "mba" on N; shivkumar.org: on the re-struck S | Pitch is the same; the book's sahityam alignment is kept. |
| Sami Ninne Kori | Dhaivata variant | shivkumar.org's PDF underlines some Ns (its probable anya N2); the app writes the raga's N3 | Teacher question; flagged in `Varnam.kt`. |
| Kamala Sulochana | Two mandra-Ni dips | Collection plain; staff sheet dots the Ni | Encoded as the collection has them, for the teacher. |

The issue threads carry the full cell-by-cell tables and the reasoning; this
document only records where the material lives. They are at
<https://github.com/baijum/purandara/issues?q=is%3Aissue+is%3Aclosed>.
Searches that came up empty for a particular piece (for example kksongs.org,
carnaticstudent.org and dharmasampradayam.com for Kamala Jadala) are recorded
there too, so the next person does not repeat them.

## 3. Raga, tala and theory

### 3.1 Online

- karnatik's guided tour: swaras, melakarta, janya, identifying ragas, tala,
  gamakas, notation symbols, glossary —
  <https://karnatik.com/infoindex.shtml>
- Wikipedia, for orientation and vocabulary:
  [Carnatic music](https://en.wikipedia.org/wiki/Carnatic_music),
  [Melakarta](https://en.wikipedia.org/wiki/Melakarta),
  [Raga](https://en.wikipedia.org/wiki/Raga),
  [Tala (music)](https://en.wikipedia.org/wiki/Tala_(music)),
  [Gamaka (music)](https://en.wikipedia.org/wiki/Gamaka_(music)),
  [Śruti (music)](https://en.wikipedia.org/wiki/%C5%9Aruti_(music)),
  [Varnam](https://en.wikipedia.org/wiki/Varnam),
  [Purandara Dasa](https://en.wikipedia.org/wiki/Purandara_Dasa),
  [Haridasa](https://en.wikipedia.org/wiki/Haridasa)
- Guruguha: English translations of the lakshana texts, including the
  *Sangita Sampradaya Pradarshini*, *Chaturdandi Prakashika*,
  *Swaramelakalanidhi* and *Ragatarangini*, plus Dikshitar kritis —
  <http://www.ibiblio.org/guruguha/>
- Anuradha Mahesh's lessons: varisai notations, varnam analyses, voice and
  raga notes — <https://anuradhamahesh.wordpress.com/carnatic-music-lessons-2/>
- Raga Surabhi: raga introductions, audio examples and quizzes —
  <https://ragasurabhi.com/>. Its audio is for personal listening only
  ("all rights reserved"); do not bundle it.
- Acharyanet: video lessons, masterclasses and e-books, including Dasa Padas
  and the *Perfecting Carnatic Music* course — <https://www.acharyanet.com/>
- The Music Academy, Madras: journal, books and the Tag digital archives —
  <https://musicacademymadras.in/>

### 3.2 Books

| Book | Author / publisher | Use it for |
|---|---|---|
| *South Indian Music*, 6 vols | P. Sambamoorthy, Indian Music Publishing House | The standard reference for swara, raga, tala, gamaka and instruments. Dense but reliable. |
| *A Dictionary of South Indian Music and Musicians*, 3 vols | P. Sambamoorthy | Names, terms, composers; the published volumes run A–N. |
| *Ragas in Carnatic Music* | S. Bhagyalekshmy, CBH Publications, 1990 | Raga scales, melakarta and janya listings; quick lookup. |
| *Raganidhi*, 4 vols | B. Subba Rao, Music Academy, 1955–66 | Raga lakshana with compositions; useful when a raga page is thin. |
| *The Ragas of Karnatic Music* | N.S. Ramachandran, University of Madras, 1938 | Historical and comparative scale analysis. Free scan: <https://archive.org/details/dli.ernet.234506> |
| *The Oxford Illustrated Companion to South Indian Classical Music* | Ludwig Pesch, Oxford University Press | Context: history, instruments, performance, listening. Good for the guide and the site, not for notation. |
| *The Grammar of Carnatic Music* | K.G. Vijayakrishnan, Mouton de Gruyter, 2007 | A formal, generative account of raga grammar; useful when modelling phrases, not for importing notes. |
| *A Southern Music: The Karnatik Story* | T.M. Krishna, HarperCollins, 2013 | History, aesthetics and the social life of the music; a perspective, not a notation authority. |
| *History of South Indian (Carnatic) Music* | R. Rangaramanuja Iyengar | The historical narrative behind the repertoire. |
| *Indian Music: History and Structure* | Emmie Te Nijenhuis, E.J. Brill, 1974 | Historical theory and structure, with sources. |
| *Sangita Ratnakara* | Sarangadeva (13th c.), trans. R.K. Shringy and Prem Lata Sharma | Medieval theory of raga, tala and gamaka. Free scans: <https://archive.org/details/sangita-ratnakara-vol-02> |
| *Chaturdandi Prakashika* | Venkatamakhin (17th c.) | The melakarta scheme and its rationale. English translation: <http://www.ibiblio.org/guruguha/cdp.htm> |
| *Swaramelakalanidhi*; *Ragatarangini* | Ramamatya; Locanakavi | Earlier lakshana texts in English at Guruguha: <http://www.ibiblio.org/guruguha/smk.htm>, <http://www.ibiblio.org/guruguha/ragat.htm> |
| *Sangita Sampradaya Pradarshini* | Subbarama Dikshitar, 1904; English translations | The great early-twentieth-century compilation of lakshana and notation; the reference for older pathantarams. English version at Guruguha: <http://www.ibiblio.org/guruguha/ssp.htm>; the Music Academy's translation project is ongoing; scans: <https://archive.org/details/sspradarshini1961sangitasampradayapradarshiniofsrisubbaramadikshitar> |
| *Carnatic Music Reader*, 7 books | V.K. Krishna Prasad, CBH Publications | Carnatic music in Western staff notation; a useful independent check on pitch and rhythm. |
| *A Practical Course in Karnataka Sangitam* | N.C. Parthasarathy and Dwaraka Parthasarathy | Graded lessons and varnams; another teaching sequence to compare with the app's. |
| *Raga Pravaham* | D. Pattammal and M.N. Dhandapani | Over 5,000 ragas with arohana/avarohana, synonyms and equivalents; useful when a name or scale is uncertain. |
| *Alphabetical Index of Karnatic Songs* | Lakshman Ragde | 33,400 song entries by title, raga, composer and mela; the place to identify a piece before notating it. |
| *Compositions of Thyagaraja*; *Compositions of Muthuswamy Dikshitar*; *Compositions of Shyama Shastri* | T.K. Govinda Rao, Ganamandir Publications | The standard notation collections for the Trinity; for the krithi stage, not yet built. |

The compositions themselves are traditional or long in the public domain,
but a modern edition, translation or notation carries its own editorial
copyright. Check against a book; do not scan or copy it into the app.

### 3.3 Journals and archives

- *Journal of the Music Academy, Madras* —
  <https://musicacademymadras.in/journal/>; the Academy's Tag digital
  archives are linked from its home page.
- *Sruti* (Chennai monthly) — <https://sruti.com/>; interviews, profiles and
  reviews, useful for the human side of the repertoire.
- Sangeet Natak Akademi — <https://sangeetnatak.gov.in/>; publications and
  archival material on Indian music and dance.
- Indira Gandhi National Centre for the Arts — <https://ignca.gov.in/>;
  manuscripts, recordings and publications.
- Internet Archive — many older books and scans, including the ones linked
  above. Quality and script vary; check the edition before relying on a page.

### 3.4 Form and performance practice

- Wikipedia, [Varnam](https://en.wikipedia.org/wiki/Varnam): the
  purvanga/uttaranga sections, the slow-then-double-speed opening, and the
  two-kalai Adi convention the app's `kalai: 2` follows.
- NIOS *Carnatic Music* Book 2, PDF pp. 69–72: the same form described in a
  teaching sequence, with the four varnams.
- Anuradha Mahesh's [Carnatic Music
  Lessons](https://anuradhamahesh.wordpress.com/carnatic-music-lessons-2/),
  especially the Ata tala varnam analyses and the varisai notes.
- Robert Morris, "Tana Varnam-s: An Entry into Rāga Delineation in Carnatic
  Music", *Analytical Approaches to World Music* 1(1), 2011 —
  <https://journal.iftawm.org/wp-content/uploads/2022/02/Morris_AAWM_Vol_1_1.pdf>.
- Sreenivasaraos, [Tana
  Varna](https://sreenivasaraos.com/tag/tana-varna/): the gana-krama, the
  historical speeds, and the shift from three speeds to the modern two.

---

## 4. Purandara Dasa and the Haridasa tradition

The app is named after Purandara Dasa (1484–1564 in the app's usage), the
*Pitamaha* of Carnatic music. Sources differ on his birth year (1470 and 1484
both appear); the app follows 1484–1564, and a future note should say which
source that choice follows.

**Composer of Sambasiva Yenave.** karnatik.com credits Chinnakrishna Dasar
and identifies him as A.K. Chinnakrishna Naidu, a clarinet exponent; his son
A.K.C. Natarajan (b. 1931) was also a clarinettist. The public-domain check
is recorded in issue #1, with the [karnatik composer
page](https://www.karnatik.com/co1112.shtml), [Natarajan's article on the
clarinet](http://www.carnatica.net/sangeet/clarinet.htm), and [Sriram V's
history of the
instrument](https://sriramv.com/2008/09/09/the-carnaticisation-of-the-clarinet).
No birth or death dates are recorded; the check rests on the family's dates
and, if the composer and the Trichy clarinettist are two people, on the
post-Trinity nineteenth-century dating. Either way the piece is out of
copyright.

- Wikipedia: <https://en.wikipedia.org/wiki/Purandara_Dasa>
- karnatik composers page: <https://karnatik.com/composers.shtml>
- *Purandara Sahitya Darshana*, vol. 1 (Kannada, scanned):
  <https://archive.org/details/PurandaraSahityaDarshanaVolume1>
- *Purandara Daasara Padagalu* (Kannada, scanned):
  <https://archive.org/details/PurandaraDaasaraPadagalu>

Books and collections:

| Book | Author / publisher | Use it for |
|---|---|---|
| *Purandara Ganamrutam* | A.S. Panchapakesa Iyer, Ganamruta Prashuram, 1983 | Purandara Dasa compositions with notation; a print cross-check. |
| *Sree Purandara Gaanamrutham* (text with notation) | — (library catalogue record) | A notation collection; verify the edition before relying on a page. |
| *Shri Purandara Dasarin Kirtana Malika* | Chittoor Appanna Bhagavatar, 1984 | Traditional collection of the songs. |
| *Purandara Dasar Keerthanaigal Thamizhakkam*, 2 vols | T.K. Jagannathan, 2006–07 | Tamil transliteration and meaning; useful for word checks. |
| *Dasa Padas* course | R.K. Shrikantan, Acharyanet | Listening and learning the style; not a notation source. |

---

## 5. Shruti, tuning and acoustics

The app plays each swara at a just-intonation ratio over a real tanpura,
using equal-tempered harmonium samples shifted by a few cents. These
references explain the ratios and the trade-offs.

- 22shruti.com — Dr Vidyadhar Oke's work on the 22 shrutis: ratios,
  demonstrations, and a shruti-based approach to tuning —
  <https://www.22shruti.com/>
- Wikipedia: [Śruti](https://en.wikipedia.org/wiki/%C5%9Aruti_(music)),
  [Just intonation](https://en.wikipedia.org/wiki/Just_intonation),
  [Equal temperament](https://en.wikipedia.org/wiki/Equal_temperament),
  [Cent (music)](https://en.wikipedia.org/wiki/Cent_(music)),
  [Harmonic series (music)](https://en.wikipedia.org/wiki/Harmonic_series_(music)),
  [A440](https://en.wikipedia.org/wiki/A440_(pitch_standard))
- Hermann Helmholtz, *On the Sensations of Tone* — the classic account of
  consonance, dissonance and just ratios.
- Arthur Benade, *Fundamentals of Musical Acoustics* — how a drone, a string
  and a voice actually behave.
- William Sethares, *Tuning, Timbre, Spectrum, Scale* — why timbre and tuning
  are linked; useful for explaining the harmonium/tanpura choice.
- J. Murray Barbour, *Tuning and Temperament: A Historical Survey* — the
  history of temperaments, including the ones the app does not use.
- W.A. Mathieu, *Harmonic Experience* — a practical, ear-first approach to
  just intonation.
- David B. Doty, *The Just Intonation Primer* — a short, precise handbook of
  ratios and lattices.

The app's own model is in `shared/.../domain/Swara.kt` (ratios) and
`HarmoniumBank`/`TanpuraBank` (playback); [`tuning.md`](tuning.md) compares the
Just and Equal choices with exact ratios, cents, frequencies and code paths
(the guide's [Tuning page](../site/pages/guide/tuning.html) is the musicians'
version).
These references are for checking and explaining it, not for changing it
casually.

---

## 6. Recordings, samples and datasets

### 6.1 The recordings already bundled

- Harmonium: "Harmonium samples – all keys and drones" by **donyaquick**,
  cut into single notes by **cabled_mess**, CC0 —
  <https://freesound.org/s/330410/>,
  <https://freesound.org/people/cabled_mess/packs/29512/>
- Tanpura: "Acoustic Tanpura 10" by **Sankalp Gulati**, CC BY 4.0 —
  <https://freesound.org/s/155272/>
- Pitch model: **SwiftF0** by lars76, MIT —
  <https://github.com/lars76/swift-f0>, paper
  [arXiv:2508.18440](https://arxiv.org/abs/2508.18440)

Every change made to these is listed in [`ATTRIBUTION.md`](../ATTRIBUTION.md).
Freesound is also the first place to look for a replacement or an additional
instrument, but check the licence per sound, not per site.

### 6.2 Research corpora (CompMusic / MTG, UPF)

These are for testing and for understanding real performances. They are not
bundled, and their licences differ: check each Zenodo record before using
anything in the app or the repository.

| Dataset | What it contains | Link |
|---|---|---|
| Saraga Carnatic | Time-aligned melody, rhythm and structure annotations for a large Carnatic corpus | <https://github.com/MTG/saraga> · <https://zenodo.org/records/5554704> |
| Carnatic Varnam Dataset | 28 solo vocal varnam recordings with tala cycles and machine-readable swara notation; made for intonation analysis | <https://zenodo.org/records/1257117> |
| Indian Music Tonic Dataset | 597 recordings annotated with the tonic, for tonic-identification work | <https://zenodo.org/records/1257113> |
| Carnatic Music Rhythm Dataset | 176 excerpts in four talas with time-aligned tala markers | <https://zenodo.org/records/1264393> |
| Mridangam Stroke Dataset | 7,162 isolated mridangam strokes at six tonics | <https://zenodo.org/records/1265187> |
| Mridangam Tani-avarthanam | Two transcribed tani-avarthanams, about 8,800 strokes | <https://zenodo.org/records/1266206> |
| Raga recognition datasets | Carnatic and Hindustani audio with raga labels | <https://doi.org/10.5281/zenodo.7278505> |

The CompMusic corpora page lists the rest:
<https://compmusic.upf.edu/corpora>.

### 6.3 Audio and analysis tools

Development only; none of these become a runtime dependency.

- **Audacity** — <https://www.audacityteam.org/> — trimming, listening,
  spectral views.
- **SoX** — <https://sox.sourceforge.net/> — scripted conversion and
  resampling.
- **FFmpeg** — <https://ffmpeg.org/> — format conversion used by the sample
  scripts.
- **Rubber Band** — <https://breakfastquay.com/rubberband/> — pitch shifting
  with tempo/formants preserved (used for the tanpura loops).
- **Praat** — <https://www.praat.org/> — pitch and formant analysis, useful
  when a sung phrase is in doubt.
- **Sonic Visualiser** — <https://www.sonicvisualiser.org/> — inspecting
  pitch tracks and annotations.
- **librosa** — <https://librosa.org/> — reference implementations of pitch,
  onset and chroma.
- **aubio** — <https://aubio.org/> — lightweight onset and pitch detection.
- **Essentia** — <https://essentia.upf.edu/> — MIR analysis from the same
  group as CompMusic.

---

## 7. Pitch detection and music information retrieval

The app's `PitchDetector` is YIN, checked by SwiftF0 through ONNX Runtime.
The code was ported from the author's
[Ukulele Companion](https://github.com/baijum/ukulele-companion) (MIT).

- A. de Cheveigné and H. Kawahara, "YIN, a fundamental frequency estimator
  for speech and music", *JASA* 111(4), 2002 —
  <https://doi.org/10.1121/1.1458024>
- M. Mauch and S. Dixon, "pYIN: A fundamental frequency estimator using
  probabilistic threshold distributions", ICASSP 2014 —
  <https://doi.org/10.1109/ICASSP.2014.6853678>
- J.W. Kim et al., "CREPE: A convolutional representation for pitch
  estimation", ICASSP 2018 —
  <https://doi.org/10.1109/ICASSP.2018.8461329>
- L. Nieradzik, "SwiftF0", 2025 —
  <https://arxiv.org/abs/2508.18440>
- P. Rao, H.A. Murthy and S.R.M. Prasanna, *Indian Art Music: A
  Computational Perspective* — free ebook:
  <https://play.google.com/store/books/details?id=g-2rEAAAQBAJ>
- ISMIR proceedings — <https://ismir.net/> — the yearly source for
  tonic, raga, tala and singing-voice work.
- CompMusic publications — <https://compmusic.upf.edu/publications>.

The app deliberately scores simple, explainable things (in tune, close, high,
low, octave off, different swara) rather than trying to transcribe a
performance. These references are for improving the detector, not for adding
a new feature without a plan.

---

## 8. Language, script and type

The app writes words in simple Latin, one syllable per cell, long vowels
single (`sri gananatha`). These references are for checking transliteration
and for the website's type, not for changing that convention.

- ISO 15919 (transliteration of Indic scripts) —
  <https://www.iso.org/standard/28333.html>
- IAST —
  <https://en.wikipedia.org/wiki/International_Alphabet_of_Sanskrit_Transliteration>
- Unicode combining diacritics (the marks used for Ḍ, Ṛ, Ṇ) —
  <https://unicode.org/charts/PDF/U0300.pdf>
- Noto fonts, for any future script support —
  <https://fonts.google.com/noto>
- Wikipedia: [Telugu script](https://en.wikipedia.org/wiki/Telugu_script),
  [Kannada script](https://en.wikipedia.org/wiki/Kannada_script)

The website already bundles **Mukta** and **Rozha One** under the SIL Open
Font License; see `ATTRIBUTION.md` and `site/static/fonts/`.

---

## 9. Platform, accessibility and engineering

- WCAG 2.2 — <https://www.w3.org/TR/WCAG22/>
- Android accessibility —
  <https://developer.android.com/guide/topics/ui/accessibility>
- Compose accessibility —
  <https://developer.android.com/develop/ui/compose/accessibility>
- Apple Human Interface Guidelines: Accessibility —
  <https://developer.apple.com/design/human-interface-guidelines/accessibility>
- SwiftUI accessibility —
  <https://developer.apple.com/documentation/swiftui/view-accessibility>
- Material 3 — <https://m3.material.io/>
- Kotlin Multiplatform —
  <https://kotlinlang.org/docs/multiplatform.html>
- Android `AudioTrack` —
  <https://developer.android.com/reference/android/media/AudioTrack>
- Apple `AVAudioEngine` —
  <https://developer.apple.com/documentation/avfaudio/avaudioengine>
- ONNX Runtime — <https://onnxruntime.ai/docs/>
- XcodeGen — <https://github.com/yonaskolb/XcodeGen>

The project's own accessibility checklist is in [`AGENTS.md`](../AGENTS.md),
and the automated checks are `:app:connectedDebugAndroidTest` and the iOS
`performAccessibilityAudit()` tests. Those are the authority; the links above
explain the platform rules behind them.

---

## 10. Licensing and attribution

- **karnatik.com, shivkumar.org, veenagana.org, Vignanam, Gurukulam & SF,
  Anuradha Mahesh.** No explicit licence is stated. Use them to check and to
  learn; do not bundle their text, notation or audio. The authors are to be
  asked before a store release, as `ATTRIBUTION.md` already says.
- **NIOS.** A Government of India open-schooling textbook, free to download,
  but check its terms before redistributing text or scans. The app uses it
  as a reference only.
- **Patantara** is CC BY-NC-SA; **rosemuralikrishnan.com** is personal-use
  only; **Raga Surabhi** and **musicmaster.in** reserve their rights;
  **Carnaticpedia** is a JavaScript site with direct PDF links; **Scribd**
  copies are re-uploads, not independent sources. None of their material may
  be bundled without permission.
- **Books.** The compositions may be public domain, but a modern edition,
  translation or notation is not. Quote a line to verify; do not copy pages,
  scans or a whole notation into the repository.
- **Issue comments.** The closed issues record the research and the
  decisions, but the linked page, book or PDF is the source. Cite that, not
  the comment.
- **Freesound.** The harmonium is CC0, the tanpura CC BY 4.0. Keep the
  credits and the list of changes in `ATTRIBUTION.md`.
- **Datasets.** Each Zenodo record has its own licence; several are for
  research use. Check before redistributing anything derived from them.
- **Wikipedia.** Text is CC BY-SA; facts are not. If prose is reused on the
  site, attribute it; do not paste article text into the app.
- **Fonts and code.** Mukta and Rozha One are OFL; the app code is MIT;
  third-party code and models are listed in `ATTRIBUTION.md`.

The hard constraint stands: **no network dependencies**. A source can be
consulted during development, but the app must not call it, bundle it
without a licence, or require it to work.

---

## 11. Adding a source: a short checklist

1. Identify the piece, the exact edition or URL, the page or section, and the
   date you looked at it.
2. Find a **second, independent** source. Two pages that copied each other
   are one source.
3. Compare cell by cell; write down every difference rather than silently
   choosing.
4. Re-encode in the app's grammar; list every fix in the notation file's
   header.
5. Add the credit and the licence or permission note to `ATTRIBUTION.md`.
6. Update the matching `tools/notation/crosscheck_*.py` and keep the expected
   output with it.
7. If there is no second source, open an issue and wait. Never write notation
   from memory.

---

## 12. Keeping this list useful

- Re-check the links before a release; annotate a dead one rather than
  deleting it silently, so the next person knows it was tried.
- Prefer a stable landing page or a DOI over a deep link to a PDF.
- When a source is used for a new piece, add the row to section 2.12 (or to
  `ATTRIBUTION.md`, which is the authority).
- Read the closed issues (#1–#7) when a decision looks arbitrary: the
  cell-by-cell tables and the source links are usually there. The notation
  file headers remain the authority.
- Sangeethapriya (<https://www.sangeethapriya.org/>) was unreachable when
  this list was checked (HTTP 521). Keep it for its concert archive, but do
  not rely on it being up.

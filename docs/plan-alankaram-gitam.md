# Plan: Alankarams and Gitams

Status: **approved** (2026-11-06). Milestone 1 done; 2–4 to come.

The next two stages of the traditional beginner path after the varisais:

- **the seven sapta tala alankarams**, the first talas other than Adi;
- **the Malahari pillari gitams** of Purandara Dasa, the first pieces with words.

Source: karnatik.com, with the gitams cross-checked against shivkumar.org; the
tala model comes from ThaaLam (the author's sibling app). The research is kept
below the milestones.

**Decided with the user:**

- **Śrī Gaṇanātha refrain:** karnatik.com's `S R | M , G R`. shivkumar.org's
  version is noted in the file header as a variant.
- **Alankarams:** the traditional seven, in Mayamalavagowla only.
- **Clicks:** a different click for a clap, a finger count and a wave, taken
  from ThaaLam, as part of the tala step.
- **Words:** simple Latin (`sri gananatha`). The same text is used on screen and
  for screen readers.
- **Gamakas:** the guide explains them and suggests Gentle strictness for
  gitams; there are no gamaka marks in the notation.
- **Rupaka:** drutam first (x v · x 1 2 3), as both sources write it.

**Outcome:** 7 alankarams and 4 gitams, in both apps. They work with the player,
sing-along, history, "next up", accessibility and the website guide.

## Milestone 1: shared talas, clicks, and per-exercise tala and raga ✅

Done 2026-11-06. Differences from the plan below:

- Raga and tala were **removed from `PerformanceSettings`**; `Schedule.tala` and
  `Schedule.raga` come from the exercise, so they can't disagree and no
  `forExercise` factory is needed.
- The harmonium bank now reaches **D5** (MIDI 74): the CC0 source pack has C♯5
  and D5, so no stretching fallback was needed. The existing files came out
  byte-identical.
- A closing `||` at the end of a notation line is allowed, as books write it.
- `Raga.letters` / `Raga.swaras` list only the letters a raga uses
  (`SwaraMapper` uses them, so Malahari doesn't crash on N).


**Tala** (`shared/.../domain/Tala.kt`)

- Add `Tala.suladi(name, shape: List<AngaKind>, jati)`, where `AngaKind` is
  LAGHU, DRUTAM or ANUDRUTAM. It is ported from ThaaLam's
  `~/baijum/ThaaLam/shared/.../domain/TalaLibrary.kt` (`saptaTalas`,
  `angasFor`).
- It produces the existing `TalaBeat` list:
  - a laghu is `x 1 2 …` (CLAP, then COUNT);
  - a drutam is `x v` (CLAP, WAVE);
  - an anudrutam is `x` (CLAP).
- **Dhruva is I O I I.** This corrects ThaaLam, which has I O I O. ThaaLam is
  not touched by this plan.
- New `Talas` catalog object, looked up by key:
  - `adi` (the existing `Tala.ADI`, unchanged);
  - `dhruva-4` (14 beats), `matya-4` (10), `rupaka-4` (6), `jhampa-7` (10),
    `triputa-3` (7), `ata-5` (14), `eka-4` (4).
  - Each has a display name such as "Dhruva" and a jati name.

**Raga** (`Raga.kt`)

- A raga may leave letters out. `swara(letter)` stays.
- Add `uses(letter)`, plus `arohanam` and `avarohanam` lists for display only.
- Add `Raga.MALAHARI`:
  - letters: S R₁ G₃ M₁ P D₁, with no N;
  - ascent: S R M P D S′;
  - descent: S′ D P M G R S.
- `Ragas.byKey("mayamalavagowla" | "malahari")`.

**Exercise model** (`domain/varisai/Varisai.kt`)

- `Varisai` gains:
  - `tala: Tala = Tala.ADI`;
  - `raga: Raga = Raga.MAYAMALAVAGOWLA`;
  - `name: String? = null`, the display title for gitams.
- `Avarta.spoken` reads the exercise's tala; this is already a parameter.
- `WrittenSwara.toString()` writes mandra with a dot below (`Ḍ`).
- `spoken` says "low D" for a mandra swara.

**Parser** (`VarisaiParser.kt`)

- The constructor drops the fixed tala.
- Header lines are allowed after an exercise number:
  - `tala: <key>` (default `adi`);
  - `raga: <key>` (default `mayamalavagowla`);
  - `title: <text>`.
- Avarta length is checked against the exercise's tala.
- The mandra mark is U+02CC after a letter (`Dˌ`), written as `Ḍ` on screen.
- Letters the raga doesn't use are an error.

**Schedule** (`domain/schedule/Schedule.kt`)

- `PerformanceSettings` takes its raga and tala from the exercise.
- New factory `PerformanceSettings.forExercise(varisai, saHz, tempo, kala,
  tuning)` for Swift.
- Callers to update:
  - Android: `PracticePreferences.performance()` gets a `varisai` argument;
    `PracticeViewModel.kt:307`.
  - iOS: `PracticeSettings.swift:53-58`; `PracticeViewModel.swift:159`.
  - The drone-only callers keep `catalog.first`.

**Clicks** (`audio/SampleRenderer.kt:142-154, 307-320`)

- Today there are two click waves (`accentClick`, `plainClick`). Replace them
  with three, chosen by `Kriya`: CLAP, COUNT (finger) and WAVE.
- Use ThaaLam's `ClickSynth.gestureTone` factors:
  - pitch 1.0 / 1.22 / 0.68;
  - decay 0.95 / 0.65 / 1.30;
  - gain 1.0 / 0.82 / 0.78.
- Any noise uses a fixed seed, so renders are repeatable.
- The count-in uses the same waves.

**Top of the harmonium range**

- Some gitams reach upper Ri (Kunda Gaura, Śrī Gaṇanātha). At Sa = C4 that is
  about MIDI 73, above the bank's C5 (72). `Schedule.noteEvent` would throw.
- First choice: regenerate the bank with `MIDI_HIGH = 74` if the CC0 source has
  those notes.
  - Files: `tools/samples/prepare_harmonium.py`, `HarmoniumBank`,
    `BundledSamplesTest`, ATTRIBUTION.md.
- Fallback: `HarmoniumBank.playbackFor` lets only the top sample stretch up to
  +2 semitones.

**Tests** (`shared/src/commonTest`)

- Tala table: beat counts and anga lengths for every key; labels for
  `jhampa-7`; Adi exactly as before. Keep `RagaTalaTuningTest`'s Adi
  assertions.
- Raga: Malahari letters, and rejection of letters a raga doesn't use.
- Parser: headers, mandra mark, wrong length for a non-Adi tala, unknown
  tala or raga key.
- Schedule: beats and contiguity for 7-, 10- and 14-beat talas at kala 1 to 4.
  This generalises `beatsFollowAdiTala` and the contiguity property.
- Renderer: each kriya gets its own wave.

## Milestone 2: alankarams in both apps

**Catalog**

- New `catalog/Alankaram.kt`: the 7 alankarams copied from karnatik.com
  lesson 6, each with its `tala:` header.
  - Lowercase letters become plain swaras; uppercase `S` becomes `S′`.
  - Each alankaram is 10 avartas.
- New `VarisaiSet.ALANKARAM` after DHATTU, with a description.
- Ids are `alankaram-1` to `alankaram-7`, in the order Dhruva, Matya, Rupaka,
  Jhampa, Triputa, Ata, Eka.
- Title: "Alankaram 1 · Dhruva tala".
- History "next up" follows catalog order with no changes:
  `PracticeHistory.nextUp`.

**Catalog tests**

- `everyAvartaIsOneAdiCycle` becomes "one cycle of its exercise's tala".
- Range test per set: varisais and alankarams stay within S to S′.
- Alankaram golden tests: first and last avarta of each.
- `ScheduleTest` "playable across Sa 43–60" covers the new pieces
  automatically.

**Player, both platforms**

- Pass the exercise's tala into the notation and the tala strip. Today both
  fall back to Adi:
  - Android `PlayerScreen.kt:103-116` → `TalaStrip` and `NotationView`
    (`NotationView.kt:62, 216`).
  - iOS `NotationView.swift:13` and `TalaStripView` `:174`, which also has a
    literal `"Adi tala:"` at `:207`. Use `tala.name` instead.
- **Long talas wrap.** When a tala has more than 8 beats, split each notation
  row and the tala strip at the anga boundary nearest the middle:
  - Dhruva: 4+2 | 4+4
  - Matya: 4+2 | 4
  - Jhampa: 7 | 1+2
  - Ata: 5+5 | 2+2

  A shared helper `Tala.rowBreaks()` gives both apps the same split.
- The player heading shows the raga and tala ("Mayamalavagowla · Dhruva tala").
- Sing-along pitch lane: `avartaSeconds` already uses `tala.aksharas`
  (Android `SingAlongViews.kt:63`, iOS `:169`). Pass the right tala.

**Landing page**

- The ALANKARAM section appears with no changes to the code that builds
  sections:
  - Android `LibraryScreen.kt:105` groups by set;
  - iOS `LibraryView.swift:13` maps over `VarisaiSet.entries`.
- Each row adds the tala name.

**Accessibility**

- Android `AccessibilityTest`: a Dhruva strip with the label "Dhruva tala: clap
  1 2 3, clap wave, …" and state "Beat n of 14".
- iOS UI test: audit the Dhruva and Jhampa players. Keep the existing audit
  exception for single swaras.

## Milestone 3: shared gitam grammar and catalog

**Grammar** (update `docs/plan.md` §3.2)

- `[pallavi]`, `[charanam 1]` and similar start a named section.
- `(pallavi)` on its own line repeats that section. Repeats are expanded when
  the piece is parsed, so the schedule, the scoring and the display all follow
  the sung order.
- A line starting with `"` gives the words for the notation line above it:
  - one token per akshara: a syllable, or `-` where the previous syllable
    continues;
  - two syllables joined with `·` when an akshara holds two swaras.
- The word count must match the akshara count. Errors give the line and column.

**Model**

- `Avarta` gains `section: String?`, set on the first avarta of each section,
  and `words: List<String>?`.
- `Avarta.spoken` adds ". Words: sri gana natha".
- `Contour` already handles mandra (negative degrees).

**Catalog** (new `catalog/Gitam.kt`)

- The four Malahari gitams, in karnatik.com's section order:
  - Sri Gananatha, Rupaka;
  - Kunda Gaura, Rupaka;
  - Kereya Neeranu, `triputa-3`;
  - Padumanabha, `triputa-3`.
- `VarisaiSet.GITAM` comes after ALANKARAM. Ids are `gitam-1` to `gitam-4`.
  Titles come from `title:`.
- Words are split at syllable boundaries in simple Latin.
- The file header lists:
  - each transcription fix;
  - each difference from shivkumar.org (the refrain variant, and holds written
    `-` versus a repeated swara);
  - the only mandra note (Padumanābha `R S Ḍ S`).
- Cross-check script `tools/notation/crosscheck_gitams.py`:
  - fetches both sites and compares the swaras of each avarta, like the
    research script;
  - is not part of the build;
  - no copies of the source pages are committed.

**Tests**

- Every gitam parses; repeats are expanded; words align with aksharas.
- Gitam range stays within lower Pa to upper Pa, and every note is playable for
  Sa 43–60. This uses the existing `ScheduleTest` check.
- Golden tests: the first avarta of each section, with its words.

## Milestone 4: gitams in both apps, docs and site

**Notation**

- Each akshara cell shows the swara, the word under it, and the existing mark.
- Section names appear as heading rows: `heading()` on Android,
  `.isHeader` on iOS.
- New choice **Swaras / Words / Both** (default Both):
  - a practice setting on both apps;
  - only shown for exercises with words.

**Sing-along**

- The pitch lane's range follows the piece. Today it is fixed at
  −150..1350 cents (Android `SingAlongViews.kt:197`, iOS `:129`).
- Below Sa, label the lines with mandra dots, as the Shruti screen does
  (`ShrutiScreen.kt:195`, `ShrutiView.swift:190`).

**Accessibility tests**

- A gitam avarta label with words; section headings.
- iOS audit of the gitam player at large text.

**Docs**

- `docs/plan-alankaram-gitam.md`: mark it final, then mark each milestone done
  as it lands.
- `docs/plan.md` §3.2 (grammar) and §3.4: Mandhra Sthayi stays excluded.
- AGENTS.md: status and code map (`Talas`, `Ragas`, the new catalog files and
  the grammar).
- README: features.
- ATTRIBUTION.md, a new **Notation** section:
  - karnatik.com (Rani), lesson 6 and the four gitam pages;
  - shivkumar.org (Shivkumar Kalyanaraman) as the cross-check;
  - ThaaLam is the author's own code (mention it in the commit).
- Before the store release, ask both site authors for permission. This is
  noted in the docs; it does not block the work.

**Website** (`site/`)

- New guide pages `alankarams.html` and `gitams.html`. The gitams page covers
  sections, words, gamakas and Gentle strictness.
- Add both to the `GUIDE` list in `site/build.py`.
- Landing page: update the exercise count and add a short line about
  alankarams and gitams.
- Credits page.

## Verification

- `./gradlew :shared:jvmTest testDebugUnitTest assembleDebug lintDebug detekt`
- `./gradlew :app:connectedDebugAndroidTest` on the emulator (check storage
  first, as AGENTS.md describes).
- `cd iosApp && xcodegen generate` and `xcodebuild … test` on the
  iPhone 17 Pro simulator, which includes the accessibility audits.
- By hand, on the emulator and the iPad mini (install whenever it is
  connected):
  - play every alankaram and check the strip, wrapping and clicks;
  - play every gitam with words in all three views;
  - sing along with the synthetic singer (`-singAlong YES -singer 0`, Android
    `--ef singer 0`);
  - check "next up" after Dhattu 6 → Alankaram 1;
  - use VoiceOver and TalkBack on a gitam line.
- Listen to `RenderDemoTest` output for one alankaram and one gitam.
- Site: run `python3 site/build.py` and check the new pages and the
  unfilled-placeholder check.
- Commit and push to main after each milestone; each milestone is reviewable
  on its own.

# Research

## The seven talas

These are the standard sapta tala alankaram talas and jatis:

| Tala | Angas | Jati | Aksharas | Strip |
|---|---|---|---|---|
| Dhruva | I O I I | Chaturasra | 14 | x 1 2 3 · x v · x 1 2 3 · x 1 2 3 |
| Matya | I O I | Chaturasra | 10 | x 1 2 3 · x v · x 1 2 3 |
| Rupaka | O I | Chaturasra | 6 | x v · x 1 2 3 |
| Jhampa | I U O | Misra | 10 | x 1 2 3 4 5 6 · x · x v |
| Triputa | I O O | Tisra | 7 | x 1 2 · x v · x v |
| Ata | I I O O | Khanda | 14 | x 1 2 3 4 · x 1 2 3 4 · x v · x v |
| Eka | I | Chaturasra | 4 | x 1 2 3 |

Here I is a laghu, O a drutam and U an anudrutam (a single clap). Adi tala is
Chaturasra Triputa, so it becomes one case of the same model.

## Source review: karnatik.com (2026-11-06)

**Alankarams** ([lesson 6](https://karnatik.com/lesson6.shtml))

- **Notation:** all seven sapta tala alankarams in Mayamalavagowla, in clean
  notation.
  - Lowercase letters are the middle octave; `S` and `R` are upper Sa and Ri.
  - Each alankaram is 10 avartas: five climbing to S′ and five coming back.
- **Rupaka:** written drutam first (`s r | s r g m ||`).
- **Jhampa:** the misra laghu is spaced 3 + 2 + 2 inside the laghu, then the
  anudrutam and the drutam. Ata's khanda laghus contain karvais.
- **Mismatch with the plan:** these all match the tala table above.
  - The only error is the page header, which says "taaLam: aadi". It is a
    template leftover and can be ignored.
- **An eighth alankaram:** Sankeerna jati Eka (9 beats), in two versions.
  Not included (decided: the traditional seven).

**Gitams** ([index](https://karnatik.com/geetams.shtml))

- **Malahari gitams:** 27 gitams are listed, and the four Malahari pillari
  gitams each have notation, words and meaning:
  - [Śrī Gaṇanātha / Lambōdara](https://karnatik.com/c1806.shtml): Rupaka. These
    are one gitam, called by its opening line and by its refrain.
  - [Kunda Gaura / Mandāra](https://karnatik.com/c1807.shtml): Rupaka. Also one
    gitam.
  - [Kereya Nīranu](https://karnatik.com/c1808.shtml): Tisra Triputa.
  - [Padumanābha](https://karnatik.com/c1809.shtml): Tisra Triputa.
- **Talas:** Rupaka is written `2 | 4 ||` (drutam first) here too.
- **Order:**
  - Śrī Gaṇanātha, Kunda Gaura and Kereya Nīranu start with *caraṇam 1*, then
    the *pallavi* (the refrain).
  - Each later caraṇam is followed by "(pallavi)", meaning sing the refrain
    again.
  - Padumanābha runs pallavi, anupallavi, caraṇam, each followed by its
    refrain.
- **What the grammar needs:**
  - Named sections and a repeat reference, expanded when the piece is parsed,
    so that the player and sing-along see the sung order.
  - The screen shows section names.
- **Words:** written one syllable group per swara, with `.` for a held vowel.
  Some groups span two swaras (`naatha` under `S R`).
  - Transcription splits these at syllable boundaries (`naa tha`), and the file
    header records that change.
  - The romanisation is the site's own (`shree`, `gaNanaatha`, capitals for
    retroflex). The app writes them in simple Latin (`sri gananatha`).

## Cross-check: shivkumar.org (2026-11-06)

[Shivkumar Kalyanaraman's archive](https://www.shivkumar.org/music/varnams/index.html)
has, for each of the four Malahari gitams:

- notation (HTML, Word, PDF);
- a recorded class;
- recordings by Deepa Krishnamurthy at kattai 4 to 6½.

**How independent it is**

- **Alankarams:** not independent. The page credits "Rani, www.karnatik.com and
  Chitraveena Ravi Kiran's book", and the notation is the same.
- **Gitams:** independent.
  - The layout, word spellings and section names differ.
  - The meanings are by Dr. Aruna Turaga.
  - Some words differ: Kereya Nīranu has *nōḍi* where karnatik.com has
    *nambi*.

**How its notation differs from karnatik.com**

- Capitals are the middle octave and lowercase letters the lower octave. The
  upper octave is not marked in the HTML.
- A held note is written `-` or as the swara again over a `-` in the words.
  Both mean the same as karnatik.com's `,`.

**Result: the swaras were compared avarta by avarta with a script.**

- **Lower octave: the two sources agree on every lower note in all four
  gitams.**
  - The only one is the `d` in Padumanābha's opening line (R S Ḍ S).
  - The doubtful places in Padumanābha (`s d , | d p`) and Kereya Nīranu
    (`d p d | s ,`) are middle octave in both sources. Each source marks the
    lower note in the line just above, so leaving the mark off here is
    deliberate.
- **Swaras:** identical, apart from how holds are written, except for one line.
  - In Śrī Gaṇanātha's refrain (*lambōdara*), karnatik.com has
    `s r | m , g r ||` (*lam· | bō , da ra*).
  - shivkumar.org has `S , | R M G R ||` (*lam - | bō - dha ra*).
  - This is a real *pāṭhāntaram*. Decided: karnatik.com's version.
- **Mistakes in shivkumar.org headers:**
  - Kereya Nīranu and Padumanābha are labelled "Chathurasra Jathi" Triputa,
    but the notation is 3 + 2 + 2 (Tisra). karnatik.com is right.
  - Malahari's descent is given with G2; it is G3 (antara gāndhāra).
- **Licence:** neither site states one.

**Plan for the gitam text:**

- Encode from karnatik.com, the primary source, in its section order.
- Cross-check every avarta against shivkumar.org in a test-like script kept
  with the source notes.
- Record each difference and the choice made in the notation file's header.

**Remaining issues to settle before encoding**

1. **Licence and credit.**
   - The compositions are traditional and public domain, and the notation is
     re-encoded in the app's own format.
   - Credit both sites in ATTRIBUTION.md, with page links, Rani (karnatik.com)
     and Shivkumar Kalyanaraman.
   - Preferably ask both before shipping.
2. **Typos to fix in transcription,** recorded in the header.
   - Example: the caraṇam 3 words of Śrī Gaṇanātha are split oddly
     (`sa r | vO t ta ma`).
   - Each fix is listed rather than made silently.

**By-product:** lessons [4](https://karnatik.com/lesson4.shtml) and
[5](https://karnatik.com/lesson5.shtml) have the Thara and Mandhra Sthayi
varisais that plan.md section 3.4 excludes.

- They have the same dropped-dot problem.
- Thara 5 reaches P′, which is above the C5 sample ceiling at the higher Sa
  choices.
- So they stay excluded for now.

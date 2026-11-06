# Plan: Alankarams and Gitams

Status: **proposed** (2026-11-06). Nothing is built yet. The open questions at
the end need answers first.

The next two stages of the traditional beginner path after the varisais are:

- **Alankarams**: the seven *sapta tala alankarams*. Each is a swara pattern
  that climbs to upper Sa and comes back down, one in each of the seven suladi
  talas. This is the student's first meeting with talas other than Adi.
- **Gitams**: the first real compositions, with words (*sahityam*). The
  traditional first gitams are Purandara Dasa's *pillari gitams* in Malahari.
  The app is named after him, so these fit well.

Both use the existing player, sing-along, history and landing page. Everything
lands on both apps.

## What each one needs that the app lacks

| Need | Alankarams | Gitams | Today |
|---|---|---|---|
| Talas other than Adi | ✅ all seven suladi talas, with different laghu lengths (jatis) | ✅ mostly Rupaka and Triputa | Adi only (`Tala.ADI`); parser and `PerformanceSettings` assume it |
| Anudrutam (a 1-beat anga) | ✅ Jhampa | n/a | Not modelled |
| Ragas other than Mayamalavagowla | Optional (see Q2) | ✅ Malahari, later Mohanam and others | One raga, and every raga must define all seven letters |
| Lower-octave notes | Rare | ✅ gitams dip below Sa | No mandra marker in the grammar (the display already has a dot below) |
| Words under the swaras | n/a | ✅ | None |
| Long pieces | n/a (2–4 lines) | ✅ 10–30 avartas | Auto-scroll to the current avarta already exists on both apps |

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

## Shared changes (`shared/`)

1. **`Tala` built from angas.** Add `Anga.Laghu(jati)`, `Anga.Drutam` and
   `Anga.Anudrutam`. `Tala.suladi(name, jati)` builds the beats and labels, and
   `Tala.ADI` stays the same value. The seven talas live in a small catalog.
   Tests check akshara counts, anga lengths and labels against the table above.
2. **Each exercise carries its tala and raga.** `Varisai` gains `tala` and
   `raga` (the existing sets default to Adi and Mayamalavagowla).
   - The parser takes the tala per set, or per exercise through a header line.
   - `PerformanceSettings` and the schedule take both from the exercise instead
     of from defaults.
   - The type keeps its name for now; renaming it to `Lesson` is churn across
     both apps that buys nothing yet.
3. **`Raga` with an ascent and a descent.**
   - Letters a raga doesn't use (M and N in Mohanam) are absent.
   - The parser rejects them, so a typo becomes an error instead of a wrong
     pitch.
   - Malahari: ascent S R₁ M₁ P D₁ S′, descent S′ D₁ P M₁ G₃ R₁ S. It is a
     janya of Mayamalavagowla.
   - The ascent and descent are shown on the player as reference text. The
     notation is never checked against them, because gitams use phrases that
     cross the pattern.
4. **Mandra marker.** Write `Dˌ` (U+02CC after the letter), parsed to
   `Octave.MANDRA` and displayed with the dot below that `Display` already
   draws. This settles plan.md's "no mandra marker yet" for written pieces; the
   unconfirmed Mandhra Sthayi varisais stay excluded.
   - Pitch check: mandra Pa at the lowest Sa offered (G2) is D2. The harmonium
     samples start at C2, so it is covered. Tests check every note of every
     piece at every offered Sa.
5. **Sahityam (gitams).**
   - **Grammar:** a lyric line starts with `"` and follows the notation line it
     belongs to.
     - It has one token per akshara: a syllable, `-` where the previous
       syllable continues, or two syllables joined by `·` when an akshara holds
       two swaras.
     - The parser checks that the lyric aksharas match the notation aksharas,
       with errors that give the line and column, as today.
   - **Display:** Latin transliteration with diacritics (*śrī gaṇanātha*).
   - **Screen readers:** a plain ASCII form (*sri gananatha*), because diacritics
     are read badly by TalkBack and VoiceOver voices.
6. **Catalog and teaching order.**
   - New sets: `ALANKARAM` (7 exercises) and `GITAM` (the first four or five,
     see Q3), after Dhattu.
   - Ids look like `alankaram-dhruva` and `gitam-sri-gananatha`.
     `PracticeHistory` "next up" follows the catalog order, so it extends
     without changes.
   - Each notation file carries a header comment with the source and a
     verbatim-copy note, like the varisai files.
7. **Credits.**
   - The compositions are traditional, and Purandara Dasa (1484–1564) is long
     in the public domain.
   - Each source of notation and words gets a line in ATTRIBUTION.md in the same
     change, per the credit rule.

## App changes (both platforms)

- **Landing page:** two more set sections, each with its description and the
  existing contour rows. A gitam row also shows its raga and tala.
- **Player:**
  - The heading shows the raga and tala ("Malahari · Rupaka").
  - The tala strip takes any length. At large text it wraps by anga instead of
    shrinking.
  - The notation shows the words under the swaras.
  - A **Swaras / Words / Both** choice is offered (default: Both).
  - Speed (kala 1–4), count-in, repeat, sing-along and strictness all work
    unchanged.
- **Sing-along:**
  - Scoring is per note, as today. Gitams have gamakas the harmonium doesn't
    play, so a held, oscillated note can read as off.
  - The guide will say this, and suggest Gentle strictness for gitams. Q5 asks
    whether the app should do more.
- **Accessibility:**
  - Each avarta's label adds the words after the swaras, for example
    "S R M P, hold, words: sri gana natha".
  - Long-tala strips keep their per-beat labels.
  - New AccessibilityTest cases on Android and audit tests on iOS cover a
    14-beat tala and a gitam screen.
- **Website guide:** new pages *Alankarams* and *Gitams*, a talas section, and
  credits.

## Milestones

| # | Milestone | Done when |
|---|---|---|
| A1 | Shared: anga-based `Tala` and the seven talas; per-exercise tala and raga; `Raga` with optional letters, ascent and descent; mandra marker | jvmTest: tala table, Adi unchanged, schedules for 7/10/14-akshara talas at every kala, mandra parses and plays in sample range |
| A2 | Alankarams: catalog, landing section, player heading, any-length tala strip, on both apps | Both apps play all seven in tala; a11y tests pass; iPad install |
| G1 | Shared: sahitya grammar and parser checks, Malahari, gitam catalog | jvmTest: every gitam parses, lyric/notation alignment, ASCII spoken form |
| G2 | Gitams in both apps: words under swaras, the Swaras / Words / Both choice, a11y labels; website guide pages; docs | Both apps play the gitams with words following; audits pass; site updated |

A1 and A2 ship alankarams on their own. Gitams follow.

## Open questions

1. **Notation source (needed before A2 and G1).** The varisais were copied
   verbatim from your workbook, which has no alankarams or gitams.
   - Which book or teacher's notes should be the reference?
   - Gitams have regional variants (*pathantaram*), and the version should
     match what you are taught.
   - I won't transcribe from memory or from a random website.
2. **Alankarams in other ragas?** The patterns are letter-only, so the same
   seven work in any seven-note raga, such as Shankarabharanam, Kalyani or
   Kharaharapriya. With the raga model above, the varisais could follow too.
   - Recommendation: Mayamalavagowla only in A2, with a raga choice as a later,
     separate plan.
3. **Which gitams first?** Recommendation: the Malahari pillari gitams in
   teaching order:
   - Śrī Gaṇanātha (Rupaka)
   - Kunda Gaura (Rupaka)
   - Kereya Nīranu (Triputa)
   - Padumanābha (Triputa)

   Optionally Lambōdara Lakumikara. Mohanam and Anandabhairavi gitams can come
   later.
4. **Script for the words.** Recommendation: Latin transliteration first.
   Kannada script, the original language of most pillari gitams, could be a
   later option. It needs a bundled font check on both platforms.
5. **Gamakas.** Should the app only show the gamaka caveat and suggest Gentle,
   as recommended? The alternative is a per-note "gamaka, judge loosely" flag
   in the notation. The flag would need a source that marks gamakas.
6. **Rupaka anga order.** Sapta tala Rupaka is drutam then laghu (x v · x 1 2 3),
   but many gitam books write it laghu first. The strip will follow the chosen
   source. Please confirm what your teacher counts.

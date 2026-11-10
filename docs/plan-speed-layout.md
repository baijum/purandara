# Plan: notation laid out by tala cycle at every speed

## Context

At speed (kala) 2–4 the player screen is wrong, not just unclear:

- **The tala strip is the column header of the notation**, which only holds at
  speed 1. At speed 2 the swara under "1" is actually sung on the clap, and one
  tala cycle covers two written rows.
- **Anga bars are fixed by cell** (`Varisai.startsAnga` → `Tala.startsAngaAt`).
  At speed 2 the bar after the fourth swara falls in the middle of the laghu.
- **Swarajatis are misaligned even at speed 1**: Rara Venu (`beat: 4`) has 4
  rows a cycle under a one-row strip of 8 marks.
- **Varnams already work and are the precedent**: 16 counts and 16 cells, with
  the strip wrapped onto the same rows as the notation (`Tala.rowStartsFor`,
  shared by Android `TalaStrip` and iOS `TalaRows`/`TalaStripView`).

What the user decided (2026-11-10):
1. **One block per tala cycle** at the chosen speed, the way books write the
   2nd and 3rd speeds. Repeats are written out where passes share a cycle
   (the plain scale at speed 3 shows twice in one cycle).
2. **The strip mirrors the notation rows while one cycle takes ≤ 2 rows.**
   Beyond that, the strip stays one compact row (for the current beat) and each
   notation row starts with its beat mark (x, 1, v) in a narrow gutter.
3. **Every piece** follows one rule, built from cells per beat × speed.

Outcome: on screen, the swaras under a mark are the ones sung on that beat,
and the anga bars fall where the hand changes, at every speed. The guide
section added in f32d7ae ("Keeping tala at a faster speed") shrinks to a
sentence.

This plan was agreed and built on 2026-11-10. Built as planned, except:

- Screen readers say "Avarta n" (not "Cycle n"): a block is one avarta of the tala.
- The strip mirrors per cycle (`CycleBlock.mirrorsStrip`), not for the whole piece: a varnam's double-speed sections use the gutter while its first-speed cycles keep the two-row strip.
- Beats with several swaras sit on a light band, so the pairs read as pairs.

## Design

### Shared model (`shared/src/commonMain`, pure Kotlin)

New `domain/varisai/NotationLayout.kt`:

```kotlin
/** One written cell as sounded: which pass, avarta and akshara. */
data class CellRef(val repetition: Int, val avarta: Int, val akshara: Int)

/** The cells sung in one tala beat (or one count of a two-kalai beat). */
data class BeatGroup(val beatInCycle: Int, val beat: TalaBeat, val countInKriya: Int,
                     val startsAnga: Boolean, val cells: List<CellRef>)

/** One notation row: whole beat groups, except a beat wider than ROW_AKSHARAS wraps. */
data class LayoutRow(val groups: List<BeatGroup>, val continuesBeat: Boolean)

/** One tala cycle at the chosen speed. */
data class CycleBlock(val index: Int, val rows: List<LayoutRow>, val columns: Int,
                      val sectionStarts: List<Pair<CellRef, String>>)

class NotationLayout(val blocks: List<CycleBlock>, val stripMirrorsRows: Boolean) {
    fun locate(position: NotationPosition): Located?   // block, row, column (hash lookup)
    fun spoken(block: Int, tala: Tala, karvaiWord: String): String
    companion object {
        fun of(varisai: Varisai, range: IntRange, kala: Int): NotationLayout
    }
}
```

How the layout is built: walk the cells in the order they are performed, the
same walk as `Schedule.of`: passes × `range` × aksharas. Each cell lasts
`1 / (cellsPerBeatIn(avarta) × 2^(kala−1) × avarta.speed)` tala beats. Use
integer ticks so there's no float drift: ticks per beat = the LCM of every cell
rate in the range. Cells are grouped into tala beats (counts when
`varisai.kalai` = 2), and beats into cycles.

- **Rows:** generalise `Tala.rowStartsFor(cellsPerBeat)` to per-beat widths:
  `Tala.rowStartsFor(beatCells: List<Int>)`. The existing function delegates to
  it, so behaviour and tests stay the same. Same rule as today: split by angas,
  ≤ `ROW_AKSHARAS` (8) cells, join short neighbours. A beat with more than 8
  cells (a swarajati at speed 3+) wraps inside the beat; those rows get
  `continuesBeat` and an empty gutter.
- **Mixed gati in one cycle:** a khanda section next to a 2-to-a-beat one at
  speed 2 gives beats of different widths. Rows still break at beats, and
  `columns` = the widest row.
- **`stripMirrorsRows`** is true when every block has ≤ 2 rows. Adi at
  1 or 2 cells a beat, Dhruva at speed 1 and varnams at speed 1 stay as today.
- **Repetitions:** pull `repetitionsToFillCycles` out of `Schedule.of` into
  something both use (`Schedule.repetitionsFor(varisai, range, kala)`), so the
  layout and the playback can't disagree. The layout doesn't need `saHz`, so it
  is available before Play.
- **Sections:** a block's heading is the section that starts on its first
  cell. A section starting mid-cycle (an odd number of @1 avartas at speed 2)
  is listed in `sectionStarts`: its label joins the block heading
  ("Pallavi · Anupallavi from beat 5") and a start bar is drawn before that
  cell.
- **What is shown is what is played.** The layout comes from the selected
  range (whole piece, section or phrase). Today the whole piece is always
  shown, with only the highlight inside the section. Under this plan, picking a
  section shows just that section, which the cycle alignment needs.
  (Agreed 2026-11-10.)
- **Marks:** `MarkKey` stays (avarta, akshara, slot), and a cell shows its mark
  in every copy. Where a cycle repeats the piece, both copies show the latest
  result, as the single row does today.

At speed 1 with one cell per beat, the result is one block per avarta with
the same rows and bars as today. That's the regression anchor.

### Android (`app/`)

- `PracticeViewModel`: expose `layout: NotationLayout` in state. Rebuild it
  when kala, section or phrase changes, and when the exercise opens.
- `ui/player/NotationView.kt`:
  - `LazyColumn` items = `layout.blocks`.
  - The `AvartaRow` → `CycleRow` loop renders groups. Anga bars come from
    `BeatGroup.startsAnga`. Add a faint beat gap between groups when cells per
    beat > 1.
  - Add the gutter column (beat mark, `onSurfaceVariant`, decorative) when
    `!stripMirrorsRows`.
  - Highlight and auto-scroll via `layout.locate(position)`. Words and marks are
    looked up through `CellRef`.
- `TalaStrip`: in mirror mode, rows and columns follow block 0's rows. Each chip
  gets `Modifier.weight(cells in group)`, so it sits over its swaras. In gutter
  mode: one row (`tala.rowStarts`, wrapped long talas as today).
- `markedSpoken` / `Avarta.spoken`: move the anga-comma logic to
  `NotationLayout.spoken`, so speech follows the new bars.

### iOS (`iosApp/`)

- `PracticeViewModel.swift`: publish `layout`, rebuilt on the same triggers.
- `Views/NotationView.swift`: `ForEach` over blocks. `TalaRows(block:)` replaces
  `TalaRows(varisai:avarta:index:)`, and the gutter and beat gap match Android.
  A lone mandra dot is still drawn as a shape.
- `TalaStripView`: mirror mode takes block rows; chip widths come from
  `layoutPriority`/`frame` proportional to cells. Gutter mode is one row.
- Swift interop: no Kotlin default arguments (add `NotationLayout.create(...)`
  if needed). `locate` returns a nullable class, not a Kotlin data class
  with `copy`.

### Accessibility

- One node per block: "Cycle 3: S R G M P D N S′, S′ N D P, M G R S", with
  commas at the new anga bars. Section headings stay separate heading nodes.
- The gutter is decorative (`clearAndSetSemantics` / `.accessibilityHidden`).
  The block's sentence carries the content.
- Strip label gains the density: "Adi tala, 2 swaras to a beat: clap 1 2 3,
  clap wave, clap wave". The state stays "Beat 3 of 8", read only when focused.
- iOS audit: blocks at speed 3/4 are tall. Keep `longNotation` handling and
  check the Dynamic Type warning exemption still covers the cells.

## Critical files

- `shared/.../domain/Tala.kt`: `rowStartsFor(beatCells)` generalisation
- `shared/.../domain/varisai/NotationLayout.kt`: **new**
- `shared/.../domain/varisai/Varisai.kt`: `Avarta.spoken`'s comma logic moves
  to the layout; `rowStartsIn`/`startsAnga` are kept only if still used
- `shared/.../domain/schedule/Schedule.kt`: shared `repetitionsFor`
- `app/.../ui/player/NotationView.kt`, `PlayerScreen.kt`,
  `app/.../viewmodel/PracticeViewModel.kt`
- `iosApp/Purandara/Views/NotationView.swift`, `PlayerView.swift`,
  `iosApp/Purandara/ViewModels/PracticeViewModel.swift`
- Docs: `docs/plan-speed-layout.md` (new), `docs/plan.md` (§3/§4.1 note),
  AGENTS.md code map, README docs index
- Site: `guide/varisai-player.html` (shrink "Keeping tala at a faster speed",
  update "The notation"/"The tala strip"), `settings.html` (Speed),
  `swarajatis.html` (gutter, rows), `varnams.html` (strip at speed 2+),
  `alankarams.html` (wrapping), `accessibility.html` ("Cycle n")
- Store screenshots only if the default (speed 1) screen changes. It shouldn't.

## Steps

1. Shared: `Tala.rowStartsFor(beatCells)`, `Schedule.repetitionsFor`,
   `NotationLayout` + tests (below). Nothing visible changes yet.
2. Android: ViewModel, `NotationView`, `TalaStrip`, speech. Run unit and
   accessibility tests.
3. iOS: the same, then `xcodegen generate` if files are added, then the UI
   tests.
4. Site and docs. Install on the iPad. Smoke-test both platforms.

## Tests (`shared/src/commonTest/.../varisai/NotationLayoutTest.kt`)

- **Regression:** for every catalog piece at speed 1, block rows and anga bars
  match today's `rowStartsIn`/`startsAnga` per avarta (except where the cell
  rate differs within a cycle).
- **Plain scale:**
  - speed 2: 1 block, 2 rows of 4 beats, 2 cells each, mirror mode;
  - speed 3: 1 block holding passes 0 and 1, 4 rows, gutter mode;
  - speed 4: 1 block, 4 passes, 8 rows.
- **Sarali at speed 2:** the anga bar falls at beat 4 (cell 8 of the
  cycle), not at written cell 4.
- **Property tests (Kotest), all pieces × kalas 1–4 × sections:**
  - every performed cursor position (`Schedule.cursor`) maps to exactly one
    layout cell (`locate` is total and injective on positions);
  - every block has `tala.aksharas × kalai` beat groups;
  - rows never exceed `ROW_AKSHARAS` cells unless they hold one wrapped beat.
- **Layout and playback agree:** the layout's tick at each cell equals
  `CursorEvent.startSeconds / talaAksharaSeconds` from `Schedule`.
- **Mixed gati:** Sambasiva's khanda charanam at speed 2.
- **Mid-cycle section start:** an odd-avarta @1 section at speed 2 is listed in
  `sectionStarts`.
- **Speech:** `spoken` puts commas at the new anga bars.

## Verification

```bash
./gradlew :shared:jvmTest testDebugUnitTest assembleDebug lintDebug detekt
./gradlew :app:connectedDebugAndroidTest
cd iosApp && xcodegen generate && cd ..
xcodebuild -project iosApp/Purandara.xcodeproj -scheme Purandara \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test
```

Manual checks: `-open plain-scale-1 -autoplay YES`, `-open sarali-3`,
`-open swarajati-1` and a varnam on the iPhone simulator, the Android emulator
and the iPad. Do each at speeds 1–4, with sing-along (`-singAlong YES -singer 0`;
Android `--ef singer 0`) and with VoiceOver/TalkBack on one block. Check that:
- the lit chip sits over the highlighted swaras;
- the anga bars fall on x;
- auto-scroll follows blocks.

Terminate apps after manual launches. Install the build on the iPad.

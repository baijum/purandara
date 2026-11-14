# Plan: practising a whole set in one run

Status: **built** (2026-11-14). **Revised the same day** after the student
asked for the full player during a run.

The first build played the set itself (a seamless `PracticeRunSource` with a
reduced screen and a live pitch lane). That left out the player's tempo,
room listening, note marks and pass summaries. The student chose to **hand
each kala to the normal player** instead: the run is now a sequencer around
the player, not a second player.

## What the student decided (2026-11-14)

| Question | Decided |
|---|---|
| How to reuse | **Hand over to the normal player**: each varisai opens in the familiar player with tempo, count-in, room check, note marks, pass summaries and settings. |
| Where the break falls | **At every speed (kala)**: each kala is a fresh player play with its count-in and room listening. |
| Repeats | Within a kala, the player loops; the run counts the passes. Speed 3 with 2 repeats is kala 1 twice, kala 2 twice, kala 3 twice. |
| Tempo | **One tempo, adjustable in the player**. Changing it restarts the current kala at the new tempo; the run's pass count starts again. |
| Feedback | **Exactly like the player**: each note marked, a summary after every pass, and the best score saved for that varisai. |
| Run controls | **Full player plus a run banner**: the plan's speed/repeat stays in charge (the speed picker is hidden), the banner shows varisai · kala · pass with Skip, and Back/Stop return to the plan. |

Rules that stay from the first round:

- The numbered sets only: Sarali, Madhya Sthayi, Janta, Vakra Janta, Dhattu.
- A set is played in catalog order; the saved continue point is the varisai.
- Entries persist per set, with speed 1–4 and repeats 1–7, and the plan is
  reconciled and clamped on read.
- A run does not cross sets.

## How a run works

1. The plan screen lists every varisai; each has a **Speed** (highest kala to
   climb to) and **Sing** (passes at each kala).
2. **Start** opens the normal player on the first varisai at first kala.
3. The player loops the kala (`loop = true` in its render options). Its
   count-in, room check, note marks and pass summary behave exactly as when
   practising that varisai alone.
4. The run counts the player's loop passes. After the wanted passes, it
   moves to the next kala (fresh player play, count-in and room check), then
   the next varisai, then the complete screen.
5. **Tempo** changes restart the current kala; the pass count starts again.
6. **Skip** in the run banner skips the rest of the varisai. **Stop**, the
   player's Stop, or Back returns to the plan with that varisai as the resume
   point. The best score for the varisai is saved as usual.

The tanpura and tala run on through the repeats *within* a kala; a new kala
starts fresh. There is no single unbroken stream across kala changes — that
was the trade for having the full player.

## Shared model (`shared/src/commonMain`, pure Kotlin)

`practice/PracticeRun.kt`:

```kotlin
@Serializable
data class RunEntry(
    val varisaiId: String,
    @SerialName("kala") val speed: Int = 1,   // 1 = first kala only, 3 = 1, 2, 3
    val repeats: Int = 1,
)

@Serializable
data class RunPlan(val set: String, val nextIndex: Int = 0, val entries: List<RunEntry> = emptyList())

class RunPlans {                     // one plan per set, encoded as JSON
    fun planFor(set: VarisaiSet, catalog: List<Varisai>): RunPlan
    fun with(plan: RunPlan): RunPlans
    fun encode(): String
    companion object { fun decode(text: String?): RunPlans }
}

data class RunStep(val varisai: Varisai, val speed: Int, val repeats: Int) {
    val kalas: IntRange get() = 1..speed
}

class PracticeRun(...) {             // the plan's timing, for the estimate
    val schedulesByStep: List<List<Schedule>>
    val passes: List<RunPass>
    val totalSeconds: Double
    fun momentAt(seconds: Double): RunMoment?
}
```

- The stored field stays `kala` for compatibility; the model calls it
  `speed`. A saved `kala: 3` from the first build now means the climb 1–3.
- Speeds clamp to 1–4 and repeats to 1–7; unknown ids drop; the catalog order
  wins; `nextIndex` clamps.
- `PracticeRun` remains for the setup estimate and timing tests. It does not
  play anything any more.

## Android (`app/`)

- `RunController` sequences the player:
  - `open/close/back`, `setSpeed/setRepeats/applySpeed/applyRepeats`.
  - `start(from)` → `playStep(step, kala = 1)`.
  - `playStep` sets the run state (step, kala, pass), the player's `selected`
    varisai and `settings.kala`, then calls the view model's normal `play`.
  - `onPlayerPass(attempt)` is called from the player tick; a pass is the
    renderer's loop pass. After `step.repeats`, `advance()` to the next kala
    or varisai, or `complete()`. A decreasing attempt means the player
    restarted (tempo, Sa, sing-along) and resets the count.
  - `stop`/`skip`/`again`; persist the continue point on every step.
- `PracticeViewModel`: the normal `play()` uses `RenderOptions.looping` when
  `run.phase == PLAYING`; the transport path is skipped for a run. The tick
  reports `attempt` to the controller. The run has no separate audio source,
  no separate listener and no reduced pitch screen any more.
- `RunScreen`: setup and complete only. The plan, bulk speed/repeats and
  estimate are unchanged.
- `PlayerScreen`: a **run banner** (set · varisai of total, kala · pass of
  repeats, Skip) in the notation header, the speed picker hidden, and the
  settings sheet hides Practice mode/Repeat for a run. Tempo and all other
  settings stay. `PlayerActions.onSkipRun` serves the banner.
- `PurandaraApp`: `run.phase == PLAYING` shows the normal `PlayerRoute` with
  run mode; the player's Back, Stop and Skip call the run controller. Setup
  and complete show `RunRoute`.
- Removed: `PracticeRunSource` (seamless run audio) and its test,
  `RunPitchTracker` (the reduced run's pitch graph), and the run playing
  screen. The normal player's sing-along does that job with marks and
  summaries.

## iOS (`iosApp/`)

- `RunUI` holds the plan, phase, step, kala and pass. `PracticeViewModel`
  sequences the normal player:
  - `startRun`/`playRunStep` set `settings.kala` and call `playExercise`.
  - `playExercise` uses `RenderOptions.companion.looping(options:)` for a run
    and skips the transport path; the sing-along engine, marks and summaries
    are the normal ones.
  - `tick` computes the loop attempt and calls `onRunPass`; after the wanted
    passes it advances or completes.
  - `stopRun`/`skipRunStep`/`practiseAgain`; the tempo/sing-along restart
    resets the pass count.
- `RunView`: setup and complete; playing shows `PlayerView(runMode: true)`.
- `PlayerView(runMode:)`: the run banner in the scroll header, a leading
  back-to-plan button, the speed picker hidden, the settings sheet hides
  Practice mode/Repeat, and Stop stops the run. The playback status moved
  into the scroll header and is a simple VStack (the old `ViewThatFits` row
  failed the Dynamic Type audit while playing).
- Removed: `RunPitchSample`, the run pitch listener, the run schedule/layout
  caches, and the run playing view.

## Feedback, scores and history

- The player's sing-along marks every note, shows a summary after each pass
  and records the varisai's best score and practised count exactly as when
  practising alone. A run needs no extra score code.
- The room check happens during each kala's count-in because sing-along
  forces the count-in.

## Not in this version

- A seamless run across kala/varisai changes with full scoring (the trade
  above).
- A run across more than one set.
- Alankarams, gitams, swarajatis, varnams (sections and written speeds need
  their own design).

## Critical files

- `shared/.../practice/PracticeRun.kt` — plan, persistence, timing, estimate
- `shared/.../audio/SampleRenderer.kt` — `RenderOptions.looping`
- `app/.../viewmodel/RunController.kt`, `RunState.kt` — the sequencer
- `app/.../viewmodel/PracticeViewModel.kt` — player loop, pass tick
- `app/.../ui/player/PlayerScreen.kt` — run banner, hidden speed/settings
- `app/.../ui/run/RunScreen.kt` — plan and complete
- `app/.../ui/PurandaraApp.kt` — run player route
- `iosApp/Purandara/ViewModels/PracticeViewModel.swift` — run sequencing
- `iosApp/Purandara/Views/RunView.swift`, `PlayerView.swift` — plan,
  complete, run-mode player, banner
- Tests: `PracticeRunTest`; Android `AccessibilityTest` (plan, banner,
  pitch lane, complete); iOS `PurandaraUITests` (run plan/player, sing-along)

## Verification

```bash
./gradlew :shared:jvmTest testDebugUnitTest assembleDebug lintDebug detekt
./gradlew :app:connectedDebugAndroidTest
cd iosApp && xcodegen generate && cd ..
xcodebuild -project iosApp/Purandara.xcodeproj -scheme Purandara \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test
```

Manual checks: a run with Speed 2 × 1 pass — the player opens Sarali 1 at
first kala with the count-in/room listening; the banner shows the position;
after one pass it moves to second kala; after that to Sarali 2; note marks
and a pass summary appear with Sing along on; a tempo change restarts the
current kala; Stop returns to the plan with the resume point; the best score
is saved.

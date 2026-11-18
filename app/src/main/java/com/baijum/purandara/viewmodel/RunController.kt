package com.baijum.purandara.viewmodel

import com.baijum.purandara.data.PracticePreferences
import com.baijum.purandara.data.PracticeSettings
import com.baijum.purandara.domain.varisai.Varisai
import com.baijum.purandara.domain.varisai.VarisaiSet
import com.baijum.purandara.practice.PracticeRun
import com.baijum.purandara.practice.RunEntry
import com.baijum.purandara.practice.RunPlan
import com.baijum.purandara.practice.RunPlans
import com.baijum.purandara.practice.RunStep

/**
 * Sequences a set's varisais through the **normal player** (docs/plan-practice-run.md):
 * each kala is played by the full player — tempo, count-in, room check, note
 * marks and pass summaries — and this controller advances to the next kala or
 * varisai when its passes are done.
 *
 * The player loops the current kala; each loop pass is counted from the
 * player's attempt number. A tempo change restarts the current kala, so the
 * pass count starts again.
 */
class RunController(
    private val catalogNow: () -> List<Varisai>,
    private val preferences: PracticePreferences,
    private val host: Host,
) {
    /** The catalog in the chosen raga, read when a run is planned. */
    private val catalog: List<Varisai> get() = catalogNow()

    /** The view model's side of a run: state, and starting/stopping the normal player. */
    @Suppress("LongParameterList") // One explicit bundle of view-model hooks, built once.
    class Host(
        val settings: () -> PracticeSettings,
        val state: () -> PracticeUiState,
        val update: ((PracticeUiState) -> PracticeUiState) -> Unit,
        /** Start the normal player at [varisai] and [kala]. */
        val play: (varisai: Varisai, kala: Int) -> Unit,
        /** Silences the player (the view model's stop). */
        val stopAll: () -> Unit,
    )

    /** The last 1-based loop pass reported by the player (0 = the kala has just restarted). */
    private var lastAttempt = 0

    /** Opens [set]'s plan at its saved continue point. */
    fun open(set: VarisaiSet) {
        if (!set.practisesAsRun) return
        host.stopAll()
        val plan = host.state().runPlans.planFor(set, catalog)
        val steps = stepsFor(set, plan)
        if (steps.isEmpty()) return
        val start = plan.nextIndex.coerceIn(0, steps.lastIndex)
        host.update {
            it.copy(
                run = RunUiState(set, RunPhase.SETUP, steps, start, start, totalSeconds = estimate(set, steps, start)),
                selected = null,
                section = null,
                phrase = null,
                playback = PlaybackState(),
            )
        }
    }

    fun close() {
        host.stopAll()
        host.update { it.copy(run = null, selected = null, playback = PlaybackState()) }
    }

    /** Back from the run: playing returns to the plan; the plan returns to the library. */
    fun back() {
        when (host.state().run?.phase) {
            RunPhase.PLAYING -> stop()
            else -> close()
        }
    }

    fun setSpeed(index: Int, speed: Int) = updateSteps {
        val s = speed.coerceIn(RunEntry.SPEED_RANGE)
        it.mapIndexed { i, step -> if (i == index) RunStep(step.varisai, s, step.repeats) else step }
    }

    fun setRepeats(index: Int, repeats: Int) = updateSteps {
        val r = repeats.coerceIn(RunEntry.REPEAT_RANGE)
        it.mapIndexed { i, step -> if (i == index) RunStep(step.varisai, step.speed, r) else step }
    }

    fun applySpeed(speed: Int) = updateSteps {
        val s = speed.coerceIn(RunEntry.SPEED_RANGE)
        it.map { step -> RunStep(step.varisai, s, step.repeats) }
    }

    fun applyRepeats(repeats: Int) = updateSteps {
        val r = repeats.coerceIn(RunEntry.REPEAT_RANGE)
        it.map { step -> RunStep(step.varisai, step.speed, r) }
    }

    fun start(from: Int) {
        val run = host.state().run ?: return
        playStep(from.coerceIn(0, run.steps.lastIndex), kala = 1, pass = 1)
    }

    /** Stop the run and return to its plan with the current varisai as the resume point. */
    fun stop() {
        val run = host.state().run ?: return
        if (run.phase != RunPhase.PLAYING) return
        host.stopAll()
        persist(run.set, run.steps, run.step)
        host.update {
            it.copy(
                run = it.run?.copy(phase = RunPhase.SETUP, startStep = it.run.step, kala = 1, pass = 0),
                selected = null,
                section = null,
                phrase = null,
                playback = PlaybackState(),
            )
        }
    }

    /** Skip the rest of this varisai and start the next one from first kala. */
    fun skip() {
        val run = host.state().run ?: return
        if (run.phase != RunPhase.PLAYING) return
        if (run.step < run.steps.lastIndex) playStep(run.step + 1, kala = 1, pass = 1) else complete()
    }

    fun again() {
        if (host.state().run?.phase == RunPhase.COMPLETE) playStep(0, kala = 1, pass = 1)
    }

    /**
     * The player has started its [attempt]-th (1-based) loop pass of the
     * current kala. After the last wanted pass, move to the next kala or
     * varisai. A decreasing attempt means the player restarted (tempo, Sa,
     * sing-along), so the pass count starts again.
     */
    fun onPlayerPass(attempt: Int) {
        val run = host.state().run ?: return
        if (run.phase != RunPhase.PLAYING || attempt <= 0) return
        if (attempt < lastAttempt) {
            lastAttempt = attempt
            host.update { it.copy(run = it.run?.copy(pass = attempt - 1)) }
            return
        }
        if (attempt == lastAttempt) return
        lastAttempt = attempt
        val step = run.steps.getOrNull(run.step) ?: return
        if (attempt > step.repeats) {
            advance()
        } else {
            host.update { it.copy(run = it.run?.copy(pass = attempt - 1)) }
        }
    }

    private fun advance() {
        val run = host.state().run ?: return
        val step = run.steps.getOrNull(run.step) ?: return
        when {
            run.kala < step.speed -> playStep(run.step, run.kala + 1, pass = 1)
            run.step < run.steps.lastIndex -> playStep(run.step + 1, kala = 1, pass = 1)
            else -> complete()
        }
    }

    private fun playStep(step: Int, kala: Int, pass: Int) {
        val run = host.state().run ?: return
        val info = run.steps.getOrNull(step) ?: return
        lastAttempt = 0
        host.update {
            it.copy(
                run = it.run?.copy(phase = RunPhase.PLAYING, step = step, startStep = step, kala = kala, pass = pass - 1),
                selected = info.varisai,
                section = null,
                phrase = null,
            )
        }
        persist(run.set, run.steps, step)
        host.play(info.varisai, kala)
    }

    private fun complete() {
        val run = host.state().run ?: return
        host.stopAll()
        persist(run.set, run.steps, 0)
        host.update {
            it.copy(
                run = it.run?.copy(phase = RunPhase.COMPLETE, step = 0, startStep = 0, kala = 1, pass = 0),
                selected = null,
                playback = PlaybackState(),
            )
        }
    }

    private fun updateSteps(transform: (List<RunStep>) -> List<RunStep>) {
        val run = host.state().run ?: return
        if (run.phase != RunPhase.SETUP) return
        val steps = transform(run.steps)
        host.update { it.copy(run = it.run?.copy(steps = steps, totalSeconds = estimate(run.set, steps, run.startStep))) }
        persist(run.set, steps, run.startStep)
    }

    private fun persist(set: VarisaiSet, steps: List<RunStep>, nextIndex: Int) {
        val key = steps.firstOrNull()?.let { RunPlans.keyFor(set, it.varisai.raga) } ?: set.name
        val plan = RunPlan(key, nextIndex, steps.map { RunEntry(it.varisai.id, it.speed, it.repeats) })
        val plans = host.state().runPlans.with(plan)
        preferences.saveRunPlans(plans)
        host.update { it.copy(runPlans = plans) }
    }

    private fun stepsFor(set: VarisaiSet, plan: RunPlan): List<RunStep> =
        catalog.filter { it.set == set }.mapIndexed { i, varisai ->
            val entry = plan.entries.getOrNull(i)
            RunStep(
                varisai,
                (entry?.speed ?: 1).coerceIn(RunEntry.SPEED_RANGE),
                (entry?.repeats ?: 1).coerceIn(RunEntry.REPEAT_RANGE),
            )
        }

    private fun estimate(set: VarisaiSet, steps: List<RunStep>, start: Int): Double {
        if (steps.isEmpty()) return 0.0
        val settings = host.settings()
        return runCatching {
            PracticeRun(set, steps, start.coerceIn(0, steps.lastIndex), settings.performance(), settings.countIn).totalSeconds
        }.getOrDefault(0.0)
    }

}

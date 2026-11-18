package com.baijum.purandara.practice

import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.schedule.PerformanceSettings
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.domain.varisai.Varisai
import com.baijum.purandara.domain.varisai.VarisaiSet
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * One varisai in a saved practice run.
 *
 * [speed] is the highest kala to climb to: 1 sings first kala only, 2 sings
 * first then second, and so on. [repeats] is how many times each kala is
 * sung, so speed 3 with 2 repeats is 1, 1, 2, 2, 3, 3.
 *
 * A decoded entry is clamped by [RunPlans] rather than rejected, so an older
 * or corrupted store cannot break the player.
 */
@Serializable
data class RunEntry(
    val varisaiId: String,
    @SerialName("kala") val speed: Int = 1,
    val repeats: Int = 1,
) {
    companion object {
        /** The fastest kala a run may climb to. */
        const val MAX_SPEED = 4

        /** The most times one kala may be repeated in a run. */
        const val MAX_REPEATS = 7

        val SPEED_RANGE = 1..MAX_SPEED
        val REPEAT_RANGE = 1..MAX_REPEATS

        /** For callers without default arguments (Swift). */
        fun create(varisaiId: String, speed: Int, repeats: Int): RunEntry = RunEntry(varisaiId, speed, repeats)
    }
}

/** The saved run for one set: its per-varisai choices and where to continue. */
@Serializable
data class RunPlan(
    /** [RunPlans.keyFor]: [VarisaiSet.name], e.g. "SARALI", or "SARALI@kalyani" in another raga. */
    val set: String,
    /** The index into the set's exercises to start at next (0 = the first). */
    val nextIndex: Int = 0,
    /** One entry per varisai, in the catalog order of the set. */
    val entries: List<RunEntry> = emptyList(),
) {
    companion object {
        /** For callers without default arguments (Swift). */
        fun create(set: String, nextIndex: Int, entries: List<RunEntry>): RunPlan = RunPlan(set, nextIndex, entries)
    }
}

/**
 * The saved run plans, one per set (stored on the device only). Immutable;
 * platforms keep the latest value and persist [encode]d text.
 *
 * [planFor] reconciles a plan with the current catalog: entries keep the
 * catalog order, unknown exercises are dropped, missing ones get the
 * defaults, and out-of-range speeds and repeats are clamped.
 */
class RunPlans(plans: Collection<RunPlan> = emptyList()) {
    private val bySet: Map<String, RunPlan> = plans.associateBy { it.set }

    val plans: Collection<RunPlan> get() = bySet.values

    operator fun get(set: VarisaiSet): RunPlan? = bySet[set.name]

    /** The saved plan for [set] sung in [raga], if any. */
    fun get(set: VarisaiSet, raga: Raga): RunPlan? = bySet[keyFor(set, raga)]

    /**
     * The saved plan for [set], reconciled with [catalog] (defaults when none
     * is saved). A set sung in another raga than Mayamalavagowla has its own
     * plan (docs/plan-other-ragas.md), found from the raga of [catalog]'s exercises.
     */
    fun planFor(set: VarisaiSet, catalog: List<Varisai>): RunPlan {
        val exercises = catalog.filter { it.set == set }
        val key = exercises.firstOrNull()?.let { keyFor(set, it.raga) } ?: set.name
        val saved = bySet[key]
        val entries = exercises.map { varisai ->
            val entry = saved?.entries?.firstOrNull { it.varisaiId == varisai.id }
            entry?.clamped() ?: RunEntry(varisai.id)
        }
        val nextIndex = if (entries.isEmpty()) 0 else (saved?.nextIndex ?: 0).coerceIn(entries.indices)
        return RunPlan(key, nextIndex, entries)
    }

    fun with(plan: RunPlan): RunPlans = RunPlans(bySet.values.filter { it.set != plan.set } + plan)

    fun encode(): String = json.encodeToString(bySet.values.sortedBy { it.set })

    override fun equals(other: Any?): Boolean = other is RunPlans && other.bySet == bySet

    override fun hashCode(): Int = bySet.hashCode()

    private fun RunEntry.clamped(): RunEntry = copy(
        speed = speed.coerceIn(RunEntry.SPEED_RANGE),
        repeats = repeats.coerceIn(RunEntry.REPEAT_RANGE),
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** A plan's key: the set's name, plus `@<raga>` when a varisai set is sung in another raga. */
        fun keyFor(set: VarisaiSet, raga: Raga): String =
            if (set.followsChosenRaga && raga != Raga.MAYAMALAVAGOWLA) "${set.name}${Varisai.RAGA_SEPARATOR}${raga.key}" else set.name

        /** Reads [encode]d text; unreadable or missing text gives no saved plans. */
        fun decode(text: String?): RunPlans = try {
            if (text.isNullOrBlank()) RunPlans() else RunPlans(json.decodeFromString<List<RunPlan>>(text))
        } catch (_: SerializationException) {
            RunPlans()
        } catch (_: IllegalArgumentException) {
            RunPlans()
        }
    }
}

/**
 * One varisai in a run as it will be played: climb from first kala to
 * [speed], singing each kala [repeats] times.
 */
data class RunStep(val varisai: Varisai, val speed: Int, val repeats: Int) {
    init {
        require(speed in RunEntry.SPEED_RANGE) { "speed $speed outside ${RunEntry.SPEED_RANGE}" }
        require(repeats in RunEntry.REPEAT_RANGE) { "repeats $repeats outside ${RunEntry.REPEAT_RANGE}" }
    }

    /** The kalas this step sings, in order. */
    val kalas: IntRange get() = 1..speed

    companion object {
        /** For callers without default arguments (Swift). */
        fun create(varisai: Varisai, speed: Int, repeats: Int): RunStep = RunStep(varisai, speed, repeats)
    }
}

/**
 * Where a moment of a run is: which varisai ([step], a piece-wide index into
 * the run's steps), which kala, which repeat of that kala (0-based) and where
 * inside the pass. [countingIn] is true during the opening count-in avarta.
 */
data class RunMoment(val step: Int, val pass: Int, val kala: Int, val secondsInPass: Double, val countingIn: Boolean) {
    /** A stable identity for this pass (step, kala and repeat): the pitch trace is tagged with it. */
    val passId: Int get() = ((step * RunEntry.MAX_SPEED + (kala - 1)) * RunEntry.MAX_REPEATS) + pass
}

/** One performance in a run: [repeat] of [kala] of [step], at [schedule]. */
data class RunPass(val step: Int, val kala: Int, val repeat: Int, val schedule: Schedule)

/**
 * One performance of a practice run: every varisai from [startIndex], each
 * climbing from first kala to its speed, singing every kala its repeat count.
 *
 * The schedules and the timing are built once, when the run starts. The
 * timeline counts only the performances: a pass's release tail is not
 * followed by a gap, because the next pass starts on the boundary. That
 * keeps the tala grid unbroken whatever the kala.
 */
class PracticeRun(
    val set: VarisaiSet,
    val steps: List<RunStep>,
    val startIndex: Int,
    val settings: PerformanceSettings,
    /** One count-in avarta before the first note (the student's count-in setting). */
    val countIn: Boolean,
) {
    init {
        require(steps.isNotEmpty()) { "a run needs at least one varisai" }
        require(startIndex in steps.indices) { "start index $startIndex outside ${steps.indices}" }
        require(steps.map { it.varisai.tala }.distinct().size == 1) { "a run needs one tala" }
        require(steps.all { it.varisai.set == set }) { "every run step must be in $set" }
    }

    /** The steps actually played: the whole run from [startIndex] on. */
    val playedSteps: List<RunStep> = steps.drop(startIndex)

    /** The schedules of each played step, indexed by kala - 1: a step's climb. */
    val schedulesByStep: List<List<Schedule>> = playedSteps.map { step ->
        step.kalas.map { kala -> Schedule.of(step.varisai, settings.copy(kala = kala)) }
    }

    /** Every pass played, in order: each kala of each step, repeated. */
    val passes: List<RunPass> = buildList {
        playedSteps.forEachIndexed { i, step ->
            step.kalas.forEach { kala ->
                val schedule = schedulesByStep[i][kala - 1]
                repeat(step.repeats) { repeat -> add(RunPass(startIndex + i, kala, repeat, schedule)) }
            }
        }
    }

    /** Seconds of the opening count-in: one avarta, or none. */
    val countInSeconds: Double = if (countIn) {
        steps.first().varisai.tala.aksharas * settings.talaAksharaSeconds
    } else {
        0.0
    }

    /** Seconds of the whole run from [startIndex] (count-in and every pass, no release tails). */
    val totalSeconds: Double = countInSeconds + passes.sumOf { it.schedule.durationSeconds }

    /** The number of passes played. */
    val passCount: Int get() = passes.size

    /** The schedule of [kala] of [step], or null outside the played run. */
    fun scheduleFor(step: Int, kala: Int): Schedule? =
        schedulesByStep.getOrNull(step - startIndex)?.getOrNull(kala - 1)

    /**
     * Where [seconds] (from the start of the run) is, or null before the
     * start and at/after the end. At a pass boundary this is the new pass.
     */
    fun momentAt(seconds: Double): RunMoment? {
        if (seconds < 0.0) return null
        if (seconds < countInSeconds) return RunMoment(startIndex, 0, 1, 0.0, countingIn = true)
        var start = countInSeconds
        for (pass in passes) {
            val end = start + pass.schedule.durationSeconds
            if (seconds < end) {
                return RunMoment(pass.step, pass.repeat, pass.kala, seconds - start, countingIn = false)
            }
            start = end
        }
        return null
    }
}

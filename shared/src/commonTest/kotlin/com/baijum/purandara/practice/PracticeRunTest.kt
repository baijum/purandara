package com.baijum.purandara.practice

import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.schedule.PerformanceSettings
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import com.baijum.purandara.domain.varisai.VarisaiSet
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PracticeRunTest {
    private val catalog = VarisaiCatalog.all
    private val settings = PerformanceSettings(saHz = Pitch.midiToHz(48), tempo = 60)

    private fun stepsOf(set: VarisaiSet): List<RunStep> =
        catalog.filter { it.set == set }.map { RunStep(it, speed = 1, repeats = 1) }

    private fun runOf(
        set: VarisaiSet = VarisaiSet.SARALI,
        steps: List<RunStep> = stepsOf(set),
        startIndex: Int = 0,
        countIn: Boolean = false,
    ) = PracticeRun(set, steps, startIndex, settings, countIn)

    @Test
    fun anUnsetPlanGivesEveryVarisaiFirstKalaOnce() {
        val plan = RunPlans().planFor(VarisaiSet.SARALI, catalog)
        assertEquals("SARALI", plan.set)
        assertEquals(0, plan.nextIndex)
        assertEquals(catalog.filter { it.set == VarisaiSet.SARALI }.map { it.id }, plan.entries.map { it.varisaiId })
        assertTrue(plan.entries.all { it.speed == 1 && it.repeats == 1 })
    }

    @Test
    fun aSavedPlanIsClampedAndFollowsTheCatalogOrder() {
        val sarali = catalog.filter { it.set == VarisaiSet.SARALI }
        val saved = RunPlan(
            set = VarisaiSet.SARALI.name,
            nextIndex = 5,
            entries = listOf(
                RunEntry("sarali-3", speed = 9, repeats = 0),
                RunEntry("gone-1", speed = 2, repeats = 4),
                RunEntry("sarali-1", speed = 0, repeats = 9),
            ),
        )
        val plan = RunPlans(listOf(saved)).planFor(VarisaiSet.SARALI, catalog)
        assertEquals(sarali.map { it.id }, plan.entries.map { it.varisaiId })
        assertEquals(1, plan.entries[0].speed)
        assertEquals(7, plan.entries[0].repeats)
        assertEquals(1, plan.entries[1].speed)
        assertEquals(1, plan.entries[1].repeats)
        assertEquals(4, plan.entries[2].speed)
        assertEquals(1, plan.entries[2].repeats)
        assertEquals(5, plan.nextIndex)
    }

    @Test
    fun theContinuePointIsClampedIntoTheSet() {
        fun plan(next: Int) = RunPlans(listOf(RunPlan(VarisaiSet.SARALI.name, next, emptyList())))
            .planFor(VarisaiSet.SARALI, catalog)
        assertEquals(0, plan(-3).nextIndex)
        assertEquals(15, plan(99).nextIndex)
        val empty = RunPlans(listOf(RunPlan("GITAM", 4, emptyList())))
            .planFor(VarisaiSet.VARNAM, catalog)
        assertEquals(0, empty.nextIndex)
    }

    @Test
    fun encodeAndDecodeRoundTripKeepsTheSpeedInTheKalaField() {
        val plans = RunPlans().with(
            RunPlan(
                VarisaiSet.SARALI.name, nextIndex = 3,
                entries = listOf(RunEntry("sarali-1", speed = 2, repeats = 3), RunEntry("sarali-2", speed = 4, repeats = 7)),
            ),
        )
        val text = plans.encode()
        assertTrue(text.contains("\"kala\":2"), "the stored field stays compatible: $text")
        assertEquals(plans, RunPlans.decode(text))
    }

    @Test
    fun aPlanFromTheFirstVersionStillReadsItsKalaAsTheSpeed() {
        val legacy = """[{"set":"SARALI","nextIndex":0,"entries":[{"varisaiId":"sarali-1","kala":3,"repeats":2}]}]"""
        val plan = RunPlans.decode(legacy).planFor(VarisaiSet.SARALI, catalog)
        assertEquals(3, plan.entries[0].speed)
        assertEquals(2, plan.entries[0].repeats)
    }

    @Test
    fun unreadableTextGivesNoPlans() {
        assertEquals(RunPlans(), RunPlans.decode(null))
        assertEquals(RunPlans(), RunPlans.decode(""))
        assertEquals(RunPlans(), RunPlans.decode("{not json"))
        assertEquals(RunPlans(), RunPlans.decode("""[{"unexpected":1}]"""))
    }

    @Test
    fun aSpeedClimbSingsEveryKalaTheRepeatCountTimes() {
        val sarali = catalog.filter { it.set == VarisaiSet.SARALI }.first()
        val run = runOf(steps = listOf(RunStep(sarali, speed = 3, repeats = 2)))
        assertEquals(listOf(1, 1, 2, 2, 3, 3), run.passes.map { it.kala })
        assertEquals(listOf(0, 1, 0, 1, 0, 1), run.passes.map { it.repeat })
        assertEquals(6, run.passCount)
        // One schedule per kala of the climb, and none beyond it.
        assertEquals(listOf(1, 2, 3), run.schedulesByStep.single().map { it.settings.kala })
        assertEquals(3, run.scheduleFor(0, 3)?.settings?.kala)
        assertNull(run.scheduleFor(0, 4))
    }

    @Test
    fun aRunBuildsSchedulesForEveryKalaOfEveryPlayedStep() {
        val sarali = catalog.filter { it.set == VarisaiSet.SARALI }.take(3)
        val steps = listOf(
            RunStep(sarali[0], speed = 1, repeats = 3),
            RunStep(sarali[1], speed = 2, repeats = 1),
            RunStep(sarali[2], speed = 4, repeats = 2),
        )
        val run = PracticeRun(VarisaiSet.SARALI, steps, startIndex = 1, settings, countIn = true)
        assertEquals(steps.drop(1), run.playedSteps)
        assertEquals(listOf(2, 4), run.schedulesByStep.map { it.size })
        assertEquals(listOf(1, 2), run.schedulesByStep[0].map { it.settings.kala })
        assertEquals(listOf(1, 2, 3, 4), run.schedulesByStep[1].map { it.settings.kala })
        assertEquals(2 + 8, run.passCount)
        val expected = run.countInSeconds + run.passes.sumOf { it.schedule.durationSeconds }
        assertEquals(expected, run.totalSeconds, 1e-9)
        // One count-in avarta: eight beats at 60 aksharas per minute.
        assertEquals(8.0, run.countInSeconds, 1e-9)
    }

    @Test
    fun momentAtClimbsEveryKalaBeforeTheNextStep() {
        val sarali = catalog.filter { it.set == VarisaiSet.SARALI }.take(2)
        val steps = listOf(
            RunStep(sarali[0], speed = 2, repeats = 2),
            RunStep(sarali[1], speed = 1, repeats = 1),
        )
        val run = PracticeRun(VarisaiSet.SARALI, steps, startIndex = 0, settings, countIn = true)
        val countIn = run.countInSeconds
        assertEquals(RunMoment(0, 0, 1, 0.0, countingIn = true), run.momentAt(0.0))
        assertEquals(RunMoment(0, 0, 1, 0.0, countingIn = true), run.momentAt(countIn - 0.001))
        val slow = run.schedulesByStep[0][0].durationSeconds
        val fast = run.schedulesByStep[0][1].durationSeconds
        assertEquals(RunMoment(0, 0, 1, 0.0, false), run.momentAt(countIn))
        assertEquals(RunMoment(0, 1, 1, 0.0, false), run.momentAt(countIn + slow))
        assertEquals(RunMoment(0, 0, 2, 0.0, false), run.momentAt(countIn + slow * 2))
        assertEquals(RunMoment(0, 1, 2, 0.0, false), run.momentAt(countIn + slow * 2 + fast))
        assertEquals(RunMoment(1, 0, 1, 0.0, false), run.momentAt(countIn + slow * 2 + fast * 2))
        assertNull(run.momentAt(run.totalSeconds + 0.001))
        assertNull(run.momentAt(-0.001))
    }

    @Test
    fun aKalaChangeLeavesTheTalaAksharaAlone() {
        val sarali = catalog.filter { it.set == VarisaiSet.SARALI }.first()
        val run = PracticeRun(VarisaiSet.SARALI, listOf(RunStep(sarali, speed = 4, repeats = 1)), 0, settings, false)
        assertEquals(listOf(1.0), run.schedulesByStep.single().map { it.settings.talaAksharaSeconds }.distinct())
        // At the fourth kala each written akshara lasts an eighth as long.
        assertEquals(0.125, run.schedulesByStep.single()[3].settings.writtenAksharaSeconds, 1e-9)
    }

    @Test
    fun aResumedRunStartsAtItsSavedVarisai() {
        val sarali = catalog.filter { it.set == VarisaiSet.SARALI }.take(2)
        val steps = listOf(RunStep(sarali[0], speed = 1, repeats = 2), RunStep(sarali[1], speed = 2, repeats = 1))
        val resumed = PracticeRun(VarisaiSet.SARALI, steps, startIndex = 1, settings, countIn = true)
        assertEquals(RunMoment(1, 0, 1, 0.0, false), resumed.momentAt(resumed.countInSeconds))
        assertNull(resumed.momentAt(resumed.totalSeconds + 0.001))
        assertNull(resumed.scheduleFor(0, 1))
    }

    @Test
    fun everyNumberedVarisaiSetCanRunAndSharesOneTala() {
        val sets = VarisaiSet.entries.filter { it.practisesAsRun }
        assertEquals(
            listOf(VarisaiSet.SARALI, VarisaiSet.MADHYA_STHAYI, VarisaiSet.JANTA, VarisaiSet.VAKRA_JANTA, VarisaiSet.DHATTU),
            sets,
        )
        sets.forEach { set ->
            val exercises = catalog.filter { it.set == set }
            val run = runOf(set, exercises.map { RunStep(it, 1, 1) })
            assertEquals(exercises.size, run.playedSteps.size, set.name)
            assertEquals(1, exercises.map { it.tala }.distinct().size, set.name)
            assertTrue(run.totalSeconds > 0, set.name)
        }
    }

    @Test
    fun aRunRejectsBadSteps() {
        val one = catalog.first()
        assertFailsWith<IllegalArgumentException> { RunStep(one, speed = 0, repeats = 1) }
        assertFailsWith<IllegalArgumentException> { RunStep(one, speed = 5, repeats = 1) }
        assertFailsWith<IllegalArgumentException> { RunStep(one, speed = 1, repeats = 8) }
        assertFailsWith<IllegalArgumentException> {
            PracticeRun(VarisaiSet.SARALI, emptyList(), 0, settings, countIn = false)
        }
        assertFailsWith<IllegalArgumentException> {
            PracticeRun(VarisaiSet.SARALI, stepsOf(VarisaiSet.SARALI), 99, settings, countIn = false)
        }
    }

    @Test
    fun aRunCannotMixSets() {
        val sarali = catalog.first { it.set == VarisaiSet.SARALI }
        val janta = catalog.first { it.set == VarisaiSet.JANTA }
        assertFailsWith<IllegalArgumentException> {
            PracticeRun(VarisaiSet.SARALI, listOf(RunStep(sarali, 1, 1), RunStep(janta, 1, 1)), 0, settings, false)
        }
    }

    @Test
    fun theTotalSecondsIsTheSumOfEveryPass() {
        val sarali = catalog.filter { it.set == VarisaiSet.SARALI }.take(2)
        val run = PracticeRun(
            VarisaiSet.SARALI,
            listOf(RunStep(sarali[0], speed = 2, repeats = 2), RunStep(sarali[1], speed = 3, repeats = 1)),
            0, settings, countIn = true,
        )
        val expected = 8.0 + run.passes.sumOf { it.schedule.durationSeconds }
        assertTrue(abs(expected - run.totalSeconds) < 1e-9)
        // speed 2 twice is four passes; speed 3 once is three.
        assertEquals(7, run.passCount)
    }

    @Test
    fun theRunKeepsThePiecesOwnAvartaNumbers() {
        // The notation of each step is the piece's own, so a section's numbering is untouched.
        val sarali = catalog.filter { it.set == VarisaiSet.SARALI }.take(2)
        val run = runOf(VarisaiSet.SARALI, sarali.map { RunStep(it, 1, 1) })
        assertEquals(sarali.map { it.id }, run.playedSteps.map { it.varisai.id })
        assertEquals(4, run.schedulesByStep[0][0].varisai.avartas.size)
    }
}

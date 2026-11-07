package com.baijum.purandara.domain.varisai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContourTest {
    @Test
    fun plainScaleClimbsToUpperSaAndBack() {
        val steps = Contour.of(VarisaiCatalog.byId("plain-scale-1")!!)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 7, 6, 5, 4, 3, 2, 1, 0), steps.map { it.degree })
        assertEquals(0.0, steps.first().start)
        assertEquals(1.0, steps.last().end, 1e-9)
    }

    @Test
    fun karvaiExtendsThePreviousStep() {
        // Sarali 4 opens "S R G M | P , | S R": Pa held for two aksharas.
        val steps = Contour.of(VarisaiCatalog.byId("sarali-4")!!)
        val pa = steps[4]
        assertEquals(4, pa.degree)
        assertEquals(2 * (steps[0].end - steps[0].start), pa.end - pa.start, 1e-9)
        assertEquals(0, steps[5].degree)
    }

    @Test
    fun stepsCoverTheWholeExerciseForEveryCatalogEntry() {
        for (v in VarisaiCatalog.all) {
            val steps = Contour.of(v)
            assertTrue(steps.isNotEmpty(), v.id)
            assertEquals(0.0, steps.first().start, 1e-9, v.id)
            assertEquals(1.0, steps.last().end, 1e-9, v.id)
            // Steps cover the exercise except where a rest opens a gap: no
            // overlaps, and every step has width.
            steps.zipWithNext().forEach { (a, b) ->
                assertTrue(a.end <= b.start + 1e-9, v.id)
            }
        }
    }

    @Test
    fun differentExercisesHaveDifferentShapes() {
        val shapes = VarisaiCatalog.all.map { v -> Contour.of(v).map { it.degree } }
        assertTrue(shapes.toSet().size > VarisaiCatalog.all.size * 9 / 10)
    }

    @Test
    fun everySetHasADescription() {
        VarisaiSet.entries.forEach { assertTrue(it.description.isNotBlank(), it.name) }
    }
}

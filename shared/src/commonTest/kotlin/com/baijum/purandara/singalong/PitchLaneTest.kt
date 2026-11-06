package com.baijum.purandara.singalong

import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.schedule.PerformanceSettings
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.domain.varisai.VarisaiCatalog
import kotlin.test.Test
import kotlin.test.assertEquals

class PitchLaneTest {
    private fun lane(id: String) = PitchLane(Schedule.of(VarisaiCatalog.byId(id)!!, PerformanceSettings(saHz = Pitch.midiToHz(48))))

    @Test
    fun varisaiLaneIsSaToUpperSaAsBefore() {
        val lane = lane("sarali-1")
        assertEquals(-150.0, lane.lowCents, 1e-9)
        assertEquals(1350.0, lane.highCents, 1e-9)
        assertEquals("S R G M P D N S′", lane.lines.joinToString(" ") { PitchLane.label(it) })
    }

    @Test
    fun gitamLaneFollowsItsRangeAndRaga() {
        // Padumanabha: down to lower Dha, up to upper Ri; Malahari has no N.
        val lane = lane("gitam-4")
        assertEquals("Ḍ S R G M P D S′ R′", lane.lines.joinToString(" ") { PitchLane.label(it) })
        assertEquals(PitchLane.centsOf(lane.lines.first()) - 150.0, lane.lowCents, 1e-9)
        assertEquals(0.0, lane.fraction(lane.lowCents), 1e-9)
        assertEquals(1.0, lane.fraction(lane.highCents), 1e-9)
    }
}

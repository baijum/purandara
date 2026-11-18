package com.baijum.purandara.audio

import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Tuning
import com.baijum.purandara.domain.schedule.PerformanceSettings
import com.baijum.purandara.domain.schedule.Schedule
import com.baijum.purandara.domain.varisai.Varisai
import com.baijum.purandara.domain.varisai.VarisaiCatalog

/**
 * Hearing a raga in the list before choosing it (docs/plan-raga-followups.md
 * F1): its plain scale, up and down once at the student's Sa and tuning,
 * with the tanpura, no count-in and no click.
 */
object RagaPreview {
    /** Tala aksharas per minute: the two avartas take about 11 seconds. */
    const val TEMPO: Int = 90

    private const val PLAIN_SCALE_ID = "plain-scale-1"

    /** The plain scale in [raga]. */
    fun varisai(raga: Raga): Varisai = VarisaiCatalog.byId(PLAIN_SCALE_ID)!!.inRaga(raga)

    fun schedule(raga: Raga, saHz: Double, tuning: Tuning): Schedule =
        Schedule.of(varisai(raga), PerformanceSettings(saHz = saHz, tempo = TEMPO, kala = 1, tuning = tuning))

    fun options(harmoniumLevel: Float, droneLevel: Float): RenderOptions = RenderOptions.practice(
        drone = true,
        click = false,
        loop = false,
        countIn = false,
        harmoniumLevel = harmoniumLevel,
        droneLevel = droneLevel,
    )
}

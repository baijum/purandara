package com.baijum.purandara.data

import android.content.Context
import com.baijum.purandara.domain.Shruti
import com.baijum.purandara.domain.Tuning
import com.baijum.purandara.domain.schedule.PerformanceSettings

/** The student's practice settings. */
data class PracticeSettings(
    val saMidi: Int = Shruti.DEFAULT_SA_MIDI,
    val tempo: Int = PerformanceSettings.DEFAULT_TEMPO,
    val kala: Int = 1,
    val drone: Boolean = true,
    val click: Boolean = false,
    val loop: Boolean = false,
    val tuning: Tuning = Tuning.JUST,
) {
    fun performance(): PerformanceSettings =
        PerformanceSettings(saHz = Shruti.saHz(saMidi), tempo = tempo, kala = kala, tuning = tuning)
}

/** Persists [PracticeSettings] in SharedPreferences. Values out of range fall back to defaults. */
class PracticePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("practice", Context.MODE_PRIVATE)

    fun load(): PracticeSettings {
        val d = PracticeSettings()
        return PracticeSettings(
            saMidi = prefs.getInt(SA, d.saMidi).takeIf { it in Shruti.SA_RANGE } ?: d.saMidi,
            tempo = prefs.getInt(TEMPO, d.tempo).takeIf { it in PerformanceSettings.TEMPO_RANGE } ?: d.tempo,
            kala = prefs.getInt(KALA, d.kala).takeIf { it in PerformanceSettings.KALA_RANGE } ?: d.kala,
            drone = prefs.getBoolean(DRONE, d.drone),
            click = prefs.getBoolean(CLICK, d.click),
            loop = prefs.getBoolean(LOOP, d.loop),
            tuning = Tuning.entries.firstOrNull { it.name == prefs.getString(TUNING, null) } ?: d.tuning,
        )
    }

    fun save(s: PracticeSettings) {
        prefs.edit()
            .putInt(SA, s.saMidi)
            .putInt(TEMPO, s.tempo)
            .putInt(KALA, s.kala)
            .putBoolean(DRONE, s.drone)
            .putBoolean(CLICK, s.click)
            .putBoolean(LOOP, s.loop)
            .putString(TUNING, s.tuning.name)
            .apply()
    }

    private companion object {
        const val SA = "sa_midi"
        const val TEMPO = "tempo"
        const val KALA = "kala"
        const val DRONE = "drone"
        const val CLICK = "click"
        const val LOOP = "loop"
        const val TUNING = "tuning"
    }
}

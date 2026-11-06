package com.baijum.purandara.data

import android.content.Context
import com.baijum.purandara.audio.RenderOptions
import com.baijum.purandara.domain.Shruti
import com.baijum.purandara.domain.Tuning
import com.baijum.purandara.domain.schedule.PerformanceSettings
import com.baijum.purandara.practice.PracticeHistory

/** The student's practice settings. */
data class PracticeSettings(
    val saMidi: Int = Shruti.DEFAULT_SA_MIDI,
    val tempo: Int = PerformanceSettings.DEFAULT_TEMPO,
    val kala: Int = 1,
    val drone: Boolean = true,
    val click: Boolean = false,
    val loop: Boolean = false,
    val countIn: Boolean = true,
    val harmoniumLevel: Float = RenderOptions.DEFAULT_HARMONIUM_LEVEL,
    val droneLevel: Float = RenderOptions.DEFAULT_DRONE_LEVEL,
    val tuning: Tuning = Tuning.JUST,
    /** Sing along with pitch feedback (needs the microphone). */
    val singAlong: Boolean = false,
    /** Harmonium plays every note (true) or stays silent so only drone and click guide (false). */
    val harmoniumGuide: Boolean = true,
    /** Sing-along cancels the app's own sound from the mic when playing through the speaker. */
    val echoCancellation: Boolean = true,
) {
    fun performance(): PerformanceSettings =
        PerformanceSettings(saHz = Shruti.saHz(saMidi), tempo = tempo, kala = kala, tuning = tuning)

    /** Sing-along forces the count-in (leakage is measured while the student is silent). */
    fun renderOptions(): RenderOptions = RenderOptions.practice(
        drone = drone,
        click = click,
        loop = loop,
        countIn = countIn || singAlong,
        harmoniumLevel = if (singAlong && !harmoniumGuide) 0f else harmoniumLevel,
        droneLevel = droneLevel,
    )
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
            countIn = prefs.getBoolean(COUNT_IN, d.countIn),
            harmoniumLevel = prefs.getFloat(HARMONIUM_LEVEL, d.harmoniumLevel).takeIf { it in 0f..1f } ?: d.harmoniumLevel,
            droneLevel = prefs.getFloat(DRONE_LEVEL, d.droneLevel).takeIf { it in 0f..1f } ?: d.droneLevel,
            singAlong = prefs.getBoolean(SING_ALONG, d.singAlong),
            harmoniumGuide = prefs.getBoolean(HARMONIUM_GUIDE, d.harmoniumGuide),
            echoCancellation = prefs.getBoolean(ECHO_CANCELLATION, d.echoCancellation),
            tuning = Tuning.entries.firstOrNull { it.name == prefs.getString(TUNING, null) } ?: d.tuning,
        )
    }

    fun loadHistory(): PracticeHistory = PracticeHistory.decode(prefs.getString(HISTORY, null))

    fun saveHistory(history: PracticeHistory) {
        prefs.edit().putString(HISTORY, history.encode()).apply()
    }

    fun save(s: PracticeSettings) {
        prefs.edit()
            .putInt(SA, s.saMidi)
            .putInt(TEMPO, s.tempo)
            .putInt(KALA, s.kala)
            .putBoolean(DRONE, s.drone)
            .putBoolean(CLICK, s.click)
            .putBoolean(LOOP, s.loop)
            .putBoolean(COUNT_IN, s.countIn)
            .putFloat(HARMONIUM_LEVEL, s.harmoniumLevel)
            .putFloat(DRONE_LEVEL, s.droneLevel)
            .putString(TUNING, s.tuning.name)
            .putBoolean(SING_ALONG, s.singAlong)
            .putBoolean(HARMONIUM_GUIDE, s.harmoniumGuide)
            .putBoolean(ECHO_CANCELLATION, s.echoCancellation)
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
        const val COUNT_IN = "count_in"
        const val HARMONIUM_LEVEL = "harmonium_level"
        const val DRONE_LEVEL = "drone_level"
        const val SING_ALONG = "sing_along"
        const val HARMONIUM_GUIDE = "harmonium_guide"
        const val ECHO_CANCELLATION = "echo_cancellation"
        const val HISTORY = "practice_history"
    }
}

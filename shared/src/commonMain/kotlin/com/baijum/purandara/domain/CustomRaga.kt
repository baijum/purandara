package com.baijum.purandara.domain

import com.baijum.purandara.singalong.Display
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The student's own raga (docs/plan-recording-insights.md, step 3): a name,
 * and the swaras of its arohana and avarohana, each in scale order without
 * Sa (Sa begins and ends both). Straight up and straight down: one swara per
 * letter, no zigzag.
 */
@Serializable
data class CustomRaga(
    val key: String,
    val name: String,
    val arohana: List<Swara>,
    val avarohana: List<Swara>,
) {
    /** As a [Raga], for naming swaras and "What are you singing?"; null if the saved one no longer passes the checks. */
    fun toRaga(): Raga? {
        if (problem(name, arohana, avarohana) != null) return null
        val byLetter = (arohana + avarohana).associateBy { letterOf(it) } + (SwaraLetter.S to Swara.S)
        return Raga(
            name.trim(),
            byLetter,
            key = key,
            arohanam = (listOf(Swara.S) + arohana).map { Note(it) } + Note(Swara.S, Octave.TARA),
            avarohanam = (listOf(Note(Swara.S, Octave.TARA)) + avarohana.reversed().map { Note(it) } + Note(Swara.S)),
        )
    }

    companion object {
        /** The letters chosen in the editor, in order (Sa is always there). */
        val LETTERS: List<SwaraLetter> = SwaraLetter.entries.drop(1)

        /** Key prefix of the student's ragas. */
        const val KEY_PREFIX = "own-"

        const val MAX_NAME = 40

        private const val MIN_SWARAS = 5

        /** The swaras [letter] can be: R₁ R₂ R₃ for Ri, P alone for Pa. */
        fun choices(letter: SwaraLetter): List<Swara> = Swara.entries.filter { letterOf(it) == letter }

        fun letterOf(swara: Swara): SwaraLetter = SwaraLetter.fromSymbol(swara.symbol.first())!!

        /** The key for a raga created at [millis]: never reused, since a deleted raga's recordings keep theirs. */
        fun keyAt(millis: Long): String = "$KEY_PREFIX$millis"

        fun isOwn(key: String): Boolean = key.startsWith(KEY_PREFIX)

        /**
         * What is wrong with this raga's swaras and name, as one plain
         * sentence, or null when it can be saved. [arohana] and [avarohana]
         * are in scale order (low to high), without Sa.
         */
        fun problem(name: String, arohana: List<Swara>, avarohana: List<Swara>): String? {
            if (name.isBlank()) return "Give the raga a name."
            if (arohana.isEmpty()) return "Choose the swaras of the arohana."
            if (avarohana.isEmpty()) return "Choose the swaras of the avarohana."
            rising(arohana, "arohana")?.let { return it }
            rising(avarohana, "avarohana")?.let { return it }
            val up = arohana.associateBy { letterOf(it) }
            avarohana.forEach { down ->
                val other = up[letterOf(down)]
                if (other != null && other != down) {
                    return "Use the same ${spoken(letterOf(down))} both ways (${label(other)} going up, ${label(down)} coming down)."
                }
            }
            val all = (arohana + avarohana).distinct()
            all.groupBy { it.semitone }.values.firstOrNull { it.size > 1 }?.let { (a, b) ->
                return "${label(a)} and ${label(b)} are the same note: use one of them."
            }
            if (all.size + 1 < MIN_SWARAS) return "A raga needs at least five swaras, counting Sa."
            return null
        }

        private fun rising(swaras: List<Swara>, which: String): String? {
            swaras.zipWithNext().forEach { (low, high) ->
                if (high.semitone <= low.semitone) {
                    return if (high.semitone == low.semitone) {
                        "In the $which, ${label(low)} and ${label(high)} are the same note: choose one of them."
                    } else {
                        "In the $which, ${label(high)} is below ${label(low)}: choose a higher ${spoken(letterOf(high))} or a lower ${spoken(letterOf(low))}."
                    }
                }
            }
            return null
        }

        /** "S R₂ G₃ P D₂ S′" (arohana) or "S′ D₂ P G₃ R₂ S" ([descending]). */
        fun line(swaras: List<Swara>, descending: Boolean): String {
            val labels = swaras.map { label(it) }
            return if (descending) (listOf("S′") + labels.reversed() + "S").joinToString(" ") else (listOf("S") + labels + "S′").joinToString(" ")
        }

        /** "Sa, Ri 2, Ga 3, Pa, Dha 2, high Sa" ([line] read aloud). */
        fun spokenLine(swaras: List<Swara>, descending: Boolean): String {
            val names = swaras.map { spokenSwara(it) }
            return if (descending) (listOf("high Sa") + names.reversed() + "Sa").joinToString(", ") else (listOf("Sa") + names + "high Sa").joinToString(", ")
        }

        /** "Ri 2", "Pa" (a chip in the editor read aloud). */
        fun spokenSwara(swara: Swara): String {
            val digit = swara.symbol.drop(1)
            return if (digit.isEmpty()) spoken(letterOf(swara)) else "${spoken(letterOf(swara))} $digit"
        }

        /** "Ri", "Dha". */
        fun spoken(letter: SwaraLetter): String = SPOKEN.getValue(letter)

        fun label(swara: Swara): String = Display.swaraLabel(swara)

        private val SPOKEN = mapOf(
            SwaraLetter.S to "Sa", SwaraLetter.R to "Ri", SwaraLetter.G to "Ga", SwaraLetter.M to "Ma",
            SwaraLetter.P to "Pa", SwaraLetter.D to "Dha", SwaraLetter.N to "Ni",
        )
    }
}

/**
 * The student's ragas, saved on the device as [FILE_NAME] beside the
 * recordings index. Immutable, like `RecordingLibrary`.
 */
class CustomRagas(ragas: Collection<CustomRaga> = emptyList()) {
    /** By name. */
    val ragas: List<CustomRaga> = ragas.distinctBy { it.key }.sortedBy { it.name.lowercase() }

    operator fun get(key: String): CustomRaga? = ragas.firstOrNull { it.key == key }

    /**
     * Why [name] with these swaras can't be saved (editing [key], or a new
     * raga when null), or null when it can: [CustomRaga.problem], and the
     * name must not be one of the app's ragas or another of the student's.
     */
    fun problem(key: String?, name: String, arohana: List<Swara>, avarohana: List<Swara>): String? {
        val trimmed = name.trim()
        Ragas.all.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return "The app already has ${it.name}: choose another name." }
        ragas.firstOrNull { it.key != key && it.name.trim().equals(trimmed, ignoreCase = true) }?.let {
            return "You already have a raga called ${it.name.trim()}."
        }
        return CustomRaga.problem(name, arohana, avarohana)
    }

    /** Adds or replaces the raga at [key]; unchanged if it has a [problem]. */
    fun save(key: String, name: String, arohana: List<Swara>, avarohana: List<Swara>): CustomRagas {
        if (problem(key, name, arohana, avarohana) != null) return this
        val raga = CustomRaga(key, name.trim().take(CustomRaga.MAX_NAME), arohana.distinct().sortedBy { it.semitone }, avarohana.distinct().sortedBy { it.semitone })
        return CustomRagas(ragas.filter { it.key != key } + raga)
    }

    fun delete(key: String): CustomRagas = CustomRagas(ragas.filter { it.key != key })

    /** As [Raga]s, by name (any that no longer pass the checks are left out). */
    fun toRagas(): List<Raga> = ragas.mapNotNull { it.toRaga() }

    fun encode(): String = json.encodeToString(ragas)

    override fun equals(other: Any?): Boolean = other is CustomRagas && other.ragas == ragas

    override fun hashCode(): Int = ragas.hashCode()

    companion object {
        const val FILE_NAME = "ragas.json"

        private val json = Json { ignoreUnknownKeys = true }

        /** The saved ragas; nothing (not a crash) for a missing or damaged file. */
        fun decode(text: String?): CustomRagas {
            if (text.isNullOrBlank()) return CustomRagas()
            return try {
                CustomRagas(json.decodeFromString<List<CustomRaga>>(text))
            } catch (e: SerializationException) {
                CustomRagas()
            } catch (e: IllegalArgumentException) {
                CustomRagas()
            }
        }
    }
}

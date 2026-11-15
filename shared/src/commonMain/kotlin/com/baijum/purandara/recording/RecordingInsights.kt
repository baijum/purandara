package com.baijum.purandara.recording

import com.baijum.purandara.domain.Note
import com.baijum.purandara.domain.Raga
import com.baijum.purandara.domain.Shruti
import com.baijum.purandara.domain.Swara
import com.baijum.purandara.domain.Tuning
import com.baijum.purandara.singalong.Display
import com.baijum.purandara.singalong.Strictness
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** What the student meant to sing, from a raga (docs/plan-recording-insights.md). */
enum class Scale {
    AROHANA,
    AVAROHANA,
    BOTH,
    ;

    /** The swaras meant, from [raga]'s arohana and avarohana ("both" shares the top Sa). */
    fun expected(raga: Raga): List<Note> = when (this) {
        AROHANA -> raga.arohanam
        AVAROHANA -> raga.avarohanam
        BOTH -> raga.arohanam + raga.avarohanam.drop(1)
    }
}

/** How close a swara was, in the Strictness's bands. */
enum class Closeness { IN_TUNE, A_LITTLE_FLAT, A_LITTLE_SHARP, FLAT, SHARP, MISSED }

/**
 * One swara in "How it went": a swara meant (or, singing freely, one heard)
 * with how close it was.
 *
 * @property cents from its target, against the fitted Sa when one is
 *   offered; null when missed.
 * @property heldSeconds how long it was sung in all.
 * @property spreadCents for a held note (at least [RecordingInsights.HELD_SECONDS]),
 *   how far its pitch strayed (90% of its frames were within this); else null.
 */
data class SwaraInsight(
    val swara: Swara,
    val octave: Int,
    val closeness: Closeness,
    val cents: Double?,
    val heldSeconds: Double,
    val spreadCents: Double?,
) {
    val label: String get() = Display.swaraLabel(swara, octave)

    /**
     * Held steadily: its held middle stayed within the Strictness's close
     * band (the in-tune band called every real held note "wavered").
     */
    fun steady(strictness: Strictness): Boolean? = spreadCents?.let { it <= strictness.closeCents }
}

/**
 * "How it went" for one recording.
 *
 * @property saOffsetCents how far the Sa the notes fit best is from the
 *   recording's Sa (0 when none is offered).
 * @property suggestedSa that Sa as a Sa choice (MIDI, fine-tune cents), when
 *   it differs by more than the in-tune band.
 * @property swaras the swaras meant in order (with [Scale]), or each swara heard.
 * @property extras notes heard that weren't meant (not counting short movement).
 * @property tanpuraNotHeard the tanpura switch was on, but its first seconds were quiet.
 */
class Insights(
    val saOffsetCents: Double,
    val suggestedSa: Pair<Int, Int>?,
    val swaras: List<SwaraInsight>,
    val extras: List<HeardNote>,
    val tanpuraNotHeard: Boolean,
    val summary: String,
)

/**
 * Builds [Insights] from a recording's transcription (docs/plan-recording-insights.md).
 * Pure: changing the Sa, raga, scale, tuning or strictness just builds it again.
 */
object RecordingInsights {
    /** A heard note within this of a swara meant can match it (a semitone and a half). */
    const val MATCH_CENTS = 150.0

    /** Notes shorter than this beside a matched note are movement (a scoop, a drift), not extras. */
    const val MOVEMENT_SECONDS = 0.25

    /** Notes held at least this long get a steadiness reading. */
    const val HELD_SECONDS = 0.5

    private const val FIT_RANGE = 50
    private const val MIN_FIT_NOTES = 3
    private const val MISS_COST = 1.0
    private const val EXTRA_COST = 1.0
    private const val MOVEMENT_COST = 0.3
    private const val ABSORB_COST = 0.05
    private const val ABSORB_AFTER_BREATH_COST = 0.6
    private const val BREATH_SECONDS = 0.15
    private const val EDGE_SECONDS = 0.1
    private const val MATCH_WEIGHT = 0.5
    private const val SPREAD_SHARE = 0.9
    private const val CENTS_PER_OCTAVE = 1200.0

    fun of(
        recording: Recording,
        track: PitchTrack,
        transcription: Transcription,
        tuning: Tuning,
        strictness: Strictness,
    ): Insights {
        val raga = recording.raga
        val scale = recording.scale
        val notes = transcription.notes
        val expected = if (raga != null && scale != null) scale.expected(raga) else null
        val groups = if (expected != null) aligned(expected, notes, tuning) else freely(notes)
        val offset = fittedOffset(groups.matched.map { it.cents(tuning) to it.seconds })
        val suggested = offset.takeIf { abs(it) > strictness.inTuneCents }?.let { Shruti.choiceNear(recording.saHz * 2.0.pow(it / CENTS_PER_OCTAVE)) }
        val applied = if (suggested != null) offset else 0.0
        val swaras = groups.items.map { item -> item.insight(applied, track, recording.saHz, tuning, strictness) }
        val tanpuraNotHeard = recording.externalTanpura && TanpuraProfile.from(track)?.heard == false
        return Insights(
            saOffsetCents = applied,
            suggestedSa = suggested,
            swaras = swaras,
            extras = groups.extras,
            tanpuraNotHeard = tanpuraNotHeard,
            summary = summary(swaras, groups.extras, strictness, expected != null),
        )
    }

    // ── Grouping notes into swaras ───────────────────────────────────

    /** A swara meant (or heard) and the notes sung for it. */
    private class Item(val swara: Swara, val octave: Int, val notes: List<HeardNote>) {
        val seconds: Double get() = notes.sumOf { it.durationSeconds }

        /**
         * Its notes' cents from the item's target: the duration-weighted
         * median, so a short drift beside a long held note doesn't pull it.
         */
        fun cents(tuning: Tuning): Double {
            val target = targetCents(swara, octave, tuning)
            val sorted = notes.map { (heardCents(it, tuning) - target) to it.durationSeconds }.sortedBy { it.first }
            var acc = 0.0
            for ((c, w) in sorted) {
                acc += w
                if (acc >= seconds / 2) return c
            }
            return sorted.last().first
        }

        fun insight(offset: Double, track: PitchTrack, saHz: Double, tuning: Tuning, strictness: Strictness): SwaraInsight {
            if (notes.isEmpty()) return SwaraInsight(swara, octave, Closeness.MISSED, null, 0.0, null)
            val c = cents(tuning) - offset
            return SwaraInsight(swara, octave, closeness(c, strictness), c, seconds, spread(track, saHz, tuning))
        }

        private fun spread(track: PitchTrack, saHz: Double, tuning: Tuning): Double? {
            if (seconds < HELD_SECONDS) return null
            // The held middle of each note: its first and last moments slide in and out.
            val deviations = notes.filter { it.durationSeconds > 2 * EDGE_SECONDS }.flatMap { note ->
                val centre = heardCents(note, tuning)
                val from = track.indexAt(note.startSeconds + EDGE_SECONDS)
                val until = track.indexAt(note.endSeconds - EDGE_SECONDS)
                (from..until).mapNotNull { i -> track.frequencyAt(i)?.let { abs(CENTS_PER_OCTAVE * log2(it / saHz) - centre) } }
                    .filter { it < MATCH_CENTS }
            }.sorted()
            if (deviations.isEmpty()) return null
            return deviations[((deviations.size - 1) * SPREAD_SHARE).roundToInt()]
        }
    }

    private class Groups(val items: List<Item>, val extras: List<HeardNote>) {
        val matched: List<Item> get() = items.filter { it.notes.isNotEmpty() }
    }

    /** Singing freely: one item per swara heard (not counting short movement), in order of first appearance. */
    private fun freely(notes: List<HeardNote>): Groups {
        val bySwara = LinkedHashMap<Pair<Swara, Int>, MutableList<HeardNote>>()
        notes.filter { it.durationSeconds >= MOVEMENT_SECONDS }.forEach { bySwara.getOrPut(it.swara to it.octave) { mutableListOf() } += it }
        return Groups(bySwara.map { (key, list) -> Item(key.first, key.second, list) }, emptyList())
    }

    /**
     * The swaras meant matched to the notes heard by an alignment (edit
     * distance), so a missed or extra note doesn't throw off the rest. A
     * swara meant can take several heard notes (a note split in two); short
     * notes left over are movement, longer ones are extras.
     */
    private fun aligned(expected: List<Note>, heard: List<HeardNote>, tuning: Tuning): Groups {
        val m = expected.size
        val n = heard.size
        val targets = expected.map { targetCents(it.swara, it.octave.offset, tuning) }
        val pitches = heard.map { heardCents(it, tuning) }
        fun near(i: Int, j: Int) = abs(pitches[j] - targets[i]) <= MATCH_CENTS
        fun extraCost(j: Int) = if (heard[j].durationSeconds < MOVEMENT_SECONDS) MOVEMENT_COST else EXTRA_COST
        // A piece sung straight on (no breath) is more of the same swara; after a breath, it's the next one.
        fun absorbCost(j: Int) = if (j > 0 && heard[j].startSeconds - heard[j - 1].endSeconds < BREATH_SECONDS) ABSORB_COST else ABSORB_AFTER_BREATH_COST
        val inf = Double.MAX_VALUE / 4
        // cost[i][j]: the first i swaras meant and the first j notes heard; the
        // second layer is "swara i-1 has taken at least one note".
        val cost = Array(2) { Array(m + 1) { DoubleArray(n + 1) { inf } } }
        val step = Array(2) { Array(m + 1) { IntArray(n + 1) { -1 } } }
        cost[0][0][0] = 0.0
        for (i in 0..m) for (j in 0..n) for (taken in 0..1) {
            val here = cost[taken][i][j]
            if (here >= inf) continue
            fun relax(t: Int, ni: Int, nj: Int, c: Double, kind: Int) {
                if (here + c < cost[t][ni][nj]) {
                    cost[t][ni][nj] = here + c
                    step[t][ni][nj] = kind * 2 + taken
                }
            }
            if (i < m) relax(0, i + 1, j, MISS_COST, MISS)
            if (j < n) relax(taken, i, j + 1, extraCost(j), EXTRA)
            if (i < m && j < n && near(i, j)) relax(1, i + 1, j + 1, MATCH_WEIGHT * abs(pitches[j] - targets[i]) / MATCH_CENTS, MATCH)
            // More of the previous swara only if it's no nearer the next one (a Ri sung straight after Sa is Ri).
            val nearerPrevious = i == m || i == 0 || j == n || abs(pitches[j] - targets[i - 1]) <= abs(pitches[j] - targets[i])
            if (taken == 1 && i > 0 && j < n && near(i - 1, j) && nearerPrevious) relax(1, i, j + 1, absorbCost(j), ABSORB)
        }
        val owner = IntArray(n) { -1 }
        var i = m
        var j = n
        var taken = if (cost[1][m][n] < cost[0][m][n]) 1 else 0
        while (i > 0 || j > 0) {
            val s = step[taken][i][j]
            val kind = s / 2
            val previous = s % 2
            when (kind) {
                MISS -> i--
                EXTRA -> j--
                MATCH -> { owner[j - 1] = i - 1; i--; j-- }
                else -> { owner[j - 1] = i - 1; j-- }
            }
            taken = previous
        }
        val items = expected.mapIndexed { k, note -> Item(note.swara, note.octave.offset, heard.filterIndexed { h, _ -> owner[h] == k }) }
        val extras = heard.filterIndexed { h, note -> owner[h] < 0 && note.durationSeconds >= MOVEMENT_SECONDS }
        return Groups(items, extras)
    }

    private const val MISS = 0
    private const val EXTRA = 1
    private const val MATCH = 2
    private const val ABSORB = 3

    // ── The fitted Sa ────────────────────────────────────────────────

    /**
     * The offset (cents, within the fine-tune range) that minimises the
     * duration-weighted median distance of the notes from their targets: the
     * Sa the singing fits best. 0 with too few notes.
     */
    private fun fittedOffset(items: List<Pair<Double, Double>>): Double {
        if (items.size < MIN_FIT_NOTES) return 0.0
        fun error(offset: Int): Double {
            val sorted = items.map { (c, w) -> abs(c - offset) to w }.sortedBy { it.first }
            val half = sorted.sumOf { it.second } / 2
            var acc = 0.0
            for ((d, w) in sorted) {
                acc += w
                if (acc >= half) return d
            }
            return sorted.last().first
        }
        return (-FIT_RANGE..FIT_RANGE).minWith(compareBy<Int> { error(it) }.thenBy { abs(it) }).toDouble()
    }

    // ── Words ────────────────────────────────────────────────────────

    private fun closeness(cents: Double, strictness: Strictness): Closeness = when {
        abs(cents) <= strictness.inTuneCents -> Closeness.IN_TUNE
        abs(cents) <= strictness.closeCents -> if (cents < 0) Closeness.A_LITTLE_FLAT else Closeness.A_LITTLE_SHARP
        else -> if (cents < 0) Closeness.FLAT else Closeness.SHARP
    }

    /** "Sa", "Ri", … for the words (with "high" / "low" outside the middle octave). */
    fun spokenName(swara: Swara, octave: Int): String {
        val name = SPOKEN.getValue(swara.symbol.first())
        return when {
            octave > 0 -> "high $name"
            octave < 0 -> "low $name"
            else -> name
        }
    }

    private val SPOKEN = mapOf('S' to "Sa", 'R' to "Ri", 'G' to "Ga", 'M' to "Ma", 'P' to "Pa", 'D' to "Dha", 'N' to "Ni")

    /** "−20¢" with a real minus sign. */
    fun centsText(cents: Double): String {
        val r = cents.roundToInt()
        return if (r < 0) "−${-r}¢" else "+$r¢"
    }

    /** "Ma was a little flat (−20¢) and Dha a little sharp (+13¢); the rest were in tune." */
    private fun summary(swaras: List<SwaraInsight>, extras: List<HeardNote>, strictness: Strictness, meant: Boolean): String {
        if (swaras.none { it.closeness != Closeness.MISSED }) return "No steady swaras were heard."
        val off = swaras.filter { it.closeness != Closeness.IN_TUNE && it.closeness != Closeness.MISSED }
        val missed = swaras.filter { it.closeness == Closeness.MISSED }
        val parts = mutableListOf<String>()
        if (off.isEmpty()) {
            parts += if (missed.isEmpty()) "Every swara was in tune (within ${strictness.inTuneCents.roundToInt()}¢)." else "The swaras you sang were in tune."
        } else {
            // "Ma was a little flat (−20¢), Pa flat (−40¢) and Dha a little sharp (+13¢)"
            val described = off.mapIndexed { k, s ->
                val verb = if (k == 0) " was" else ""
                "${spokenName(s.swara, s.octave)}$verb ${words(s.closeness)} (${centsText(s.cents ?: 0.0)})"
            }
            val joined = if (described.size == 1) described[0] else described.dropLast(1).joinToString(", ") + " and " + described.last()
            val rest = if (off.size + missed.size < swaras.size) "; the rest were in tune" else ""
            parts += joined.replaceFirstChar { it.uppercase() } + rest + "."
        }
        if (missed.isNotEmpty()) parts += "Not heard: ${missed.joinToString(", ") { spokenName(it.swara, it.octave) }}."
        if (meant && extras.isNotEmpty()) parts += "Also heard: ${extras.joinToString(", ") { spokenName(it.swara, it.octave) }}."
        return parts.joinToString(" ")
    }

    private fun words(c: Closeness): String = when (c) {
        Closeness.A_LITTLE_FLAT -> "a little flat"
        Closeness.A_LITTLE_SHARP -> "a little sharp"
        Closeness.FLAT -> "flat"
        Closeness.SHARP -> "sharp"
        Closeness.IN_TUNE -> "in tune"
        Closeness.MISSED -> "not heard"
    }

    // ── Cents ────────────────────────────────────────────────────────

    /** A swara's target, in cents above Sa, in [tuning]. */
    private fun targetCents(swara: Swara, octave: Int, tuning: Tuning): Double = when (tuning) {
        Tuning.JUST -> CENTS_PER_OCTAVE * log2(swara.ratio)
        Tuning.EQUAL -> swara.semitone * 100.0
    } + CENTS_PER_OCTAVE * octave

    /** A heard note's median pitch in cents above Sa. */
    private fun heardCents(note: HeardNote, tuning: Tuning): Double = targetCents(note.swara, note.octave, tuning) + note.centsOff
}

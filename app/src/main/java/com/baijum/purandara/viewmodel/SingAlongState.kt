package com.baijum.purandara.viewmodel

import com.baijum.purandara.singalong.NoteMark
import com.baijum.purandara.singalong.NoteResult
import com.baijum.purandara.singalong.PassSummary
import com.baijum.purandara.singalong.SwaraReading
import com.baijum.purandara.singalong.Zone

/** Where a mark goes in the notation. */
data class MarkKey(val avarta: Int, val akshara: Int, val slot: Int)

internal fun List<NoteMark>.toMarkEntries(): List<Pair<MarkKey, NoteResult>> =
    map { MarkKey(it.event.position.avarta, it.event.position.akshara, it.event.position.slot) to it.result }

/** One point of the pitch lane: when in the pass, and how high relative to Sa. */
data class TraceSample(val pass: Int, val secondsInPass: Double, val centsFromSa: Double, val zone: Zone)

/** What the player shows for sing-along. */
data class SingAlongState(
    /** Microphone permission granted. */
    val micPermitted: Boolean = false,
    /** Audio is going to the built-in speaker (suggest headphones). */
    val onSpeaker: Boolean = true,
    /** Current reading while singing, null when silent. */
    val live: SwaraReading? = null,
    /** Which whole-performance loop the live readings and trace are from (0-based); null during the count-in. */
    val pass: Int? = null,
    /** Recent pitch lane points (about the last avarta). */
    val trace: List<TraceSample> = emptyList(),
    /** Latest mark for each note position. */
    val marks: Map<MarkKey, NoteResult> = emptyMap(),
    /** Summary of the last completed pass, with its 1-based number. */
    val lastSummary: Pair<Int, PassSummary>? = null,
)

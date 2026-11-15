package com.baijum.purandara.ui.recordings

import android.content.Context
import android.content.res.Resources
import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baijum.purandara.R
import com.baijum.purandara.domain.Note
import com.baijum.purandara.domain.Pitch
import com.baijum.purandara.domain.CustomRagas
import com.baijum.purandara.domain.Ragas
import com.baijum.purandara.recording.HeardNote
import com.baijum.purandara.recording.PitchTrack
import com.baijum.purandara.recording.Recording
import com.baijum.purandara.recording.RecordingLibrary
import com.baijum.purandara.recording.TanpuraProfile
import com.baijum.purandara.singalong.PitchLane
import com.baijum.purandara.ui.FineTuneControl
import com.baijum.purandara.ui.LocalReduceMotion
import com.baijum.purandara.ui.SaPicker
import com.baijum.purandara.ui.saLabel
import com.baijum.purandara.viewmodel.RecorderUi
import com.baijum.purandara.viewmodel.RecordingDetailUi
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** "15 Nov, 6:42 pm" in the device's locale: the date part of the automatic title. */
fun recordingDateLabel(context: Context, millis: Long): String = DateUtils.formatDateTime(
    context, millis,
    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR or DateUtils.FORMAT_SHOW_TIME,
)

@Composable
private fun titleOf(recording: Recording): String = recording.title(recordingDateLabel(LocalContext.current, recording.createdMillis))

private fun clock(seconds: Double): String = DateUtils.formatElapsedTime(seconds.toLong())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingsScaffold(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding -> content(Modifier.padding(padding).fillMaxSize()) }
}

// --- The list ---

@Composable
fun RecordingsListScreen(library: RecordingLibrary, actions: RecordingsActions) {
    RecordingsScaffold(stringResource(R.string.recordings_title), actions.onBack) { modifier ->
        LazyColumn(modifier) {
            item(key = "new") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = actions.onNew, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(painterResource(R.drawable.ic_mic), contentDescription = null)
                        Text(stringResource(R.string.recordings_new), modifier = Modifier.padding(start = 8.dp))
                    }
                    if (library.isEmpty) Text(stringResource(R.string.recordings_empty), style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider()
            }
            items(library.recordings, key = { it.id }) { recording ->
                RecordingRow(recording) { actions.onOpen(recording.id) }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun RecordingRow(recording: Recording, onClick: () -> Unit) {
    val title = titleOf(recording)
    val detail = listOf(
        clock(recording.durationSeconds),
        DateUtils.getRelativeTimeSpanString(recording.createdMillis, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString(),
    ).joinToString(" · ")
    val res = LocalResources.current
    val spokenLength = spokenDuration(res, recording.durationSeconds)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clearAndSetSemantics {
                contentDescription = res.getString(R.string.recording_row_desc, title, spokenLength)
                role = Role.Button
            },
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "2 minutes, 14 seconds" for screen readers (the screen shows 2:14). */
private fun spokenDuration(res: Resources, seconds: Double): String {
    val total = seconds.roundToInt()
    val minutes = total / SECONDS_PER_MINUTE
    val secs = total % SECONDS_PER_MINUTE
    val m = res.getQuantityString(R.plurals.minutes, minutes, minutes)
    val s = res.getQuantityString(R.plurals.seconds, secs, secs)
    return if (minutes == 0) s else "$m, $s"
}

// --- Recording ---

@Composable
fun RecordScreen(
    recorder: RecorderUi,
    micAllowed: Boolean,
    actions: RecordingsActions,
    recentRagas: List<String> = emptyList(),
    own: CustomRagas = CustomRagas(),
) {
    RecordingsScaffold(stringResource(R.string.record_title), actions.onBack) { modifier ->
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.record_sa, saLabel(recorder.saMidi, recorder.saCents)),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            val analysing = recorder.analysing
            when {
                analysing != null -> {
                    Text(stringResource(R.string.record_analysing), style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { analysing }, modifier = Modifier.fillMaxWidth())
                }
                !micAllowed -> {
                    Text(stringResource(R.string.mic_needed_record), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = actions.onAllowMic) { Text(stringResource(R.string.allow_mic)) }
                }
                else -> {
                    if (!recorder.recording) {
                        IntentPicker(recorder.ragaKey, recorder.scale, recentRagas, actions.onIntent, own, actions.onEditRaga)
                        TanpuraSwitch(recorder.tanpura, actions.onTanpura, help = true)
                    }
                    val raga = recorder.ragaKey?.let { Ragas.byKey(it) }
                    if (raga != null && recorder.scale != null) ReferenceLine(raga, recorder.scale)
                    RecordControls(recorder, actions)
                }
            }
            if (recorder.failed) {
                Text(
                    stringResource(R.string.record_failed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Text(
                stringResource(R.string.record_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RecordControls(recorder: RecorderUi, actions: RecordingsActions) {
    val label = stringResource(if (recorder.recording) R.string.record_stop else R.string.record_start)
    FilledIconButton(
        onClick = if (recorder.recording) actions.onStop else actions.onStart,
        modifier = Modifier.size(RECORD_BUTTON).semantics { contentDescription = label },
    ) {
        Icon(
            painterResource(if (recorder.recording) R.drawable.ic_stop else R.drawable.ic_mic),
            contentDescription = null,
            modifier = Modifier.size(RECORD_ICON),
        )
    }
    Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics {})
    if (recorder.recording) {
        if (recorder.tanpura) TanpuraCue(recorder)
        // Not a live region: a screen reader reads it when focused, not every second.
        Text(clock(recorder.seconds), style = MaterialTheme.typography.displaySmall)
        LinearProgressIndicator(
            progress = { (recorder.level * LEVEL_GAIN).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}, // decorative level meter
        )
        if (recorder.seconds >= RecordingLibrary.WARN_SECONDS) {
            Text(
                stringResource(R.string.record_limit_near),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/**
 * "Listening to your tanpura…" with a countdown for the first seconds, then
 * "Now sing" (announced, with a haptic tap). The live region carries only
 * the two messages, not the countdown, so a screen reader says each once.
 */
@Composable
private fun TanpuraCue(recorder: RecorderUi) {
    val listening = recorder.listening
    val haptics = LocalHapticFeedback.current
    var wasListening by remember { mutableStateOf(listening) }
    LaunchedEffect(listening) {
        if (wasListening && !listening) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        wasListening = listening
    }
    val message = stringResource(if (listening) R.string.record_listening else R.string.record_now_sing)
    Text(
        message,
        style = MaterialTheme.typography.titleLarge,
        color = if (listening) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
    )
    if (listening) {
        val left = ceil(TanpuraProfile.LISTEN_SECONDS - recorder.seconds).toInt().coerceAtLeast(1)
        Text(left.toString(), style = MaterialTheme.typography.displayMedium, modifier = Modifier.clearAndSetSemantics {})
    }
}

/** "External tanpura playing": a switch with its label (and, on the Record screen, its help). */
@Composable
private fun TanpuraSwitch(on: Boolean, onChange: (Boolean) -> Unit, help: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.record_tanpura), style = MaterialTheme.typography.bodyLarge)
            if (help) {
                Text(
                    stringResource(R.string.record_tanpura_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = on, onCheckedChange = null)
    }
}

// --- One recording ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordingDetailScreen(
    detail: RecordingDetailUi,
    actions: RecordingsActions,
    recentRagas: List<String> = emptyList(),
    own: CustomRagas = CustomRagas(),
) {
    val recording = detail.recording
    val title = titleOf(recording)
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    RecordingsScaffold(stringResource(R.string.recordings_title), actions.onBack) { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { renaming = true }) { Text(stringResource(R.string.recording_rename)) }
                OutlinedButton(onClick = { deleting = true }) { Text(stringResource(R.string.recording_delete)) }
                actions.onReanalyse?.let { reanalyse ->
                    OutlinedButton(onClick = { reanalyse(recording.id) }, enabled = detail.updating == null) {
                        Text(stringResource(R.string.debug_reanalyse))
                    }
                }
            }
            detail.updating?.let { progress ->
                Text(stringResource(R.string.recording_updating), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            PlaybackControls(detail, actions)
            SwaraSection(detail, actions)
            detail.insights?.let { HowItWent(it) { midi, cents -> actions.onChangeSa(recording.id, midi, cents) } }
            Text(
                stringResource(R.string.recording_trace),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            RecordingTrace(detail, actions.onSeek)
            SaPicker(recording.saMidi, recording.saCents) { midi -> actions.onChangeSa(recording.id, midi, recording.saCents) }
            FineTuneControl(recording.saCents) { cents -> actions.onChangeSa(recording.id, recording.saMidi, cents) }
            IntentPicker(recording.ragaKey, recording.scale, recentRagas, { key, scale -> actions.onChangeIntent(recording.id, key, scale) }, own, actions.onEditRaga)
            TanpuraSwitch(recording.externalTanpura, { actions.onChangeTanpura(recording.id, it) }, help = false)
            detail.tanpuraSa?.let { (midi, cents) -> TanpuraOffer(midi, cents) { actions.onChangeSa(recording.id, midi, cents) } }
        }
    }
    if (renaming) {
        RenameDialog(recording.customTitle.orEmpty(), onDismiss = { renaming = false }) {
            actions.onRename(recording.id, it)
            renaming = false
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.recording_delete_title)) },
            text = { Text(stringResource(R.string.recording_delete_body, title)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    actions.onDelete(recording.id)
                }) { Text(stringResource(R.string.recording_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PlaybackControls(detail: RecordingDetailUi, actions: RecordingsActions) {
    val duration = detail.recording.durationSeconds
    val position = detail.positionSeconds.coerceIn(0.0, duration)
    val positionLabel = stringResource(R.string.recording_position)
    val positionValue = stringResource(R.string.recording_position_value, clock(position), clock(duration))
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(onClick = actions.onTogglePlay, modifier = Modifier.size(PLAY_BUTTON)) {
            Icon(
                painterResource(if (detail.playing) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = stringResource(if (detail.playing) R.string.recording_pause else R.string.recording_play),
            )
        }
        Slider(
            value = position.toFloat(),
            onValueChange = { actions.onSeek(it.toDouble()) },
            valueRange = 0f..duration.toFloat().coerceAtLeast(MIN_SLIDER_SECONDS),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp).semantics {
                contentDescription = positionLabel
                stateDescription = positionValue
            },
        )
        Text(clock(position), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clearAndSetSemantics {})
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwaraSection(detail: RecordingDetailUi, actions: RecordingsActions) {
    val res = LocalResources.current
    Text(stringResource(R.string.recording_swaras), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Text(
        stringResource(R.string.recording_approximate),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val phrases = detail.transcription.phrases
    when {
        detail.track == null -> Text(stringResource(R.string.recording_unreadable), color = MaterialTheme.colorScheme.error)
        phrases.isEmpty() -> Text(stringResource(R.string.recording_no_swaras))
    }
    val current = detail.transcription.noteIndexAt(detail.positionSeconds).takeIf { detail.playing || detail.positionSeconds > 0 }
    var flatIndex = 0
    phrases.forEachIndexed { p, phrase ->
        val first = flatIndex
        flatIndex += phrase.size
        val spoken = res.getString(R.string.recording_phrase_desc, p + 1, phrase.joinToString(", ") { spokenNote(res, it) })
        val jump = stringResource(R.string.recording_jump_hint)
        // One element per phrase for screen readers; activating it plays from the phrase's start.
        FlowRow(
            Modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = spoken
                onClick(label = jump) {
                    actions.onSeek(phrase.first().startSeconds)
                    true
                }
            },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            phrase.forEachIndexed { i, note -> NoteChip(note, first + i == current) { actions.onSeek(note.startSeconds) } }
        }
    }
}

@Composable
private fun NoteChip(note: HeardNote, highlighted: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val text = note.label + " ,".repeat(note.holds)
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = when {
            highlighted -> colors.onPrimary
            !note.inRaga -> colors.error
            else -> colors.onSurface
        },
        modifier = Modifier
            .widthIn(min = NOTE_MIN_WIDTH)
            .background(if (highlighted) colors.primary else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/** "R1", "low P", "high S held", "R2, not in the raga". */
private fun spokenNote(res: Resources, note: HeardNote): String {
    var s = note.swara.symbol
    if (note.octave < 0) s = res.getString(R.string.swara_low, s)
    if (note.octave > 0) s = res.getString(R.string.swara_high, s)
    if (note.holds > 0) s = res.getString(R.string.swara_held, s)
    if (!note.inRaga) s = res.getString(R.string.swara_not_in_raga, s)
    return s
}

/**
 * The pitch on the swara lines, a window of [TRACE_WINDOW_SECONDS] that
 * follows playback (paging, with reduced motion); tap or drag to move.
 */
@Composable
private fun RecordingTrace(detail: RecordingDetailUi, onSeek: (Double) -> Unit) {
    val track = detail.track ?: return
    val colors = TraceColors(
        sa = MaterialTheme.colorScheme.primary,
        line = MaterialTheme.colorScheme.outlineVariant,
        label = MaterialTheme.colorScheme.onSurfaceVariant,
        pitch = MaterialTheme.colorScheme.secondary,
        note = MaterialTheme.colorScheme.primary,
        cursor = MaterialTheme.colorScheme.tertiary,
    )
    val measurer = rememberTextMeasurer()
    val summary = traceSummary(detail)
    val label = stringResource(R.string.recording_trace)
    val duration = detail.recording.durationSeconds
    val position = detail.positionSeconds
    val windowStart = traceWindowStart(position, duration, LocalReduceMotion.current)
    val currentPosition by rememberUpdatedState(position)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(TRACE_HEIGHT)
            .clipToBounds()
            .pointerInput(windowStart, duration) {
                detectTapGestures { offset -> onSeek(windowStart + offset.x / size.width * TRACE_WINDOW_SECONDS) }
            }
            .pointerInput(duration) {
                // Dragging moves the pitch under the cursor, like scrolling it.
                detectHorizontalDragGestures { change, amount ->
                    change.consume()
                    onSeek(currentPosition - amount / size.width * TRACE_WINDOW_SECONDS)
                }
            }
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = summary
            },
    ) {
        val view = TraceView(windowStart, TRACE_LABEL_WIDTH.dp.toPx(), size.width, size.height, detail.lane)
        drawLines(view, colors) { note, at ->
            drawText(measurer, PitchLane.label(note), at, style = TextStyle(color = colors.label, fontSize = TRACE_LABEL_SP.sp))
        }
        if (detail.recording.externalTanpura) {
            // The listening seconds (the tanpura alone), which the swaras ignore.
            val end = view.x(TanpuraProfile.LISTEN_SECONDS)
            if (end > view.left) drawRect(colors.line.copy(alpha = LISTEN_SHADE), Offset(view.left, 0f), Size(end - view.left, size.height))
        }
        drawPitch(view, track, detail.recording.saHz, colors.pitch)
        drawNotes(view, detail.transcription.notes, colors.note)
        val cx = view.x(position)
        if (cx >= view.left) drawLine(colors.cursor, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = CURSOR_STROKE)
    }
}

/** "Your tanpura is at C♯3 +12¢" with a button to use it as the recording's Sa. */
@Composable
private fun TanpuraOffer(midi: Int, cents: Int, onUse: () -> Unit) {
    val sa = saLabel(midi, cents)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.recording_tanpura_at, sa), style = MaterialTheme.typography.bodyLarge)
        val description = stringResource(R.string.recording_tanpura_use_description, sa)
        OutlinedButton(onClick = onUse, modifier = Modifier.semantics { contentDescription = description }) {
            Text(stringResource(R.string.recording_tanpura_use))
        }
    }
}

private data class TraceColors(val sa: Color, val line: Color, val label: Color, val pitch: Color, val note: Color, val cursor: Color)

/** Maps seconds and cents to the canvas, for the window starting at [start]. */
private class TraceView(val start: Double, val left: Float, val width: Float, val height: Float, val lane: PitchLane) {
    val end: Double get() = start + TRACE_WINDOW_SECONDS

    fun x(seconds: Double): Float = left + ((seconds - start) / TRACE_WINDOW_SECONDS * (width - left)).toFloat()

    fun y(cents: Double): Float = (height * (1 - lane.fraction(cents))).toFloat()
}

private fun traceWindowStart(position: Double, duration: Double, reduceMotion: Boolean): Double = when {
    duration <= TRACE_WINDOW_SECONDS -> 0.0
    reduceMotion -> floor(position / TRACE_WINDOW_SECONDS) * TRACE_WINDOW_SECONDS
    else -> position - TRACE_WINDOW_SECONDS / 3
}.coerceIn(0.0, maxOf(0.0, duration - TRACE_WINDOW_SECONDS))

@Composable
private fun traceSummary(detail: RecordingDetailUi): String {
    val t = detail.transcription
    val res = LocalResources.current
    val lowest = t.lowest ?: return stringResource(R.string.recording_trace_empty)
    val highest = t.highest ?: return stringResource(R.string.recording_trace_empty)
    return stringResource(
        R.string.recording_trace_summary,
        res.getQuantityString(R.plurals.notes, t.notes.size, t.notes.size),
        res.getQuantityString(R.plurals.phrases, t.phrases.size, t.phrases.size),
        spokenNote(res, lowest.copy(holds = 0, inRaga = true)), spokenNote(res, highest.copy(holds = 0, inRaga = true)),
    )
}

private fun DrawScope.drawLines(view: TraceView, colors: TraceColors, label: DrawScope.(Note, Offset) -> Unit) {
    view.lane.lines.forEach { note ->
        val y = view.y(PitchLane.centsOf(note))
        val isSa = note.swara.semitone == 0
        drawLine(if (isSa) colors.sa else colors.line, Offset(view.left, y), Offset(view.width, y), strokeWidth = if (isSa) 2f else 1f)
        label(note, Offset(0f, y - TRACE_LABEL_RAISE))
    }
}

private fun DrawScope.drawPitch(view: TraceView, track: PitchTrack, saHz: Double, color: Color) {
    for (i in track.indexAt(view.start)..track.indexAt(view.end)) {
        val cents = track.frequencyAt(i)?.let { Pitch.cents(saHz, it) }
        if (cents != null && view.lane.fraction(cents) in 0.0..1.0) {
            drawCircle(color, radius = TRACE_DOT, center = Offset(view.x(track.timeAt(i)), view.y(cents)))
        }
    }
}

private fun DrawScope.drawNotes(view: TraceView, notes: List<HeardNote>, color: Color) {
    notes.filter { it.endSeconds >= view.start && it.startSeconds <= view.end }.forEach { note ->
        val y = view.y(PitchLane.centsOf(Note(note.swara)) + note.octave * CENTS_PER_OCTAVE)
        drawLine(color, Offset(maxOf(view.left, view.x(note.startSeconds)), y), Offset(view.x(note.endSeconds), y), strokeWidth = NOTE_STROKE)
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recording_rename_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(MAX_TITLE) },
                    label = { Text(stringResource(R.string.recording_title_field)) },
                    singleLine = true,
                )
                Text(stringResource(R.string.recording_rename_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private val RECORD_BUTTON = 96.dp
private val RECORD_ICON = 40.dp
private val PLAY_BUTTON = 56.dp
private val NOTE_MIN_WIDTH = 40.dp
private val TRACE_HEIGHT = 220.dp
private const val TRACE_WINDOW_SECONDS = 8.0
private const val TRACE_LABEL_WIDTH = 22
private const val TRACE_LABEL_SP = 11
private const val TRACE_LABEL_RAISE = 18f
private const val TRACE_DOT = 2.5f
private const val NOTE_STROKE = 5f
private const val CURSOR_STROKE = 2f
private const val CENTS_PER_OCTAVE = 1200.0
private const val LEVEL_GAIN = 4f
private const val MIN_SLIDER_SECONDS = 0.1f
private const val MAX_TITLE = 80
private const val SECONDS_PER_MINUTE = 60
private const val LISTEN_SHADE = 0.35f

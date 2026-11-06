package com.baijum.purandara.ui.library

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.baijum.purandara.R
import com.baijum.purandara.domain.varisai.Contour
import com.baijum.purandara.domain.varisai.Varisai
import com.baijum.purandara.domain.varisai.VarisaiSet
import com.baijum.purandara.practice.PracticeHistory
import com.baijum.purandara.practice.PracticeRecord
import com.baijum.purandara.ui.player.SaPicker

/**
 * The landing page: continue / next up, the student's Sa with a tanpura to
 * tune to, then every exercise by set with its shape and progress
 * (docs/plan-landing.md).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    catalog: List<Varisai>,
    state: LibraryState,
    actions: LibraryActions,
    nowMillis: Long = System.currentTimeMillis(),
) {
    val history = state.history
    val compact = LocalDensity.current.fontScale < LARGE_FONT_SCALE
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.library_title))
                        Text(
                            stringResource(R.string.library_raga),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loadError -> Text(
                    stringResource(R.string.load_error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
                !state.samplesLoaded -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "continue") { ContinueCard(catalog, history, nowMillis, compact, actions.onSelect) }
                item(key = "sa") { SaCard(state.saMidi, state.tanpuraPlaying, state.samplesLoaded, actions) }
                catalog.groupBy { it.set }.forEach { (set, exercises) ->
                    stickyHeader(key = set.name) {
                        SetHeader(set, history.practisedCount(set, catalog), exercises.size)
                    }
                    item(key = set.name + "-about") {
                        Text(
                            set.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        )
                    }
                    items(exercises, key = { it.id }) { varisai ->
                        ExerciseRow(varisai, history[varisai.id], compact) { actions.onSelect(varisai) }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(
    catalog: List<Varisai>,
    history: PracticeHistory,
    nowMillis: Long,
    compact: Boolean,
    onSelect: (Varisai) -> Unit,
) {
    val last = history.continueWith(catalog)
    val next = if (last == null) catalog.firstOrNull() else history.nextUp(catalog)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Text(
            stringResource(if (last == null) R.string.start_heading else R.string.continue_heading),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp).semantics { heading() },
        )
        if (last != null) LastPractisedRow(last, history[last.id], nowMillis, compact) { onSelect(last) }
        if (next != null) {
            NextRow(if (last == null) next.title else stringResource(R.string.next_up, next.title)) { onSelect(next) }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun LastPractisedRow(last: Varisai, record: PracticeRecord?, nowMillis: Long, compact: Boolean, onClick: () -> Unit) {
    val detail = listOfNotNull(
        record?.let { relativeDay(it.lastPracticedMillis, nowMillis) },
        record?.takeIf { it.hasBest }?.let { stringResource(R.string.best_score, it.bestInTuneOrClose, it.bestJudged) },
    ).joinToString(" · ")
    val action = stringResource(R.string.continue_action, last.title)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clearAndSetSemantics {
                contentDescription = listOf(action, detail).filter { it.isNotEmpty() }.joinToString(", ")
                role = Role.Button
            },
    ) {
        Column(Modifier.weight(1f)) {
            Text(last.title, style = MaterialTheme.typography.titleLarge)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
        if (compact) ContourLine(last, Modifier.width(CONTOUR_WIDTH).height(CONTOUR_HEIGHT))
        Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun NextRow(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
            },
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
    }
}

@Composable
private fun SaCard(saMidi: Int, tanpuraPlaying: Boolean, samplesLoaded: Boolean, actions: LibraryActions) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.your_sa),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            SaPicker(saMidi, actions.onSaChange)
            FilledTonalButton(onClick = actions.onToggleTanpura, enabled = samplesLoaded) {
                Icon(painterResource(if (tanpuraPlaying) R.drawable.ic_stop else R.drawable.ic_play), contentDescription = null)
                Text(
                    stringResource(if (tanpuraPlaying) R.string.tanpura_stop else R.string.tanpura_play),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                stringResource(R.string.tanpura_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetHeader(set: VarisaiSet, practised: Int, total: Int) {
    val count = stringResource(R.string.practised_count, practised, total)
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { heading() },
        ) {
            Text(
                set.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (practised > 0) {
                Text(count, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExerciseRow(varisai: Varisai, record: PracticeRecord?, compact: Boolean, onClick: () -> Unit) {
    val practised = record != null && record.timesPracticed > 0
    val progress = listOfNotNull(
        if (practised) stringResource(R.string.practised) else null,
        record?.takeIf { it.hasBest }?.let { stringResource(R.string.best_score, it.bestInTuneOrClose, it.bestJudged) },
    ).joinToString(" · ")
    // Screen readers hear the notation the drawing stands for, and the progress.
    val description = listOf(varisai.title, varisai.avartas.first().toString(), progress)
        .filter { it.isNotEmpty() }.joinToString(", ")
    ListItem(
        headlineContent = { Text(varisai.title) },
        supportingContent = if (progress.isEmpty()) null else ({ Text(progress) }),
        leadingContent = {
            if (practised) {
                Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else {
                Spacer(Modifier.size(24.dp))
            }
        },
        trailingContent = if (compact) ({ ContourLine(varisai, Modifier.width(CONTOUR_WIDTH).height(CONTOUR_HEIGHT)) }) else null,
        modifier = Modifier
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
    )
}

/** The exercise's melodic shape as a stepped line (decorative). */
@Composable
private fun ContourLine(varisai: Varisai, modifier: Modifier) {
    val steps = Contour.of(varisai)
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (steps.isEmpty()) return@Canvas
        val low = steps.minOf { it.degree }
        val high = maxOf(steps.maxOf { it.degree }, low + 1)
        val stroke = 2.dp.toPx()
        fun y(degree: Int) = stroke + (size.height - 2 * stroke) * (1f - (degree - low).toFloat() / (high - low))
        val path = Path()
        steps.forEachIndexed { i, step ->
            val x0 = (step.start * size.width).toFloat()
            val x1 = (step.end * size.width).toFloat()
            val yy = y(step.degree)
            if (i == 0) path.moveTo(x0, yy) else path.lineTo(x0, yy)
            path.lineTo(x1, yy)
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = stroke * 1.2f, center = Offset(0f, y(steps.first().degree)))
    }
}

@Composable
private fun relativeDay(millis: Long, nowMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, nowMillis, DateUtils.DAY_IN_MILLIS).toString()

private val CONTOUR_WIDTH = 120.dp
private val CONTOUR_HEIGHT = 32.dp

/** From this font scale the drawings give their space to text. */
private const val LARGE_FONT_SCALE = 1.5f

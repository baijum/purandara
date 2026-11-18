package com.baijum.purandara.ui.library

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.res.pluralStringResource
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
import com.baijum.purandara.navigation.NavDestination
import com.baijum.purandara.navigation.NavGroup
import com.baijum.purandara.ui.RagaPicker
import com.baijum.purandara.ui.SaPicker
import com.baijum.purandara.ui.navigation.NavigationButton

/**
 * Home: continue / next up, the student's Sa with a tanpura to tune to, then
 * the sets by group, each leading to its page (docs/plan-landing.md,
 * docs/plan-navigation.md).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    catalog: List<Varisai>,
    state: LibraryState,
    actions: LibraryActions,
    nowMillis: Long = System.currentTimeMillis(),
) {
    val compact = LocalDensity.current.fontScale < LARGE_FONT_SCALE
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { NavigationButton(onBack = null) },
                title = {
                    Column {
                        Text(stringResource(R.string.library_title))
                        Text(
                            stringResource(R.string.library_raga, state.raga.name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LoadStatus(state)
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "continue") { ContinueCard(catalog, state.history, nowMillis, compact, actions.onSelect) }
                item(key = "sa") { SaCard(state, actions) }
                val bySet = catalog.groupBy { it.set }
                NavGroup.entries.filter { it.foldable }.forEach { group ->
                    item(key = group.name) { GroupHeader(group.title) }
                    items(group.destinations.filterIsInstance<NavDestination.SetPage>(), key = { it.id }) { page ->
                        val exercises = bySet[page.set].orEmpty()
                        if (exercises.isNotEmpty()) {
                            SetRow(page.set, state.history.practisedCount(page.set, catalog), exercises.size) { actions.onOpenSet(page.set) }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/**
 * One set's page: what it trains, a run through it (numbered varisai sets),
 * then its exercises with their shape and progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetScreen(
    set: VarisaiSet,
    catalog: List<Varisai>,
    state: LibraryState,
    actions: LibraryActions,
) {
    val compact = LocalDensity.current.fontScale < LARGE_FONT_SCALE
    val exercises = catalog.filter { it.set == set }
    val practised = state.history.practisedCount(set, catalog)
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { NavigationButton(onBack = actions.onBack) },
                title = { Text(set.shortTitle, maxLines = 1, modifier = Modifier.semantics { heading() }) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LoadStatus(state)
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "about") {
                    SetAbout(set, state, practised, exercises.size)
                    HorizontalDivider()
                }
                if (set.practisesAsRun) {
                    item(key = "run") {
                        RunSetRow(
                            set = set,
                            total = exercises.size,
                            nextIndex = state.runPlans.planFor(set, catalog).nextIndex,
                            onClick = { actions.onOpenRun(set) },
                        )
                        HorizontalDivider()
                    }
                }
                items(exercises, key = { it.id }) { varisai ->
                    ExerciseRow(varisai, state.history[varisai.id], compact) { actions.onSelect(varisai) }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** A set page's header: its full title, what it trains, the raga it's sung in and progress. */
@Composable
private fun SetAbout(set: VarisaiSet, state: LibraryState, practised: Int, total: Int) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
        if (set.shortTitle != set.title) Text(set.title, style = MaterialTheme.typography.titleMedium)
        Text(
            set.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (set.followsChosenRaga) {
            Text(
                stringResource(R.string.set_raga, state.raga.name),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (practised > 0) {
            Text(
                stringResource(R.string.practised_count, practised, total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        val others = state.history.practisedInOtherRagas(set, state.raga)
        if (others.isNotEmpty()) {
            val each = others.map { stringResource(R.string.raga_progress, it.raga.name, it.practised, it.total) }
            Text(
                stringResource(R.string.other_ragas_progress, each.joinToString(" · ")),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun LoadStatus(state: LibraryState) {
    when {
        state.loadError -> Text(
            stringResource(R.string.load_error),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp),
        )
        !state.samplesLoaded -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

/** A set on Home: its title and progress, leading to its page. */
@Composable
private fun SetRow(set: VarisaiSet, practised: Int, total: Int, onClick: () -> Unit) {
    val detail = if (practised > 0) {
        stringResource(R.string.practised_count, practised, total)
    } else {
        pluralStringResource(R.plurals.home_set_count, total, total)
    }
    ListItem(
        headlineContent = { Text(set.title) },
        supportingContent = { Text(detail) },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
        modifier = Modifier
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "${set.title}, $detail"
                role = Role.Button
            },
    )
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
private fun SaCard(state: LibraryState, actions: LibraryActions) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.your_sa),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            SaPicker(state.saMidi, state.saCents, actions.onSaChange)
            RagaPicker(state.raga, state.previewRagaKey, actions.onRagaChange, actions.onPreviewRaga, actions.onStopPreview)
            Text(
                stringResource(R.string.varisai_raga_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = actions.onToggleTanpura, enabled = state.samplesLoaded) {
                Icon(painterResource(if (state.tanpuraPlaying) R.drawable.ic_stop else R.drawable.ic_play), contentDescription = null)
                Text(
                    stringResource(if (state.tanpuraPlaying) R.string.tanpura_stop else R.string.tanpura_play),
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

/** "Practise Sarali Varisai 1–16": one continuous run through a set, with its saved continue point. */
@Composable
private fun RunSetRow(set: VarisaiSet, total: Int, nextIndex: Int, onClick: () -> Unit) {
    val resuming = nextIndex > 0
    val title = if (resuming) {
        stringResource(R.string.run_continue, set.title)
    } else {
        stringResource(R.string.run_practise_set, set.title)
    }
    val detail = if (resuming) {
        stringResource(R.string.run_continue_from, nextIndex + 1, total)
    } else {
        stringResource(R.string.run_practise_set_detail, total)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clearAndSetSemantics {
                contentDescription = "$title, $detail"
                role = Role.Button
            },
    ) {
        Icon(painterResource(R.drawable.ic_play), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
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

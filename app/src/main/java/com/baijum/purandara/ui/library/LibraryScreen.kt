package com.baijum.purandara.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.baijum.purandara.R
import com.baijum.purandara.domain.varisai.Varisai

/** All exercises, grouped by set in teaching order. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    catalog: List<Varisai>,
    samplesLoaded: Boolean,
    loadError: Boolean,
    onSelect: (Varisai) -> Unit,
) {
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
                loadError -> Text(
                    stringResource(R.string.load_error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
                !samplesLoaded -> LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                catalog.groupBy { it.set }.forEach { (set, exercises) ->
                    stickyHeader(key = set.name) { SetHeader(set.title) }
                    items(exercises, key = { it.id }) { varisai ->
                        ListItem(
                            headlineContent = { Text(varisai.title) },
                            supportingContent = {
                                Text(
                                    varisai.avartas.first().toString(),
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                )
                            },
                            modifier = Modifier.clickable { onSelect(varisai) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SetHeader(title: String) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics { heading() },
        )
    }
}

package com.baijum.purandara.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.baijum.purandara.R
import com.baijum.purandara.ui.navigation.NavigationButton

/** The app's website: the guide and the Learn pages. Opened only when the student taps it. */
const val WEBSITE_URL = "https://baijum.github.io/purandara/"

/** About: version, what the app is, its licence, and a link to the website (docs/plan-navigation.md). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(versionName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { NavigationButton(onBack) },
                title = { Text(stringResource(R.string.nav_about), modifier = Modifier.semantics { heading() }) },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.about_version, versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.about_licence_heading),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.about_licence), style = MaterialTheme.typography.bodyMedium)
            val hint = stringResource(R.string.about_website_hint)
            val open = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE_URL)))
                } catch (_: ActivityNotFoundException) {
                    // No browser: nothing to open.
                }
            }
            OutlinedButton(
                onClick = open,
                modifier = Modifier.semantics { onClick(label = hint) { open(); true } },
            ) { Text(stringResource(R.string.about_website)) }
        }
    }
}

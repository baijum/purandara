package com.baijum.purandara.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.baijum.purandara.R
import com.baijum.purandara.navigation.NavDestination
import com.baijum.purandara.navigation.NavGroup
import com.baijum.purandara.ui.LocalReduceMotion
import kotlinx.coroutines.launch

/**
 * How a top-level destination's top bar leads to the menu
 * (docs/plan-navigation.md). Null (the default) means the screen was pushed
 * above a destination, or is shown on its own (tests): it shows Back.
 */
class TopLevelNav(
    /** Opens the modal drawer; null when the drawer is permanently beside the content. */
    val openMenu: (() -> Unit)?,
)

val LocalTopLevelNav = staticCompositionLocalOf<TopLevelNav?> { null }

/**
 * A top bar's navigation icon: the menu button on a top-level destination
 * with the modal drawer, nothing beside a permanent drawer, Back otherwise
 * (none if [onBack] is null: Home on its own).
 */
@Composable
fun NavigationButton(onBack: (() -> Unit)?) {
    val nav = LocalTopLevelNav.current
    when {
        nav == null -> if (onBack != null) IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
        }
        nav.openMenu != null -> IconButton(onClick = nav.openMenu) {
            Icon(painterResource(R.drawable.ic_menu), contentDescription = stringResource(R.string.nav_open_menu))
        }
    }
}

/** From this width the drawer stays open beside the content (Material's expanded width). */
private const val PERMANENT_DRAWER_MIN_WIDTH_DP = 840

/**
 * A top-level destination with the menu: a modal drawer on phones, a
 * permanent one on wide screens. The menu button reaches the content's top
 * bar through [LocalTopLevelNav].
 */
@Composable
fun MenuScaffold(
    current: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    // Session only: groups start open (docs/plan-navigation.md).
    val expanded = remember { mutableStateMapOf<NavGroup, Boolean>() }
    if (LocalConfiguration.current.screenWidthDp >= PERMANENT_DRAWER_MIN_WIDTH_DP) {
        Row {
            PermanentDrawerSheet(Modifier.width(DRAWER_WIDTH)) {
                DrawerContent(current, expanded, onNavigate)
            }
            VerticalDivider()
            CompositionLocalProvider(LocalTopLevelNav provides TopLevelNav(openMenu = null)) { content() }
        }
        return
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(Modifier.width(DRAWER_WIDTH)) {
                DrawerContent(current, expanded) { destination ->
                    scope.launch { drawerState.close() }
                    onNavigate(destination)
                }
            }
        },
    ) {
        val nav = remember(drawerState) { TopLevelNav(openMenu = { scope.launch { drawerState.open() } }) }
        CompositionLocalProvider(LocalTopLevelNav provides nav) { content() }
        // Back closes an open drawer first. The last handler registered wins, so
        // this one comes after the content's and is renewed with each destination.
        key(current) {
            BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
        }
    }
}

private val DRAWER_WIDTH = 300.dp

@Composable
private fun DrawerContent(
    current: NavDestination,
    expanded: MutableMap<NavGroup, Boolean>,
    onNavigate: (NavDestination) -> Unit,
) {
    val description = stringResource(R.string.nav_menu)
    Column(
        Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .semantics { contentDescription = description },
    ) {
        Header()
        DrawerItem(NavDestination.Home, current, onNavigate)
        NavGroup.entries.forEach { group ->
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            val open = !group.foldable || expanded[group] ?: true
            GroupHeading(group, open) { expanded[group] = !open }
            val reduceMotion = LocalReduceMotion.current
            AnimatedVisibility(
                visible = open,
                enter = if (reduceMotion) EnterTransition.None else expandVertically(),
                exit = if (reduceMotion) ExitTransition.None else shrinkVertically(),
            ) {
                Column { group.destinations.forEach { DrawerItem(it, current, onNavigate) } }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        DrawerItem(NavDestination.About, current, onNavigate)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Header() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        // The app icon: the gold mark on its maroon ground (the mark alone would vanish on cream).
        Box(Modifier.size(48.dp).clip(CircleShape)) {
            Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.matchParentSize())
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.matchParentSize())
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.nav_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GroupHeading(group: NavGroup, open: Boolean, onToggle: () -> Unit) {
    val title = group.title
    if (!group.foldable) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp).semantics { heading() },
        )
        return
    }
    val state = stringResource(if (open) R.string.nav_group_expanded else R.string.nav_group_collapsed)
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle)
            .semantics(mergeDescendants = true) {
                heading()
                role = Role.Button
                stateDescription = state
            }
            .padding(horizontal = 28.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(
            painterResource(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DrawerItem(destination: NavDestination, current: NavDestination, onNavigate: (NavDestination) -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(painterResource(iconOf(destination)), contentDescription = null) },
        label = { Text(labelOf(destination)) },
        selected = destination == current,
        onClick = { onNavigate(destination) },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

/** The menu label for [destination]: set pages by their set's title. */
@Composable
fun labelOf(destination: NavDestination): String = when (destination) {
    NavDestination.Home -> stringResource(R.string.nav_home)
    is NavDestination.SetPage -> destination.set.title
    NavDestination.ShrutiPractice -> stringResource(R.string.shruti_title)
    NavDestination.FindSa -> stringResource(R.string.find_my_sa)
    NavDestination.Recordings -> stringResource(R.string.recordings_title)
    NavDestination.About -> stringResource(R.string.nav_about)
}

private fun iconOf(destination: NavDestination): Int = when (destination) {
    NavDestination.Home -> R.drawable.ic_home
    is NavDestination.SetPage -> R.drawable.ic_steps
    NavDestination.ShrutiPractice -> R.drawable.ic_tuning_fork
    NavDestination.FindSa -> R.drawable.ic_search
    NavDestination.Recordings -> R.drawable.ic_mic
    NavDestination.About -> R.drawable.ic_info
}

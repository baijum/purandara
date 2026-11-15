package com.baijum.purandara.navigation

import com.baijum.purandara.domain.varisai.VarisaiSet

/**
 * The app's top-level destinations: what the Android drawer, the iOS sidebar
 * and the iOS tabs lead to (`docs/plan-navigation.md`). The player, a
 * practice run, the recorder and a recording's detail are not destinations:
 * they are pushed above one, with Back.
 *
 * [id] is stable, so it can be saved (Android `rememberSaveable`, iOS
 * selection) and given as a debug launch argument (`-open set-SARALI`).
 */
sealed class NavDestination(val id: String) {
    /** Continue / next up, Your Sa (tanpura, Sa picker) and links to the sets. */
    data object Home : NavDestination("home")

    /** One set's page: description, run row, exercises. */
    data class SetPage(val set: VarisaiSet) : NavDestination(SET_PREFIX + set.name)

    /** Shruti practice: hold Sa against the tanpura. */
    data object ShrutiPractice : NavDestination("shruti")

    /** Find my Sa. */
    data object FindSa : NavDestination("find-sa")

    /** The recordings list. */
    data object Recordings : NavDestination("recordings")

    /** Version, licence, the website. */
    data object About : NavDestination("about")

    /**
     * Where Back goes from this destination (Android): Home from anywhere
     * else, and nowhere from Home (the app leaves). Null means leave.
     */
    val back: NavDestination? get() = if (this == Home) null else Home

    companion object {
        private const val SET_PREFIX = "set-"

        /**
         * Every destination, in menu order: Home, the groups' items, About.
         * Lazy: built eagerly, the companion would read [Home] while the
         * subclasses are still initialising (null on the JVM).
         */
        val all: List<NavDestination> by lazy {
            listOf(Home) + NavGroup.entries.flatMap { it.destinations } + About
        }

        /** The destination with [id], or null if there's none. */
        fun fromId(id: String): NavDestination? = all.firstOrNull { it.id == id }
    }
}

/**
 * The menu's groups, in order, between Home and About. On Android and the
 * iPad sidebar each is a heading (the set groups fold open and shut); the
 * iPhone tabs show the set groups on Home and the Sa group as the Sa tab.
 */
enum class NavGroup(val title: String) {
    VARISAIS("Varisais"),
    SONGS("Alankaram & songs"),
    SA_AND_VOICE("Sa & voice"),
    ;

    /** The group's items, in menu order. */
    val destinations: List<NavDestination>
        get() = when (this) {
            VARISAIS, SONGS -> VarisaiSet.entries.filter { groupOf(it) == this }.map { NavDestination.SetPage(it) }
            SA_AND_VOICE -> listOf(NavDestination.ShrutiPractice, NavDestination.FindSa, NavDestination.Recordings)
        }

    /** Whether the heading folds its items open and shut (the set groups do). */
    val foldable: Boolean get() = this != SA_AND_VOICE

    companion object {
        /** The group a set is listed under. */
        fun groupOf(set: VarisaiSet): NavGroup = when (set) {
            VarisaiSet.PLAIN_SCALE, VarisaiSet.SARALI, VarisaiSet.MADHYA_STHAYI,
            VarisaiSet.JANTA, VarisaiSet.VAKRA_JANTA, VarisaiSet.DHATTU,
            -> VARISAIS
            VarisaiSet.ALANKARAM, VarisaiSet.GITAM, VarisaiSet.SWARAJATI, VarisaiSet.VARNAM -> SONGS
        }
    }
}

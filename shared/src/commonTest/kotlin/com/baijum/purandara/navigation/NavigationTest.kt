package com.baijum.purandara.navigation

import com.baijum.purandara.domain.varisai.VarisaiSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationTest {
    private val setPages = NavGroup.entries.flatMap { it.destinations }.filterIsInstance<NavDestination.SetPage>()

    @Test
    fun everySetIsListedExactlyOnce() {
        assertEquals(VarisaiSet.entries.toList(), setPages.map { it.set }.sorted())
        assertEquals(setPages.size, setPages.toSet().size)
    }

    @Test
    fun setsAppearInTeachingOrder() {
        assertEquals(VarisaiSet.entries.toList(), setPages.map { it.set })
    }

    @Test
    fun eachSetIsUnderTheGroupThatListsIt() {
        for (group in NavGroup.entries) {
            for (page in group.destinations.filterIsInstance<NavDestination.SetPage>()) {
                assertEquals(group, NavGroup.groupOf(page.set))
            }
        }
    }

    @Test
    fun theGroupsAreVarisaisThenSongsThenSaAndVoice() {
        assertEquals(VarisaiSet.PLAIN_SCALE, (NavGroup.VARISAIS.destinations.first() as NavDestination.SetPage).set)
        assertEquals(VarisaiSet.ALANKARAM, (NavGroup.SONGS.destinations.first() as NavDestination.SetPage).set)
        assertEquals(
            listOf(NavDestination.ShrutiPractice, NavDestination.FindSa, NavDestination.Recordings),
            NavGroup.SA_AND_VOICE.destinations,
        )
        assertTrue(NavGroup.VARISAIS.foldable)
        assertTrue(NavGroup.SONGS.foldable)
        assertFalse(NavGroup.SA_AND_VOICE.foldable)
    }

    @Test
    fun allStartsWithHomeAndEndsWithAbout() {
        assertEquals(NavDestination.Home, NavDestination.all.first())
        assertEquals(NavDestination.About, NavDestination.all.last())
        assertEquals(2 + VarisaiSet.entries.size + 3, NavDestination.all.size)
    }

    @Test
    fun idsAreUniqueAndRoundTrip() {
        val ids = NavDestination.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (destination in NavDestination.all) assertEquals(destination, NavDestination.fromId(destination.id))
    }

    @Test
    fun idsMatchTheLaunchArguments() {
        assertEquals("shruti", NavDestination.ShrutiPractice.id)
        assertEquals("find-sa", NavDestination.FindSa.id)
        assertEquals("recordings", NavDestination.Recordings.id)
        assertEquals("set-SARALI", NavDestination.SetPage(VarisaiSet.SARALI).id)
    }

    @Test
    fun unknownIdsGiveNothing() {
        assertNull(NavDestination.fromId(""))
        assertNull(NavDestination.fromId("set-NOPE"))
        assertNull(NavDestination.fromId("sarali-1")) // an exercise, not a destination
    }

    @Test
    fun backGoesHomeAndHomeLeaves() {
        assertNull(NavDestination.Home.back)
        for (destination in NavDestination.all - NavDestination.Home) {
            assertEquals(NavDestination.Home, destination.back)
        }
    }
}

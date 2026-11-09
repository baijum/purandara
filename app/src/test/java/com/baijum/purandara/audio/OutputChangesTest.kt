package com.baijum.purandara.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputChangesTest {
    @Test
    fun initialUnknownAndFirstActualRouteAreBaselines() {
        val routes = RoutedOutputChanges()
        assertFalse(routes.observe(null))
        assertFalse(routes.observe(null))
        assertFalse(routes.observe(10))
        assertFalse(routes.observe(10))
    }

    @Test
    fun changingDeviceIdentityStopsOnceEvenWithinTheSameDeviceType() {
        val routes = RoutedOutputChanges()
        assertFalse(routes.observe(10))
        assertTrue(routes.observe(11))
        assertFalse(routes.observe(11))
        assertFalse(routes.observe(10))
    }

    @Test
    fun losingAKnownRouteCountsAsAChangeAndANewSessionGetsItsOwnBaseline() {
        val old = RoutedOutputChanges()
        assertFalse(old.observe(10))
        assertTrue(old.observe(null))
        val replacement = RoutedOutputChanges()
        assertFalse(replacement.observe(11))
        assertFalse(replacement.observe(11))
    }

    @Test
    fun automaticReplacementRetainsThePriorOutputEvenBeforeItsFirstRouteCallback() {
        val original = RoutedOutputChanges()
        assertFalse(original.observe(10))
        // Settings replaced the old track before its queued change callback ran.
        val automatic = RoutedOutputChanges(original.baselineDeviceId)
        assertFalse(automatic.observe(null)) // new track hasn't been routed yet
        assertTrue(automatic.observe(11))
        assertFalse(automatic.observe(11))
        // Only an explicit user start accepts a fresh baseline.
        assertFalse(RoutedOutputChanges().observe(11))
    }

    @Test
    fun foregroundSnapshotCatchesChangesBeforeAQueuedAvailabilityCallback() {
        val inventory = AvailableOutputChanges(setOf(1))
        assertTrue(inventory.reconcile(setOf(1, 2)))
        assertFalse(inventory.added(setOf(2))) // delayed callback is now a duplicate
        assertFalse(inventory.reconcile(setOf(1, 2)))
        assertTrue(inventory.reconcile(setOf(1)))
        assertFalse(inventory.removed(setOf(2)))
    }

    @Test
    fun initialAndInputOnlyInventoryNotificationsDoNotChangeOutputs() {
        val inventory = AvailableOutputChanges(setOf(1, 2))
        assertFalse(inventory.added(setOf(1, 2)))
        assertFalse(inventory.added(emptySet())) // callback filtered to sinks before this decision
        assertFalse(inventory.removed(emptySet()))
        assertFalse(inventory.removed(setOf(99)))
    }

    @Test
    fun outputAdditionsAndRemovalsAreDeduplicated() {
        val inventory = AvailableOutputChanges(setOf(1))
        assertTrue(inventory.added(setOf(2)))
        assertFalse(inventory.added(setOf(2)))
        assertTrue(inventory.removed(setOf(2)))
        assertFalse(inventory.removed(setOf(2)))
    }
}

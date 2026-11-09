package com.baijum.purandara.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputChangesTest {
    private val start = 0L
    private val settled = RoutedOutputChanges.SETTLE_MILLIS

    @Test
    fun initialUnknownAndFirstActualRouteAreBaselines() {
        val routes = RoutedOutputChanges()
        assertFalse(routes.observe(null, start))
        assertFalse(routes.observe(null, start))
        assertFalse(routes.observe(10, start))
        assertFalse(routes.observe(10, settled))
    }

    @Test
    fun changingDeviceIdentityStopsOnceEvenWithinTheSameDeviceType() {
        val routes = RoutedOutputChanges()
        assertFalse(routes.observe(10, start))
        assertTrue(routes.observe(11, settled))
        assertFalse(routes.observe(11, settled))
        assertFalse(routes.observe(10, settled))
    }

    @Test
    fun losingAKnownRouteCountsAsAChangeAndANewSessionGetsItsOwnBaseline() {
        val old = RoutedOutputChanges()
        assertFalse(old.observe(10, start))
        assertTrue(old.observe(null, settled))
        val replacement = RoutedOutputChanges()
        assertFalse(replacement.observe(11, start))
        assertFalse(replacement.observe(11, settled))
    }

    @Test
    fun automaticReplacementRetainsThePriorOutputEvenBeforeItsFirstRouteCallback() {
        val original = RoutedOutputChanges()
        assertFalse(original.observe(10, start))
        // Settings replaced the old track before its queued change callback ran.
        val automatic = RoutedOutputChanges(original.baselineDeviceId)
        assertFalse(automatic.observe(null, start)) // new track hasn't been routed yet
        assertTrue(automatic.observe(11, start)) // no settle window for a preset baseline
        assertFalse(automatic.observe(11, settled))
        // Only an explicit user start accepts a fresh baseline.
        assertFalse(RoutedOutputChanges().observe(11, start))
    }

    @Test
    fun aMoveWhileAUserStartSettlesBecomesTheBaseline() {
        val routes = RoutedOutputChanges()
        assertFalse(routes.observe(10, start)) // speaker first
        assertFalse(routes.observe(11, settled - 1)) // Bluetooth finishes connecting
        assertFalse(routes.observe(11, settled))
        assertTrue(routes.observe(10, settled + 1))
        assertEquals(11, routes.baselineDeviceId)
    }

    @Test
    fun theSettleWindowStartsAtTheFirstActualRoute() {
        val routes = RoutedOutputChanges()
        assertFalse(routes.observe(null, start))
        assertFalse(routes.observe(10, 5_000))
        assertFalse(routes.observe(11, 5_000 + settled - 1))
        assertTrue(routes.observe(10, 5_000 + settled))
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

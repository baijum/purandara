package com.baijum.purandara.audio

/**
 * One active track's actual route, not the inventory of available devices.
 *
 * A user start takes its first route as the baseline, then gives the system
 * [SETTLE_MILLIS] to finish routing (a Bluetooth device still connecting moves
 * a fresh track off the speaker a moment later): a move inside that window is
 * the new baseline, not an output change. A preset baseline (an automatic
 * restart keeping the previous output) has no window.
 */
internal class RoutedOutputChanges(initialDeviceId: Int? = null) {
    var baselineDeviceId: Int? = initialDeviceId
        private set
    private var observedRoute = false
    private var changed = false
    private var settleUntil = Long.MIN_VALUE

    /** Initial null/first route, a move while settling and repeated notifications are not changes. */
    fun observe(deviceId: Int?, nowMillis: Long): Boolean {
        val firstRoute = !observedRoute
        if (changed || (firstRoute && deviceId == null)) return false // a replacement track hasn't routed yet
        observedRoute = true
        when {
            firstRoute && baselineDeviceId == null -> {
                baselineDeviceId = deviceId
                settleUntil = nowMillis + SETTLE_MILLIS
            }
            deviceId == baselineDeviceId -> {}
            nowMillis < settleUntil -> baselineDeviceId = deviceId
            else -> changed = true
        }
        return changed
    }

    companion object {
        const val SETTLE_MILLIS = 1_500L
    }
}

/** Output-only inventory fallback for mic-only sessions and pending background resumes. */
internal class AvailableOutputChanges(initialDeviceIds: Set<Int>) {
    private val deviceIds = initialDeviceIds.toMutableSet()

    /** Reconcile synchronously before resuming; the main-thread callback may still be queued. */
    fun reconcile(currentDeviceIds: Set<Int>): Boolean {
        if (deviceIds == currentDeviceIds) return false
        deviceIds.clear()
        deviceIds.addAll(currentDeviceIds)
        return true
    }

    /** Registering AudioDeviceCallback sends the initial inventory again; ignore it. */
    fun added(outputDeviceIds: Set<Int>): Boolean = deviceIds.addAll(outputDeviceIds)

    fun removed(outputDeviceIds: Set<Int>): Boolean = deviceIds.removeAll(outputDeviceIds)
}

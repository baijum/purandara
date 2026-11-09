package com.baijum.purandara.audio

/** One active track's actual route, not the inventory of available devices. */
internal class RoutedOutputChanges(initialDeviceId: Int? = null) {
    var baselineDeviceId: Int? = initialDeviceId
        private set
    private var observedRoute = false
    private var changed = false

    /** Initial null/first route and repeated notifications are not changes. */
    fun observe(deviceId: Int?): Boolean {
        if (changed) return false
        if (!observedRoute && deviceId == null) return false // a replacement track hasn't routed yet
        observedRoute = true
        if (baselineDeviceId == null) {
            baselineDeviceId = deviceId
            return false
        }
        if (deviceId == baselineDeviceId) return false
        changed = true
        return true
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

// Ported from ukulele-companion (same author, MIT).
package com.baijum.purandara.platform

import java.util.concurrent.locks.ReentrantLock

actual class PlatformLock {
    private val lock = ReentrantLock()
    actual fun lock() = lock.lock()
    actual fun unlock() = lock.unlock()
}

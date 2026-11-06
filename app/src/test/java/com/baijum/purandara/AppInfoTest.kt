package com.baijum.purandara

import org.junit.Assert.assertEquals
import org.junit.Test

class AppInfoTest {
    @Test
    fun sharedModuleIsLinked() {
        assertEquals("Purandara", AppInfo.NAME)
    }
}

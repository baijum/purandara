package com.baijum.purandara

import com.baijum.purandara.platform.platformName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppInfoTest {
    @Test
    fun appNameIsPurandara() {
        assertEquals("Purandara", AppInfo.NAME)
    }

    @Test
    fun platformNameIsNotBlank() {
        assertTrue(platformName().isNotBlank())
    }
}

package com.baijum.purandara.platform

import android.os.Build

actual fun platformName(): String = "Android ${Build.VERSION.RELEASE}"

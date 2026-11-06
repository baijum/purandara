package com.baijum.purandara.platform

actual fun platformName(): String = "JVM ${System.getProperty("java.version")}"

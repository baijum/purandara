package com.baijum.purandara.domain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Keeps the shared [HarmoniumBank] and the files in `audio/` in sync:
 * every asset path the app may request must exist, and nothing extra.
 */
class BundledSamplesTest {
    private val audioRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "audio") }
        .first { it.isDirectory }

    @Test
    fun everyBankAssetExists() {
        HarmoniumBank.allAssetPaths().forEach { path ->
            val file = File(audioRoot, path)
            assertTrue(file.isFile, "missing $file")
            assertTrue(file.length() > 10_000, "$file looks truncated (${file.length()} bytes)")
        }
    }

    @Test
    fun noUnexpectedFiles() {
        val expected = HarmoniumBank.allAssetPaths().map { it.substringAfter('/') }.toSet()
        val actual = File(audioRoot, HarmoniumBank.DIRECTORY).list()!!.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun filesAreFlac() {
        HarmoniumBank.allAssetPaths().forEach { path ->
            val magic = File(audioRoot, path).inputStream().use { it.readNBytes(4) }
            assertEquals("fLaC", String(magic, Charsets.US_ASCII), "$path is not FLAC")
        }
    }
}

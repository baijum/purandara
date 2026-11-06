package com.baijum.purandara

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import com.baijum.purandara.ui.LocalReduceMotion
import com.baijum.purandara.ui.PurandaraApp
import com.baijum.purandara.ui.rememberReduceMotion
import com.baijum.purandara.ui.theme.PurandaraTheme
import com.baijum.purandara.viewmodel.PracticeViewModel

/**
 * Single-activity entry point.
 *
 * Extends [AppCompatActivity] so per-app language support via
 * [androidx.appcompat.app.AppCompatDelegate.setApplicationLocales]
 * works on all supported API levels.
 */
class MainActivity : AppCompatActivity() {
    private val practiceViewModel: PracticeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug builds: `adb shell am start -n com.baijum.purandara/.MainActivity --ef singer 0`
        // replaces the microphone with a synthetic singer (cents off target).
        if (BuildConfig.DEBUG && intent.hasExtra(EXTRA_SINGER)) {
            practiceViewModel.debugSyntheticSingerCents = intent.getFloatExtra(EXTRA_SINGER, 0f).toDouble()
        }
        setContent {
            CompositionLocalProvider(LocalReduceMotion provides rememberReduceMotion()) {
                PurandaraTheme {
                    PurandaraApp(practiceViewModel)
                }
            }
        }
    }

    private companion object {
        const val EXTRA_SINGER = "singer"
    }

    /** Practice audio stops when the app leaves the screen. */
    override fun onStop() {
        super.onStop()
        practiceViewModel.stop()
    }
}

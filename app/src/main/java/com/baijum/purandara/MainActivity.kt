package com.baijum.purandara

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import com.baijum.purandara.ui.HomeScreen
import com.baijum.purandara.ui.LocalReduceMotion
import com.baijum.purandara.ui.rememberReduceMotion
import com.baijum.purandara.ui.theme.PurandaraTheme

/**
 * Single-activity entry point.
 *
 * Extends [AppCompatActivity] so per-app language support via
 * [androidx.appcompat.app.AppCompatDelegate.setApplicationLocales]
 * works on all supported API levels.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalReduceMotion provides rememberReduceMotion()) {
                PurandaraTheme {
                    HomeScreen()
                }
            }
        }
    }
}

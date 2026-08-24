package com.loresuelvo.serviceprovider

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.loresuelvo.serviceprovider.ui.navigation.LoResuelvoNav
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-Activity host. `@AndroidEntryPoint` is required so Hilt
 * can resolve `@HiltViewModel` consumers inside the navigation
 * graph (Welcome's ViewModel is the first one injected in the
 * process). Without the annotation the first `hiltViewModel()` call
 * crashes with `IllegalStateException: Given component holder class
 * MainActivity does not implement interface
 * dagger.hilt.internal.GeneratedComponent`.
 *
 * `onCreate` is intentionally minimal: composition root lives in
 * [LoResuelvoNav] and theme wrapping lives in [LoresuelvoTheme].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LoresuelvoTheme {
                LoResuelvoNav()
            }
        }
    }
}
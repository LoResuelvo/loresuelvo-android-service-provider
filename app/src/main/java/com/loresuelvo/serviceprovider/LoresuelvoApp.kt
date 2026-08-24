package com.loresuelvo.serviceprovider

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. `@HiltAndroidApp` triggers Hilt's code
 * generation; the generated `Hilt_LoresuelvoApp` then becomes the
 * base class for the actual application object the runtime uses.
 *
 * No initialization belongs in the constructor itself: Hilt builds
 * the component graph lazily on first injection (typically the
 * first `hiltViewModel()` call in the navigation graph).
 */
@HiltAndroidApp
class LoresuelvoApp : Application()
package com.loresuelvo.serviceprovider

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom AndroidJUnitRunner that swaps the production
 * [LoresuelvoApp] for [HiltTestApplication] in instrumented tests.
 *
 * Required by `@HiltAndroidTest`: `HiltTestApplication.generatedComponent()`
 * returns a fresh `SingletonComponent` for the test process; without
 * this runner the production app boots, the `HiltAndroidRule` finds
 * an un-initialized graph, and the first `hiltRule.inject()` crashes
 * with `IllegalStateException: The component was not created. Check
 * that you have added the HiltAndroidRule.`
 *
 * The runner is declared in `app/build.gradle.kts` via
 * `testInstrumentationRunner` (Fase 1).
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        name: String?,
        context: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
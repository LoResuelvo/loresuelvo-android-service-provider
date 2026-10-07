package com.loresuelvo.serviceprovider

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
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
    private var messagingPreferences: SharedPreferences? = null
    private var originalMessagingFlags = emptyMap<String, Boolean?>()
    override fun newApplication(
        cl: ClassLoader?,
        name: String?,
        context: Context?,
    ): Application {
        // Before FirebaseInitProvider runs, isolate the pinned SDK even when a real flavor is configured.
        // These SDK flags are test-process state, restored at finish; no app runtime switch is introduced.
        val prefs = checkNotNull(context).getSharedPreferences("com.google.firebase.messaging", Context.MODE_PRIVATE)
        messagingPreferences = prefs
        originalMessagingFlags = listOf("auto_init", "export_to_big_query").associateWith {
            if (prefs.contains(it)) prefs.getBoolean(it, false) else null
        }
        check(prefs.edit().putBoolean("auto_init", false).putBoolean("export_to_big_query", false).commit())
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }

    override fun finish(resultCode: Int, results: Bundle?) {
        messagingPreferences?.edit()?.apply {
            originalMessagingFlags.forEach { (key, value) -> if (value == null) remove(key) else putBoolean(key, value) }
        }?.commit()
        super.finish(resultCode, results)
    }
}

package com.loresuelvo.serviceprovider

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sanity check for the Android instrumentation pipeline.
 *
 * Asserts that the running application is in the `com.loresuelvo.serviceprovider`
 * namespace. The exact package name depends on the flavor suffix
 * (`.dev`, `.staging`, or none for `prod`), so we only assert the namespace root.
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    fun should_run_under_service_provider_namespace() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(
            "Expected package name to start with 'com.loresuelvo.serviceprovider' but was '${appContext.packageName}'",
            appContext.packageName.startsWith("com.loresuelvo.serviceprovider"),
        )
    }
}

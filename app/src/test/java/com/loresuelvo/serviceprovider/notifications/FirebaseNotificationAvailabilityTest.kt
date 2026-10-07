package com.loresuelvo.serviceprovider.notifications

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.loresuelvo.serviceprovider.platform.notifications.FirebaseNotificationTokenSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FirebaseNotificationAvailabilityTest {
    @Test fun missing_real_client_configuration_returns_no_token_without_attempting_transport() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FirebaseApp.getApps(context).forEach(FirebaseApp::delete)
        assertNull(FirebaseNotificationTokenSource(context).token())
    }
}

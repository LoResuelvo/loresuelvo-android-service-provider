package com.loresuelvo.serviceprovider.platform.auth

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.loresuelvo.serviceprovider.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Auth0CallbackManifestTest {
    @Test
    fun callback_is_handled_only_by_the_auth0_sdk() {
        val app = RuntimeEnvironment.getApplication()
        val callback = Uri.parse(
            "${BuildConfig.AUTH0_SCHEME}://${BuildConfig.AUTH0_DOMAIN}" +
                "/android/${BuildConfig.APPLICATION_ID}/callback",
        )
        val intent = Intent(Intent.ACTION_VIEW, callback)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(app.packageName)

        val handlers = app.packageManager.queryIntentActivities(
            intent, PackageManager.MATCH_DEFAULT_ONLY,
        ).map { it.activityInfo.name }

        assertEquals(listOf("com.auth0.android.provider.RedirectActivity"), handlers)
    }
}

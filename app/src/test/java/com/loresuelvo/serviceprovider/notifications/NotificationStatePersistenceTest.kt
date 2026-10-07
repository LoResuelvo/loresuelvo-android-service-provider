package com.loresuelvo.serviceprovider.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.data.notifications.EncryptedNotificationStateStore
import com.loresuelvo.serviceprovider.domain.notifications.HandledNotification
import com.loresuelvo.serviceprovider.domain.notifications.NotificationBinding
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationStatePersistenceTest {
    @Test fun process_recreated_storage_retains_installation_binding_handled_ids_and_permission_denial() {
        val preferences = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("push-persistence", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val first = EncryptedNotificationStateStore(preferences)
        val installation = first.read().copy(binding = NotificationBinding("binding", "auth0-subject", 7, true, true),
            previousBindingId = "previous", permissionRequested = true,
            handled = listOf(HandledNotification("message:123:7", "binding", 42, 60_000, "capability")))
        assertTrue(first.write(installation))
        val recreated = EncryptedNotificationStateStore(preferences)
        assertEquals(installation, recreated.read())
        assertTrue(recreated.write(recreated.read().copy(binding = installation.binding!!.copy(active = false), handled = emptyList())))
        assertFalse(EncryptedNotificationStateStore(preferences).read().binding!!.active)
        assertTrue(EncryptedNotificationStateStore(preferences).read().handled.isEmpty())
    }

    @Test fun corrupt_or_missing_storage_disables_notifications_without_inventing_replacement_possession() {
        val preferences = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("push-corrupt", Context.MODE_PRIVATE)
        preferences.edit().clear().putString("state", "not-json").commit()
        val corrupted = EncryptedNotificationStateStore(preferences)
        assertFalse(corrupted.write(corrupted.read()))
        assertEquals("not-json", preferences.getString("state", null))
        val missing = EncryptedNotificationStateStore(null)
        assertFalse(missing.write(missing.read()))
    }
}

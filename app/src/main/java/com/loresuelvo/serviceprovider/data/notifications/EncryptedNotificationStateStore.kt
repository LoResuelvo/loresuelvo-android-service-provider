package com.loresuelvo.serviceprovider.data.notifications

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.loresuelvo.serviceprovider.domain.notifications.HandledNotification
import com.loresuelvo.serviceprovider.domain.notifications.NotificationBinding
import com.loresuelvo.serviceprovider.domain.notifications.NotificationInstallation
import com.loresuelvo.serviceprovider.domain.notifications.NotificationStateStore
import com.loresuelvo.serviceprovider.domain.notifications.NotificationDestination
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class EncryptedNotificationStateStore internal constructor(private val preferences: SharedPreferences?) : NotificationStateStore {
    @Inject constructor(@ApplicationContext context: Context) : this(openPreferences(context))
    private var readable = true
    private val persisted = try { preferences?.getString("state", null) } catch (_: RuntimeException) { readable = false; null }
    private val loaded = persisted?.let(::decode)
    private val corrupted = !readable || (persisted != null && loaded == null)
    private var state = loaded ?: NotificationInstallation(UUID.randomUUID().toString(), UUID.randomUUID().toString())

    override fun read(): NotificationInstallation = state

    override fun write(state: NotificationInstallation): Boolean {
        if (corrupted) return false
        val prefs = preferences ?: return false
        return try {
            val saved = prefs.edit().putString("state", encode(state).toString()).commit()
            if (saved) this.state = state
            saved
        } catch (_: RuntimeException) { false }
    }

    private fun decode(raw: String): NotificationInstallation? = try {
            val json = JSONObject(raw)
            val binding = json.optJSONObject("binding")?.let {
                NotificationBinding(it.getString("id"), it.getString("subject"), it.getInt("recipient"),
                    it.getBoolean("active"), it.getBoolean("acknowledged"), it.optString("sessionKey").takeIf(String::isNotBlank))
            }
            val records = json.getJSONArray("handled")
            NotificationInstallation(json.getString("id"), json.getString("secret"), binding,
                json.optString("previous").takeIf { it.isNotBlank() },
                (0 until records.length()).mapNotNull { index ->
                    val record = records.getJSONObject(index)
                    val destination = when (record.optString("destination")) {
                        "conversation" -> NotificationDestination.Conversation(record.getInt("resource"))
                        "work_order" -> NotificationDestination.WorkOrder(record.getInt("resource"))
                        else -> return@mapNotNull null
                    }
                    HandledNotification(record.getString("event"), record.getString("binding"), destination,
                        record.getLong("expires"), record.optString("tap").takeIf { it.isNotBlank() })
                }, json.optBoolean("permission"),
                json.optString("acknowledgedBinding").takeIf(String::isNotBlank) ?: binding?.takeIf { it.acknowledged }?.id ?: json.optString("previous").takeIf(String::isNotBlank),
                json.optJSONArray("attemptedBindings")?.let { values -> (0 until values.length()).map(values::getString) }.orEmpty(),
                json.optString("registrationToken").takeIf(String::isNotBlank),
                json.optString("registrationLocale").takeIf(String::isNotBlank), json.optBoolean("registrationRejected"),
                json.optString("establishedSessionKey").takeIf(String::isNotBlank))
    } catch (_: RuntimeException) { null } catch (_: org.json.JSONException) { null }

    private fun encode(state: NotificationInstallation): JSONObject = JSONObject().apply {
        put("id", state.id); put("secret", state.secret); put("previous", state.previousBindingId)
        put("permission", state.permissionRequested)
        put("acknowledgedBinding", state.acknowledgedBindingId)
        put("attemptedBindings", JSONArray(state.attemptedBindingIds))
        put("registrationToken", state.registrationToken); put("registrationLocale", state.registrationLocale)
        put("registrationRejected", state.registrationRejected)
        put("establishedSessionKey", state.establishedSessionKey)
        state.binding?.let { binding -> put("binding", JSONObject().apply {
            put("id", binding.id); put("subject", binding.subject); put("recipient", binding.recipientId)
            put("active", binding.active); put("acknowledged", binding.acknowledged)
            put("sessionKey", binding.sessionKey)
        }) }
        put("handled", JSONArray().apply { state.handled.forEach { record -> put(JSONObject().apply {
            put("event", record.eventId); put("binding", record.bindingId); put("resource", record.destination.id)
            put("destination", when (record.destination) {
                is NotificationDestination.Conversation -> "conversation"
                is NotificationDestination.WorkOrder -> "work_order"
            })
            put("expires", record.expiresAt); put("tap", record.tapId)
        }) } })
    }

    companion object {
        const val PREFS_NAME = "provider_notifications_secure"

        // Optional notification storage failure must not break ordinary app use or replace a known installation.
        private fun openPreferences(context: Context): SharedPreferences? = try {
            val key = MasterKey.Builder(context, "provider_notifications_key").setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(context, PREFS_NAME, key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        } catch (_: java.security.GeneralSecurityException) { null } catch (_: java.io.IOException) { null }
        catch (_: RuntimeException) { null }
    }
}

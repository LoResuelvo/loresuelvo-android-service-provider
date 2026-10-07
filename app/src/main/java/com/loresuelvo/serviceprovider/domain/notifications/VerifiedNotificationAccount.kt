package com.loresuelvo.serviceprovider.domain.notifications

import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import java.security.MessageDigest

/** Possession metadata references a verified login without retaining its credentials. */
data class VerifiedNotificationAccount(val subject: String, val sessionKey: String, val recipientId: Int) {
    fun matches(session: AuthSession): Boolean = subject == session.user.id && sessionKey == key(session)

    companion object {
        fun from(session: AuthSession, recipientId: Int) = VerifiedNotificationAccount(session.user.id, key(session), recipientId)
        private fun key(session: AuthSession): String = MessageDigest.getInstance("SHA-256")
            .digest(session.accessToken.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

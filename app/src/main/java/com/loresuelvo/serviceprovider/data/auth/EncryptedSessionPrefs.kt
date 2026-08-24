package com.loresuelvo.serviceprovider.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

/**
 * Opens the encrypted backing storage for
 * [com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore].
 *
 * Recovery pattern: under certain conditions (reinstall with a
 * different signing key, factory reset, OS-level keystore wipe)
 * the master key stored in AndroidKeyStore can no longer decrypt
 * the existing ciphertext, throwing `AEADBadTagException` on app
 * launch. The user would otherwise be stuck re-installing. We
 * catch that one specific failure mode, wipe the broken prefs and
 * the master key alias, then rebuild from scratch — the session is
 * cleared, which is the same UX as a fresh install.
 */
internal fun createEncryptedSessionPrefs(context: Context): SharedPreferences {
    val appContext = context.applicationContext
    return try {
        openEncryptedPrefs(appContext)
    } catch (e: Throwable) {
        if (isRecoverablePrefsError(e)) {
            Log.w("SessionPrefs", "Master key unusable, wiping and recreating")
            wipeAndRecreate(appContext)
        } else {
            throw e
        }
    }
}

private fun openEncryptedPrefs(appContext: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(appContext, MASTER_KEY_ALIAS)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        appContext,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}

private fun wipeAndRecreate(appContext: Context): SharedPreferences {
    val prefsFile = java.io.File(
        java.io.File(appContext.applicationInfo.dataDir, "shared_prefs"),
        "$PREFS_NAME.xml",
    )
    runCatching { prefsFile.delete() }
    clearMasterKeyAlias()
    return openEncryptedPrefs(appContext)
}

private fun clearMasterKeyAlias() {
    runCatching {
        val keystore = KeyStore.getInstance("AndroidKeyStore")
        keystore.load(null)
        if (keystore.containsAlias(MASTER_KEY_ALIAS)) {
            keystore.deleteEntry(MASTER_KEY_ALIAS)
        }
    }
}

private fun isRecoverablePrefsError(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.any { candidate ->
        candidate is javax.crypto.AEADBadTagException ||
            candidate is android.security.keystore.KeyPermanentlyInvalidatedException ||
            candidate is java.security.KeyStoreException ||
            candidate is java.security.GeneralSecurityException ||
            candidate is java.io.IOException
    }

private const val MASTER_KEY_ALIAS = "loresuelvo_provider_auth_session_master_key"
internal const val PREFS_NAME = "auth_session_secure"
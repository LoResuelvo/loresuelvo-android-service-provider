package com.loresuelvo.serviceprovider.domain.auth

import kotlinx.coroutines.flow.StateFlow

interface AuthSessionStore {

    val sessionFlow: StateFlow<AuthSession?>

    fun getSession(): AuthSession?

    fun saveSession(session: AuthSession)

    fun clearSession()
}
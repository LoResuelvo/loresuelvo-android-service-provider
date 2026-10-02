package com.loresuelvo.serviceprovider.acceptance.messaging

import com.loresuelvo.serviceprovider.di.RealtimeModule
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.realtime.*
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

/** All instrumented tests use an in-process transport, including older acceptance suites. */
class ProviderRealtimeTestClient : RealtimeClient {
    private val incoming = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 64)
    override val events = incoming.asSharedFlow()
    override val state = MutableStateFlow(RealtimeState())
    val started = mutableListOf<AuthSession>()
    var active = 0
        private set
    var maximumActive = 0
        private set

    override suspend fun connect(session: AuthSession) {
        started += session
        active++
        maximumActive = maxOf(maximumActive, active)
        state.value = RealtimeState(session, RealtimeState.Connection.Connected)
        try { awaitCancellation() } finally {
            active--
            if (state.value.session == session) state.value = RealtimeState(session, RealtimeState.Connection.Stopped)
        }
    }

    /** Called on the main thread; Compose idling drains the subscribed collectors. */
    fun deliver(session: AuthSession, event: ProviderEvent) {
        check(incoming.subscriptionCount.value > 0) { "Open a subscribed destination before delivering" }
        check(incoming.tryEmit(SessionEvent(session, event))) { "Test event buffer exhausted" }
    }

    fun reset() {
        check(active == 0) { "Close the previous Activity before resetting" }
        started.clear()
        maximumActive = 0
        state.value = RealtimeState()
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RealtimeModule::class])
object ProviderRealtimeTestModule {
    @Provides @Singleton fun client(): ProviderRealtimeTestClient = ProviderRealtimeTestClient()
    @Provides @Singleton fun realtime(client: ProviderRealtimeTestClient): RealtimeClient = client
}

package com.loresuelvo.serviceprovider.acceptance.notifications

import com.loresuelvo.serviceprovider.di.NotificationModule
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.notifications.*
import com.loresuelvo.serviceprovider.platform.notifications.AndroidNotificationDisplay
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.util.UUID
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred

/** Only transport/storage are synthetic. Receiver, native display, clock and UI use production code. */
class ProviderNotificationTestStore : NotificationStateStore {
    @Volatile private var state = NotificationInstallation(UUID.randomUUID().toString(), UUID.randomUUID().toString(), permissionRequested = true)
    override fun read() = state
    override fun write(state: NotificationInstallation): Boolean { this.state = state; return true }
}
class ProviderNotificationTestToken : NotificationTokenSource {
    var current: String? = "instrumented-synthetic-token"
    override suspend fun token() = current
}
class ProviderNotificationTestInstallations : InstallationRepository {
    @Volatile var result: InstallationResult = InstallationResult.Applied
    @Volatile var removalGate: CompletableDeferred<Unit>? = null
    @Volatile var removalStarted = false
    override suspend fun register(installation: NotificationInstallation, token: String, locale: String, session: AuthSession) = result
    override suspend fun remove(installation: NotificationInstallation, session: AuthSession): InstallationResult {
        removalStarted = true
        removalGate?.await()
        return result
    }
}
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [NotificationModule::class])
object ProviderNotificationTestModule {
    @Provides @Singleton fun store() = ProviderNotificationTestStore()
    @Provides fun state(store: ProviderNotificationTestStore): NotificationStateStore = store
    @Provides @Singleton fun token() = ProviderNotificationTestToken()
    @Provides fun tokens(token: ProviderNotificationTestToken): NotificationTokenSource = token
    @Provides @Singleton fun installations() = ProviderNotificationTestInstallations()
    @Provides fun repository(installations: ProviderNotificationTestInstallations): InstallationRepository = installations
    @Provides fun display(display: AndroidNotificationDisplay): NotificationDisplay = display
    @Provides fun cleanup(local: NotificationLocalSession): NotificationSessionCleanup = local
    @Provides fun clock(): NotificationClock = NotificationClock(System::currentTimeMillis)
}

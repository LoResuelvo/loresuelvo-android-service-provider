package com.loresuelvo.serviceprovider.di

import com.loresuelvo.serviceprovider.data.api.ApiInstallationRepository
import com.loresuelvo.serviceprovider.data.notifications.EncryptedNotificationStateStore
import com.loresuelvo.serviceprovider.domain.notifications.*
import com.loresuelvo.serviceprovider.platform.notifications.AndroidNotificationDisplay
import com.loresuelvo.serviceprovider.platform.notifications.FirebaseNotificationTokenSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
    @Binds abstract fun state(store: EncryptedNotificationStateStore): NotificationStateStore
    @Binds abstract fun display(display: AndroidNotificationDisplay): NotificationDisplay
    @Binds abstract fun tokens(source: FirebaseNotificationTokenSource): NotificationTokenSource
    @Binds abstract fun installations(repository: ApiInstallationRepository): InstallationRepository
    @Binds abstract fun cleanup(local: NotificationLocalSession): NotificationSessionCleanup

    companion object {
        @Provides fun clock(): NotificationClock = NotificationClock(System::currentTimeMillis)
    }
}

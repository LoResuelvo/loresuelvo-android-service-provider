package com.loresuelvo.serviceprovider.di

import com.loresuelvo.serviceprovider.platform.calendar.CalendarConsentLauncher
import com.loresuelvo.serviceprovider.platform.calendar.GoogleCalendarConsentLauncher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarConsentModule {
    @Binds
    abstract fun bindCalendarConnectionRepository(
        implementation: com.loresuelvo.serviceprovider.data.api.ApiCalendarConnectionRepository,
    ): com.loresuelvo.serviceprovider.domain.calendar.CalendarConnectionRepository

    @Binds
    abstract fun bindCalendarConsentLauncher(implementation: GoogleCalendarConsentLauncher): CalendarConsentLauncher
}

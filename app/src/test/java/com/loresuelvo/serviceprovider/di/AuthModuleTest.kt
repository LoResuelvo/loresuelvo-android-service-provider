package com.loresuelvo.serviceprovider.di

import com.loresuelvo.serviceprovider.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthModuleTest {

    @Test
    fun auth0_config_exposes_the_flavor_provider_database_connection() {
        assertEquals(
            BuildConfig.AUTH0_PROVIDER_DATABASE_CONNECTION,
            AuthModule.provideAuth0Config().providerDatabaseConnection,
        )
    }
}

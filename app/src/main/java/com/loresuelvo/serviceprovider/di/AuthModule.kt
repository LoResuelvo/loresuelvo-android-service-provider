package com.loresuelvo.serviceprovider.di

import com.auth0.android.Auth0
import com.loresuelvo.serviceprovider.BuildConfig
import com.loresuelvo.serviceprovider.data.auth.Auth0AuthProvider
import com.loresuelvo.serviceprovider.data.auth.Auth0Config
import com.loresuelvo.serviceprovider.data.auth.Auth0SdkWebAuthLauncher
import com.loresuelvo.serviceprovider.data.auth.Auth0WebAuthLauncher
import com.loresuelvo.serviceprovider.domain.auth.AuthProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for the Auth0 adapter layer.
 *
 * - `@Binds` the two ports (`AuthProvider`, `Auth0WebAuthLauncher`)
 *   to their production implementations.
 * - `@Provides` [Auth0Config] from `BuildConfig.AUTH0_*` so the SDK
 *   is never instantiated by hand.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthProvider(impl: Auth0AuthProvider): AuthProvider

    @Binds
    @Singleton
    abstract fun bindAuth0WebAuthLauncher(
        impl: Auth0SdkWebAuthLauncher,
    ): Auth0WebAuthLauncher

    companion object {
        @Provides
        @Singleton
        fun provideAuth0Config(): Auth0Config = Auth0Config(
            domain = BuildConfig.AUTH0_DOMAIN,
            clientId = BuildConfig.AUTH0_CLIENT_ID,
            scheme = BuildConfig.AUTH0_SCHEME,
            audience = BuildConfig.AUTH0_AUDIENCE,
        )

        @Provides
        @Singleton
        fun provideAuth0(config: Auth0Config): Auth0 = Auth0(config.clientId, config.domain)
    }
}
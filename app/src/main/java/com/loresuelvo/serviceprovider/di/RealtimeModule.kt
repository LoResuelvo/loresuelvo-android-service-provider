package com.loresuelvo.serviceprovider.di

import com.loresuelvo.serviceprovider.BuildConfig
import com.loresuelvo.serviceprovider.data.api.BackendApi
import com.loresuelvo.serviceprovider.data.api.realtime.OkHttpRealtimeClient
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.realtime.RealtimeClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object RealtimeModule {
    @Provides
    @Singleton
    fun provideRealtimeClient(api: BackendApi, client: OkHttpClient, json: Json, sessions: AuthSessionStore): RealtimeClient {
        // Reuse connection settings while removing HTTP bearer authentication from the upgrade.
        val socketClient = client.newBuilder().apply {
            interceptors().clear()
            networkInterceptors().clear()
        }.callTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            .followRedirects(false).followSslRedirects(false).build()
        return OkHttpRealtimeClient(api, socketClient, json, sessions, BuildConfig.API_URL.toHttpUrl(),
            BuildConfig.FLAVOR.equals("dev", ignoreCase = true))
    }
}

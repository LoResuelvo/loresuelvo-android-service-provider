package com.loresuelvo.serviceprovider.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.loresuelvo.serviceprovider.BuildConfig
import com.loresuelvo.serviceprovider.data.api.ApiConfig
import com.loresuelvo.serviceprovider.data.api.AuthInterceptor
import com.loresuelvo.serviceprovider.data.api.BackendApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Provides the entire HTTP stack as Hilt-managed singletons.
 *
 * - [Json]: configured for the wire format (snake_case unknown
 *   keys ignored, nulls collapsed).
 * - [OkHttpClient]: carries the [AuthInterceptor] (token injection).
 *   A retry-on-401 authenticator lands alongside the Auth0 refresh
 *   flow in a later US.
 * - [Retrofit]: bound to the `API_URL` build-config field and the
 *   OkHttpClient above.
 * - [BackendApi]: Retrofit-typed facade. The only Retrofit type
 *   exposed to the rest of the app.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(ApiConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(ApiConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(ApiConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(ApiConfig.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .also {
            // Body-level logging in debug builds. Without this interceptor
            // we'd have no visibility into what /categories actually sent or
            // got back when debugging the Welcome flow. Throttled to debug
            // builds (BuildConfig.DEBUG is generated; missing here would be a
            // compile error).
            if (BuildConfig.DEBUG) {
                val logger = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
                it.addInterceptor(logger)
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideBackendApi(retrofit: Retrofit): BackendApi =
        retrofit.create(BackendApi::class.java)
}
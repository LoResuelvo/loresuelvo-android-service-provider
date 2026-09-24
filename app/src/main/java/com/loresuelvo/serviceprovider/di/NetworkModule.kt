package com.loresuelvo.serviceprovider.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.loresuelvo.serviceprovider.BuildConfig
import com.loresuelvo.serviceprovider.data.api.ApiConfig
import com.loresuelvo.serviceprovider.data.api.AuthInterceptor
import com.loresuelvo.serviceprovider.data.api.BackendApi
import com.loresuelvo.serviceprovider.data.api.ServiceProposalApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.MediaType
import okio.BufferedSink
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
        .build()

    @Provides
    @Singleton
    @Named("uploadOkHttp")
    fun provideUploadOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(ApiConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(ApiConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(ApiConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(ApiConfig.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
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

    @Provides
    @Singleton
    fun provideServiceProposalApi(client: OkHttpClient, json: Json): ServiceProposalApi =
        createServiceProposalApi(BuildConfig.API_URL, client, json)

    internal fun createServiceProposalApi(baseUrl: String, client: OkHttpClient, json: Json): ServiceProposalApi {
        val proposalClient = client.newBuilder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                val request = chain.request()
                val body = checkNotNull(request.body)
                val oneShot = object : RequestBody() {
                    override fun contentType(): MediaType? = body.contentType()
                    override fun contentLength(): Long = body.contentLength()
                    override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
                    override fun isOneShot(): Boolean = true
                }
                chain.proceed(request.newBuilder().method(request.method, oneShot).build())
            }
            .build()
        return Retrofit.Builder().baseUrl(baseUrl).client(proposalClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(ServiceProposalApi::class.java)
    }
}

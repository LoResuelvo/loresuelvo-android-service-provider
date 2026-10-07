package com.loresuelvo.serviceprovider.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.notifications.InstallationResult
import com.loresuelvo.serviceprovider.notifications.NotificationFixture
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit

class ApiInstallationRepositoryTest {
    @Test fun put_and_delete_send_possession_binding_auth_and_no_store_contract() = NotificationFixture().use { world ->
        world.register()
        runTest {
            MockWebServer().use { server ->
                val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(world.sessions)).build()
                val repository = ApiInstallationRepository(api(server, client))
                val installation = world.store.read().copy(previousBindingId = "30000000-0000-4000-8000-000000000003")
                val session = world.sessions.getSession()!!
                server.enqueue(MockResponse().setResponseCode(201))
                assertEquals(InstallationResult.Applied, repository.register(installation, "fcm-token", "en", session))
                val put = server.takeRequest()
                assertEquals("PUT", put.method)
                assertEquals("/installations/${installation.id}", put.path)
                assertEquals("Bearer token-a", put.getHeader("Authorization"))
                assertEquals("no-store", put.getHeader("Cache-Control"))
                val body = Json.parseToJsonElement(put.body.readUtf8()).jsonObject
                assertEquals(setOf("installation_secret", "app", "fcm_token", "locale", "binding_id", "previous_binding_id"), body.keys)
                assertEquals(installation.secret, body.getValue("installation_secret").jsonPrimitive.content)
                assertEquals("provider", body.getValue("app").jsonPrimitive.content)
                assertEquals("fcm-token", body.getValue("fcm_token").jsonPrimitive.content)
                assertEquals("en", body.getValue("locale").jsonPrimitive.content)
                assertEquals(installation.binding!!.id, body.getValue("binding_id").jsonPrimitive.content)
                assertEquals(installation.previousBindingId, body.getValue("previous_binding_id").jsonPrimitive.content)
                server.enqueue(MockResponse().setResponseCode(204))
                assertEquals(InstallationResult.Applied, repository.remove(installation, session))
                val delete = server.takeRequest()
                assertEquals("DELETE", delete.method)
                assertEquals(put.path, delete.path)
                assertEquals("Bearer token-a", delete.getHeader("Authorization"))
                assertEquals("no-store", delete.getHeader("Cache-Control"))
                assertEquals(setOf("installation_secret", "binding_id"), Json.parseToJsonElement(delete.body.readUtf8()).jsonObject.keys)
            }
        }
    }

    @Test fun response_mapping_never_retries_ordinary_client_errors() = NotificationFixture().use { world ->
        world.register()
        runTest {
            MockWebServer().use { server ->
                val repository = ApiInstallationRepository(api(server, OkHttpClient()))
                mapOf(200 to InstallationResult.Applied, 201 to InstallationResult.Applied, 400 to InstallationResult.Invalid,
                    401 to InstallationResult.Unauthorized, 403 to InstallationResult.Forbidden, 409 to InstallationResult.Conflict,
                    500 to InstallationResult.TransientFailure).forEach { (code, outcome) ->
                    server.enqueue(MockResponse().setResponseCode(code))
                    assertEquals(outcome, repository.register(world.store.read(), "fcm-token", "es", world.sessions.getSession()!!))
                    server.takeRequest()
                }
                assertEquals(7, server.requestCount)
            }
        }
    }

    @Test fun unexpected_success_status_does_not_acknowledge_registration_or_removal() = NotificationFixture().use { world ->
        world.register()
        runTest {
            MockWebServer().use { server ->
                val repository = ApiInstallationRepository(api(server, OkHttpClient()))
                server.enqueue(MockResponse().setResponseCode(204))
                assertEquals(InstallationResult.Invalid, repository.register(world.store.read(), "fcm-token", "es", world.sessions.getSession()!!))
                server.takeRequest()
                server.enqueue(MockResponse().setResponseCode(200))
                assertEquals(InstallationResult.Invalid, repository.remove(world.store.read(), world.sessions.getSession()!!))
                server.takeRequest()
            }
        }
    }

    @Test fun stale_remove_cannot_use_replacement_credentials_or_revoke_new_binding() = NotificationFixture().use { world ->
        world.register()
        runTest {
            MockWebServer().use { server ->
                val previous = world.sessions.getSession()!!
                val replacement = AuthSession(User("replacement", "replacement@example.test"), "replacement-token")
                val client = OkHttpClient.Builder().addInterceptor { chain ->
                    world.sessions.saveSession(replacement)
                    chain.proceed(chain.request())
                }.addInterceptor(AuthInterceptor(world.sessions)).build()
                val repository = ApiInstallationRepository(api(server, client))
                assertEquals(InstallationResult.TransientFailure, repository.remove(world.store.read(), previous))
                assertEquals(0, server.requestCount)
                assertEquals(replacement, world.sessions.getSession())
            }
        }
    }

    @Test fun offline_failure_is_typed_and_coroutine_cancellation_propagates() = NotificationFixture().use { world ->
        world.register()
        runTest {
            val backend = mockk<BackendApi>()
            val repository = ApiInstallationRepository(backend)
            coEvery { backend.removeInstallation(any(), any(), any()) } throws java.io.IOException("offline")
            assertEquals(InstallationResult.TransientFailure, repository.remove(world.store.read(), world.sessions.getSession()!!))
            val cancellation = CancellationException("cancel")
            coEvery { backend.removeInstallation(any(), any(), any()) } throws cancellation
            try { repository.remove(world.store.read(), world.sessions.getSession()!!); fail("Cancellation must propagate") }
            catch (error: CancellationException) { assertSame(cancellation, error) }
        }
    }

    private fun api(server: MockWebServer, client: OkHttpClient): BackendApi = Retrofit.Builder()
        .baseUrl(server.url("/")).client(client)
        .addConverterFactory(Json { explicitNulls = false }.asConverterFactory("application/json".toMediaType()))
        .build().create(BackendApi::class.java)
}

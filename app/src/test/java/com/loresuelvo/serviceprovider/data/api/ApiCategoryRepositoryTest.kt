package com.loresuelvo.serviceprovider.data.api

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.assertFailsWith
import org.junit.Test

class ApiCategoryRepositoryTest {

    private val backendApi = mockk<BackendApi>()
    private val repository = ApiCategoryRepository(backendApi)

    @Test
    fun `getCategories propagates cancellation thrown by backend request`() = runTest {
        coEvery { backendApi.getCategories() } throws CancellationException("Request cancelled")

        assertFailsWith<CancellationException> { repository.getCategories() }
    }
}

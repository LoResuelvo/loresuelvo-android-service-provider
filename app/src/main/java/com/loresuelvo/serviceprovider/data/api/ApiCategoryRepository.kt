package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.mapper.toDomain
import com.loresuelvo.serviceprovider.domain.api.ApiError
import com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome
import com.loresuelvo.serviceprovider.domain.category.CategoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of the [CategoryRepository] port. Adapts
 * the [BackendApi] (Retrofit-typed) `GET /categories` call to the
 * domain's [CategoriesOutcome] hierarchy.
 *
 * It never throws on HTTP / network failures: every exception is
 * translated to a typed failure via [toApiError], so callers handle
 * each branch explicitly.
 */
@Singleton
class ApiCategoryRepository @Inject constructor(
    private val backendApi: BackendApi,
) : CategoryRepository {

    override suspend fun getCategories(): CategoriesOutcome =
        try {
            CategoriesOutcome.Success(backendApi.getCategories().toDomain())
        } catch (e: Throwable) {
            mapToFailure(e)
        }

    private fun mapToFailure(e: Throwable): CategoriesOutcome.Failure =
        when (val error = e.toApiError()) {
            is ApiError.Network ->
                CategoriesOutcome.Failure.Network(error.networkCause)
            is ApiError.Unauthorized ->
                CategoriesOutcome.Failure.Server(401, error.errorMessage)
            is ApiError.Server ->
                CategoriesOutcome.Failure.Server(error.code, error.errorMessage)
            is ApiError.Unknown ->
                CategoriesOutcome.Failure.Server(0, error.message ?: "Unknown error")
        }
}
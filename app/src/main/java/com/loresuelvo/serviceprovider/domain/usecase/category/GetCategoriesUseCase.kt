package com.loresuelvo.serviceprovider.domain.usecase.category

import com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome
import com.loresuelvo.serviceprovider.domain.category.CategoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the platform's service categories through the
 * [CategoryRepository] port. Stateless; propagates the typed
 * [CategoriesOutcome] without swallowing errors. The endpoint is
 * public, so no session is required — this can run on the Welcome
 * screen before the user authenticates.
 */
@Singleton
class GetCategoriesUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
) {
    suspend operator fun invoke(): CategoriesOutcome =
        categoryRepository.getCategories()
}
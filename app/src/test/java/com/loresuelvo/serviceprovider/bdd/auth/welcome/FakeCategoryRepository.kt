package com.loresuelvo.serviceprovider.bdd.auth.welcome

import com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.category.CategoryRepository

/**
 * Test double for [CategoryRepository]. Default behaviour: every call
 * returns [CategoriesOutcome.Failure.Network] (no categories
 * available). Tests can swap [nextOutcome] before triggering a load
 * to drive [com.loresuelvo.serviceprovider.ui.auth.WelcomeViewModel]
 * through the success and failure branches of `loadCategories`.
 */
class FakeCategoryRepository(
    var nextOutcome: CategoriesOutcome =
        CategoriesOutcome.Failure.Network(IllegalStateException("no categories stubbed")),
) : CategoryRepository {

    var getCategoriesCalls: Int = 0
        private set

    override suspend fun getCategories(): CategoriesOutcome {
        getCategoriesCalls++
        return nextOutcome
    }

    companion object {
        fun successWith(vararg categories: Pair<Int, String>): CategoriesOutcome.Success {
            val list = categories.map { (id, name) -> Category(id = id, name = name) }
            return CategoriesOutcome.Success(list)
        }
    }
}
package com.loresuelvo.serviceprovider.di

import com.loresuelvo.serviceprovider.data.api.ApiCategoryRepository
import com.loresuelvo.serviceprovider.data.auth.EncryptedAuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.category.CategoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds every `domain.XxxRepository` port to its production
 * `data/.../XxxRepositoryImpl`. Add a new port by:
 *   1. declaring `interface XxxRepository` in `domain/;
 *   2. writing `class XxxRepositoryImpl @Inject constructor(...) : XxxRepository` in `data/;
 *   3. adding a `@Binds fun bindXxxRepository(impl: XxxRepositoryImpl): XxxRepository`
 *      line here.
 *
 * `AuthSessionStore` is registered here because it plays the role of a
 * persistent repository from the provider-flow perspective. Its
 * `EncryptedSharedPreferences` lives in `data/auth/` (see
 * `SessionStoreModule`).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: ApiCategoryRepository): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindAuthSessionStore(impl: EncryptedAuthSessionStore): AuthSessionStore
}
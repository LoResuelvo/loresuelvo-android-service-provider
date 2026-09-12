package com.loresuelvo.serviceprovider.di

import com.loresuelvo.serviceprovider.data.api.ApiFileRepository
import com.loresuelvo.serviceprovider.data.api.upload.FileUploader
import com.loresuelvo.serviceprovider.data.api.upload.OkHttpFileUploader
import com.loresuelvo.serviceprovider.domain.file.FileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding [FileRepository] to [ApiFileRepository]
 * and [FileUploader] to [OkHttpFileUploader].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FileRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFileRepository(impl: ApiFileRepository): FileRepository

    @Binds
    @Singleton
    abstract fun bindFileUploader(impl: OkHttpFileUploader): FileUploader
}

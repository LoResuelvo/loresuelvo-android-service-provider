package com.loresuelvo.serviceprovider.di

import com.loresuelvo.serviceprovider.data.media.AndroidProfilePhotoPreparer
import com.loresuelvo.serviceprovider.data.media.AndroidCompletionEvidencePreparer
import com.loresuelvo.serviceprovider.domain.activity.CompletionEvidencePreparer
import com.loresuelvo.serviceprovider.domain.profile.ProfilePhotoPreparer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the shared bounded image preparation adapters. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProfilePhotoModule {

    @Binds
    @Singleton
    abstract fun bindProfilePhotoPreparer(
        impl: AndroidProfilePhotoPreparer,
    ): ProfilePhotoPreparer

    @Binds
    @Singleton
    abstract fun bindCompletionEvidencePreparer(
        impl: AndroidCompletionEvidencePreparer,
    ): CompletionEvidencePreparer
}

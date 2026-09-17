package com.loresuelvo.serviceprovider.di

import com.loresuelvo.serviceprovider.data.media.AndroidMediaReader
import com.loresuelvo.serviceprovider.data.media.MediaReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt binding for the platform [MediaReader]. The
 * `AndroidMediaReader` is the only place in the codebase that
 * touches `ContentResolver` / `OpenableColumns`; the bind keeps
 * the call site free of the Android-specific dependency so JVM
 * tests can swap a fake without Robolectric.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {

    @Binds
    @Singleton
    abstract fun bindMediaReader(impl: AndroidMediaReader): MediaReader
}

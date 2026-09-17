package com.loresuelvo.serviceprovider.di

import com.loresuelvo.serviceprovider.data.media.AndroidAudioPlayer
import com.loresuelvo.serviceprovider.data.media.AndroidAudioRecorder
import com.loresuelvo.serviceprovider.data.media.AndroidMediaReader
import com.loresuelvo.serviceprovider.data.media.AudioPlayer
import com.loresuelvo.serviceprovider.data.media.AudioRecorder
import com.loresuelvo.serviceprovider.data.media.MediaReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the platform media abstractions used by the
 * chat surface. Each impl is the only place in the codebase
 * that touches the corresponding Android API (ContentResolver,
 * MediaRecorder, MediaPlayer); the binds keep the call sites
 * free of those Android-specific dependencies so JVM tests can
 * swap fakes without Robolectric.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {

    @Binds
    @Singleton
    abstract fun bindMediaReader(impl: AndroidMediaReader): MediaReader

    @Binds
    @Singleton
    abstract fun bindAudioRecorder(impl: AndroidAudioRecorder): AudioRecorder

    @Binds
    @Singleton
    abstract fun bindAudioPlayer(impl: AndroidAudioPlayer): AudioPlayer
}

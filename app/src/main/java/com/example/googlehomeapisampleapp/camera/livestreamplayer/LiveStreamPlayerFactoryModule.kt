package com.example.googlehomeapisampleapp.camera.livestreamplayer

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface LiveStreamPlayerFactoryModule {
    @Binds fun bindLiveStreamPlayerFactory(impl: LiveStreamPlayerFactoryImpl): LiveStreamPlayerFactory
}
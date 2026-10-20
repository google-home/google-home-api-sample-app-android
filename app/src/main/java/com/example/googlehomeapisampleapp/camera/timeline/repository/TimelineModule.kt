/* Copyright 2025 Google LLC */
package com.example.googlehomeapisampleapp.camera.timeline.repository

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TimelineModule {
    @Binds
    @Singleton
    abstract fun bindTimelineDataSource(impl: GhpTimelineDataSource): TimelineDataSource

    @Binds
    @Singleton
    abstract fun bindTimelineCache(impl: InMemoryTimelineCache): TimelineCache

    @Binds
    @Singleton
    abstract fun bindTimelineRepository(impl: TimelineRepositoryImpl): TimelineRepository
}
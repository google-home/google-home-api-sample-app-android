package com.example.googlehomeapisampleapp.camera.timeline.repository

import kotlinx.coroutines.flow.Flow

/** Repository for retrieving timeline data. */
interface TimelineRepository {
    /**
     * Returns a flow of all currently cached timeline data for the given device.
     */
    fun observeTimelines(deviceId: String): Flow<TimelineData>

    /**
     * Fetches timeline data for a device within a given time range,
     * using the cache where possible.
     */
    suspend fun fetchTimelinePeriod(deviceId: String, range: TimelineRange)
}
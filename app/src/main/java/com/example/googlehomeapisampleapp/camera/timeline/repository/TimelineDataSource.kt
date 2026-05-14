package com.example.googlehomeapisampleapp.camera.timeline.repository

import kotlinx.coroutines.flow.Flow

/** Interface for fetching timeline data from the network/trait. */
interface TimelineDataSource {
    /**
     * Fetches timeline data for a device within a given time range.
     */
    fun fetch(deviceId: String, range: TimelineRange): Flow<TimelineData>
}
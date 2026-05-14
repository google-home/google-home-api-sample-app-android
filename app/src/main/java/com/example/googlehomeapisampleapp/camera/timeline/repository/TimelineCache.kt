package com.example.googlehomeapisampleapp.camera.timeline.repository

import kotlinx.coroutines.flow.Flow

interface TimelineCache {
    /** Returns a flow of all cached timeline intervals for a device. */
    fun getAllTimelines(deviceId: String): Flow<TimelineData>

    /**
     * Returns the cached timeline intervals for a device within the given range.
     */
    fun getTimelines(deviceId: String, range: TimelineRange): TimelineData

    /**
     * Returns the portions of the given `range` that are missing from the cache.
     */
    fun getMissingRanges(deviceId: String, range: TimelineRange): List<TimelineRange>

    /**
     * Adds the given results to the cache and marks the range as covered.
     */
    fun updateTimelines(deviceId: String, range: TimelineRange, results: TimelineData)

    /** Clears the cache. */
    fun clear()
}
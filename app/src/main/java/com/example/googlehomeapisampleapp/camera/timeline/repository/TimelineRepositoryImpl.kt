package com.example.googlehomeapisampleapp.camera.timeline.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TimelineRepositoryImpl"

@Singleton
class TimelineRepositoryImpl @Inject constructor(
    private val dataSource: TimelineDataSource,
    private val cache: TimelineCache,
) : TimelineRepository {

    override fun observeTimelines(deviceId: String): Flow<TimelineData> {
        return cache.getAllTimelines(deviceId)
    }

    override suspend fun fetchTimelinePeriod(deviceId: String, range: TimelineRange) {
        val missingRanges = cache.getMissingRanges(deviceId, range)
        if (missingRanges.isEmpty()) {
            Log.d(TAG, "Cache fully covers range for device: $deviceId")
            return
        }

        for (missingRange in missingRanges) {
            Log.d(TAG, "Fetching missing range $missingRange for device: $deviceId")
            dataSource.fetch(deviceId, missingRange).collect { chunk ->
                cache.updateTimelines(deviceId, missingRange, chunk)
            }
        }
    }
}
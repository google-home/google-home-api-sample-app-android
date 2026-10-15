package com.example.googlehomeapisampleapp.camera.timeline.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryTimelineCache @Inject constructor() : TimelineCache {
    private val deviceCaches = MutableStateFlow<Map<String, DeviceCache>>(emptyMap())

    override fun getAllTimelines(deviceId: String): Flow<TimelineData> {
        return deviceCaches.map { it[deviceId]?.timelines ?: TimelineData() }.distinctUntilChanged()
    }

    override fun getTimelines(deviceId: String, range: TimelineRange): TimelineData {
        val timelines = deviceCaches.value[deviceId]?.timelines ?: return TimelineData()

        return TimelineData(
            videoTimelines = timelines.videoTimelines.overlappingWith(range),
            eventTimelines = timelines.eventTimelines.overlappingWith(range),
            cameraStateTimelines = timelines.cameraStateTimelines.overlappingWith(range),
        )
    }

    override fun getMissingRanges(deviceId: String, range: TimelineRange): List<TimelineRange> {
        if (range.start >= range.endInclusive) return emptyList()

        val fetchedRanges = deviceCaches.value[deviceId]?.fetchedRanges ?: return listOf(range)
        if (fetchedRanges.isEmpty()) {
            return listOf(range)
        }

        val gaps = mutableListOf<TimelineRange>()
        var coveredUntil = range.start

        for (fetchedRange in fetchedRanges) {
            if (fetchedRange.start >= range.endInclusive) break
            if (fetchedRange.endInclusive <= coveredUntil) continue

            if (fetchedRange.start > coveredUntil) {
                gaps.add(TimelineRange(coveredUntil, fetchedRange.start))
            }
            coveredUntil = maxOf(coveredUntil, fetchedRange.endInclusive)
        }

        if (coveredUntil < range.endInclusive) {
            gaps.add(TimelineRange(coveredUntil, range.endInclusive))
        }

        return gaps
    }

    override fun updateTimelines(deviceId: String, range: TimelineRange, results: TimelineData) {
        Log.i(TAG, "updateTimelines: deviceId=$deviceId")

        deviceCaches.update { currentMap ->
            val deviceCache = currentMap[deviceId] ?: DeviceCache()
            val timelines = deviceCache.timelines

            val videoList = timelines.videoTimelines.toMutableList()
            val eventList = timelines.eventTimelines.toMutableList()
            val cameraStateList = timelines.cameraStateTimelines.toMutableList()

            insertSortedResults(videoList, results.videoTimelines)
            insertSortedResults(eventList, results.eventTimelines)
            insertSortedResults(cameraStateList, results.cameraStateTimelines)

            val newTimelines = TimelineData(
                videoTimelines = videoList.takeLast(MAX_PERIODS_PER_TYPE),
                eventTimelines = eventList.takeLast(MAX_PERIODS_PER_TYPE),
                cameraStateTimelines = cameraStateList.takeLast(MAX_PERIODS_PER_TYPE),
            )

            val newFetchedRanges = deviceCache.fetchedRanges.toMutableList()
            mergeAndAdd(newFetchedRanges, TimelineRange(range.start, range.endInclusive))

            currentMap + (deviceId to DeviceCache(newTimelines, newFetchedRanges))
        }
    }

    private fun <T : TimelinePeriod> insertSortedResults(current: MutableList<T>, new: List<T>) {
        val existingKeys = current.map { it.startTime to it.endTime }.toHashSet()
        val toInsert = new.filter { (it.startTime to it.endTime) !in existingKeys }
        if (toInsert.isEmpty()) return
        val insertionIndex = run {
            val idx = current.binarySearchBy(toInsert.first().startTime) { it.startTime }
            if (idx < 0) -(idx + 1) else idx
        }
        current.addAll(insertionIndex, toInsert)
    }

    private fun mergeAndAdd(list: MutableList<TimelineRange>, newInterval: TimelineRange) {
        if (newInterval.start > newInterval.endInclusive) return

        var insertionIndex = 0
        while (insertionIndex < list.size && list[insertionIndex].start < newInterval.start) {
            insertionIndex++
        }

        var mergedStart = newInterval.start
        var mergedEnd = newInterval.endInclusive

        if (insertionIndex > 0) {
            val prev = list[insertionIndex - 1]
            if (prev.endInclusive >= mergedStart) {
                mergedStart = prev.start
                mergedEnd = maxOf(mergedEnd, prev.endInclusive)
                list.removeAt(insertionIndex - 1)
                insertionIndex--
            }
        }

        while (insertionIndex < list.size) {
            val next = list[insertionIndex]
            if (next.start <= mergedEnd) {
                mergedEnd = maxOf(mergedEnd, next.endInclusive)
                list.removeAt(insertionIndex)
            } else {
                break
            }
        }

        list.add(insertionIndex, TimelineRange(mergedStart, mergedEnd))
    }

    private fun <T : TimelinePeriod> insertSorted(list: MutableList<T>, newInterval: T) {
        if (newInterval.startTime > newInterval.endTime) return

        val index = list.binarySearchBy(newInterval.startTime) { it.startTime }
        val insertionIndex = if (index < 0) -(index + 1) else index
        list.add(insertionIndex, newInterval)
    }

    private fun <T : TimelinePeriod> List<T>.overlappingWith(range: TimelineRange): List<T> {
        if (isEmpty()) return emptyList()

        var fromIndex = binarySearchBy(range.start) { it.endTime }
        if (fromIndex < 0) {
            fromIndex = -(fromIndex + 1)
        }

        var toIndex = binarySearchBy(range.endInclusive) { it.startTime }
        if (toIndex < 0) {
            toIndex = -(toIndex + 1)
        } else {
            toIndex++
        }

        if (fromIndex > toIndex || fromIndex >= size) return emptyList()
        return subList(fromIndex, toIndex)
    }

    override fun clear() {
        Log.i(TAG, "Clearing cache")
        deviceCaches.update { emptyMap() }
    }

    private data class DeviceCache(
        val timelines: TimelineData = TimelineData(),
        val fetchedRanges: List<TimelineRange> = emptyList(),
    )

    companion object {
        private const val TAG = "InMemoryTimelineCache"
        private const val MAX_PERIODS_PER_TYPE = 500
    }
}
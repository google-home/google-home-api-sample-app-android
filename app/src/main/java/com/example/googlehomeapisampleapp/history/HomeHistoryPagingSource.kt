/* Copyright 2026 Google LLC
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
    https://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.example.googlehomeapisampleapp.history
import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.home.HistoryFilter
import com.google.home.HistoryItem
import com.google.home.HistoryItemsPage
import com.google.home.HistoryManager
import com.google.home.annotation.HomeExperimentalApi
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
/**
 * Paging source for fetching history events from the Home History API.
 *
 * @param historyManager The history manager to fetch history events from.
 * @param historyFilters The list of filters to apply to the history query.
 */
@OptIn(HomeExperimentalApi::class)
class HomeHistoryPagingSource private constructor(
    private val historyManager: HistoryManager,
    private val historyFilters: List<HistoryFilter>,
) : PagingSource<HistoryItemsPage.Success, HistoryItem>() {
    private var currentPage: HistoryItemsPage.Success? = null
    @OptIn(HomeExperimentalApi::class)
    override suspend fun load(
        params: LoadParams<HistoryItemsPage.Success>
    ): LoadResult<HistoryItemsPage.Success, HistoryItem> {
        return try {
            // Use getHistory with the filters list passed to the constructor
            // The key is the 'nextPageToken' from the previous page, cast as a HistoryItemsPage.Success
            val response = withTimeoutOrNull(TIMEOUT_MS) {
                params.key?.loadNext() ?: historyManager.getHistory(*historyFilters.toTypedArray())
            }
            if (response == null) {
                Log.e(TAG, "Failed to query history events: Timeout.")
                return LoadResult.Error(Error("Failed to query history events."))
            }
            when (response) {
                is HistoryItemsPage.Success -> {
                    Log.i(TAG, "Success fetching history page!")
                    currentPage = response
                    LoadResult.Page(
                        data = response.result,
                        prevKey = null, // Forward paging only
                        // If this is the last page, nextKey is null
                        nextKey = if (response.endOfPagination) null else response
                    )
                }
                is HistoryItemsPage.Error -> {
                    Log.w(TAG, "Failed to fetch history: ${response.exception.message}")
                    LoadResult.Error(response.exception)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during history load", e)
            LoadResult.Error(e)
        }
    }
    override fun getRefreshKey(state: PagingState<HistoryItemsPage.Success, HistoryItem>): HistoryItemsPage.Success? {
        return currentPage
    }
    class Builder(private val historyManager: HistoryManager) {
        private val historyFilters: MutableList<HistoryFilter> = mutableListOf()
        fun addHistoryFilters(vararg filters: HistoryFilter): Builder {
            historyFilters.addAll(filters)
            return this
        }
        fun build() = HomeHistoryPagingSource(historyManager, historyFilters.toList())
    }
    companion object {
        private const val TAG = "HomeHistoryPagingSource"
        private val TIMEOUT_MS = 10.seconds
    }
}

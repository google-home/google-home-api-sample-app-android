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

package com.example.googlehomeapisampleapp.searchablehome

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.googlehomeapisampleapp.history.CameraMediaAuth
import com.google.home.HomeClient
import com.google.home.HomeDevice
import com.google.home.google.SearchableHome
import com.google.home.google.SearchableHomeTrait
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import com.google.home.annotation.HomeExperimentalApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Represents a chat message in the Searchable Home feature. */
sealed interface ChatMessage {
  val isOutgoing: Boolean
  val isError: Boolean
    get() = this is ChatMessage.Text && this.isError
  val text: String
    get() = if (this is ChatMessage.Text) this.text else ""

  /** A text-based chat message. */
  data class Text(
    override val text: String,
    override val isOutgoing: Boolean,
    override val isError: Boolean = false,
  ) : ChatMessage

  /** A richer chat message containing a single [CameraEvent] item. */
  data class CameraEvent(
    val event: CameraEventData,
    override val isOutgoing: Boolean
  ) : ChatMessage
}

/** Data class representing Camera Events tied to Searchable Home answers. */
data class CameraEventData(
  val cameraName: String,
  val startTime: Instant?,
  val endTime: Instant?,
  val shortCaption: String?,
  val previewUrl: String?,
  val thumbnailUrl: String?,
)

@OptIn(ExperimentalCoroutinesApi::class, HomeExperimentalApi::class)
@HiltViewModel
class SearchableHomeViewModel @Inject constructor(
  private val homeClient: HomeClient,
  private val savedStateHandle: SavedStateHandle,
  val cameraMediaAuth: CameraMediaAuth,
) : ViewModel() {

  companion object {
    private const val TAG = "SearchableHomeVM"
    private const val STRUCTURE_ID_KEY = "structure_id"
    internal const val MAX_MESSAGES = 100
    private const val TRAIT_TIMEOUT_MS = 5_000L
    private const val RESPONSE_TIMEOUT_MS = 30_000L
  }

  private val structureIdFlow: StateFlow<String> = 
    savedStateHandle.getStateFlow(STRUCTURE_ID_KEY, "")

  fun setStructureId(id: String) {
    savedStateHandle[STRUCTURE_ID_KEY] = id
  }

  private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
  val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

  private val _isSending = MutableStateFlow(false)
  val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

  private var devices: Set<HomeDevice> = emptySet()

  // A flow that monitors the active structure ID and resolves it to the corresponding
  // Structure object from the HomeClient. Emits null-filtered structures.
  private val structureFlow = structureIdFlow
    .filter { it.isNotEmpty() }
    .flatMapLatest { id ->
      homeClient.structures().map { structures ->
        structures.firstOrNull { it.id.id == id }
      }
    }
    .filterNotNull()

  // A flow that retrieves the SearchableHome trait for the active structure.
  // This trait is used to execute the natural language search queries.
  private val searchableHomeFlow: Flow<SearchableHome> =
    structureFlow.flatMapLatest { structure ->
      structure.trait(SearchableHome)
    }

  // Initialize the ViewModel by observing the devices in the active structure.
  // This list of devices is used to resolve friendly camera names from camera IDs.
  init {
    Log.d(TAG, "SearchableHomeViewModel created")
    viewModelScope.launch {
      structureFlow
          .flatMapLatest { structure ->
            structure.devices()
          }
          .collect {
            devices = it
          }
    }
  }

  /**
   * Sends a search query to the SearchableHome trait.
   * Handles trait resolution, timeouts, response parsing, and error reporting.
   */
  fun sendMessage(text: String) {
    val structureId = structureIdFlow.value
    if (text.isNotBlank() && !_isSending.value) {
      _isSending.value = true
      appendMessage(ChatMessage.Text(text, isOutgoing = true))

      viewModelScope.launch {
        var errorType: String? = null
        try {
          // Resolve the SearchableHome trait, timing out if it takes too long.
          val trait =
            withTimeoutOrNull(TRAIT_TIMEOUT_MS) { searchableHomeFlow.filterNotNull().first() }

          if (trait == null) {
            Log.w(TAG, "SearchableHome trait not available in structure $structureId after ${TRAIT_TIMEOUT_MS}ms")
            errorType = "TraitNotAvailable"
            return@launch
          }

          // Execute the search query with a timeout.
          val response = withTimeoutOrNull(RESPONSE_TIMEOUT_MS) { trait.search(text) }
          val queryResponse = response?.queryResponse

          when {
            response == null || queryResponse == null -> {
              Log.w(TAG, "Search command in structure $structureId timed out")
              errorType = "Response Timeout"
            }
            queryResponse.isBlank() && response.cameraEventsList.isEmpty() -> {
              Log.w(TAG, "Search command in structure $structureId returned empty response")
              errorType = "Empty Response"
            }
            else -> {
              // Post the text response if present.
              if (queryResponse.isNotBlank()) {
                appendMessage(ChatMessage.Text(text = queryResponse, isOutgoing = false))
              }
              // Map and post up to 10 relevant camera events.
              val cameraEvents =
                response.cameraEventsList.take(10).map { event -> event.toCameraEvent() }
              for (event in cameraEvents) {
                appendMessage(ChatMessage.CameraEvent(event, isOutgoing = false))
              }
            }
          }
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          Log.e(TAG, "Search command in structure $structureId failed", e)
          errorType = e.javaClass.simpleName
        } finally {
          // If an error occurred, append an error message.
          errorType?.let {
            appendMessage(ChatMessage.Text(text = it, isOutgoing = false, isError = true))
          }
          _isSending.value = false
        }
      }
    }
  }

  private fun appendMessage(message: ChatMessage) {
    _messages.value = (_messages.value + message).takeLast(MAX_MESSAGES)
  }

  // Maps raw SDK camera event details to the UI data model, resolving the camera's
  // friendly name by matching the camera ID against the list of structure devices.
  private fun SearchableHomeTrait.BasicCameraEventDetails.toCameraEvent(): CameraEventData {
    val friendlyName = devices.firstOrNull { it.id.id == entityObjectId }?.name ?: "Unknown Camera"
    return CameraEventData(
      cameraName = friendlyName,
      startTime = startTime,
      endTime = endTime,
      shortCaption = shortCaption,
      previewUrl = previewUrl,
      thumbnailUrl = thumbnailUrl,
    )
  }
}

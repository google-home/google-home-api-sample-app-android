/* Copyright 2025 Google LLC

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

package com.example.googlehomeapisampleapp.viewmodel.hubs

import android.content.Intent
import android.util.Log
import com.google.home.ConnectivityState
import com.google.home.HomeException
import com.google.home.Structure
import com.google.home.annotation.HomeExperimentalApi
import com.google.home.annotation.HomeExperimentalGenericApi
import com.google.home.google.HubManagement
import com.google.home.google.HubManagementTrait
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

const val TAG = "HubDiscoveryViewModel"

//**
// * ViewModel responsible for the business logic of discovering and activating
// * Google Hub devices on the local network.
// */
class HubDiscoveryViewModel(
  structureFlow: Flow<Structure>,
  private val viewModelScope: CoroutineScope,
  private val errorsEmitter: MutableSharedFlow<Exception>,
  ioDispatcher: CoroutineDispatcher,
) {
  private val _hubActivationIntentFlow = MutableSharedFlow<Intent>()
  val hubActivationIntentFlow: SharedFlow<Intent> = _hubActivationIntentFlow

  private fun <T> Flow<T>.handleErrors(): Flow<T> = catch { e ->
    Log.e(
      TAG_HUB_DISCOVERY,
      "Caught exception that will be displayed to user: ${e.stackTraceToString()}",
    )
    errorsEmitter.emit(e as Exception)
  }

  @OptIn(ExperimentalCoroutinesApi::class, HomeExperimentalApi::class)
  val hubManagementTraitFlow = structureFlow.flatMapLatest { it.trait(HubManagement) }

  val discoveryStatus =
    hubManagementTraitFlow
      .map { it.discoveryStatus }
      .handleErrors()
      .flowOn(ioDispatcher)
      .stateIn(
        scope = CoroutineScope(viewModelScope.coroutineContext + ioDispatcher),
        started = SharingStarted.WhileSubscribed(),
        HubManagementTrait.DiscoveryStatus.DiscoveryStatusUnspecified
      )

  val discoveredHubs =
    hubManagementTraitFlow
      .map { it.discoveredHubsList }
      .handleErrors()
      .flowOn(ioDispatcher)
      .stateIn(
        scope = CoroutineScope(viewModelScope.coroutineContext + ioDispatcher),
        started = SharingStarted.WhileSubscribed(),
        listOf()
      )

  @OptIn(HomeExperimentalApi::class)
  fun activateHub(hub: HubManagementTrait.Hub) {
    viewModelScope.launch {
      withTimeoutOrNull(HUB_ACTIVATION_TIMEOUT) {
        val hubManagementTrait = getOnlineHubManagementTrait() ?: return@withTimeoutOrNull
        try {
          val hubActivationIntent: Intent = hubManagementTrait.activateHub(hub)
          _hubActivationIntentFlow.emit(hubActivationIntent)
        } catch (e: Exception) {
          Log.d(TAG_HUB_ACTIVATION, "Error activating hub $e")
          errorsEmitter.emit(e)
        }
      }
    }
  }

  @OptIn(HomeExperimentalApi::class, HomeExperimentalGenericApi::class)
  fun startDiscovery() {
    viewModelScope.launch {
      withTimeoutOrNull(HUB_DISCOVERY_TIMEOUT) {
        val hubManagementTrait = getOnlineHubManagementTrait() ?: return@withTimeoutOrNull
        try {
          val unused = hubManagementTrait.discoverAvailableHubs()
        } catch (e: Exception) {
          Log.d(TAG_HUB_DISCOVERY, "Error discovering hubs $e")
          errorsEmitter.emit(e)
        }
      }
    }
  }

  private suspend fun getOnlineHubManagementTrait(): HubManagement? {
    val hubManagementTrait =
      hubManagementTraitFlow.firstOrNull {
        Log.d(
          TAG,
          "HubManagementTrait connectivityState: ${it.metadata.sourceConnectivity?.connectivityState}",
        )
        it.metadata.sourceConnectivity?.connectivityState == ConnectivityState.ONLINE
      }
    if (hubManagementTrait == null) {
      errorsEmitter.emit(HomeException.notFound("HubManagement trait isn't online"))
    }
    return hubManagementTrait
  }

  companion object {
    private val HUB_ACTIVATION_TIMEOUT = 3.0.seconds
    private val HUB_DISCOVERY_TIMEOUT = 5.0.seconds
    private const val TAG_HUB_DISCOVERY = "HubDiscovery"
    private const val TAG_HUB_ACTIVATION = "HubActivation"
  }
}
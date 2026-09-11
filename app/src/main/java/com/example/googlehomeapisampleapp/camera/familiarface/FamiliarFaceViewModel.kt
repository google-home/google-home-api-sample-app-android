package com.example.googlehomeapisampleapp.camera.familiarface

import android.os.Bundle
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.home.HomeDevice
import com.google.home.HomeException
import com.google.home.Structure
import com.google.home.annotation.HomeExperimentalApi
import com.google.home.annotation.HomeExperimentalGenericApi
import com.google.home.google.AvStreamAnalysis
import com.google.home.google.AvStreamAnalysisTrait
import com.google.home.google.FaceLibrary
import com.google.home.google.FaceLibraryTrait
import com.google.home.google.FaceLibraryTrait.FaceCategories
import com.google.home.google.FaceLibraryTrait.FaceCategory.FaceCategoryKnown
import com.google.home.google.FaceLibraryTrait.FaceCategory.FaceCategoryNotAPerson
import com.google.home.google.FaceLibraryTrait.FaceCategory.FaceCategoryUnknown
import com.google.home.google.FaceLibraryTrait.FaceCategory.FaceCategoryUnlabeled
import com.google.home.FeatureConsentType
import com.example.googlehomeapisampleapp.AuthenticatedImageLoader
import com.example.googlehomeapisampleapp.HomeClientProvider
import coil3.ImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "FamiliarFace"

data class CameraFaceDetectionState(
    val deviceId: String,
    val deviceName: String,
    val roomName: String,
    val isFaceDetectionEnabled: Boolean
)

data class FaceLibraryState(
    val isLoading: Boolean = true,
    val isConsentGranted: Boolean = false,
    val library: Library = Library.Available(),
)

sealed interface Library {
    data class Available(
        val unlabeledFacesCount: Int = 0,
        val knownFaces: List<FaceLibraryTrait.Face> = emptyList(),
        val unlabeledFaces: List<FaceLibraryTrait.Face> = emptyList(),
        val unknownFaces: List<FaceLibraryTrait.Face> = emptyList(),
        val notAPersonFaces: List<FaceLibraryTrait.Face> = emptyList(),
    ) : Library

    data object Unavailable : Library
}

@OptIn(
    ExperimentalCoroutinesApi::class,
    HomeExperimentalApi::class,
    HomeExperimentalGenericApi::class,
)
@HiltViewModel
open class FamiliarFaceViewModel @Inject internal constructor(
    private val homeClientProvider: HomeClientProvider,
    @param:AuthenticatedImageLoader val authenticatedImageLoader: ImageLoader,
) : ViewModel() {
    private val lock = Mutex()
    private val _structureId = MutableStateFlow<String?>(null)
    fun setStructureId(id: String) {
        _structureId.value = id
    }
    private val structureFlow: SharedFlow<Structure> =
        _structureId
            .filterNotNull()
            .flatMapLatest { structureIdStr ->
                flowOf(homeClientProvider.getClient())
                    .flatMapLatest { client ->
                        client.structures().map { structureSet ->
                            structureSet.firstOrNull { it.id.id == structureIdStr }
                        }.filterNotNull()
                    }
            }
            .onEach { Log.d(TAG, "Found relevant structure") }
            .shareIn(scope = viewModelScope, started = SharingStarted.Lazily, replay = 1)

    private val _state = MutableStateFlow<FaceLibraryState>(FaceLibraryState())
    val state: StateFlow<FaceLibraryState> =
        _state
            .onSubscription { refresh() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = FaceLibraryState(isLoading = true),
            )

    fun refresh() {
        viewModelScope.launchWithLock(lock) { refreshInternal() }
    }

    private suspend fun HomeDevice.getAvStreamAnalysisTrait(): AvStreamAnalysis? {
        return types().first().firstNotNullOfOrNull { it.trait(AvStreamAnalysis) }
    }

    private fun HomeDevice.cameraStateFlow(): Flow<CameraFaceDetectionState> {
        return types().flatMapLatest { typeSet ->
            val typeWithTrait = typeSet.firstOrNull { it.trait(AvStreamAnalysis) != null }
            if (typeWithTrait != null) {
                type(typeWithTrait.factory).map { updatedType ->
                    val trait = updatedType.trait(AvStreamAnalysis)
                    val triggers = trait?.enabledEventTriggers ?: emptyList()
                    val isEnabled = triggers.contains(AvStreamAnalysisTrait.EventTriggerTypeEnum.Face)
                    CameraFaceDetectionState(
                        deviceId = id.id,
                        deviceName = name ?: "Unknown Camera",
                        roomName = room()?.name ?: "Unknown Room",
                        isFaceDetectionEnabled = isEnabled
                    )
                }
            } else {
                flowOf(
                    CameraFaceDetectionState(
                        deviceId = id.id,
                        deviceName = name ?: "Unknown Camera",
                        roomName = room()?.name ?: "Unknown Room",
                        isFaceDetectionEnabled = false
                    )
                )
            }
        }
    }

    val cameraStates: StateFlow<List<CameraFaceDetectionState>> = structureFlow
        .flatMapLatest { structure ->
            structure.devices().flatMapLatest { deviceSet ->
                val cameras = deviceSet.filter { it.has(AvStreamAnalysis) }
                if (cameras.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(cameras.map { it.cameraStateFlow() }) { statesArray ->
                        statesArray.toList()
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Updates the Face trigger state (enable/disable) based on the toggle switch operation.
     */
    fun toggleFaceDetection(deviceId: String, enable: Boolean) {
        viewModelScope.launchWithLock(lock) {
            val structure = structureFlow.first()
            val devices = structure.devices().first()
            val device = devices.find { it.id.id == deviceId }

            if (device == null || !device.has(AvStreamAnalysis)) {
                Log.w(TAG, "Device not found or does not support AvStreamAnalysis")
                return@launchWithLock
            }

            try {
                val trait = device.getAvStreamAnalysisTrait()
                if (trait != null) {
                    val status = if (enable) {
                        AvStreamAnalysisTrait.EnablementStatusEnum.Enabled
                    } else {
                        AvStreamAnalysisTrait.EnablementStatusEnum.Disabled
                    }
                    val enablement = AvStreamAnalysisTrait.EventTriggerEnablement(
                        AvStreamAnalysisTrait.EventTriggerTypeEnum.Face,
                        status
                    )
                    trait.setOrUpdateEventDetectionTriggers(listOf(enablement))
                }
            } catch (e: HomeException) {
                Log.e(TAG, "Failed to update event detection triggers for $deviceId", e)
            }
        }
    }
    // Safely retrieve the trait. Returns null if the trait is missing instead of hanging forever.
    private suspend fun getFaceLibraryTraitOrNull(): FaceLibrary? {
        val structure = structureFlow.first()
        if (!structure.has(FaceLibrary)) {
            Log.w(TAG, "Structure does not have FaceLibrary trait.")
            return null
        }
        return structure.trait(FaceLibrary).first()
    }

    private suspend fun refreshInternal() {
        if (!_state.value.isConsentGranted) {
            _state.update { oldState ->
                oldState.copy(isLoading = false, library = Library.Unavailable)
            }
            return
        }

        _state.update { oldState -> oldState.copy(isLoading = true) }
        Log.i(TAG, "Refreshing faces...")

        val trait = getFaceLibraryTraitOrNull()
        if (trait == null) {
            // Handle missing trait gracefully
            _state.update { it.copy(isLoading = false, library = Library.Unavailable) }
            return
        }

        if (trait.faceLibraryStatus != FaceLibraryTrait.FaceLibraryStatus.FaceLibraryStatusAvailable) {
            Log.w(TAG, "Could not call getFaces, the face library is unavailable.")
            _state.update { it.copy(isLoading = false, library = Library.Unavailable) }
            return
        }

        try {
            val faces =
                trait.getFaces(
                    faceCategories =
                        FaceCategories(
                            listOf(
                                FaceCategoryKnown,
                                FaceCategoryUnlabeled,
                                FaceCategoryNotAPerson,
                                FaceCategoryUnknown,
                            )
                        )
                )
            _state.update { oldState ->
                oldState.copy(
                    library =
                        Library.Available(
                            knownFaces = faces.filter { it.category == FaceCategoryKnown },
                            unlabeledFaces = faces.filter { it.category == FaceCategoryUnlabeled },
                            unknownFaces = faces.filter { it.category == FaceCategoryUnknown },
                            notAPersonFaces = faces.filter { it.category == FaceCategoryNotAPerson },
                            unlabeledFacesCount = faces.count { it.category == FaceCategoryUnlabeled },
                        )
                )
            }
        } catch (e: HomeException) {
            Log.e(TAG, "Failed to get faces", e)
        } finally {
            // Ensure isLoading is disabled regardless of success or failure
            _state.update { oldState -> oldState.copy(isLoading = false) }
        }
    }

    fun checkAndRequestConsent() {
        viewModelScope.launchWithLock(lock) {
            val structureIdStr = _structureId.value
            if (structureIdStr == null) {
                Log.w(TAG, "Structure ID is not set yet.")
                return@launchWithLock
            }

            _state.update { it.copy(isLoading = true) }

            try {
                val client = homeClientProvider.getClient()
                val result = client.updateFeatureConsent(
                    features = listOf(FeatureConsentType("FEATURE_FACE_LIBRARY", 1)),
                    structureId = structureIdStr
                )

                if (result.granted) {
                    Log.i(TAG, "Feature consent granted or already active.")
                    _state.update { it.copy(isConsentGranted = true) }
                    refreshInternal()
                } else {
                    Log.i(TAG, "Feature consent flow cancelled or denied.")
                    _state.update { it.copy(isConsentGranted = false, isLoading = false) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting feature consent flow", e)
                _state.update { it.copy(isConsentGranted = false, isLoading = false) }
            }
        }
    }

    fun merge(faceIds: List<FaceId>, targetFaceId: FaceId) {
        viewModelScope.launchWithLock(lock) {
            val trait = getFaceLibraryTraitOrNull() ?: return@launchWithLock
            try {
                trait.mergeFaces(mergedFaceId = targetFaceId.value, faceIdsToMergeList = faceIds.map { it.value })
            } catch (e: HomeException) {
                Log.e(TAG, "Failed to merge faces", e)
            }
            refreshInternal()
        }
    }

    fun removeFaces(faceIds: List<FaceId>) {
        viewModelScope.launchWithLock(lock) {
            val trait = getFaceLibraryTraitOrNull() ?: return@launchWithLock
            try {
                trait.removeFaces(faceIds.map { it.value })
            } catch (e: HomeException) {
                Log.e(TAG, "Failed to remove faces", e)
            }
            refreshInternal()
        }
    }

    fun labelFace(faceId: FaceId, category: FaceLibraryTrait.FaceCategory, name: String? = null) {
        viewModelScope.launchWithLock(lock) {
            val trait = getFaceLibraryTraitOrNull() ?: return@launchWithLock
            try {
                trait.updateFace(id = faceId.value, category = category, Name = name)
            } catch (e: HomeException) {
                Log.e(TAG, "Failed to label face", e)
            }
            refreshInternal()
        }
    }

    fun clearLibrary() {
        viewModelScope.launchWithLock(lock) {
            val trait = getFaceLibraryTraitOrNull() ?: return@launchWithLock
            try {
                trait.removeLibrary()
            } catch (e: HomeException) {
                Log.e(TAG, "Failed to clear library", e)
            }
            refreshInternal()
        }
    }

    @CanIgnoreReturnValue
    private fun CoroutineScope.launchWithLock(mutex: Mutex, f: suspend () -> (Unit)) = launch {
        mutex.withLock { f() }
    }
}

@JvmInline
value class FaceId(val value: String)
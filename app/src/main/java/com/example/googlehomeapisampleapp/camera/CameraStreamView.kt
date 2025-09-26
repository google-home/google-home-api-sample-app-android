package com.example.googlehomeapisampleapp.camera

import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * View for displaying the camera stream and controls.
 *
 * @param deviceId The ID of the camera device.
 * @param paddingValues The padding values to apply to the view.
 * @param onShowSnackbar Callback to show a snackbar.
 * @param viewModel The view model for the camera stream.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraStreamView(
    deviceId: String,
    paddingValues: PaddingValues,
    onShowSnackbar: (String) -> Unit,
    viewModel: CameraStreamViewModel = hiltViewModel(), // Get ViewModel instance
) {
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onPause() }

    LaunchedEffect(deviceId) {
        viewModel.initialize(deviceId)
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            onShowSnackbar(it)
            viewModel.errorShown()
        }
    }

    val currentViewModel by rememberUpdatedState(viewModel)
    DisposableEffect(currentViewModel) {
        onDispose {
            // When the CameraStreamView is permanently disposed (e.g., navigating away),
            // call onPause() to trigger the stopPlayer/dispose cleanup logic in the ViewModel.
            currentViewModel.onPause()
        }
    }

    Scaffold(
        modifier =
            Modifier.padding(paddingValues = paddingValues).fillMaxSize().testTag("CameraStreamScreen"),
        floatingActionButton = {
            FloatingActionButton(onClick = { showBottomSheet = true }, shape = CircleShape) {
                Icon(imageVector = Icons.Filled.Menu, contentDescription = "Open Menu")
            }
        },
    ) { paddingValues ->
        val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
        val supportsTalkback by viewModel.supportsTalkback.collectAsStateWithLifecycle()
        val isToggleRecordingInProgress by
        viewModel.isToggleRecordingInProgress.collectAsStateWithLifecycle()
        val isToggleTalkbackInProgress by
        viewModel.isToggleTalkbackInProgress.collectAsStateWithLifecycle()
        val playerState by viewModel.state.collectAsStateWithLifecycle()
        val isCurrentlyStreaming by
        remember(playerState, isToggleRecordingInProgress) {
            mutableStateOf(
                (playerState == CameraStreamState.STREAMING_WITH_TALKBACK ||
                        playerState == CameraStreamState.STREAMING_WITHOUT_TALKBACK) &&
                        !isToggleRecordingInProgress
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            CameraStreamPlayer(
                playerState = playerState,
                onSurfaceCreated = { surface: Surface -> viewModel.onSurfaceCreated(surface) },
                onSurfaceDestroyed = { viewModel.onSurfaceDestroyed() },
                isCurrentlyStreaming = isCurrentlyStreaming,
            )
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                val isTalkbackEnabled by
                remember(playerState) {
                    mutableStateOf(playerState == CameraStreamState.STREAMING_WITH_TALKBACK)
                }
                val isCameraToggleable =
                    !isToggleRecordingInProgress &&
                            playerState != CameraStreamState.ERROR &&
                            playerState != CameraStreamState.NOT_STARTED

                Column(modifier = Modifier.fillMaxWidth()) {
                    if (supportsTalkback) {
                        ListItem(
                            headlineContent = { Text(text = "Microphone") },
                            supportingContent = { Text(text = if (isTalkbackEnabled) "On" else "Off") },
                            trailingContent = {
                                Switch(
                                    checked = isTalkbackEnabled,
                                    onCheckedChange = { isEnabled -> viewModel.setTalkback(isEnabled) },
                                    enabled =
                                        !isToggleTalkbackInProgress &&
                                                !isToggleRecordingInProgress &&
                                                isCurrentlyStreaming,
                                )
                            },
                        )
                    }

                    ListItem(
                        headlineContent = { Text(text = "Camera") },
                        supportingContent = { Text(text = if (isRecording) "On" else "Off") },
                        trailingContent = {
                            Switch(
                                checked = isRecording,
                                onCheckedChange = { viewModel.setRecording(it) },
                                enabled = isCameraToggleable,
                            )
                        },
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ListItem(
                        headlineContent = { Text(text = "Settings") },
                        modifier = Modifier.clickable { onShowSnackbar("Settings not implemented") },
                    )
                    ListItem(
                        headlineContent = { Text(text = "History") },
                        modifier = Modifier.clickable { onShowSnackbar("History not implemented") },
                    )
                }
            }
        }
    }
}

@Composable
fun CameraStreamPlayer(
    playerState: CameraStreamState,
    onSurfaceCreated: (surface: Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    isCurrentlyStreaming: Boolean,
) {
    val context = LocalContext.current.applicationContext
    val currentOnSurfaceCreated by rememberUpdatedState(onSurfaceCreated)
    val currentOnSurfaceDestroyed by rememberUpdatedState(onSurfaceDestroyed)


    val callback =
        remember {
            object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    Log.d("CameraStreamPlayer", "surfaceCreated")
                    currentOnSurfaceCreated(holder.surface)
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    Log.d("CameraStreamPlayer", "surfaceDestroyed")
                    currentOnSurfaceDestroyed()
                }
            }
        }

    val surfaceView: SurfaceView = remember {
        SurfaceView(context).apply { holder.addCallback(callback) }
    }

    if (isCurrentlyStreaming) {
        surfaceView.visibility = View.VISIBLE
    } else {
        surfaceView.visibility = View.INVISIBLE
    }

    Box(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Color.Black)) {
        AndroidView(factory = { surfaceView }, modifier = Modifier.fillMaxSize())

        if (playerState == CameraStreamState.ERROR) {
            Text(
                text = "Error starting camera stream. Please check your connection and try again.",
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        } else if (playerState == CameraStreamState.READY_OFF) {
            Text(text = "Camera is Off", modifier = Modifier.align(Alignment.Center), color = Color.White)
        } else if (!isCurrentlyStreaming) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
        }
    }
}
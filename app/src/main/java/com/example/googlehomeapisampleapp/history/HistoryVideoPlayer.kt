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
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.DefaultDashChunkSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage

private const val TAG = "HistoryVideoPlayer"

/** Playback state for [HistoryVideoPlayer]. */
enum class VideoPlayerState {
    IDLE,
    LOADING,
    PLAYING,
    ENDED,  // Distinct state for when the clip finishes playing
    ERROR,
}

/**
 * Composable that displays the appropriate media for a camera history event:
 *
 * - **Video** (MP4, DASH, HLS): Plays the clip with audio via ExoPlayer.
 * - **Still Image** (previewUrl or thumbnailUrl): Shows the snapshot image.
 * - **No media**: Shows a placeholder icon.
 *
 * Playback order for video: MP4 (primary) → DASH (fallback) → HLS (final fallback).
 * MP4 is preferred because Nest DASH/HLS streams embed short-lived tokens in segment
 * URLs that can expire before ExoPlayer fetches them.
 *
 * @param mediaUrl The [MediaUrl] for this event, containing all available media URLs.
 * @param modifier Optional modifier for the outer container.
 */
@OptIn(UnstableApi::class)
@Composable
fun HistoryVideoPlayer(
    mediaUrl: MediaUrl,
    modifier: Modifier = Modifier,
    viewModel: HistoryPlayerViewModel = hiltViewModel(),
) {
    val hasVideo = mediaUrl.hasVideo
    val imageUrl = mediaUrl.previewUrl.ifBlank { mediaUrl.thumbnailUrl }
    val hasImage = imageUrl.isNotBlank()

    val isAuthGranted by viewModel.isAuthGranted.collectAsStateWithLifecycle()
    val pendingAuthIntent by viewModel.pendingAuthIntent.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        Log.i(TAG, "OAuth resolution completed. Result code: ${result.resultCode}")
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.handleAuthResult(result.data)
        } else {
            viewModel.onAuthFailed()
        }
    }

    LaunchedEffect(pendingAuthIntent) {
        pendingAuthIntent?.let {
            authLauncher.launch(IntentSenderRequest.Builder(it).build())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkAndRequestAuth()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (authError != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = authError ?: "Unknown error",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Retry authentication",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .size(32.dp)
                        .clickable { viewModel.checkAndRequestAuth() },
                )
            }
        } else if (!isAuthGranted) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp),
            )
        } else {
            when {
                // No media available
                !hasVideo && !hasImage -> {
                    Icon(
                        imageVector = Icons.Outlined.Videocam,
                        contentDescription = "No media available",
                        modifier = Modifier.size(64.dp),
                        tint = Color.DarkGray,
                    )
                }

                // Still image — show snapshot captured in Still Images recording mode
                !hasVideo && hasImage -> {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Event snapshot",
                        imageLoader = viewModel.cameraMediaAuth.imageLoader,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        onError = { state ->
                            Log.w(TAG, "AsyncImage load error for $imageUrl", state.result.throwable)
                        }
                    )
                    Text(
                        text = "Still Image",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Video available — play with audio
                else -> {
                    VideoPlayerContent(
                        mediaUrl = mediaUrl,
                        cameraMediaAuth = viewModel.cameraMediaAuth,
                    )
                }
            }
        }
    }
}

/**
 * Internal composable that handles video playback with full audio support.
 * Manages ExoPlayer lifecycle, fallback URL logic, and buffering state.
 */
@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerContent(
    mediaUrl: MediaUrl,
    cameraMediaAuth: CameraMediaAuth,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentMediaUrl by rememberUpdatedState(mediaUrl)

    var playerState by remember { mutableStateOf(VideoPlayerState.IDLE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Tracks which fallback we are currently on
    var usingDash by remember { mutableStateOf(false) }
    var usingHls by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            // Configure audio attributes so the system grants audio focus
            // and routes audio through the correct output stream.
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            volume = 1f
            playWhenReady = false
        }
    }

    val (dusiAuthenticatedDataSourceFactory, chunkDataSourceFactory) = remember(cameraMediaAuth, mediaUrl) {
        cameraMediaAuth.createDataSourceFactories()
    }

    fun loadMediaSource(url: String, mimeType: String) {
        val mediaItem = MediaItem.Builder()
            .setUri(url.toUri())
            .setMimeType(mimeType)
            .build()

        val mediaSource = when (mimeType) {
            MimeTypes.APPLICATION_MPD -> DashMediaSource.Factory(
                DefaultDashChunkSource.Factory(chunkDataSourceFactory),
                dusiAuthenticatedDataSourceFactory
            ).createMediaSource(mediaItem)
            MimeTypes.APPLICATION_M3U8 -> HlsMediaSource.Factory(dusiAuthenticatedDataSourceFactory)
                .createMediaSource(mediaItem)
            else -> ProgressiveMediaSource.Factory(dusiAuthenticatedDataSourceFactory)
                .createMediaSource(mediaItem)
        }

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
    }

    /**
     * Selects the best available URL, resets fallback flags, and starts playback.
     * Called from both LaunchedEffect (initial load) and the retry button.
     * Uses rememberUpdatedState refs to ensure latest URL values are always used.
     */
    fun startPlayback() {
        usingDash = false
        usingHls = false
        exoPlayer.clearMediaItems()
        playerState = VideoPlayerState.LOADING
        errorMessage = null

        when {
            currentMediaUrl.mp4DownloadUrl.isNotBlank() ->
                loadMediaSource(currentMediaUrl.mp4DownloadUrl, MimeTypes.VIDEO_MP4)
            currentMediaUrl.dashManifestUrl.isNotBlank() -> {
                usingDash = true
                loadMediaSource(currentMediaUrl.dashManifestUrl, MimeTypes.APPLICATION_MPD)
            }
            currentMediaUrl.hlsMasterPlaylistUrl.isNotBlank() -> {
                usingHls = true
                loadMediaSource(currentMediaUrl.hlsMasterPlaylistUrl, MimeTypes.APPLICATION_M3U8)
            }
            else -> playerState = VideoPlayerState.IDLE
        }
    }

    // Player state listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                playerState = when (playbackState) {
                    Player.STATE_BUFFERING -> VideoPlayerState.LOADING
                    Player.STATE_READY -> VideoPlayerState.PLAYING
                    Player.STATE_ENDED -> VideoPlayerState.ENDED
                    Player.STATE_IDLE -> VideoPlayerState.IDLE
                    else -> playerState
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Attempt fallback URLs in order: MP4 → DASH → HLS
                when {
                    !usingDash && !usingHls && currentMediaUrl.dashManifestUrl.isNotBlank() -> {
                        usingDash = true
                        playerState = VideoPlayerState.LOADING
                        errorMessage = null
                        loadMediaSource(currentMediaUrl.dashManifestUrl, MimeTypes.APPLICATION_MPD)
                    }
                    !usingDash && !usingHls && currentMediaUrl.hlsMasterPlaylistUrl.isNotBlank() -> {
                        usingHls = true
                        playerState = VideoPlayerState.LOADING
                        errorMessage = null
                        loadMediaSource(currentMediaUrl.hlsMasterPlaylistUrl, MimeTypes.APPLICATION_M3U8)
                    }
                    usingDash && !usingHls && currentMediaUrl.hlsMasterPlaylistUrl.isNotBlank() -> {
                        usingDash = false
                        usingHls = true
                        playerState = VideoPlayerState.LOADING
                        errorMessage = null
                        loadMediaSource(currentMediaUrl.hlsMasterPlaylistUrl, MimeTypes.APPLICATION_M3U8)
                    }
                    else -> {
                        playerState = VideoPlayerState.ERROR
                        errorMessage = error.localizedMessage ?: "Playback error"
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(mediaUrl) {
        startPlayback()
    }

    // Pause/resume with Activity lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Release player when leaving the composition tree
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    if (playerState == VideoPlayerState.ERROR) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = errorMessage ?: "Unable to play video",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "Retry playback",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .size(32.dp)
                    .clickable { startPlayback() },
            )
        }
    } else {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    controllerAutoShow = true
                    controllerShowTimeoutMs = 3000
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { playerView -> playerView.player = exoPlayer },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = playerState == VideoPlayerState.LOADING,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

/**
 * Full-screen media viewer shown when a user taps a camera history event.
 * Displays video with audio for clip events, or a still image for snapshot events.
 */
@Composable
fun HistoryVideoPlayerScreen(
    event: HistoryUiDataModel.CameraEvent,
    onBack: () -> Unit,
) {
    BackHandler(enabled = true) { onBack() }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column {
            // Top bar with back arrow and event info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onBack() },
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = event.eventType.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = event.displaySubtitle,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // Video player — MP4 primary, DASH/HLS as fallbacks
            HistoryVideoPlayer(
                mediaUrl = event.mediaUrl,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
package com.example.googlehomeapisampleapp.camera.livestreamplayer

import android.view.Surface
import kotlinx.coroutines.flow.Flow

/**
 * Interface for a live stream player that can be rendered on a [Surface].
 *
 */
interface LiveStreamPlayer {

    /** Whether the player supports talkback. */
    val supportsTalkback: Boolean

    /** Whether talkback is enabled. */
    val isTalkbackEnabled: Flow<Boolean>

    /** The state of the player. */
    val state: Flow<LiveStreamPlayerState>

    /**
     * Toggles talkback on or off.
     *
     * @param enabled Whether to enable or disable talkback.
     */
    suspend fun toggleTalkback(enabled: Boolean)

    /** Starts the player. */
    suspend fun start()

    /**
     * Attaches the player to a [Surface] to render the video stream.
     *
     * @param renderTarget The [Surface] to render the video stream on.
     */
    fun attachRenderer(renderTarget: Surface)

    /** Detaches the player from the [Surface]. */
    fun detachRenderer()

    /** Disposes of the player and releases all resources. */
    suspend fun dispose()
}

enum class LiveStreamPlayerState {
    NOT_STARTED,
    READY,
    STARTING,
    STREAMING,
    STOPPING,
    DISPOSED,
}
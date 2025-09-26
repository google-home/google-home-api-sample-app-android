package com.example.googlehomeapisampleapp.camera.livestreamplayer

import com.google.home.HomeDevice
import kotlinx.coroutines.CoroutineScope

/** Interface for a factory class for creating live stream players based on the available traits. */
interface LiveStreamPlayerFactory {
    /**
     * Creates a [LiveStreamPlayer] from a [HomeDevice].
     *
     * @param device The device to create stream for
     * @param scope The [CoroutineScope] to use for the player.
     * @return The created [LiveStreamPlayer], or null if creation fails.
     */
    suspend fun createPlayerFromDevice(device: HomeDevice, scope: CoroutineScope): LiveStreamPlayer?
}
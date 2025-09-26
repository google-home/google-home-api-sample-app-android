package com.example.googlehomeapisampleapp.camera.livestreamplayer

import kotlinx.coroutines.flow.Flow

/** Interface for managing talkback functionality. */
interface TalkbackController {

    /** Whether talkback is enabled. */
    val isTalkbackEnabled: Flow<Boolean>

    /**
     * Toggles the microphone on or off.
     *
     * @param enabled Whether to enable or disable the microphone.
     */
    suspend fun toggleTalkback(enabled: Boolean)
}
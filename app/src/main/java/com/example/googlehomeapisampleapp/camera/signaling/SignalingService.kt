package com.example.googlehomeapisampleapp.camera.signaling

/**
 * Interface for a signaling service that can be used to send and receive SDP offers and answers.
 * This is a stateful service that should be instantiated per live stream.
 */
interface SignalingService {

    /**
     * Data class for an SDP offer or answer.
     *
     * @property rawSdp The raw SDP string.
     */
    data class Sdp(val rawSdp: String)

    /** Sealed interface for the response to a sendOffer request. */
    sealed interface SendOfferResponse {
        /**
         * The response was an offer.
         *
         * @property sdp The SDP offer.
         */
        data class Offer(val sdp: Sdp) : SendOfferResponse

        /**
         * The response was an answer.
         *
         * @property sdp The SDP answer.
         */
        data class Answer(val sdp: Sdp) : SendOfferResponse

        /**
         * The request failed.
         *
         * @property error The error that occurred.
         */
        data class Error(val errorMessage: String) : SendOfferResponse
    }

    /** Sealed interface for the response to a sendAnswer request. */
    sealed interface SendAnswerResponse {
        /** The request was successful. */
        data object Ok : SendAnswerResponse

        /**
         * The request failed.
         *
         * @property error The error that occurred.
         */
        data class Error(val errorMessage: String) : SendAnswerResponse
    }

    /**
     * Sends an SDP offer to the signaling service.
     *
     * @param sdpOffer The SDP offer to send.
     * @return The response from the signaling service.
     */
    suspend fun sendOffer(sdpOffer: Sdp): SendOfferResponse

    /**
     * Sends an SDP answer to the signaling service.
     *
     * @param sdpAnswer The SDP answer to send.
     * @return The response from the signaling service.
     */
    suspend fun sendAnswer(sdpAnswer: String): SendAnswerResponse

    /**
     * Configures talkback on or off.
     *
     * @param enabled Whether to enable or disable talkback.
     * @return Whether talkback was configured successfully.
     */
    suspend fun configureTalkback(enabled: Boolean): Boolean

    /** Disposes of the signaling service and releases all resources. */
    suspend fun dispose() {}
}
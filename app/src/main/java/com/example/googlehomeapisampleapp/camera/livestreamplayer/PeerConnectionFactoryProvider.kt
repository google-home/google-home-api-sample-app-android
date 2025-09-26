package com.example.googlehomeapisampleapp.camera.livestreamplayer

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule
import javax.inject.Inject
import javax.inject.Singleton

/** Provides a singleton instance of [PeerConnectionFactory]. */
@Singleton
class PeerConnectionFactoryProvider
@Inject
internal constructor(@ApplicationContext private val context: Context) {
    private val eglBase = EglBase.create()

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var audioDeviceModule: JavaAudioDeviceModule

    init {
        initializeFactory()
    }

    private fun initializeFactory() {
        Log.d(TAG, "Initializing PeerConnectionFactory")
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setNativeLibraryName(NATIVE_LIBRARY_NAME)
                .createInitializationOptions()
        )

        audioDeviceModule = JavaAudioDeviceModule.builder(context).createAudioDeviceModule()
        val videoDecoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        val videoEncoderFactory =
            DefaultVideoEncoderFactory(
                eglBase.eglBaseContext,
                /* enableIntelVp8Encoder= */ false,
                /* enableH264HighProfile= */ true,
            )

        peerConnectionFactory =
            PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoDecoderFactory(videoDecoderFactory)
                .setVideoEncoderFactory(videoEncoderFactory)
                .createPeerConnectionFactory()
    }

    /** Returns the singleton instance of [PeerConnectionFactory]. */
    fun getPeerConnectionFactory(): PeerConnectionFactory {
        return peerConnectionFactory
    }

    /** Returns the singleton instance of [EglBase.Context]. */
    fun getEglBaseContext(): EglBase.Context {
        return eglBase.eglBaseContext
    }

    /** Returns the singleton instance of [JavaAudioDeviceModule]. */
    fun getAudioDeviceModule(): JavaAudioDeviceModule {
        return audioDeviceModule
    }

    companion object {
        private const val TAG = "PeerConnectionFactoryProvider"
        private const val NATIVE_LIBRARY_NAME = "jingle_peerconnection_so"

        init {
            try {
                System.loadLibrary(NATIVE_LIBRARY_NAME)
                Log.d(TAG, "Loaded native library: $NATIVE_LIBRARY_NAME")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library: $NATIVE_LIBRARY_NAME", e)
            }
        }
    }
}
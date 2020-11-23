package com.connect.club.jitsiclient.webrtc.jitsi

import android.content.Context
import org.webrtc.*

class RemoteParticipant(val stream: MediaStream?) {

}

class Room(
    val name: String,
    private val peerConnection: PeerConnection?
) {

    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onConnectionFailed()
        fun onRemoteParticipantConnected(remoteParticipant: RemoteParticipant)
    }

    fun publishAudio(track: AudioTrack) {
        peerConnection?.addTrack(track, listOf(name))
    }

    fun publishVideo(track: VideoTrack) {
        peerConnection?.addTrack(track, listOf(name))
    }

    fun release() {
        peerConnection?.dispose()
    }
}

private abstract class SimplePeerConnectionObserver: PeerConnection.Observer {
    override fun onIceCandidate(iceCandidate: IceCandidate?) {

    }

    override fun onDataChannel(dataChannel: DataChannel?) {

    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {

    }

    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {

    }

    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {

    }

    override fun onAddStream(stream: MediaStream?) {

    }

    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {

    }

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {

    }

    override fun onRemoveStream(stream: MediaStream?) {

    }

    override fun onRenegotiationNeeded() {

    }

    override fun onAddTrack(p0: RtpReceiver?, streams: Array<out MediaStream>?) {

    }
}

class WebRtcClient(private val context: Context) {

    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private val eglBase = EglBase.create()
    private val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)

    init {
        initPeerConnectionFactory(eglBase.eglBaseContext)
    }

    private fun initPeerConnectionFactory(eglBaseContext: EglBase.Context) {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(context)
                .createInitializationOptions()
        )

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBaseContext, true, false))
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = true
            })
            .createPeerConnectionFactory()
    }

    fun createAudio(name: String): AudioTrack {
        val source = peerConnectionFactory.createAudioSource(MediaConstraints())
        return peerConnectionFactory.createAudioTrack(name, source)
    }

    fun createVideo(name: String, cameraCapturer: CameraVideoCapturer): VideoTrack {
        val videoSource = peerConnectionFactory.createVideoSource(cameraCapturer.isScreencast)
        cameraCapturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)

        return peerConnectionFactory.createVideoTrack(name, videoSource)
    }

    fun createRoom(name: String, listener: Room.Listener): Room {
        val peerConnection = peerConnectionFactory.createPeerConnection(
            PeerConnection.RTCConfiguration(emptyList()).apply {
                keyType = PeerConnection.KeyType.ECDSA
                enableDtlsSrtp = true
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            },
            roomListenerProxy(listener)
        )

        return Room(name, peerConnection)
    }

    private fun roomListenerProxy(listener: Room.Listener): SimplePeerConnectionObserver {
        return object: SimplePeerConnectionObserver() {
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                super.onConnectionChange(newState)
                when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> listener.onConnected()
                    PeerConnection.PeerConnectionState.DISCONNECTED -> listener.onDisconnected()
                    PeerConnection.PeerConnectionState.FAILED -> listener.onConnectionFailed()
                    else -> {}
                }
            }

            override fun onAddStream(stream: MediaStream?) {
                super.onAddStream(stream)
                listener.onRemoteParticipantConnected(
                    RemoteParticipant(stream)
                )
            }
        }
    }

    fun release() {
        surfaceTextureHelper.dispose()
        eglBase.release()
        peerConnectionFactory.dispose()
    }
}


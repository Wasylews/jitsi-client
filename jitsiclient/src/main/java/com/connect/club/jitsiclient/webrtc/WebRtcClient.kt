package com.connect.club.jitsiclient.webrtc

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.connect.club.jitsiclient.ViewFactory
import com.google.gson.Gson
import kotlinx.android.synthetic.main.view_jitsi.view.*
import org.webrtc.*
import org.webrtc.voiceengine.WebRtcAudioUtils
import java.nio.ByteBuffer


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

private abstract class SimpleSdpObserver: SdpObserver {
    override fun onSetFailure(p0: String?) {

    }

    override fun onSetSuccess() {

    }

    override fun onCreateSuccess(sdp: SessionDescription?) {

    }

    override fun onCreateFailure(p0: String?) {

    }
}

private abstract class SimpleDataChannelObserver: DataChannel.Observer {

    override fun onMessage(buffer: DataChannel.Buffer?) {
        buffer?.let {
            val data = it.data
            val bytes = ByteArray(data.remaining())
            data.get(bytes)
            onMessage(String(bytes))
        }
    }

    abstract fun onMessage(message: String)

    override fun onBufferedAmountChange(p0: Long) {
    }

    override fun onStateChange() {
    }
}

private data class PinnedEndpointsChangedEvent(
    val colibriClass: String = "PinnedEndpointsChangedEvent",
    val pinnedEndpoints: List<String>
)

private data class DataChannelEvent(
    val colibriClass: String,
    val active: Boolean,
    val endpoint: String
)

class WebRtcClient(
    private val context: Context,
    eglBaseContext: EglBase.Context,
    viewFactory: ViewFactory
) {

    companion object {
        const val TAG = "WebRtcClient"
    }

    /**
     * Peer connection events.
     */
    interface PeerConnectionEvents {
        /**
         * Callback fired once local SDP is created and set.
         */
        fun onLocalDescription(sdp: SessionDescription?)

        /**
         * Callback fired once remote SDP needs to be updated.
         */
        fun onRenegotiationNeeded(sdp: SessionDescription?)

        /**
         * Callback fired once local Ice candidate is generated.
         */
        fun onIceCandidate(candidate: IceCandidate?)

        /**
         * Callback fired once local ICE candidates are removed.
         */
        fun onIceCandidatesRemoved(candidates: Array<IceCandidate?>?)

        /**
         * Callback fired once connection is established (IceConnectionState is
         * CONNECTED).
         */
        fun onIceConnected()

        /**
         * Callback fired once connection is disconnected (IceConnectionState is
         * DISCONNECTED).
         */
        fun onIceDisconnected()

        /**
         * Callback fired once DTLS connection is established (PeerConnectionState
         * is CONNECTED).
         */
        fun onConnected()

        /**
         * Callback fired once DTLS connection is disconnected (PeerConnectionState
         * is DISCONNECTED).
         */
        fun onDisconnected()

        /**
         * Callback fired once peer connection is closed.
         */
        fun onPeerConnectionClosed()

        /**
         * Callback fired once peer connection statistics is ready.
         */
        fun onPeerConnectionStatsReady(reports: Array<StatsReport?>?)

        /**
         * Callback fired once peer connection error happened.
         */
        fun onPeerConnectionError(description: String?)

        /**
         * Callback fired once new stream added
         */
        fun onAddStream(stream: MediaStream)

        /**
         * Callback fired once stream removed
         */
        fun onRemoveStream(streamId: String)
    }

    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private lateinit var audioManager: AppRTCAudioManager
    private lateinit var videoTrack: VideoTrack
    private lateinit var audioTrack: AudioTrack
    private var eventListener: PeerConnectionEvents? = null
    var iceServers = listOf<PeerConnection.IceServer>()
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
    private val videoCapturer = createCameraCapturer()
    private val endpointSet = mutableSetOf<String>()
    private val streamList = listOf("ARDAMS")

    init
    {
        initPeerConnectionFactory(eglBaseContext)
        initLocalVideoTrack(viewFactory)
        initLocalAudioTrack()
    }

    private fun initLocalAudioTrack() {
        audioManager = AppRTCAudioManager.create(context)
        audioManager.start(object :
            AppRTCAudioManager.AudioManagerEvents {
            override fun onAudioDeviceChanged(
                selectedAudioDevice: AppRTCAudioManager.AudioDevice?,
                availableAudioDevices: Set<AppRTCAudioManager.AudioDevice>?
            ) {
                Log.d(
                    TAG, "onAudioManagerDevicesChanged: " + availableAudioDevices + ", "
                            + "selected: " + selectedAudioDevice
                )
            }
        })

        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        audioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource)

        WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true)
        WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(true)
        WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(true)
    }

    private fun initLocalVideoTrack(viewFactory: ViewFactory) {
        val videoSource = peerConnectionFactory.createVideoSource(videoCapturer?.isScreencast!!)
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
        videoCapturer.startCapture(480, 320, 30)

        videoTrack = peerConnectionFactory.createVideoTrack("ARDAMSv0", videoSource)
        videoTrack.addSink(viewFactory.getLocalView().renderer)
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

    fun setEventListener(listener: PeerConnectionEvents?) {
        eventListener = listener
    }

    @Synchronized
    private fun getOrCreatePeerConnection(clientId: String): PeerConnection? {
        if (peerConnection != null) {
            return peerConnection
        }

        val peerConnection = peerConnectionFactory.createPeerConnection(
            PeerConnection.RTCConfiguration(iceServers).apply {
                keyType = PeerConnection.KeyType.ECDSA
                enableDtlsSrtp = true
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            },
            object : SimplePeerConnectionObserver() {
                override fun onDataChannel(dataChannel: DataChannel?) {
                    super.onDataChannel(dataChannel)
                    Log.d(TAG, "onDataChannel $dataChannel")
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    super.onIceConnectionChange(state)
                    Log.d(TAG, "onIceConnectionChange $state")
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED -> eventListener?.onIceConnected()
                        PeerConnection.IceConnectionState.DISCONNECTED -> eventListener?.onIceDisconnected()
                        else -> {}
                    }
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    super.onConnectionChange(newState)
                    Log.d(TAG, "onConnectionChange $newState")
                    when (newState) {
                        PeerConnection.PeerConnectionState.CONNECTED -> eventListener?.onConnected()
                        PeerConnection.PeerConnectionState.DISCONNECTED -> eventListener?.onDisconnected()
                        else -> {}
                    }
                }

                override fun onAddStream(stream: MediaStream?) {
                    super.onAddStream(stream)
                    stream?.let {
                        eventListener?.onAddStream(it)
                    }
                }

                override fun onRemoveStream(stream: MediaStream?) {
                    super.onRemoveStream(stream)
                    stream?.let {
                        eventListener?.onRemoveStream(it.id)
                    }
                }
            })
        peerConnection?.addTrack(audioTrack, streamList)
        peerConnection?.addTrack(videoTrack, streamList)
        dataChannel = peerConnection?.createDataChannel("ARDAMSd0", DataChannel.Init())
        dataChannel?.registerObserver(object : SimpleDataChannelObserver() {
            override fun onMessage(message: String) {
                Log.d(TAG, "onDataChannel message $message")
                val event = Gson().fromJson(message, DataChannelEvent::class.java)
                if (event.colibriClass == "EndpointConnectivityStatusChangeEvent") {
                    if (event.active && event.endpoint != clientId) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            eventListener?.onRenegotiationNeeded(peerConnection?.remoteDescription)
                        }, 10000)
                    }
                } else if (event.colibriClass == "EndpointExpiredEvent") {
                    Handler(Looper.getMainLooper()).postDelayed({
                        eventListener?.onRenegotiationNeeded(peerConnection?.remoteDescription)
                    }, 10000)
                    eventListener?.onRemoveStream(event.endpoint)
                } else {
                    Log.w(TAG, "onDataChannel unknown event ${event.colibriClass}")
                }
            }
        })

        return peerConnection
    }

    fun disconnect() {
        audioManager.stop()
        peerConnection?.dispose()
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val camera2 = Camera2Enumerator(context)
        if (camera2.deviceNames.isNotEmpty()) {
            val selectedDevice = camera2.deviceNames.firstOrNull(camera2::isFrontFacing) ?: camera2.deviceNames.first()
            return camera2.createCapturer(selectedDevice, null)
        }

        return null
    }

    fun onOfferReceived(clientId: String, sdp: SessionDescription?) {
        peerConnection = getOrCreatePeerConnection(clientId)
        peerConnection?.setRemoteDescription(object: SimpleSdpObserver() {
            override fun onSetSuccess() {
                super.onSetSuccess()
                Log.d(TAG, "$clientId remote description set")
            }
        }, sdp)

        peerConnection?.createAnswer(object: SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                super.onCreateSuccess(sdp)
                Log.d(TAG, "answer created")

                peerConnection?.setLocalDescription(object: SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        Log.d(TAG, "$clientId local description set")
                        Log.d(TAG, "Send answer to $clientId $sdp")
                        eventListener?.onLocalDescription(sdp)
                    }
                }, sdp)
            }
        }, MediaConstraints())
    }

    fun setAudioEnabled(isEnabled: Boolean) {
        audioTrack.setEnabled(isEnabled)
    }

    fun isAudioEnabled(): Boolean {
        return audioTrack.enabled()
    }

    fun setVideoEnabled(isEnabled: Boolean) {
        videoTrack.setEnabled(isEnabled)
    }

    fun isVideoEnabled(): Boolean {
        return videoTrack.enabled()
    }

    fun subscribe(endpointId: String) {
        endpointSet.add(endpointId)
        updateSubscription()
    }

    fun unsubscribe(endpointId: String) {
        endpointSet.remove(endpointId)
        updateSubscription()
    }

    private fun updateSubscription() {
        val event = PinnedEndpointsChangedEvent(pinnedEndpoints = endpointSet.toList())
        dataChannel?.send(
            DataChannel.Buffer(
                ByteBuffer.wrap(Gson().toJson(event).toByteArray()),
                false
            )
        )
    }
}
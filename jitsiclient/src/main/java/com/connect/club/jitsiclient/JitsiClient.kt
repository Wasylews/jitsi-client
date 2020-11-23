package com.connect.club.jitsiclient

import android.util.Log
import com.connect.club.jitsiclient.webrtc.WebRtcClient
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.SessionDescription
import org.webrtc.StatsReport

class JitsiClient(
    private val signalClient: SignalClient,
    private val webRtcClient: WebRtcClient,
    private val viewFactory: ViewFactory
) : SignalClient.SignalingEvents, WebRtcClient.PeerConnectionEvents {

    private lateinit var connectParameters: SignalClient.SignalingParameters
    companion object {
        const val TAG = "JitsiClient"
    }

    fun connect(conferenceId: String, username: String, password: String) {
        signalClient.setEventListener(this)
        signalClient.connect(
            SignalClient.ConnectionParameters(
                username,
                password,
                "https://jitsi-vb2.cnnct.support/",
                conferenceId,
                mapOf(
                    "channelLastN" to "-1",
                    "disableRtx" to "false",
                    "enableLipSync" to "true",
                    "openSctp" to "true"
                )
            )
        )
    }

    override fun onConnected(signalingParameters: SignalClient.SignalingParameters) {
        Log.d(TAG, "onConnected")
        connectParameters = signalingParameters
        webRtcClient.iceServers = signalingParameters.iceServers
        webRtcClient.setEventListener(this)
        webRtcClient.onOfferReceived(signalingParameters.clientId!!, signalingParameters.initOffer)
    }

    override fun onAddStream(stream: MediaStream) {
        viewFactory.getRemoteView(stream.id)?.stream = stream
    }

    override fun onRemoveStream(streamId: String) {
        viewFactory.getRemoteView(streamId)?.stream = null
    }

    override fun onRemoteDescription(sdp: SessionDescription?) {
        Log.d(TAG, "onRemoteDescription")
        webRtcClient.onOfferReceived(connectParameters.clientId!!, sdp)
    }

    override fun onRemoteIceCandidate(candidate: IceCandidate?) {
        Log.d(TAG, "onRemoteIceCandidate")
    }

    override fun onRemoteIceCandidatesRemoved(candidates: Array<IceCandidate?>?) {
        Log.d(TAG, "onRemoteIceCandidatesRemoved")
    }

    override fun onChannelClose() {
        Log.d(TAG, "onChannelClose")
    }

    override fun onChannelError(description: String?) {
        Log.d(TAG, "onChannelError: $description")
    }

    override fun onLocalDescription(sdp: SessionDescription?) {
        Log.d(TAG, "onLocalDescription")
        signalClient.sendAnswerSdp(sdp)
    }

    override fun onRenegotiationNeeded(sdp: SessionDescription?) {
        Log.d(TAG, "onRenegotiationNeeded")
        signalClient.sendOfferSdp(sdp)
    }

    override fun onIceCandidate(candidate: IceCandidate?) {
        Log.d(TAG, "onIceCandidate")
    }

    override fun onIceCandidatesRemoved(candidates: Array<IceCandidate?>?) {
        Log.d(TAG, "onIceCandidatesRemoved")
    }

    override fun onIceConnected() {
        Log.d(TAG, "onIceConnected")
    }

    override fun onIceDisconnected() {
        Log.d(TAG, "onIceDisconnected")
    }

    override fun onConnected() {
        Log.d(TAG, "webrtc onConnected")
    }

    override fun onDisconnected() {
        Log.d(TAG, "webrtc onDisconnected")
    }

    override fun onPeerConnectionClosed() {
        Log.d(TAG, "onPeerConnectionClosed")
    }

    override fun onPeerConnectionStatsReady(reports: Array<StatsReport?>?) {
        Log.d(TAG, "onPeerConnectionStatsReady")
    }

    override fun onPeerConnectionError(description: String?) {
        Log.d(TAG, "onPeerConnectionError")
    }
}
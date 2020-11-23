package com.connect.club.jitsiclient

import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

interface SignalClient {

    data class ConnectionParameters(
        val username: String,
        val password: String,
        val baseUrl: String? = null,
        val roomId: String,
        val conferenceConfig: Map<String, String>
    )

    /**
     * Set event handler.
     */
    fun setEventListener(listener: SignalingEvents?)

    /**
     * Connect to the room.
     */
    fun connect(parameters: ConnectionParameters)

    /**
     * Send offer SDP to the other participant.
     */
    fun sendOfferSdp(sdp: SessionDescription?)

    /**
     * Send answer SDP to the other participant.
     */
    fun sendAnswerSdp(sdp: SessionDescription?)

    /**
     * Disconnect from room.
     */
    fun disconnect()

    /**
     * Struct holding the signaling parameters of room.
     */
    data class SignalingParameters(
        val iceServers: List<PeerConnection.IceServer> = listOf(),
        val initOffer: SessionDescription? = null,
        val clientId: String? = null
    )

    /**
     * Callback interface for messages delivered on signaling channel.
     *
     * Methods are guaranteed to be invoked on the UI thread of |activity|.
     */
    interface SignalingEvents {
        /**
         * Callback fired once connected and authorised in the room
         */
        fun onConnected(signalingParameters: SignalingParameters)

        /**
         * Callback fired once remote SDP is received.
         */
        fun onRemoteDescription(sdp: SessionDescription?)

        /**
         * Callback fired once remote Ice candidate is received.
         */
        fun onRemoteIceCandidate(candidate: IceCandidate?)

        /**
         * Callback fired once remote Ice candidate removals are received.
         */
        fun onRemoteIceCandidatesRemoved(candidates: Array<IceCandidate?>?)

        /**
         * Callback fired once channel is closed.
         */
        fun onChannelClose()

        /**
         * Callback fired once channel error happened.
         */
        fun onChannelError(description: String?)
    }
}
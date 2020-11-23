package com.connect.club.jitsiclient

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.webrtc.SessionDescription
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.*

interface SignalApi {

    @GET("/conferenceGid/{conferenceGid}/endpoint/{endpoint}")
    suspend fun connect(
        @Path("conferenceGid") conferenceGid: String,
        @Path("endpoint") endpoint: String
    ): String

    @POST("/conferenceGid/{conferenceGid}/endpoint/{endpoint}")
    suspend fun sendAnswer(
        @Path("conferenceGid") conferenceGid: String,
        @Path("endpoint") endpoint: String,
        @Body sdp: String
    )

    @PATCH("/conferenceGid/{conferenceGid}/endpoint/{endpoint}")
    suspend fun updateOffer(
        @Path("conferenceGid") conferenceGid: String,
        @Path("endpoint") endpoint: String,
        @Body sdp: String
    ): String

    @DELETE("/conferenceGid/{conferenceGid}/endpoint/{endpoint}")
    suspend fun disconnect(
        @Path("conferenceGid") conferenceGid: String,
        @Path("endpoint") endpoint: String
    )
}

class HttpSignalClient : SignalClient {

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().also {
            it.level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private lateinit var service: SignalApi
    private lateinit var connectionParameters: SignalClient.ConnectionParameters
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var eventListener: SignalClient.SignalingEvents? = null

    override fun setEventListener(listener: SignalClient.SignalingEvents?) {
        eventListener = listener
    }

    override fun connect(parameters: SignalClient.ConnectionParameters) {
        connectionParameters = parameters

        service = Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(parameters.baseUrl!!)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(SignalApi::class.java)

        scope.launch {
            val offer = service.connect(parameters.roomId, parameters.username)
            eventListener?.onConnected(
                SignalClient.SignalingParameters(
                    initOffer = SessionDescription(SessionDescription.Type.OFFER, offer),
                    clientId = parameters.username
                ))
        }
    }

    override fun sendOfferSdp(sdp: SessionDescription?) {
        scope.launch {
            sdp?.let {
                val newOffer = service.updateOffer(connectionParameters.roomId, connectionParameters.username, it.description)
                eventListener?.onRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, newOffer))
            }
        }
    }

    override fun sendAnswerSdp(sdp: SessionDescription?) {
        scope.launch {
            sdp?.let {
                service.sendAnswer(connectionParameters.roomId, connectionParameters.username, it.description)
            }
        }
    }

    override fun disconnect() {
        scope.launch {
            service.disconnect(connectionParameters.roomId, connectionParameters.username)
            eventListener?.onChannelClose()
        }
    }
}
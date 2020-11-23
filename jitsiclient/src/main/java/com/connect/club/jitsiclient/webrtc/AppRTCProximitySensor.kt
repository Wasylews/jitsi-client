package com.connect.club.jitsiclient.webrtc

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import org.webrtc.ThreadUtils
import org.webrtc.voiceengine.WebRtcAudioUtils

/**
 * AppRTCProximitySensor manages functions related to the proximity sensor.
 * On most device, the proximity sensor is implemented as a boolean-sensor.
 * It returns just two values "NEAR" or "FAR". Thresholding is done on the LUX
 * value i.e. the LUX value of the light sensor is compared with a threshold.
 * A LUX-value more than the threshold means the proximity sensor returns "FAR".
 * Anything less than the threshold value and the sensor  returns "NEAR".
 */
class AppRTCProximitySensor private constructor(
    context: Context,
    sensorStateListener: Runnable
) : SensorEventListener {
    // This class should be created, started and stopped on one thread
    // (e.g. the main thread). We use |nonThreadSafe| to ensure that this is
    // the case. Only active when |DEBUG| is set to true.
    private val threadChecker = ThreadUtils.ThreadChecker()
    private val onSensorStateListener: Runnable?
    private val sensorManager: SensorManager

    private var proximitySensor: Sensor? = null
    private var lastStateReportIsNear = false

    /**
     * Activate the proximity sensor. Also do initialization if called for the
     * first time.
     */
    fun start(): Boolean {
        threadChecker.checkIsOnValidThread()
        Log.d(
            TAG,
            "start" + WebRtcAudioUtils.getThreadInfo()
        )
        if (!initDefaultSensor()) {
            // Proximity sensor is not supported on this device.
            return false
        }
        sensorManager.registerListener(
            this,
            proximitySensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        return true
    }

    /** Deactivate the proximity sensor.  */
    fun stop() {
        threadChecker.checkIsOnValidThread()
        Log.d(
            TAG,
            "stop" + WebRtcAudioUtils.getThreadInfo()
        )
        if (proximitySensor == null) {
            return
        }
        sensorManager.unregisterListener(this, proximitySensor)
    }

    /** Getter for last reported state. Set to true if "near" is reported.  */
    fun sensorReportsNearState(): Boolean {
        threadChecker.checkIsOnValidThread()
        return lastStateReportIsNear
    }

    override fun onAccuracyChanged(
        sensor: Sensor,
        accuracy: Int
    ) {
        threadChecker.checkIsOnValidThread()
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            Log.e(
                TAG,
                "The values returned by this sensor cannot be trusted"
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        threadChecker.checkIsOnValidThread()
        // As a best practice; do as little as possible within this method and
        // avoid blocking.
        val distanceInCentimeters = event.values[0]
        lastStateReportIsNear = if (distanceInCentimeters < proximitySensor!!.maximumRange) {
            Log.d(
                TAG,
                "Proximity sensor => NEAR state"
            )
            true
        } else {
            Log.d(TAG, "Proximity sensor => FAR state")
            false
        }
        // Report about new state to listening client. Client can then call
        // sensorReportsNearState() to query the current state (NEAR or FAR).
        onSensorStateListener?.run()
        Log.d(
            TAG,
            "onSensorChanged" + WebRtcAudioUtils.getThreadInfo() + ": "
                    + "accuracy=" + event.accuracy + ", timestamp=" + event.timestamp + ", distance="
                    + event.values[0]
        )
    }

    /**
     * Get default proximity sensor if it exists. Tablet devices (e.g. Nexus 7)
     * does not support this type of sensor and false will be returned in such
     * cases.
     */
    private fun initDefaultSensor(): Boolean {
        if (proximitySensor != null) {
            return true
        }
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (proximitySensor == null) {
            return false
        }
        logProximitySensorInfo()
        return true
    }

    /** Helper method for logging information about the proximity sensor.  */
    private fun logProximitySensorInfo() {
        if (proximitySensor == null) {
            return
        }
        val info = "Proximity sensor: " + "name=" + proximitySensor!!.name +
                ", vendor: " + proximitySensor!!.vendor +
                ", power: " + proximitySensor!!.power +
                ", resolution: " + proximitySensor!!.resolution +
                ", max range: " + proximitySensor!!.maximumRange +
                ", min delay: " + proximitySensor!!.minDelay +  // Added in API level 20.
                ", type: " + proximitySensor!!.stringType +  // Added in API level 21.
                ", max delay: " + proximitySensor!!.maxDelay +
                ", reporting mode: " + proximitySensor!!.reportingMode +
                ", isWakeUpSensor: " + proximitySensor!!.isWakeUpSensor
        Log.d(TAG, info)
    }

    companion object {
        private const val TAG = "AppRTCProximitySensor"

        /** Construction  */
        @JvmStatic
        fun create(
            context: Context,
            sensorStateListener: Runnable
        ): AppRTCProximitySensor {
            return AppRTCProximitySensor(
                context,
                sensorStateListener
            )
        }
    }

    init {
        Log.d(
            TAG,
            "AppRTCProximitySensor" + WebRtcAudioUtils.getThreadInfo()
        )
        onSensorStateListener = sensorStateListener
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
}
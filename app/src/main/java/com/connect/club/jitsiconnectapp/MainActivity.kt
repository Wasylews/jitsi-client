package com.connect.club.jitsiconnectapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.widget.GridLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.connect.club.jitsiclient.*
import com.connect.club.jitsiclient.webrtc.WebRtcClient
import kotlinx.android.synthetic.main.activity_main.*
import org.webrtc.EglBase

class MainActivity : AppCompatActivity() {

    private lateinit var eglBase: EglBase
    private lateinit var webRtcClient: WebRtcClient
    private lateinit var signalClient: SignalClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ActivityCompat.requestPermissions(
            this, arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ), REQUEST_CODE
        )

       sound_action.setOnClickListener {
           toggleSound()
       }

        video_action.setOnClickListener {
            toggleVideo()
        }
    }

    private fun toggleVideo() {
        if (webRtcClient.isVideoEnabled()) {
            webRtcClient.setVideoEnabled(false)
            video_action.setImageResource(R.drawable.ic_camera_off)
        } else {
            webRtcClient.setVideoEnabled(true)
            video_action.setImageResource(R.drawable.ic_camera)
        }
    }

    private fun toggleSound() {
        if (webRtcClient.isAudioEnabled()) {
            webRtcClient.setAudioEnabled(false)
            sound_action.setImageResource(R.drawable.ic_mute)
        } else {
            webRtcClient.setAudioEnabled(true)
            sound_action.setImageResource(R.drawable.ic_unmute)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showConferenceDialog()
        }
    }

    private fun showConferenceDialog() {
        val inputField = AppCompatEditText(this)

        AlertDialog.Builder(this)
            .setCancelable(false)
            .setTitle(R.string.enter_conference_id)
            .setView(inputField)
            .setPositiveButton(android.R.string.ok) {
                    dialog, _ ->
                dialog.dismiss()
                startConnection(inputField.text.toString())
            }
            .show()
    }

    private fun startConnection(conferenceId: String) {
        eglBase = EglBase.create()

        val viewFactory = ViewFactory(makeViews(eglBase.eglBaseContext))

        signalClient = HttpSignalClient()
        webRtcClient = WebRtcClient(
            applicationContext,
            eglBase.eglBaseContext,
            viewFactory
        )

        lifecycleScope.launchWhenCreated {
            JitsiClient(
                signalClient,
                webRtcClient,
                viewFactory
            ).connect(
                conferenceId,
                Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID),
                "654321"
            )
        }
    }

    private fun makeViews(eglBaseContext: EglBase.Context): List<JitsiView> {
        val views = mutableListOf<JitsiView>()

        for (row in 0 until layout_grid.rowCount) {
            for (column in 0 until layout_grid.columnCount) {


                val view = JitsiView(this).apply {
                    init(row != 0 || column != 0, eglBaseContext)
                    subscribeCheckedListener = { view, isSubscribe ->
                        if (isSubscribe) {
                            webRtcClient.subscribe(view.stream?.id!!)
                        } else {
                            webRtcClient.unsubscribe(view.stream?.id!!)
                        }
                        Log.d("subscribeCheckedListener", "${view.stream?.id} subscribe: $isSubscribe")
                    }
                    setCanSubscribe(row != 0 || column != 0)
                    isEnabled = row == 0 && column == 0
                }

                val lp = GridLayout.LayoutParams(
                    GridLayout.spec(row),
                    GridLayout.spec(column)
                ).apply {
                    height = dpToPx(150)
                    width = dpToPx(100)
                    marginEnd = dpToPx(2)
                    bottomMargin = dpToPx(2)
                }
                layout_grid.addView(view, lp)
                views.add(view)
            }
        }

        return views
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        eglBase.release()
        signalClient.disconnect()
        webRtcClient.disconnect()
    }

    companion object {
        const val REQUEST_CODE = 0
    }
}

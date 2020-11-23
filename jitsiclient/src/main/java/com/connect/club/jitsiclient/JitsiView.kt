package com.connect.club.jitsiclient

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isInvisible
import kotlinx.android.synthetic.main.view_jitsi.view.*
import org.webrtc.EglBase
import org.webrtc.MediaStream

class JitsiView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    var stream: MediaStream? = null
        set(value) {
            field = value
            val videoTrack = value?.videoTracks?.get(0)
            videoTrack?.addSink(renderer)
            post {
                text_label.text = value?.id
                isEnabled = value != null
            }
        }

    var subscribeCheckedListener: ((JitsiView, Boolean) -> Unit)? = null

    init {
        inflate(context, R.layout.view_jitsi, this)
        switch_subscribe.setOnCheckedChangeListener { _, isChecked ->
            subscribeCheckedListener?.invoke(this, isChecked)
        }
    }

    fun init(mirror: Boolean, eglBaseContext: EglBase.Context) {
        renderer.setMirror(mirror)
        renderer.setEnableHardwareScaler(true)
        renderer.init(eglBaseContext, null)
    }

    fun setCanSubscribe(canSubscribe: Boolean) {
        switch_subscribe.isInvisible = !canSubscribe
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        children.forEach { it.isEnabled = enabled }
        if (enabled) {
            renderer.foreground = null
        } else {
            renderer.foreground = ColorDrawable(ContextCompat.getColor(context, android.R.color.darker_gray))
        }
    }
}
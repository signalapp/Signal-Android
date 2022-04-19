package org.thoughtcrime.securesms.webrtc.video

import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.webrtc.data.quadrantRotation
import org.webrtc.CapturerObserver
import org.webrtc.VideoFrame
import org.webrtc.VideoProcessor
import org.webrtc.VideoSink
import java.lang.ref.SoftReference
import java.util.concurrent.atomic.AtomicBoolean

class RotationVideoSink: CapturerObserver, VideoProcessor {

    var rotation: Int = 0
    var mirrored = false

    private val capturing = AtomicBoolean(false)
    private var capturerObserver = SoftReference<CapturerObserver>(null)
    private var sink = SoftReference<VideoSink>(null)

    override fun onCapturerStarted(ignored: Boolean) {
        capturing.set(true)
    }

    override fun onCapturerStopped() {
        capturing.set(false)
    }

    override fun onFrameCaptured(videoFrame: VideoFrame?) {
        // rotate if need
        val observer = capturerObserver.get()
        if (videoFrame == null || observer == null || !capturing.get()) return

        val quadrantRotation = rotation.quadrantRotation()

        val newFrame = VideoFrame(videoFrame.buffer, (videoFrame.rotation + quadrantRotation * if (mirrored && quadrantRotation in listOf(90,270)) -1 else 1) % 360, videoFrame.timestampNs)
        val localFrame = VideoFrame(videoFrame.buffer, videoFrame.rotation * if (mirrored && quadrantRotation in listOf(90,270)) -1 else 1, videoFrame.timestampNs)

        observer.onFrameCaptured(newFrame)
        sink.get()?.onFrame(localFrame)
    }

    override fun setSink(sink: VideoSink?) {
        this.sink = SoftReference(sink)
    }

    fun setObserver(videoSink: CapturerObserver?) {
        capturerObserver = SoftReference(videoSink)
    }
}
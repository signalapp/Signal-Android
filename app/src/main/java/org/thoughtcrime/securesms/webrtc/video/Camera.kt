package org.thoughtcrime.securesms.webrtc.video

import android.content.Context
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.webrtc.video.CameraState.Direction.*
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer

class Camera(context: Context,
             private val cameraEventListener: CameraEventListener): CameraVideoCapturer.CameraSwitchHandler {

    companion object {
        private val TAG = Log.tag(Camera::class.java)
    }

    val capturer: CameraVideoCapturer?
    val cameraCount: Int
    var activeDirection: CameraState.Direction = PENDING
    var enabled: Boolean = false
    set(value) {
        field = value
        capturer ?: return
        try {
            if (value) {
                capturer.startCapture(1280,720,30)
            } else {
                capturer.stopCapture()
            }
        } catch (e: InterruptedException) {
            Log.e(TAG,"Interrupted while stopping video capture")
        }
    }

    init {
        val enumerator = Camera2Enumerator(context)
        cameraCount = enumerator.deviceNames.size
        capturer = createVideoCapturer(enumerator, FRONT)?.apply {
            activeDirection = FRONT
        } ?: createVideoCapturer(enumerator, BACK)?.apply {
            activeDirection = BACK
        } ?: run {
            activeDirection = NONE
            null
        }
    }

    fun dispose() {
        capturer?.dispose()
    }

    fun flip() {
        if (capturer == null || cameraCount < 2) {
            Log.w(TAG, "Tried to flip camera without capturer or less than 2 cameras")
            return
        }
        activeDirection = PENDING
        capturer.switchCamera(this)
    }

    override fun onCameraSwitchDone(isFrontFacing: Boolean) {
        activeDirection = if (isFrontFacing) FRONT else BACK
        cameraEventListener.onCameraSwitchCompleted(CameraState(activeDirection, cameraCount))
    }

    override fun onCameraSwitchError(errorMessage: String?) {
        Log.e(TAG,"onCameraSwitchError: $errorMessage")
        cameraEventListener.onCameraSwitchCompleted(CameraState(activeDirection, cameraCount))

    }

    private fun createVideoCapturer(enumerator: CameraEnumerator, direction: CameraState.Direction): CameraVideoCapturer? =
        enumerator.deviceNames.firstOrNull { device ->
            (direction == FRONT && enumerator.isFrontFacing(device)) ||
                    (direction == BACK && enumerator.isBackFacing(device))
        }?.let { enumerator.createCapturer(it, null) }

}
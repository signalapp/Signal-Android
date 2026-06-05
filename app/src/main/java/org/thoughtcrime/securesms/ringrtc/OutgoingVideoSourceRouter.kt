package org.thoughtcrime.securesms.ringrtc

import android.content.Context
import android.content.Intent
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.signal.ringrtc.CameraControl
import org.thoughtcrime.securesms.components.webrtc.EglBaseWrapper
import org.webrtc.CapturerObserver
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import kotlin.concurrent.Volatile

/**
 * Owns the outgoing video pipeline: a [Camera] and a lazily-created
 * [ScreenShareCapturer], and the routing logic that decides which one's
 * frames flow into the WebRTC [CapturerObserver] that RingRTC supplies.
 */
class OutgoingVideoSourceRouter(
  private val context: Context,
  private val eglBase: EglBaseWrapper,
  private val cameraEventListener: CameraEventListener,
  desiredCameraDirection: CameraState.Direction
) : CameraControl {

  companion object {
    private val TAG = tag(OutgoingVideoSourceRouter::class.java)
  }

  private val camera: Camera = Camera(context, cameraEventListener, eglBase, desiredCameraDirection)

  private var screenShareCapturer: ScreenShareCapturer? = null
  private var downstream: CapturerObserver? = null

  @Volatile
  var isScreenSharing: Boolean = false
    private set

  override fun hasCapturer(): Boolean {
    return camera.hasCapturer()
  }

  override fun initCapturer(observer: CapturerObserver) {
    Log.i(TAG, "initCapturer()")
    this.downstream = observer
    camera.initCapturer(CameraSideObserver())
  }

  override fun setEnabled(enable: Boolean) {
    camera.setEnabled(enable)
  }

  override fun flip() {
    camera.flip()
  }

  override fun setOrientation(orientation: Int?) {
    camera.setOrientation(orientation)
  }

  val cameraState: CameraState
    get() = camera.cameraState

  val isInitialized: Boolean
    get() = camera.isInitialized

  fun setCameraEventListener(cameraEventListener: CameraEventListener?) {
    camera.setCameraEventListener(cameraEventListener)
  }

  fun setVanitySink(vanitySink: VideoSink?) {
    camera.setVanitySink(vanitySink)
  }

  fun startScreenShare(mediaProjectionData: Intent) {
    if (isScreenSharing) {
      Log.w(TAG, "Already screen sharing")
      return
    }

    if (downstream == null) {
      Log.w(TAG, "Cannot start screen share before initCapturer()")
      return
    }

    Log.i(TAG, "startScreenShare()")

    isScreenSharing = true

    if (camera.isCapturing) {
      camera.pauseCapture()
    }

    if (screenShareCapturer == null) {
      screenShareCapturer = ScreenShareCapturer(
        context = context,
        eglBase = eglBase,
        sink = ScreenSideObserver(),
        onMediaProjectionStopped = cameraEventListener::onScreenShareStopped
      )
    }

    screenShareCapturer!!.start(mediaProjectionData)
  }

  fun stopScreenShare() {
    if (!isScreenSharing) {
      return
    }

    Log.i(TAG, "stopScreenShare()")

    screenShareCapturer?.stop()

    isScreenSharing = false

    if (camera.shouldBeCapturing()) {
      camera.resumeCapture()
    }
  }

  fun dispose() {
    screenShareCapturer?.dispose()
    screenShareCapturer = null
    isScreenSharing = false
    camera.dispose()
  }

  private inner class CameraSideObserver : CapturerObserver {
    override fun onCapturerStarted(success: Boolean) {
      if (!isScreenSharing) {
        downstream?.onCapturerStarted(success)
      }
    }

    override fun onCapturerStopped() {
      if (!isScreenSharing) {
        downstream?.onCapturerStopped()
      }
    }

    override fun onFrameCaptured(videoFrame: VideoFrame) {
      if (!isScreenSharing) {
        downstream?.onFrameCaptured(videoFrame)
      }
      camera.deliverToVanitySink(videoFrame)
    }
  }

  private inner class ScreenSideObserver : CapturerObserver {
    override fun onCapturerStarted(success: Boolean) {
      if (isScreenSharing) {
        downstream?.onCapturerStarted(success)
      }
    }

    override fun onCapturerStopped() {
      if (isScreenSharing) {
        downstream?.onCapturerStopped()
      }
    }

    override fun onFrameCaptured(videoFrame: VideoFrame?) {
      if (isScreenSharing) {
        downstream?.onFrameCaptured(videoFrame)
      }
    }
  }
}

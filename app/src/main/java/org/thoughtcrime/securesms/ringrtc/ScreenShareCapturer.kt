package org.thoughtcrime.securesms.ringrtc

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.thoughtcrime.securesms.components.webrtc.EglBaseWrapper
import org.webrtc.CapturerObserver
import org.webrtc.EglBase
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import kotlin.math.max

/**
 * Captures the device screen via [MediaProjection] and forwards frames to a
 * [CapturerObserver] sink.
 */
class ScreenShareCapturer(
  private val context: Context,
  private val eglBase: EglBaseWrapper,
  private val sink: CapturerObserver,
  private val onMediaProjectionStopped: () -> Unit
) {

  companion object {
    private val TAG = tag(ScreenShareCapturer::class.java)

    private const val MAX_DIMENSION = 1280
    private const val FRAME_RATE = 15
  }

  private val displayManager: DisplayManager by lazy {
    context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
  }

  private val displayListener = object : DisplayManager.DisplayListener {
    override fun onDisplayChanged(displayId: Int) {
      if (displayId == Display.DEFAULT_DISPLAY) {
        onDisplayChangedInternal()
      }
    }
    override fun onDisplayAdded(displayId: Int) = Unit
    override fun onDisplayRemoved(displayId: Int) = Unit
  }

  private var screenCapturer: ScreenCapturerAndroid? = null
  private var surfaceHelper: SurfaceTextureHelper? = null
  private var captureWidth: Int = 0
  private var captureHeight: Int = 0
  var isCapturing: Boolean = false
    private set

  fun start(mediaProjectionData: Intent) {
    if (isCapturing) {
      Log.w(TAG, "Already capturing")
      return
    }

    Log.i(TAG, "start()")
    isCapturing = true

    eglBase.performWithValidEglBase { base: EglBase? ->
      screenCapturer = ScreenCapturerAndroid(
        mediaProjectionData,
        object : MediaProjection.Callback() {
          override fun onStop() {
            Log.i(TAG, "MediaProjection stopped")
            onMediaProjectionStopped()
          }

          override fun onCapturedContentResize(width: Int, height: Int) {
            Log.i(TAG, "onCapturedContentResize($width, $height)")
            applyCaptureFormat(width, height)
          }
        }
      )

      val (width, height) = scaleForEncoder(readDisplayBounds())
      captureWidth = width
      captureHeight = height

      Log.i(TAG, "start(): capture dimensions " + width + "x" + height)

      surfaceHelper = SurfaceTextureHelper.create("WebRTC-ScreenShareHelper", base!!.getEglBaseContext())
      screenCapturer!!.initialize(surfaceHelper, context, sink)
      screenCapturer!!.startCapture(width, height, FRAME_RATE)

      if (Build.VERSION.SDK_INT < 34) {
        displayManager.registerDisplayListener(displayListener, surfaceHelper!!.handler)
      }
    }
  }

  private fun onDisplayChangedInternal() {
    if (!isCapturing) return
    applyCaptureFormat(readDisplayBounds())
  }

  private fun applyCaptureFormat(rawDimensions: Pair<Int, Int>) {
    applyCaptureFormat(rawDimensions.first, rawDimensions.second)
  }

  private fun applyCaptureFormat(rawWidth: Int, rawHeight: Int) {
    val (width, height) = scaleForEncoder(rawWidth to rawHeight)
    if (width == captureWidth && height == captureHeight) {
      return
    }

    Log.i(TAG, "applyCaptureFormat(): capture dimensions " + width + "x" + height)
    captureWidth = width
    captureHeight = height
    screenCapturer?.changeCaptureFormat(width, height, FRAME_RATE)
  }

  private fun scaleForEncoder(raw: Pair<Int, Int>): Pair<Int, Int> {
    var width = raw.first
    var height = raw.second

    val maxDimension = max(width, height)
    if (maxDimension > MAX_DIMENSION) {
      val scale = MAX_DIMENSION.toFloat() / maxDimension
      width = (width * scale).toInt()
      height = (height * scale).toInt()
    }

    // Encoders require even dimensions
    width = width and 1.inv()
    height = height and 1.inv()

    return width to height
  }

  @Suppress("DEPRECATION")
  private fun readDisplayBounds(): Pair<Int, Int> {
    return if (Build.VERSION.SDK_INT >= 30) {
      val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
      val bounds = windowManager.maximumWindowMetrics.bounds
      bounds.width() to bounds.height()
    } else {
      val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
      val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
      val metrics = DisplayMetrics()
      display.getRealMetrics(metrics)
      metrics.widthPixels to metrics.heightPixels
    }
  }

  fun stop() {
    if (!isCapturing) {
      return
    }

    Log.i(TAG, "stop()")

    if (Build.VERSION.SDK_INT < 34) {
      displayManager.unregisterDisplayListener(displayListener)
    }

    if (screenCapturer != null) {
      screenCapturer!!.stopCapture()
      screenCapturer!!.dispose()
      screenCapturer = null
    }

    if (surfaceHelper != null) {
      surfaceHelper!!.dispose()
      surfaceHelper = null
    }

    captureWidth = 0
    captureHeight = 0
    isCapturing = false
  }

  fun dispose() {
    stop()
  }
}

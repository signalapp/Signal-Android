package org.signal.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.compose.ui.geometry.Offset

/**
 * State for CameraScreen.
 * Contains UI-related state for camera functionality.
 */
data class CameraScreenState(
  val focusPoint: Offset? = null,
  val showFocusIndicator: Boolean = false,
  val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
  val zoomRatio: Float = 1f,
  val flashMode: FlashMode = FlashMode.Off,
  val isRecording: Boolean = false,
  val recordingDuration: Long = 0L,
  val showShutter: Boolean = false,
  val showSelfieFlash: Boolean = false,
  val captureError: CaptureError? = null
)

sealed interface CaptureError {
  data class PhotoCaptureFailed(val message: String?) : CaptureError
  data class PhotoProcessingFailed(val message: String?) : CaptureError
}

/**
 * Flash mode for the camera.
 */
enum class FlashMode(val cameraxMode: Int) {
  Off(ImageCapture.FLASH_MODE_OFF),
  On(ImageCapture.FLASH_MODE_ON),
  Auto(ImageCapture.FLASH_MODE_AUTO);

  /**
   * Returns the next flash mode in the cycle: OFF -> ON -> AUTO -> OFF
   */
  fun next(): FlashMode {
    return when (this) {
      Off -> On
      On -> Auto
      Auto -> Off
    }
  }
}


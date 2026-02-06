package org.signal.camera

import android.content.Context
import androidx.annotation.FloatRange
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner

sealed interface CameraScreenEvents {

  /** Binds a camera to a sruface provider. */
  data class BindCamera(
    val lifecycleOwner: LifecycleOwner,
    val cameraProvider: ProcessCameraProvider,
    val surfaceProvider: Preview.SurfaceProvider,
    val context: Context
  ) : CameraScreenEvents

  /** Focuses the camera on a point. */
  data class TapToFocus(
    val viewX: Float,
    val viewY: Float,
    val surfaceX: Float,
    val surfaceY: Float,
    val surfaceWidth: Float,
    val surfaceHeight: Float
  ) : CameraScreenEvents

  /** Zoom that happens when you pinch your fingers. */
  data class PinchZoom(val zoomFactor: Float) : CameraScreenEvents

  /** Zoom that happens when you move your finger up and down during recording. */
  data class LinearZoom(@param:FloatRange(from = 0.0, to = 1.0) val linearZoom: Float) : CameraScreenEvents

  /** Switches between available cameras (i.e. front and rear cameras). */
  data class SwitchCamera(val context: Context) : CameraScreenEvents

  /** Sets the flash to a specific mode. */
  data class SetFlashMode(val flashMode: FlashMode) : CameraScreenEvents

  /** Moves the flash to the next available mode. */
  data object NextFlashMode : CameraScreenEvents

  /** Indicates the capture error has been handled and can be cleared. */
  data object ClearCaptureError : CameraScreenEvents
}
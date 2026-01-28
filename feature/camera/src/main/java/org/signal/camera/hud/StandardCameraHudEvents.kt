package org.signal.camera.hud

import androidx.annotation.FloatRange

/**
 * Events emitted by camera HUD components like [StandardCameraHud].
 * The parent composable handles these events to respond to user actions.
 */
sealed interface StandardCameraHudEvents {

  data object PhotoCaptureTriggered : StandardCameraHudEvents

  data object VideoCaptureStarted : StandardCameraHudEvents

  data object VideoCaptureStopped : StandardCameraHudEvents

  data object SwitchCamera : StandardCameraHudEvents

  data class SetZoomLevel(@param:FloatRange(from = 0.0, to = 1.0) val zoomLevel: Float) : StandardCameraHudEvents

  /**
   * Emitted when the gallery button is clicked.
   */
  data object GalleryClick : StandardCameraHudEvents

  /**
   * Emitted when the media selection indicator is clicked to advance to the next screen.
   */
  data object MediaSelectionClick : StandardCameraHudEvents

  /**
   * Emitted when the flash toggle button is clicked.
   */
  data object ToggleFlash : StandardCameraHudEvents

  /**
   * Emitted when a capture error should be cleared (after displaying to user).
   */
  data object ClearCaptureError : StandardCameraHudEvents
}

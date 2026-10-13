package org.signal.camera.hud

import androidx.annotation.FloatRange

/**
 * Events emitted by camera HUD components like [StandardCameraHud].
 * The parent composable handles these events to respond to user actions.
 */
sealed interface StandardCameraHudEvents {

  data object PhotoCaptureTriggered : StandardCameraHudEvents

  /**
   * @param isLocked Whether the recording runs until it is stopped rather than for only as long as the capture button
   *   is held.
   */
  data class VideoCaptureStarted(val isLocked: Boolean) : StandardCameraHudEvents

  data object VideoCaptureStopped : StandardCameraHudEvents

  /** A drag reached the lock, so the recording that was being held should carry on without the finger. */
  data object VideoCaptureLocked : StandardCameraHudEvents

  /** The running recording was asked to pause, or a paused one to resume. */
  data object RecordingPauseToggled : StandardCameraHudEvents

  data object SwitchCamera : StandardCameraHudEvents

  data class SetZoomLevel(@param:FloatRange(from = -1.0, to = 1.0) val zoomLevel: Float) : StandardCameraHudEvents

  /** A level was picked off the zoom bar: a ratio to go straight to rather than a drag away from the current one. */
  data class SetZoomRatio(val zoomRatio: Float) : StandardCameraHudEvents

  /**
   * Emitted when the gallery button is clicked.
   */
  data object GalleryClick : StandardCameraHudEvents

  /**
   * Emitted when the x is clicked to navigate to the previous screen.
   */
  data object CloseClick : StandardCameraHudEvents

  /**
   * Emitted when the flash toggle button is clicked.
   */
  data object ToggleFlash : StandardCameraHudEvents

  /**
   * Emitted when a capture error should be cleared (after displaying to user).
   */
  data object ClearCaptureError : StandardCameraHudEvents

  /**
   * Emitted when the user attempts to start video recording but audio permission has not been granted.
   */
  data object AudioPermissionRequired : StandardCameraHudEvents
}

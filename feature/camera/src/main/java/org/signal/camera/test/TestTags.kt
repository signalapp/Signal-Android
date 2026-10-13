/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera.test

/**
 * Tags for finding the camera's controls from a test. Each is applied by the control it names rather than by whichever
 * screen composed it.
 *
 * A tag names one control, not one place it is put. The flash and the camera switch each stand alone on a portrait phone
 * and sit together in a pill on anything larger; the two layouts are gated on the same condition, so whichever is up
 * there is exactly one of each to find.
 */
object TestTags {

  // Camera HUD. The close button is the one tag the HUD applies itself, having no component of its own to hang it on.
  const val CAMERA_HUD_CLOSE_BUTTON = "camera_hud_close_button"
  const val CAMERA_HUD_FLASH_BUTTON = "camera_hud_flash_button"
  const val CAMERA_HUD_SWITCH_BUTTON = "camera_hud_switch_button"
  const val CAMERA_HUD_CAPTURE_BUTTON = "camera_hud_capture_button"
  const val CAMERA_HUD_RECORDING_DURATION = "camera_hud_recording_duration"
  const val CAMERA_HUD_RECORDING_PAUSED = "camera_hud_recording_paused"
  const val CAMERA_HUD_ZOOM_BAR = "camera_hud_zoom_bar"

  // What the gallery's corner holds, one at a time
  const val CAMERA_HUD_GALLERY_BUTTON = "camera_hud_gallery_button"
  const val CAMERA_HUD_LOCK_BUTTON = "camera_hud_lock_button"
  const val CAMERA_HUD_PAUSE_BUTTON = "camera_hud_pause_button"
}

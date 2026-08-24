/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.test

/**
 * Test tags for Compose UI testing.
 */
object TestTags {

  // Add A Message Row
  const val ADD_A_MESSAGE_NEXT_BUTTON = "add_a_message_next_button"

  // Media Editor Toolbar
  const val MEDIA_EDITOR_TOOLBAR_QUALITY_BUTTON = "media_editor_toolbar_quality_button"
  const val MEDIA_EDITOR_TOOLBAR_SAVE_BUTTON = "media_editor_toolbar_save_button"
  const val MEDIA_EDITOR_TOOLBAR_ADD_MEDIA_BUTTON = "media_editor_toolbar_add_media_button"
  const val MEDIA_EDITOR_TOOLBAR_MUTE_BUTTON = "media_editor_toolbar_mute_button"

  // Media Edit Screen

  /** Tag for the delete affordance the thumbnail row puts on the media at [uri]. */
  fun thumbnailRowDeleteIcon(uri: String): String = "thumbnail_row_delete_icon_$uri"

  // Media Capture Screen
  const val MEDIA_CAPTURE_SCREEN = "media_capture_screen"
  const val MEDIA_CAPTURE_CAMERA_TOGGLE = "media_capture_camera_toggle"
  const val MEDIA_CAPTURE_TEXT_STORY_TOGGLE = "media_capture_text_story_toggle"
  const val MEDIA_CAPTURE_MEDIA_COUNT = "media_capture_media_count"
  const val MEDIA_CAPTURE_NEXT_BUTTON = "media_capture_next_button"

  // Media Select Screen
  const val MEDIA_SELECT_GRID = "media_select_grid"

  /** Tag for the selected media rail's thumbnail of the media at [uri]. */
  fun selectedMediaThumbnail(uri: String): String = "selected_media_thumbnail_$uri"

  // Schedule Send Menu
  const val SCHEDULE_SEND_PICK_TIME_OPTION = "schedule_send_pick_time_option"

  /**
   * Tag for the suggested time at [timeMs], since the suggestions themselves depend on when the menu was opened.
   */
  fun scheduleSendPresetOption(timeMs: Long): String = "schedule_send_preset_option_$timeMs"
}

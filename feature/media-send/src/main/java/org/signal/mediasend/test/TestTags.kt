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

  // Media Select Screen
  const val MEDIA_SELECT_GRID = "media_select_grid"

  // Schedule Send Menu
  const val SCHEDULE_SEND_PICK_TIME_OPTION = "schedule_send_pick_time_option"

  /**
   * Tag for the suggested time at [timeMs], since the suggestions themselves depend on when the menu was opened.
   */
  fun scheduleSendPresetOption(timeMs: Long): String = "schedule_send_preset_option_$timeMs"
}

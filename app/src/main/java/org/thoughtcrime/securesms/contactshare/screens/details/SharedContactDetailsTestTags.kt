/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.details

object SharedContactDetailsTestTags {
  const val CONTENT = "content"

  const val MESSAGE_BUTTON = "message_button"
  const val VIDEO_CALL_BUTTON = "video_call_button"
  const val AUDIO_CALL_BUTTON = "audio_call_button"

  /** Suffixed with the [SharedContactDetailsState.ContactAction] the row stands for. */
  const val ACTION_ROW = "action_row"

  /** Suffixed with the id of the detail the row renders. */
  const val DETAIL_ROW = "detail_row"

  /** Suffixed with the [SharedContactDetailsState.DetailAction] the entry stands for. */
  const val MENU_ITEM = "menu_item"

  fun actionRow(action: SharedContactDetailsState.ContactAction) = "$ACTION_ROW:$action"

  fun detailRow(id: String) = "$DETAIL_ROW:$id"

  fun menuItem(action: SharedContactDetailsState.DetailAction) = "$MENU_ITEM:$action"
}

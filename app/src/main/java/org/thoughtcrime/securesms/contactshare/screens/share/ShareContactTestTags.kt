/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.share

object ShareContactTestTags {
  const val CONTENT = "content"

  const val AVATAR_ROW = "avatar_row"
  const val EDIT_PHOTO_BUTTON = "edit_photo_button"

  const val NAME_ROW = "name_row"
  const val EDIT_NAME_BUTTON = "edit_name_button"

  const val SEND_BUTTON = "send_button"

  /** Suffixed with the id of the detail the row renders. */
  const val DETAIL_ROW = "detail_row"

  fun detailRow(id: String) = "$DETAIL_ROW:$id"
}

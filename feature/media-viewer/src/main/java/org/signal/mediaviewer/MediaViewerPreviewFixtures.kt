/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaviewer

import androidx.core.net.toUri
import org.signal.core.models.database.AttachmentId

/**
 * Shared fixtures for the module's previews.
 */
internal object MediaViewerPreviewFixtures {

  const val LOREM_IPSUM = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et " +
    "dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. " +
    "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur."

  fun previewItem(
    id: Long = 0,
    contentType: String = "image/jpeg",
    isVideoGif: Boolean = false,
    caption: String? = null,
    isOutgoing: Boolean = false,
    isThreadGroup: Boolean = true,
    isNoteToSelf: Boolean = false
  ): MediaViewerItem {
    return MediaViewerItem(
      attachmentId = AttachmentId(id),
      uri = "part://preview/$id".toUri(),
      playbackUri = "part://preview/$id".toUri(),
      contentType = contentType,
      size = 1024,
      isVideoGif = isVideoGif,
      hasData = true,
      transferNeeded = false,
      messageId = 1,
      threadId = 1L,
      date = 1700000000000,
      formattedDate = "1 hour ago",
      formattedDateContentDescription = "1 hour ago",
      isOutgoing = isOutgoing,
      caption = caption,
      fromDisplayName = "Miles",
      threadDisplayName = "Spider Friends",
      isThreadGroup = isThreadGroup,
      isNoteToSelf = isNoteToSelf
    )
  }

  fun previewItems(count: Int): List<MediaViewerItem?> {
    return (0 until count).map { previewItem(id = it.toLong(), isOutgoing = it % 2 == 0) }
  }
}

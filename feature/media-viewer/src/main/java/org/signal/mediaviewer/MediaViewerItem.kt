/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaviewer

import android.net.Uri
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.ContentTypeUtil

/**
 * A single piece of media rendered by the viewer. Built in the app layer from a media record so
 * that the feature module never depends on database types.
 */
data class MediaViewerItem(
  val attachmentId: AttachmentId,
  /** What the page renders. May be a thumbnail uri when the full data is not yet available. */
  val uri: Uri,
  /** The full-data uri, used for playback, share, save, and forward. Null when only a thumbnail exists. */
  val playbackUri: Uri?,
  val contentType: String?,
  val size: Long,
  /** Pixel dimensions of the media, or 0 when unknown. */
  val width: Int = 0,
  val height: Int = 0,
  val isVideoGif: Boolean,
  /** False when the media has been offloaded from the device (backup storage optimization). */
  val hasData: Boolean,
  /** True when the attachment still needs to be downloaded. */
  val transferNeeded: Boolean,
  val messageId: Long,
  val threadId: Long,
  val date: Long,
  /** Pre-formatted relative timestamp for the toolbar subtitle. */
  val formattedDate: String,
  /** Accessibility-friendly form of [formattedDate]. */
  val formattedDateContentDescription: String,
  val isOutgoing: Boolean,
  val caption: String?,
  val fromDisplayName: String,
  val threadDisplayName: String,
  val isThreadGroup: Boolean,
  val isNoteToSelf: Boolean
) {
  val isVideo: Boolean get() = ContentTypeUtil.isVideoType(contentType)

  override fun toString(): String {
    return "MediaViewerItem(attachmentId=$attachmentId, contentType=$contentType, size=$size, isVideoGif=$isVideoGif)"
  }
}

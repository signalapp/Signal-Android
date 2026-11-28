/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaoverview

import android.net.Uri
import org.signal.blurhash.BlurHash
import org.signal.core.models.database.AttachmentId

/**
 * A single piece of media in the overview. Built in the app layer from a media record so that the
 * feature module never depends on database types.
 */
data class MediaOverviewItem(
  val key: Key,
  val kind: Kind,
  val messageId: Long,
  val threadId: Long,
  val date: Long,
  /** Pre-formatted [date] for the detail rows. */
  val formattedDate: String,
  val size: Long = 0,
  val contentType: String? = null,
  /** The full-data uri. Null when the attachment has not been downloaded or has been offloaded. */
  val uri: Uri? = null,
  /** What thumbnails render: the full-data uri when present, otherwise the archive thumbnail. */
  val displayUri: Uri? = null,
  val blurHash: BlurHash? = null,
  val fileName: String? = null,
  /** Lowercase file extension shown on the document icon. */
  val fileExtension: String? = null,
  val transferState: TransferState = TransferState.DONE,
  val showPlayOverlay: Boolean = false,
  val isOutgoing: Boolean = false,
  val senderName: String = "",
  val threadName: String = "",
  val isThreadGroup: Boolean = false,
  val isNoteToSelf: Boolean = false,
  val linkUrl: String? = null,
  val linkTitle: String? = null,
  /** Only set when the internal archive state hint is enabled. */
  val archiveHint: ArchiveHint? = null
) {

  val hasAttachment: Boolean
    get() = key is Key.Attachment

  /** True when the attachment exists but its data has been offloaded from the device. */
  val isOffloaded: Boolean
    get() = transferState == TransferState.OFFLOADED

  val isLink: Boolean
    get() = kind == Kind.LINK

  val canForward: Boolean
    get() = !isLink && uri != null

  val canShare: Boolean
    get() = if (isLink) !linkUrl.isNullOrEmpty() else uri != null

  val canSave: Boolean
    get() = hasAttachment

  override fun toString(): String {
    return "MediaOverviewItem(key=$key, kind=$kind, contentType=$contentType, size=$size, transferState=$transferState)"
  }

  /** Identifies an item across loads and selection. */
  sealed interface Key {
    data class Attachment(val attachmentId: AttachmentId) : Key
    data class Message(val messageId: Long) : Key
  }

  enum class Kind {
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    LINK
  }

  enum class TransferState {
    DONE,
    IN_PROGRESS,
    OFFLOADED,
    FAILED
  }

  /** Mirrors the backup archive transfer state, for the internal debug hint. */
  enum class ArchiveHint {
    NONE,
    COPY_PENDING,
    UPLOAD_IN_PROGRESS,
    FINISHED,
    TEMPORARY_FAILURE,
    PERMANENT_FAILURE
  }
}

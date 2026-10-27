/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaviewer

import android.net.Uri
import org.signal.core.models.database.AttachmentId
import org.signal.uicomponents.styledtext.StyledString

/**
 * State for [MediaViewerScreen].
 */
data class MediaViewerState(
  val loadState: LoadState = LoadState.INIT,
  /** Paged snapshot of the media in display order. Null entries are placeholders not yet loaded. */
  val items: List<MediaViewerItem?> = emptyList(),
  /** The view model's view of the settled page. Binds chrome only and never drives the pager directly. */
  val position: Int = 0,
  /** Programmatic pager jumps (initial anchor, rail tap, deletion advance). Keyed by [ScrollRequest.id] so a request fires exactly once. */
  val scrollRequest: ScrollRequest? = null,
  /** True when previewing a draft (not part of any thread): no menu, no rail, no jump-to-chat. */
  val isDraft: Boolean = false,
  val showThread: Boolean = false,
  val allMediaInRail: Boolean = false,
  /** Resolved message bodies by message id, used as the caption fallback. */
  val messageBodies: Map<Long, StyledString> = emptyMap(),
  val isInSharedAnimation: Boolean = true,
  val sharedElementAttachmentId: AttachmentId? = null,
  val hdrCapableUris: Set<Uri> = emptySet(),
  val zoomedInAttachmentIds: Set<AttachmentId> = emptySet(),
  val chromeVisible: Boolean = true,
  val dialog: Dialog = Dialog.None
) {
  val currentItem: MediaViewerItem? get() = items.getOrNull(position)

  val currentMediaUri: Uri? get() = currentItem?.uri

  val canDismissToSharedElement: Boolean get() = sharedElementAttachmentId != null && currentItem?.attachmentId == sharedElementAttachmentId
  val isCurrentMediaZoomedIn: Boolean get() = currentItem?.attachmentId in zoomedInAttachmentIds
  val shouldRenderHdr: Boolean get() = !isInSharedAnimation && currentMediaUri?.let { it in hdrCapableUris } == true

  /** Attachment caption if present, otherwise the message body. */
  val currentCaption: StyledString? get() = currentItem?.let { item -> item.caption?.let { StyledString(it) } ?: messageBodies[item.messageId] }

  val controlMode: ControlMode
    get() = if (currentItem?.isVideo == true && currentItem?.isVideoGif == false) {
      ControlMode.VIDEO
    } else {
      ControlMode.IMAGE
    }

  /** Rail contents: the whole gallery when [allMediaInRail], otherwise the current message's album. */
  val railItems: List<MediaViewerItem?> get() = if (allMediaInRail) items else currentAlbum

  /** The contiguous run of loaded items around [position] sharing the current message id. */
  val currentAlbum: List<MediaViewerItem> get() {
    val current = currentItem ?: return emptyList()
    var start = position
    while (start > 0 && items.getOrNull(start - 1)?.messageId == current.messageId) {
      start--
    }
    var end = position
    while (end < items.lastIndex && items.getOrNull(end + 1)?.messageId == current.messageId) {
      end++
    }
    return items.subList(start, end + 1).filterNotNull()
  }

  enum class LoadState { INIT, DATA_LOADED, MEDIA_READY }

  enum class ControlMode { IMAGE, VIDEO }

  sealed interface Dialog {
    data object None : Dialog

    data class DeleteConfirmation(val canDeleteForEveryone: Boolean, val isNoteToSelf: Boolean) : Dialog

    data object SaveOffloadedMedia : Dialog
  }

  override fun toString(): String {
    return "MediaViewerState(loadState=$loadState, size=${items.size}, position=$position, chromeVisible=$chromeVisible, dialog=$dialog)"
  }
}

/** A one-shot programmatic pager scroll. A new [id] is minted for each request. */
data class ScrollRequest(
  val id: Long,
  val position: Int,
  val animated: Boolean,
  /**
   * Whether the pager has begun executing this request. A settle that arrives before then is
   * leftover pager state from before the request and must not complete it.
   */
  val started: Boolean = false
)

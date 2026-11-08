/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaviewer

import android.net.Uri
import org.signal.core.models.database.AttachmentId
import org.signal.uicomponents.styledtext.StyledString

/**
 * Events for [MediaViewerScreen]. Every event is logged, so events must not carry captions,
 * message bodies, or display names.
 */
sealed interface MediaViewerEvent {
  /** The pager's current page changed, including mid-fling. Drives paging around the index. */
  data class PageScrolled(val position: Int) : MediaViewerEvent

  data class PageSettled(val position: Int) : MediaViewerEvent

  /** The pager began executing the [ScrollRequest] with the given id. */
  data class ScrollRequestStarted(val id: Long) : MediaViewerEvent

  /** The album rail scrolled to show the given item range. Drives paging when the rail shows all media. */
  data class RailVisibleRange(val first: Int, val last: Int) : MediaViewerEvent

  /** The current page finished decoding or rendered its first frame. */
  data object MediaReady : MediaViewerEvent

  /** The user tapped the media, toggling the chrome. */
  data object MediaTapped : MediaViewerEvent

  /** The activity's shared element transition started or ended. */
  data class SharedAnimationChanged(val running: Boolean) : MediaViewerEvent

  /** An image page was zoomed in past its minimum scale, or back out of it. */
  data class ImageZoomChanged(val attachmentId: AttachmentId, val isZoomedIn: Boolean) : MediaViewerEvent

  /** An UltraHDR gainmap was detected in the image at [uri]. */
  data class HdrDetected(val uri: Uri) : MediaViewerEvent

  data object VideoPlaybackStarted : MediaViewerEvent

  data object VideoPlaybackStopped : MediaViewerEvent

  /** Video playback failed. The host inspects [error] for incremental MAC failures. */
  data class VideoPlaybackError(val uri: Uri, val error: Exception) : MediaViewerEvent {
    override fun toString(): String = "VideoPlaybackError(${error.javaClass.simpleName})"
  }

  /** The user tapped the toolbar back arrow or pressed back. */
  data object BackClicked : MediaViewerEvent

  /** The user dragged the media far enough to dismiss the viewer. */
  data class DragDismissed(val toSharedElement: Boolean) : MediaViewerEvent

  /** The user tapped the toolbar title, requesting a jump to the message in its chat. */
  data object ToolbarTitleClicked : MediaViewerEvent

  data object ShareClicked : MediaViewerEvent

  data object ForwardClicked : MediaViewerEvent

  data object SaveClicked : MediaViewerEvent

  data object EditClicked : MediaViewerEvent

  data object DeleteClicked : MediaViewerEvent

  data class RailItemClicked(val attachmentId: AttachmentId) : MediaViewerEvent

  data class DeleteConfirmed(val deleteForEveryone: Boolean) : MediaViewerEvent

  /** The user tapped the storage settings button on the offloaded-media dialog. */
  data object ViewStorageSettingsClicked : MediaViewerEvent

  data object DialogDismissed : MediaViewerEvent

  /** The attachment database changed (debounced). Also self-posted after an in-viewer delete. */
  data object DataInvalidated : MediaViewerEvent

  data class PagedDataUpdated(val items: List<MediaViewerItem?>) : MediaViewerEvent {
    override fun toString(): String = "PagedDataUpdated(size=${items.size})"
  }

  /** The pager started or stopped scrolling (user gesture, fling, or animation). */
  data class PagerScrollStateChanged(val inProgress: Boolean) : MediaViewerEvent

  /** The fast-path single-item read of the initially opened attachment finished. */
  data class InitialItemLoaded(val item: MediaViewerItem) : MediaViewerEvent

  /** The opened attachment is not part of the viewable gallery, so there is nothing to page through. */
  data object GalleryAnchorMissing : MediaViewerEvent

  data class MessageBodiesResolved(val bodies: Map<Long, StyledString>) : MediaViewerEvent {
    override fun toString(): String = "MessageBodiesResolved(count=${bodies.size})"
  }

  /** Whether the current item may be remotely deleted, resolved off the event queue. */
  data class DeleteForEveryoneValidityResolved(val canDeleteForEveryone: Boolean, val isNoteToSelf: Boolean) : MediaViewerEvent

  /** The post-playback chrome auto-hide delay elapsed. */
  data object ChromeAutoHideElapsed : MediaViewerEvent
}

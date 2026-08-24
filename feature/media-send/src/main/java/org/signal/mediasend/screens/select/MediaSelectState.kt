/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.select

import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder
import org.signal.mediasend.MediaRecipientId

sealed interface MediaSelectState {

  /** The flow's current selection, as last reported by the parent. */
  val selectedMedia: List<Media>

  /**
   * The single recipient this media is headed to, or null when the destination is still to be chosen. Fixed for the
   * life of the flow, so it is read once at construction rather than mirrored from the parent.
   */
  val recipientId: MediaRecipientId?

  /** How much of the device's media we are allowed to read. */
  val mediaPermissions: MediaPermissions

  /** Whether media just handed to the flow was refused, as last reported by the parent. */
  val isSelectionRejected: Boolean

  /** Whether the media store actually gave us anything to render for this screen. */
  val hasContent: Boolean

  data class Folders(
    val mediaFolders: List<MediaFolder>,
    override val selectedMedia: List<Media>,
    override val recipientId: MediaRecipientId? = null,
    override val mediaPermissions: MediaPermissions = MediaPermissions.FULL,
    override val isSelectionRejected: Boolean = false
  ) : MediaSelectState {
    override val hasContent: Boolean
      get() = mediaFolders.isNotEmpty()
  }

  data class Files(
    val selectedMediaFolder: MediaFolder,
    val selectedMediaFolderItems: List<Media>,
    override val selectedMedia: List<Media>,
    override val recipientId: MediaRecipientId? = null,
    override val mediaPermissions: MediaPermissions = MediaPermissions.FULL,
    override val isSelectionRejected: Boolean = false
  ) : MediaSelectState {
    override val hasContent: Boolean
      get() = selectedMediaFolderItems.isNotEmpty()
  }

  fun withParentState(selectedMedia: List<Media>, isSelectionRejected: Boolean): MediaSelectState = when (this) {
    is Folders -> copy(selectedMedia = selectedMedia, isSelectionRejected = isSelectionRejected)
    is Files -> copy(selectedMedia = selectedMedia, isSelectionRejected = isSelectionRejected)
  }

  fun withMediaPermissions(mediaPermissions: MediaPermissions): MediaSelectState = when (this) {
    is Folders -> copy(mediaPermissions = mediaPermissions)
    is Files -> copy(mediaPermissions = mediaPermissions)
  }
}

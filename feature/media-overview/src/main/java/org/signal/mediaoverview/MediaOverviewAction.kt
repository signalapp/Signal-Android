/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaoverview

import android.content.Intent
import android.net.Uri

/**
 * One-shot side effects for the host of [MediaOverviewScreen].
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface MediaOverviewAction {

  data object NavigateBack : MediaOverviewAction

  /**
   * Open the media viewer. When [key] is showing in the grid, the host animates from its thumbnail
   * via [MediaOverviewSharedElements].
   */
  data class OpenMediaPreview(val key: MediaOverviewItem.Key, val intent: Intent) : MediaOverviewAction

  /** Open the conversation at the message's position. */
  data class OpenConversation(val intent: Intent) : MediaOverviewAction

  data class OpenLink(val url: String) : MediaOverviewAction {
    override fun toString(): String = "OpenLink"
  }

  /** Hand the file to another app to view. */
  data class OpenFileExternally(val uri: Uri, val contentType: String?) : MediaOverviewAction

  data class ShareMedia(val uri: Uri, val contentType: String?) : MediaOverviewAction

  data class ShareLink(val url: String) : MediaOverviewAction {
    override fun toString(): String = "ShareLink"
  }

  data class ShowForwardSheet(val threadId: Long, val uri: Uri, val contentType: String?) : MediaOverviewAction

  /** Save the media to the device's storage, then report back with [MediaOverviewEvent.SaveFinished]. */
  data class SaveMedia(val media: List<SaveableMedia>) : MediaOverviewAction {
    override fun toString(): String = "SaveMedia(count=${media.size})"
  }

  data class PlayAudio(val uri: Uri, val messageId: Long, val progress: Double) : MediaOverviewAction

  data class PauseAudio(val uri: Uri) : MediaOverviewAction

  data object OpenStorageSettings : MediaOverviewAction

  /** Tell the user the media can't be opened because it hasn't finished sending. */
  data object ShowMediaNotSentYet : MediaOverviewAction

  data class SaveableMedia(
    val uri: Uri,
    val contentType: String,
    val date: Long,
    val fileName: String?
  ) {
    override fun toString(): String = "SaveableMedia(contentType=$contentType)"
  }
}

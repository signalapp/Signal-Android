/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.select

import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder

sealed interface MediaSelectScreenState {

  val selectedMedia: List<Media>

  /** How much of the device's media we are allowed to read. */
  val mediaPermissions: MediaPermissions

  /** Whether the media store actually gave us anything to render for this screen. */
  val hasContent: Boolean

  data class Folders(
    val mediaFolders: List<MediaFolder>,
    override val selectedMedia: List<Media>,
    override val mediaPermissions: MediaPermissions = MediaPermissions.FULL
  ) : MediaSelectScreenState {
    override val hasContent: Boolean
      get() = mediaFolders.isNotEmpty()
  }

  data class Files(
    val selectedMediaFolder: MediaFolder,
    val selectedMediaFolderItems: List<Media>,
    override val selectedMedia: List<Media>,
    override val mediaPermissions: MediaPermissions = MediaPermissions.FULL
  ) : MediaSelectScreenState {
    override val hasContent: Boolean
      get() = selectedMediaFolderItems.isNotEmpty()
  }
}

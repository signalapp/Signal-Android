/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.select

import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder

sealed interface MediaSelectScreenState {

  val selectedMedia: List<Media>

  data class Folders(
    val mediaFolders: List<MediaFolder>,
    override val selectedMedia: List<Media>
  ) : MediaSelectScreenState

  data class Files(
    val selectedMediaFolder: MediaFolder,
    val selectedMediaFolderItems: List<Media>,
    override val selectedMedia: List<Media>
  ) : MediaSelectScreenState
}

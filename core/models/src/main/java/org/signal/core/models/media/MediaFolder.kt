/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.models.media

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a folder that's shown in a media selector, containing [Media] items.
 */
@Parcelize
data class MediaFolder(
  val thumbnailUri: Uri,
  val title: String,
  val itemCount: Int,
  val bucketId: String,
  val folderType: FolderType
) : Parcelable {
  enum class FolderType {
    NORMAL, CAMERA
  }
}

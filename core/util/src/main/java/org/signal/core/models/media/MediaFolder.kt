/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.models.media

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import org.signal.core.models.UriSerializer

/**
 * Represents a folder that's shown in a media selector, containing [Media] items.
 */
@Parcelize
@Serializable
data class MediaFolder(
  @Serializable(with = UriSerializer::class) val thumbnailUri: Uri,
  val title: String,
  val itemCount: Int,
  val bucketId: String,
  val folderType: FolderType
) : Parcelable {
  enum class FolderType {
    NORMAL, CAMERA
  }
}

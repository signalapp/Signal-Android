package org.thoughtcrime.securesms.mediasend

import android.net.Uri
import org.thoughtcrime.securesms.mediasend.v2.gallery.MediaGalleryFragment

/**
 * Represents a folder that's shown in [MediaGalleryFragment].
 */
data class MediaFolder(
  val thumbnailUri: Uri,
  val title: String,
  val itemCount: Int,
  val bucketId: String,
  val folderType: FolderType,
) {
  enum class FolderType {
    NORMAL, CAMERA
  }
}
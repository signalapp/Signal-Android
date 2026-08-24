package org.thoughtcrime.securesms.mediapreview

import android.net.Uri
import android.text.SpannableString
import org.signal.core.models.media.Media
import org.thoughtcrime.securesms.database.MediaTable

data class MediaPreviewState(
  val mediaRecords: List<MediaTable.MediaRecord> = emptyList(),
  val loadState: LoadState = LoadState.INIT,
  val position: Int = 0,
  val showThread: Boolean = false,
  val allMediaInAlbumRail: Boolean = false,
  val leftIsRecent: Boolean = false,
  val albums: Map<Long, List<Media>> = mapOf(),
  val messageBodies: Map<Long, SpannableString> = mapOf(),
  val isInSharedAnimation: Boolean = true,
  val hdrCapableUris: Set<Uri> = emptySet()
) {
  enum class LoadState { INIT, DATA_LOADED, MEDIA_READY }

  /** The uri of the media on the currently-visible page, or null if the position is not backed by a record. */
  val currentMediaUri: Uri?
    get() = mediaRecords.getOrNull(position)?.attachment?.displayUri

  /** True when the currently-visible page is a known UltraHDR image and no shared-element transition is running. */
  val shouldRenderHdr: Boolean
    get() = !isInSharedAnimation && currentMediaUri?.let { hdrCapableUris.contains(it) } == true
}

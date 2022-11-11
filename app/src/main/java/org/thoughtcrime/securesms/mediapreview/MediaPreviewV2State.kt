package org.thoughtcrime.securesms.mediapreview

import org.thoughtcrime.securesms.database.MediaDatabase
import org.thoughtcrime.securesms.mediasend.Media

data class MediaPreviewV2State(
  val mediaRecords: List<MediaDatabase.MediaRecord> = emptyList(),
  val loadState: LoadState = LoadState.INIT,
  val position: Int = 0,
  val showThread: Boolean = false,
  val allMediaInAlbumRail: Boolean = false,
  val leftIsRecent: Boolean = false,
  val albums: Map<Long, List<Media>> = mapOf(),
) {
  enum class LoadState { INIT, DATA_LOADED, MEDIA_READY }
}

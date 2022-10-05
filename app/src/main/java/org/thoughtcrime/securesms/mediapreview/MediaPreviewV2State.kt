package org.thoughtcrime.securesms.mediapreview

import org.thoughtcrime.securesms.database.MediaDatabase

data class MediaPreviewV2State(
  val mediaRecords: List<MediaDatabase.MediaRecord> = emptyList(),
  val loadState: LoadState = LoadState.INIT,
  val position: Int = 0,
  val showThread: Boolean = false
) {
  enum class LoadState { INIT, READY, LOADED }
}

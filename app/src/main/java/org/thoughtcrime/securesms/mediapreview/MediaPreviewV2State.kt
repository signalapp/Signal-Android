package org.thoughtcrime.securesms.mediapreview

import org.thoughtcrime.securesms.attachments.Attachment

data class MediaPreviewV2State(
  val attachments: List<Attachment> = emptyList(),
  val loadState: LoadState = LoadState.INIT
) {
  enum class LoadState { INIT, READY, }
}

package org.thoughtcrime.securesms.mediasend.v2.capture

import org.signal.core.models.media.Media

data class MediaCaptureState(
  val mostRecentMedia: Media? = null
)

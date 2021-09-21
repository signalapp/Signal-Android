package org.thoughtcrime.securesms.mediasend.v2.capture

import org.thoughtcrime.securesms.mediasend.Media

sealed class MediaCaptureEvent {
  data class MediaCaptureRendered(val media: Media) : MediaCaptureEvent()
  object MediaCaptureRenderFailed : MediaCaptureEvent()
}

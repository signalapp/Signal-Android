package org.thoughtcrime.securesms.mediasend.v2.capture

import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.recipients.Recipient

sealed class MediaCaptureEvent {
  data class MediaCaptureRendered(val media: Media) : MediaCaptureEvent()
  data class UsernameScannedFromQrCode(val recipient: Recipient, val username: String) : MediaCaptureEvent()
  object DeviceLinkScannedFromQrCode : MediaCaptureEvent()
  object MediaCaptureRenderFailed : MediaCaptureEvent()
}

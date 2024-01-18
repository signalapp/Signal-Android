package org.thoughtcrime.securesms.video.interfaces

fun interface TranscoderCancelationSignal {
  fun isCanceled(): Boolean
}

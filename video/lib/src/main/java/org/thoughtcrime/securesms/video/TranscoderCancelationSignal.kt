package org.thoughtcrime.securesms.video

fun interface TranscoderCancelationSignal {
  fun isCanceled(): Boolean
}

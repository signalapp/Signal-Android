package org.thoughtcrime.securesms.messages

import android.content.Context
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.testing.LogPredicate
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.internal.push.SignalServiceProtos

class TimingMessageContentProcessorV2(context: Context) : MessageContentProcessorV2(context) {
  companion object {
    val TAG = Log.tag(TimingMessageContentProcessorV2::class.java)

    fun endTagPredicate(timestamp: Long): LogPredicate = { entry ->
      entry.tag == TAG && entry.message == endTag(timestamp)
    }

    private fun startTag(timestamp: Long) = "$timestamp start"
    fun endTag(timestamp: Long) = "$timestamp end"
  }

  override fun process(envelope: SignalServiceProtos.Envelope, content: SignalServiceProtos.Content, metadata: EnvelopeMetadata, serverDeliveredTimestamp: Long, processingEarlyContent: Boolean) {
    Log.d(TAG, startTag(envelope.timestamp))
    super.process(envelope, content, metadata, serverDeliveredTimestamp, processingEarlyContent)
    Log.d(TAG, endTag(envelope.timestamp))
  }
}

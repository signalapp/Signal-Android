package org.thoughtcrime.securesms.messages

import android.content.Context
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.testing.LogPredicate
import org.thoughtcrime.securesms.util.SignalLocalMetrics
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.Envelope

class TimingMessageContentProcessor(context: Context) : MessageContentProcessor(context) {
  companion object {
    val TAG = Log.tag(TimingMessageContentProcessor::class.java)

    fun endTagPredicate(timestamp: Long): LogPredicate = { entry ->
      entry.tag == TAG && entry.message == endTag(timestamp)
    }

    private fun startTag(timestamp: Long) = "$timestamp start"
    fun endTag(timestamp: Long) = "$timestamp end"
  }

  override fun process(envelope: Envelope, content: Content, metadata: EnvelopeMetadata, serverDeliveredTimestamp: Long, processingEarlyContent: Boolean, localMetric: SignalLocalMetrics.MessageReceive?) {
    Log.d(TAG, startTag(envelope.timestamp!!))
    super.process(envelope, content, metadata, serverDeliveredTimestamp, processingEarlyContent, localMetric)
    Log.d(TAG, endTag(envelope.timestamp!!))
  }
}

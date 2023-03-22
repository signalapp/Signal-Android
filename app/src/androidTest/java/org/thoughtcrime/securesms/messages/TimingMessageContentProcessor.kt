package org.thoughtcrime.securesms.messages

import android.content.Context
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.testing.LogPredicate
import org.whispersystems.signalservice.api.messages.SignalServiceContent

class TimingMessageContentProcessor(context: Context) : MessageContentProcessor(context) {
  companion object {
    val TAG = Log.tag(TimingMessageContentProcessor::class.java)

    fun endTagPredicate(timestamp: Long): LogPredicate = { entry ->
      entry.tag == TAG && entry.message == endTag(timestamp)
    }

    private fun startTag(timestamp: Long) = "$timestamp start"
    fun endTag(timestamp: Long) = "$timestamp end"
  }

  override fun process(messageState: MessageState?, content: SignalServiceContent?, exceptionMetadata: ExceptionMetadata?, envelopeTimestamp: Long, smsMessageId: Long) {
    Log.d(TAG, startTag(envelopeTimestamp))
    super.process(messageState, content, exceptionMetadata, envelopeTimestamp, smsMessageId)
    Log.d(TAG, endTag(envelopeTimestamp))
  }
}

package org.thoughtcrime.securesms.messages

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.thoughtcrime.securesms.messages.MessageContentProcessor.ExceptionMetadata
import org.thoughtcrime.securesms.messages.MessageContentProcessor.MessageState
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.whispersystems.signalservice.api.messages.SignalServiceContent

abstract class MessageContentProcessorTest {

  @get:Rule
  val harness = SignalActivityRule()

  protected fun MessageContentProcessor.doProcess(
    messageState: MessageState = MessageState.DECRYPTED_OK,
    content: SignalServiceContent,
    exceptionMetadata: ExceptionMetadata = ExceptionMetadata("sender", 1),
    timestamp: Long = 100L,
    smsMessageId: Long = -1L
  ) {
    process(messageState, content, exceptionMetadata, timestamp, smsMessageId)
  }

  protected fun createNormalContentTestSubject(): MessageContentProcessor {
    val context = ApplicationProvider.getApplicationContext<Application>()

    return MessageContentProcessor.forNormalContent(context)
  }
}

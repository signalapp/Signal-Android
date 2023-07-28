package org.thoughtcrime.securesms.messages

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.thoughtcrime.securesms.messages.MessageContentProcessor.ExceptionMetadata
import org.thoughtcrime.securesms.messages.MessageContentProcessor.MessageState
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.TestProtos
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import org.whispersystems.signalservice.internal.push.SignalServiceProtos
import org.whispersystems.signalservice.internal.serialize.protos.SignalServiceContentProto

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

    return MessageContentProcessor.create(context)
  }

  /**
   * Creates a valid ServiceContentProto with a data message which can be built via
   * `injectDataMessage`. This function is intended to be built on-top of for more
   * specific scenario in subclasses.
   *
   * Example can be seen in __handleStoryMessageTest
   */
  protected fun createServiceContentWithDataMessage(
    messageSender: Recipient = Recipient.resolved(harness.others.first()),
    injectDataMessage: SignalServiceProtos.DataMessage.Builder.() -> Unit
  ): SignalServiceContentProto {
    return TestProtos.build {
      serviceContent(
        localAddress = address(uuid = harness.self.requireServiceId().rawUuid).build(),
        metadata = metadata(
          address = address(uuid = messageSender.requireServiceId().rawUuid).build()
        ).build()
      ).apply {
        content = content().apply {
          dataMessage = dataMessage().apply {
            injectDataMessage()
          }.build()
        }.build()
      }.build()
    }
  }
}

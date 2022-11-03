package org.thoughtcrime.securesms.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.testing.TestProtos
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import org.whispersystems.signalservice.internal.serialize.protos.SignalServiceContentProto

@Suppress("ClassName")
class MessageContentProcessor__handleTextMessageTest : MessageContentProcessorTest() {
  @Test
  fun givenContentWithATextMessageWhenIProcessThenIInsertTheTextMessage() {
    val testSubject: MessageContentProcessor = createNormalContentTestSubject()
    val expectedBody = "Hello, World!"
    val contentProto: SignalServiceContentProto = TestProtos.build {
      val dataMessage = dataMessage().apply { body = expectedBody }
      val content = content().apply { this.dataMessage = dataMessage.build() }
      serviceContent().apply { this.content = content.build() }
    }.build()

    val content = SignalServiceContent.createFromProto(contentProto)

    // WHEN
    testSubject.doProcess(content = content)

    // THEN
    val record = SignalDatabase.sms.getMessageRecord(1)
    val threadSize = SignalDatabase.mmsSms.getConversationCount(record.threadId)
    assertEquals(1, threadSize)

    assertTrue(record.isSecure)
    assertEquals(expectedBody, record.body)
  }
}

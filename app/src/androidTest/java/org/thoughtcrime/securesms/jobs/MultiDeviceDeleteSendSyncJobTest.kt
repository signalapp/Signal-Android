/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.protos.DeleteSyncJobData
import org.thoughtcrime.securesms.messages.MessageHelper
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.assertIs
import org.thoughtcrime.securesms.testing.assertIsNotNull
import org.thoughtcrime.securesms.testing.assertIsSize
import org.thoughtcrime.securesms.util.MessageTableTestUtils
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.messages.SendMessageResult
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.Content
import java.util.Optional

@RunWith(AndroidJUnit4::class)
class MultiDeviceDeleteSendSyncJobTest {

  @get:Rule
  val harness = SignalActivityRule(createGroup = true)

  private lateinit var messageHelper: MessageHelper

  private lateinit var success: SendMessageResult
  private lateinit var failure: SendMessageResult
  private lateinit var content: CapturingSlot<Content>

  @Before
  fun setUp() {
    messageHelper = MessageHelper(harness)

    mockkStatic(TextSecurePreferences::class)
    every { TextSecurePreferences.isMultiDevice(any()) } answers {
      true
    }

    success = SendMessageResult.success(SignalServiceAddress(Recipient.self().requireServiceId()), listOf(2), true, false, 0, Optional.empty())
    failure = SendMessageResult.networkFailure(SignalServiceAddress(Recipient.self().requireServiceId()))
    content = slot<Content>()
  }

  @After
  fun tearDown() {
    messageHelper.tearDown()

    unmockkStatic(TextSecurePreferences::class)
  }

  @Test
  fun messageDeletes() {
    // GIVEN
    val messages = mutableListOf<MessageHelper.MessageData>()
    messages += messageHelper.incomingText()
    messages += messageHelper.incomingText()
    messages += messageHelper.outgoingText()

    val threadId = SignalDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    val records: Set<MessageRecord> = MessageTableTestUtils.getMessages(threadId).toSet()

    // WHEN
    every { AppDependencies.signalServiceMessageSender.sendSyncMessage(capture(content), any(), any()) } returns success

    val job = MultiDeviceDeleteSendSyncJob.createMessageDeletes(records)
    val result = job.run()

    // THEN
    result.isSuccess assertIs true
    assertDeleteSync(messageHelper.alice, messages)
  }

  @Test
  fun groupMessageDeletes() {
    // GIVEN
    val messages = mutableListOf<MessageHelper.MessageData>()
    messages += messageHelper.incomingText(destination = messageHelper.group.recipientId)
    messages += messageHelper.incomingText(destination = messageHelper.group.recipientId)
    messages += messageHelper.outgoingText(conversationId = messageHelper.group.recipientId)

    val threadId = SignalDatabase.threads.getThreadIdFor(messageHelper.group.recipientId)!!
    val records: Set<MessageRecord> = MessageTableTestUtils.getMessages(threadId).toSet()

    // WHEN
    every { AppDependencies.signalServiceMessageSender.sendSyncMessage(capture(content), any(), any()) } returns success

    val job = MultiDeviceDeleteSendSyncJob.createMessageDeletes(records)
    val result = job.run()

    // THEN
    result.isSuccess assertIs true
    assertDeleteSync(messageHelper.group.recipientId, messages)
  }

  @Test
  fun retryOfDeletes() {
    // GIVEN
    val alice = messageHelper.alice.toLong()

    // WHEN
    every { AppDependencies.signalServiceMessageSender.sendSyncMessage(capture(content), any(), any()) } returns failure

    val job = MultiDeviceDeleteSendSyncJob(
      messages = listOf(DeleteSyncJobData.AddressableMessage(alice, 1, alice)),
      threads = listOf(DeleteSyncJobData.ThreadDelete(alice, listOf(DeleteSyncJobData.AddressableMessage(alice, 1, alice)))),
      localOnlyThreads = listOf(DeleteSyncJobData.ThreadDelete(alice))
    )

    val result = job.run()
    val data = DeleteSyncJobData.ADAPTER.decode(job.serialize())

    // THEN
    result.isRetry assertIs true
    data.messageDeletes.assertIsSize(1)
    data.threadDeletes.assertIsSize(1)
    data.localOnlyThreadDeletes.assertIsSize(1)
  }

  private fun assertDeleteSync(conversation: RecipientId, inputMessages: List<MessageHelper.MessageData>) {
    val messagesMap = inputMessages.associateBy { it.timestamp }

    val content = this.content.captured

    content.syncMessage?.padding.assertIsNotNull()
    content.syncMessage?.deleteForMe.assertIsNotNull()

    val deleteForMe = content.syncMessage!!.deleteForMe!!
    deleteForMe.messageDeletes.assertIsSize(1)
    deleteForMe.conversationDeletes.assertIsSize(0)
    deleteForMe.localOnlyConversationDeletes.assertIsSize(0)

    val messageDeletes = deleteForMe.messageDeletes[0]
    val conversationRecipient = Recipient.resolved(conversation)
    if (conversationRecipient.isGroup) {
      messageDeletes.conversation!!.threadGroupId assertIs conversationRecipient.requireGroupId().decodedId.toByteString()
    } else {
      messageDeletes.conversation!!.threadAci assertIs conversationRecipient.requireAci().toString()
    }

    messageDeletes
      .messages
      .forEach { delete ->
        val messageData = messagesMap[delete.sentTimestamp]
        delete.sentTimestamp assertIs messageData!!.timestamp
        delete.authorAci assertIs Recipient.resolved(messageData.author).requireAci().toString()
      }
  }
}

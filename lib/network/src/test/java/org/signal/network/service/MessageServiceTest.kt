/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import arrow.core.left
import arrow.core.right
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.signal.core.models.ServiceId
import org.signal.libsignal.zkgroup.groups.GroupIdentifier
import org.signal.network.crypto.SealedSenderAccess
import org.signal.network.service.MessageService.RecipientSendResult
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.crypto.EnvelopeContent
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.DataMessage
import java.util.UUID

class MessageServiceTest {

  private val individualMessageService: IndividualMessageService = mockk()
  private val groupMessageService: GroupMessageService = mockk()
  private val logged = mutableListOf<Logged>()
  private val dependencies: MessageSendDependencies = mockk {
    every { logSendResult(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
      logged += Logged(entryId = arg(0), recipientId = arg(1), messageId = arg(6))
      100L
    }
  }
  private val messageService = MessageService(individualMessageService, groupMessageService, dependencies)

  private val serviceId = ServiceId.ACI.from(UUID.randomUUID())
  private val envelopeContent: EnvelopeContent = mockk()
  private val sealedSenderAccess: SealedSenderAccess = mockk()
  private val onEncrypted: () -> Unit = {}

  @Test
  fun `sendMessage delegates to the individual service`() = runTest {
    val success = MessageService.SendSuccess(envelopeContent = envelopeContent, sentSealedSender = true, devices = listOf(1))
    coEvery {
      individualMessageService.sendMessage(serviceId, envelopeContent, 1000L, sealedSenderAccess, false, false, onEncrypted)
    } returns success.right()

    val result = messageService.sendMessage(serviceId, sealedSenderAccess, SendRequest(envelopeContent, timestamp = 1000L, urgent = false), onEncrypted)

    assertThat(result).isEqualTo(success.right())
  }

  @Test
  fun `sendSyncMessage delegates to the individual service`() = runTest {
    val error = MessageService.SendError.RateLimited(retryAfter = null)
    coEvery {
      individualMessageService.sendSyncMessage(1000L, envelopeContent, true, onEncrypted)
    } returns error.left()

    val result = messageService.sendSyncMessage(timestamp = 1000L, envelopeContent = envelopeContent, urgent = true, onEncrypted = onEncrypted)

    assertThat(result).isEqualTo(error.left())
  }

  @Test
  fun `sendSyncMessage with a request unpacks it for the individual service`() = runTest {
    val error = MessageService.SendError.RateLimited(retryAfter = null)
    coEvery {
      individualMessageService.sendSyncMessage(1000L, envelopeContent, false, onEncrypted)
    } returns error.left()

    val result = messageService.sendSyncMessage(request = SendRequest(envelopeContent, timestamp = 1000L, urgent = false, isOnline = true), onEncrypted = onEncrypted)

    assertThat(result).isEqualTo(error.left())
  }

  @Test
  fun `sendGroupMessage with a message id logs every result in one entry`() = runTest {
    val alice = GroupSendRecipient(1, ServiceId.ACI.from(UUID.randomUUID()), unidentifiedAccess = null)
    val bob = GroupSendRecipient(2, ServiceId.ACI.from(UUID.randomUUID()), unidentifiedAccess = null)
    stubGroupSend(alice, bob)

    messageService.sendGroupMessage(destination, listOf(alice, bob), request(), messageId = 7L)

    assertThat(logged).containsExactlyInAnyOrder(Logged(entryId = -1L, recipientId = 1L, messageId = 7L), Logged(entryId = 100L, recipientId = 2L, messageId = 7L))
  }

  @Test
  fun `sendGroupMessage does not log failures`() = runTest {
    val alice = GroupSendRecipient(1, ServiceId.ACI.from(UUID.randomUUID()), unidentifiedAccess = null)
    val bob = GroupSendRecipient(2, ServiceId.ACI.from(UUID.randomUUID()), unidentifiedAccess = null)
    stubGroupSend(alice, bob, failed = setOf(bob))

    messageService.sendGroupMessage(destination, listOf(alice, bob), request(), messageId = 7L)

    assertThat(logged).containsExactly(Logged(entryId = -1L, recipientId = 1L, messageId = 7L))
  }

  @Test
  fun `sendGroupMessage without a message id logs nothing`() = runTest {
    val alice = GroupSendRecipient(1, ServiceId.ACI.from(UUID.randomUUID()), unidentifiedAccess = null)
    stubGroupSend(alice)

    messageService.sendGroupMessage(destination, listOf(alice), request())

    assertThat(logged).isEmpty()
  }

  @Test
  fun `sendGroupMessage unpacks the request for the group service`() = runTest {
    val alice = GroupSendRecipient(1, ServiceId.ACI.from(UUID.randomUUID()), unidentifiedAccess = null)
    stubGroupSend(alice)

    messageService.sendGroupMessage(destination, listOf(alice), SendRequest(EnvelopeContent.encrypted(content, ContentHint.RESENDABLE), timestamp = 1000L, urgent = false, isOnline = true))

    coVerify { groupMessageService.sendMessage(destination, listOf(alice), content, ContentHint.RESENDABLE, 1000L, true, false, any()) }
  }

  private val destination = GroupSendDestination.Group(GroupIdentifier(ByteArray(32)))
  private val content = Content(dataMessage = DataMessage(timestamp = 1000L))

  private fun request(): SendRequest<EnvelopeContent.Encrypted> {
    return SendRequest(EnvelopeContent.encrypted(content, ContentHint.RESENDABLE), timestamp = 1000L)
  }

  private fun stubGroupSend(vararg recipients: GroupSendRecipient, failed: Set<GroupSendRecipient> = emptySet()) {
    coEvery { groupMessageService.sendMessage(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
      val results = recipients.map {
        val result = if (it in failed) MessageService.SendError.NotRegistered().left() else MessageService.SendSuccess(envelopeContent, true, listOf(1)).right()
        RecipientSendResult(it.serviceId, result)
      }
      results.forEach { arg<(RecipientSendResult) -> Unit>(7)(it) }
      results.right()
    }
  }

  private data class Logged(val entryId: Long, val recipientId: Long, val messageId: Long)
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import arrow.core.left
import arrow.core.right
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.signal.core.models.ServiceId
import org.whispersystems.signalservice.api.crypto.EnvelopeContent
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import java.util.UUID

class MessageServiceTest {

  private val individualMessageService: IndividualMessageService = mockk()
  private val messageService = MessageService(individualMessageService)

  private val serviceId = ServiceId.ACI.from(UUID.randomUUID())
  private val envelopeContent: EnvelopeContent = mockk()
  private val sealedSenderAccess: SealedSenderAccess = mockk()
  private val onEncrypted: () -> Unit = {}

  @Test
  fun `sendMessage delegates to the individual service`() = runTest {
    val success = MessageService.SendSuccess(envelopeContent = envelopeContent, sentSealedSender = true, devices = listOf(1))
    coEvery {
      individualMessageService.sendMessage(serviceId, envelopeContent, 1000L, sealedSenderAccess, true, false, false, onEncrypted)
    } returns success.right()

    val result = messageService.sendMessage(serviceId, envelopeContent, 1000L, sealedSenderAccess, story = true, isOnline = false, urgent = false, onEncrypted = onEncrypted)

    assertThat(result).isEqualTo(success.right())
  }

  @Test
  fun `sendSyncMessage delegates to the individual service`() = runTest {
    val error = MessageService.SendError.RateLimited(retryAfter = null)
    coEvery {
      individualMessageService.sendSyncMessage(1000L, envelopeContent, true, onEncrypted)
    } returns error.left()

    val result = messageService.sendSyncMessage(1000L, envelopeContent, urgent = true, onEncrypted = onEncrypted)

    assertThat(result).isEqualTo(error.left())
  }
}

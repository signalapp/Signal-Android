/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import arrow.core.Either
import arrow.core.raise.Raise
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThanOrEqualTo
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.signal.core.models.ServiceId
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.ServiceIdNotFoundException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.network.api.KeysApiV2
import org.signal.network.api.MessageApiV2
import org.signal.network.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore
import org.whispersystems.signalservice.api.SignalSessionLock
import org.whispersystems.signalservice.api.crypto.EnvelopeContent
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class GroupMessageServiceFanOutTest {

  private val messageApi: MessageApiV2 = mockk()
  private val keysApi: KeysApiV2 = mockk()
  private val protocolStore: SignalServiceAccountDataStore = mockk(relaxUnitFun = true)
  private val sessionLock: SignalSessionLock = mockk()
  private val cipher: SignalServiceCipher = mockk()

  private val localAci = ServiceId.ACI.from(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"))

  private val timestamp = 1_700_000_000L
  private val storyAccess = SealedSenderAccess.Story(mockk())
  private val envelopeContent: EnvelopeContent = mockk {
    every { size() } returns 0
  }

  @Test
  fun `fan-out sends to every target and returns results in target order`() = runTest {
    val service = newService()
    val targets = fanOutTargets(3)
    every { protocolStore.getSubDeviceSessions(any()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")
    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns RequestResult.Success(Unit)

    val results = service.sendFanOut(targets, envelopeContent, timestamp, isOnline = false, urgent = true)

    assertThat(results.map { it.serviceId }).isEqualTo(targets.map { it.serviceId })
    assertThat(results.all { it.result is Either.Right }).isEqualTo(true)
    targets.forEach { target -> coVerify(exactly = 1) { messageApi.sendSealedSenderMessage(eq(target.serviceId), any(), any(), any(), any(), any()) } }
  }

  @Test
  fun `fan-out reports a target's failure without affecting the others`() = runTest {
    val service = newService()
    val targets = fanOutTargets(3)
    val failing = targets[1].serviceId
    every { protocolStore.getSubDeviceSessions(any()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")
    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns RequestResult.Success(Unit)
    coEvery { messageApi.sendSealedSenderMessage(eq(failing), any(), any(), any(), any(), any()) } returns RequestResult.NonSuccess(ServiceIdNotFoundException("not registered"))

    val results = service.sendFanOut(targets, envelopeContent, timestamp, isOnline = false, urgent = true)

    assertThat((results[1].result as Either.Left).value).isInstanceOf(MessageService.SendError.NotRegistered::class)
    assertThat(results[0].result).isInstanceOf(Either.Right::class)
    assertThat(results[2].result).isInstanceOf(Either.Right::class)
  }

  @Test
  fun `fan-out prefetches prekeys for targets without a session before sending`() = runTest {
    val service = newService()
    val targets = fanOutTargets(2)
    val noSession = targets[1].serviceId
    val noSessionAddress = SignalProtocolAddress(noSession.libSignalServiceId, SignalServiceAddress.DEFAULT_DEVICE_ID)
    every { protocolStore.containsSession(noSessionAddress) } returnsMany listOf(false, true)
    every { protocolStore.getSubDeviceSessions(any()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")
    coEvery { keysApi.getPreKey(noSession.toString(), SignalServiceAddress.DEFAULT_DEVICE_ID, storyAccess) } returns
      RequestResult.Success(KeysApiV2.PreKeyResponse(identityKey = ByteArray(0), devices = emptyList()))
    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns RequestResult.Success(Unit)

    val results = service.sendFanOut(targets, envelopeContent, timestamp, isOnline = false, urgent = true)

    assertThat(results.all { it.result is Either.Right }).isEqualTo(true)
    coVerify(exactly = 1) { keysApi.getPreKey(any(), any(), any()) }
    coVerifyOrder {
      keysApi.getPreKey(noSession.toString(), SignalServiceAddress.DEFAULT_DEVICE_ID, storyAccess)
      messageApi.sendSealedSenderMessage(eq(noSession), any(), any(), any(), any(), any())
    }
  }

  @Test
  fun `fan-out prefetch failure is left for the target's send to report`() = runTest {
    val service = newService()
    val targets = fanOutTargets(2)
    val unregistered = targets[1].serviceId
    every { protocolStore.containsSession(SignalProtocolAddress(unregistered.libSignalServiceId, SignalServiceAddress.DEFAULT_DEVICE_ID)) } returns false
    every { protocolStore.getSubDeviceSessions(any()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")
    coEvery { keysApi.getPreKey(unregistered.toString(), SignalServiceAddress.DEFAULT_DEVICE_ID, storyAccess) } returns
      RequestResult.NonSuccess(KeysApiV2.GetPreKeysError.NotFound(authenticated = true))
    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns RequestResult.Success(Unit)

    val results = service.sendFanOut(targets, envelopeContent, timestamp, isOnline = false, urgent = true)

    assertThat(results[0].result).isInstanceOf(Either.Right::class)
    assertThat((results[1].result as Either.Left).value).isInstanceOf(MessageService.SendError.NotRegistered::class)
    coVerify(exactly = 2) { keysApi.getPreKey(unregistered.toString(), any(), any()) }
    coVerify(exactly = 0) { messageApi.sendSealedSenderMessage(eq(unregistered), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `fan-out sends at most MAX_CONCURRENT_SENDS at a time`() = runTest {
    val service = newService()
    val targets = fanOutTargets(GroupMessageService.MAX_CONCURRENT_SENDS + 8)
    val inFlight = AtomicInteger(0)
    val maxInFlight = AtomicInteger(0)
    every { protocolStore.getSubDeviceSessions(any()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")
    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } coAnswers {
      maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
      delay(50)
      inFlight.decrementAndGet()
      RequestResult.Success(Unit)
    }

    val results = service.sendFanOut(targets, envelopeContent, timestamp, isOnline = false, urgent = true)

    assertThat(results.size).isEqualTo(targets.size)
    assertThat(maxInFlight.get()).isLessThanOrEqualTo(GroupMessageService.MAX_CONCURRENT_SENDS)
    assertThat(maxInFlight.get()).isGreaterThan(1)
  }

  @Test
  fun `fan-out calls onResult once per target`() = runTest {
    val service = newService()
    val targets = fanOutTargets(3)
    val reported = mutableListOf<MessageService.RecipientSendResult>()
    every { protocolStore.getSubDeviceSessions(any()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")
    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns RequestResult.Success(Unit)

    val results = service.sendFanOut(targets, envelopeContent, timestamp, isOnline = false, urgent = true) { synchronized(reported) { reported += it } }

    assertThat(reported.toSet()).isEqualTo(results.toSet())
    assertThat(reported.size).isEqualTo(targets.size)
  }

  private fun fanOutTargets(count: Int): List<GroupMessageService.SendTarget> {
    return (1..count).map { GroupMessageService.SendTarget(ServiceId.ACI.from(UUID.randomUUID()), sealedSenderAccess = storyAccess) }
  }

  private fun newService(): GroupMessageService {
    every { protocolStore.containsSession(any()) } returns true

    val dependencies: MessageSendDependencies = mockk {
      every { this@mockk.localAci } returns this@GroupMessageServiceFanOutTest.localAci
      every { localDeviceId } returns 1
      every { maxContentSizeBytes } returns 0L
    }
    val individual: IndividualMessageService = spyk(IndividualMessageService(messageApi, keysApi, protocolStore, sessionLock, cipher, dependencies))
    every { with(individual) { any<Raise<MessageService.SendError>>().createSessionFromPreKeys(any(), any(), any()) } } just Runs

    return GroupMessageService(individual, messageApi, protocolStore, sessionLock, cipher, dependencies)
  }
}

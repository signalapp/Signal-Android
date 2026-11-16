/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import arrow.core.Either
import arrow.core.raise.Raise
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.signal.core.models.ServiceId
import org.signal.libsignal.metadata.certificate.SenderCertificate
import org.signal.libsignal.net.MismatchedDeviceException
import org.signal.libsignal.net.MultiRecipientMessageResponse
import org.signal.libsignal.net.MultiRecipientSendAuthorization
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.RequestUnauthorizedException
import org.signal.libsignal.net.ServiceIdNotFoundException
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.InvalidRegistrationIdException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.network.api.KeysApiV2
import org.signal.network.api.MessageApiV2
import org.signal.network.crypto.SealedSenderAccess
import org.signal.network.service.GroupMessageService.SendTarget
import org.signal.network.service.GroupMessageService.SenderKeyAuthorization
import org.signal.network.service.GroupMessageService.SenderKeySendResult
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore
import org.whispersystems.signalservice.api.SignalSessionLock
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher
import org.whispersystems.signalservice.api.groupsv2.GroupSendEndorsements
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage
import java.io.IOException
import java.util.UUID

class GroupMessageServiceSenderKeyTest {

  private val messageApi: MessageApiV2 = mockk()
  private val keysApi: KeysApiV2 = mockk()
  private val protocolStore: SignalServiceAccountDataStore = mockk(relaxUnitFun = true)
  private val sessionLock: SignalSessionLock = mockk()
  private val cipher: SignalServiceCipher = mockk()

  private val localAci = ServiceId.ACI.from(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"))
  private val alice = ServiceId.ACI.from(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002"))
  private val bob = ServiceId.ACI.from(UUID.fromString("cccccccc-0000-0000-0000-000000000003"))

  private val distributionId = DistributionId.from(UUID.fromString("dddddddd-0000-0000-0000-000000000004"))
  private val content = Content(dataMessage = DataMessage(body = "hi"))
  private val timestamp = 1_700_000_000L

  private val endorsements: GroupSendEndorsements = mockk {
    every { sealedSenderCertificate } returns mockk()
    every { toFullToken(any<List<ServiceId>>()) } returns mockk()
  }
  private val groupSend = SenderKeyAuthorization.GroupSend(endorsements)

  private val targets = listOf(alice, bob).map { SendTarget(it, mockk<SealedSenderAccess.IndividualUnidentifiedAccessFirst>()) }

  private var sharedWith: Set<SignalProtocolAddress> = targets.map { device1(it.serviceId) }.toSet()

  @Before
  fun setUp() {
    every { endorsements.endorsements } returns mapOf(alice to mockk(), bob to mockk())
    every { protocolStore.getAllAddressesWithActiveSessions(any()) } answers {
      firstArg<List<String>>().associate { SignalProtocolAddress(it, SignalServiceAddress.DEFAULT_DEVICE_ID) to mockk<SessionRecord>() }
    }
    every { protocolStore.getSenderKeySharedWith(distributionId) } answers { sharedWith }
    every { protocolStore.containsSession(any()) } returns true
    every { protocolStore.getSubDeviceSessions(any()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")
    every { cipher.encryptForGroup(any(), any(), any(), any(), any(), any(), any()) } returns ByteArray(1)
    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns RequestResult.Success(Unit)
    coEvery { messageApi.sendMultiRecipientMessage(any(), any(), any(), any(), any()) } returns RequestResult.Success(MultiRecipientMessageResponse(emptyList()))
  }

  @Test
  fun `sends once to every target when they all have our sender key`() = runTest {
    val result = send()

    val sent = (result as Either.Right).value
    assertThat(sent.results.map { it.serviceId }).containsExactly(alice, bob)
    assertThat(sent.results.map { (it.result as Either.Right).value.devices }).containsExactly(listOf(1), listOf(1))
    coVerify(exactly = 1) { messageApi.sendMultiRecipientMessage(any(), timestamp, ofType<MultiRecipientSendAuthorization.GroupSend>(), false, true) }
    coVerify(exactly = 0) { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `distributes our sender key only to targets missing it and marks them shared`() = runTest {
    sharedWith = setOf(device1(alice))

    val result = send()

    assertThat((result as Either.Right).value.fanOut).isEqualTo(emptyList())
    coVerify(exactly = 1) { messageApi.sendSealedSenderMessage(eq(bob), any(), any(), any(), any(), any()) }
    coVerify(exactly = 0) { messageApi.sendSealedSenderMessage(eq(alice), any(), any(), any(), any(), any()) }
    verify { protocolStore.markSenderKeySharedWith(distributionId, listOf(device1(bob))) }
  }

  @Test
  fun `target whose sender key distribution fails is left out with its failure`() = runTest {
    sharedWith = setOf(device1(alice))
    coEvery { messageApi.sendSealedSenderMessage(eq(bob), any(), any(), any(), any(), any()) } returns RequestResult.NonSuccess(ServiceIdNotFoundException("gone"))

    val result = send()

    val sent = (result as Either.Right).value
    assertThat(sent.resultFor(bob)).isInstanceOf(MessageService.SendError.NotRegistered::class)
    assertThat(sent.results.single { it.serviceId == alice }.result).isInstanceOf(Either.Right::class)
    verify { cipher.encryptForGroup(any(), match { destinations -> destinations.all { it.name == alice.toString() } }, any(), any(), any(), any(), any()) }
  }

  @Test
  fun `mismatched devices are recovered and the send is retried`() = runTest {
    coEvery { messageApi.sendMultiRecipientMessage(any(), any(), any(), any(), any()) } returnsMany listOf(
      RequestResult.NonSuccess(mismatched(alice, extra = intArrayOf(2))),
      RequestResult.Success(MultiRecipientMessageResponse(emptyList()))
    )

    val result = send()

    val sent = (result as Either.Right).value
    assertThat(sent.results.all { it.result is Either.Right }).isEqualTo(true)
    verify { protocolStore.archiveSession(SignalProtocolAddress(alice.toString(), 2)) }
    verify { protocolStore.clearSenderKeySharedWith(listOf(SignalProtocolAddress(alice.toString(), 2))) }
    coVerify(exactly = 2) { messageApi.sendMultiRecipientMessage(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `target whose device recovery fails is left out and the rest are retried`() = runTest {
    coEvery { messageApi.sendMultiRecipientMessage(any(), any(), any(), any(), any()) } returnsMany listOf(
      RequestResult.NonSuccess(mismatched(bob, missing = intArrayOf(2))),
      RequestResult.Success(MultiRecipientMessageResponse(emptyList()))
    )
    coEvery { keysApi.getPreKey(bob.toString(), 2, null) } returns RequestResult.NonSuccess(KeysApiV2.GetPreKeysError.NotFound(authenticated = true))

    val result = send()

    val sent = (result as Either.Right).value
    assertThat(sent.resultFor(bob)).isInstanceOf(MessageService.SendError.PreKeyUnavailable::class)
    assertThat(sent.results.single { it.serviceId == alice }.result).isInstanceOf(Either.Right::class)
    verify { endorsements.toFullToken(listOf(alice)) }
  }

  @Test
  fun `unauthorized multi-recipient send falls back to fan-out for the remaining targets`() = runTest {
    coEvery { messageApi.sendMultiRecipientMessage(any(), any(), any(), any(), any()) } returns RequestResult.NonSuccess(RequestUnauthorizedException("bad token"))

    val result = send()

    val sent = (result as Either.Right).value
    assertThat(sent.fanOut).isEqualTo(targets)
    assertThat(sent.results).isEqualTo(emptyList())
  }

  @Test
  fun `encryption failure that names no target fans out to everyone left`() = runTest {
    every { cipher.encryptForGroup(any(), any(), any(), any(), any(), any(), any()) } throws InvalidKeyException("bad key")

    val result = send()

    assertThat((result as Either.Right).value.fanOut).isEqualTo(targets)
    coVerify(exactly = 0) { messageApi.sendMultiRecipientMessage(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `missing session for one target fans out only that target`() = runTest {
    every { cipher.encryptForGroup(any(), any(), any(), any(), any(), any(), any()) } throws NoSessionException(device1(bob), "no session") andThen ByteArray(1)

    val result = send()

    val sent = (result as Either.Right).value
    assertThat(sent.fanOut.map { it.serviceId }).containsExactly(bob)
    assertThat(sent.results.map { it.serviceId }).containsExactly(alice)
    verify { endorsements.toFullToken(listOf(alice)) }
  }

  @Test
  fun `invalid registration id for one target fans out only that target`() = runTest {
    every { cipher.encryptForGroup(any(), any(), any(), any(), any(), any(), any()) } throws InvalidRegistrationIdException(device1(bob), "bad id") andThen ByteArray(1)

    val result = send()

    val sent = (result as Either.Right).value
    assertThat(sent.fanOut.map { it.serviceId }).containsExactly(bob)
    assertThat(sent.results.map { it.serviceId }).containsExactly(alice)
  }

  @Test
  fun `target without a group send endorsement is fanned out`() = runTest {
    every { endorsements.endorsements } returns mapOf(alice to mockk())

    val result = send()

    val sent = (result as Either.Right).value
    assertThat(sent.fanOut.map { it.serviceId }).containsExactly(bob)
    assertThat(sent.results.map { it.serviceId }).containsExactly(alice)
  }

  @Test
  fun `repeated mismatched devices give up with SessionAttemptsExhausted`() = runTest {
    coEvery { messageApi.sendMultiRecipientMessage(any(), any(), any(), any(), any()) } returns RequestResult.NonSuccess(mismatched(alice, extra = intArrayOf(2)))

    val result = send()

    assertThat((result as Either.Left).value).isInstanceOf(MessageService.SendError.SessionAttemptsExhausted::class)
    coVerify(exactly = 3) { messageApi.sendMultiRecipientMessage(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `identity mismatch during encryption leaves that target out`() = runTest {
    every { cipher.encryptForGroup(any(), any(), any(), any(), any(), any(), any()) } throws UntrustedIdentityException(bob.toString()) andThen ByteArray(1)

    val result = send()

    val sent = (result as Either.Right).value
    assertThat(sent.resultFor(bob)).isInstanceOf(MessageService.SendError.IdentityMismatch::class)
    assertThat(sent.results.single { it.serviceId == alice }.result).isInstanceOf(Either.Right::class)
  }

  @Test
  fun `unregistered ids in the response are NotRegistered`() = runTest {
    coEvery { messageApi.sendMultiRecipientMessage(any(), any(), any(), any(), any()) } returns RequestResult.Success(MultiRecipientMessageResponse(listOf(bob.libSignalServiceId)))

    val result = send()

    val sent = (result as Either.Right).value
    assertThat(sent.resultFor(bob)).isInstanceOf(MessageService.SendError.NotRegistered::class)
    assertThat(sent.results.single { it.serviceId == alice }.result).isInstanceOf(Either.Right::class)
  }

  @Test
  fun `story authorization sends with story auth and its sender certificate`() = runTest {
    val certificate = mockk<SenderCertificate>()

    send(SenderKeyAuthorization.Story(certificate))

    coVerify { messageApi.sendMultiRecipientMessage(any(), any(), MultiRecipientSendAuthorization.Story, any(), any()) }
    verify { cipher.encryptForGroup(any(), any(), any(), certificate, any(), any(), any()) }
    verify(exactly = 0) { endorsements.toFullToken(any<List<ServiceId>>()) }
  }

  @Test
  fun `network error fails the whole send`() = runTest {
    coEvery { messageApi.sendMultiRecipientMessage(any(), any(), any(), any(), any()) } returns RequestResult.RetryableNetworkError(IOException("offline"))

    val result = send()

    assertThat((result as Either.Left).value).isInstanceOf(MessageService.SendError.NetworkError::class)
  }

  @Test
  fun `onResults reports settled failures and then the sent batch`() = runTest {
    sharedWith = setOf(device1(alice))
    coEvery { messageApi.sendSealedSenderMessage(eq(bob), any(), any(), any(), any(), any()) } returns RequestResult.NonSuccess(ServiceIdNotFoundException("gone"))
    val reported = mutableListOf<List<ServiceId>>()

    newService().sendMessageViaSenderKey(distributionId, targets, content, ContentHint.RESENDABLE, null, timestamp, groupSend, isOnline = false, urgent = true) { batch ->
      reported += batch.map { it.serviceId }
    }

    assertThat(reported).containsExactly(listOf(bob), listOf(alice))
  }

  @Test
  fun `non-ACI target raises ApplicationError without sending`() = runTest {
    val pniTarget = SendTarget(ServiceId.PNI.from(UUID.randomUUID()), null)

    val result = newService().sendMessageViaSenderKey(distributionId, listOf(pniTarget), content, ContentHint.RESENDABLE, null, timestamp, groupSend, isOnline = false, urgent = true)

    assertThat((result as Either.Left).value).isInstanceOf(MessageService.SendError.ApplicationError::class)
    coVerify(exactly = 0) { messageApi.sendMultiRecipientMessage(any(), any(), any(), any(), any()) }
  }

  private suspend fun send(authorization: SenderKeyAuthorization = groupSend): Either<MessageService.SendError, SenderKeySendResult> {
    return newService().sendMessageViaSenderKey(distributionId, targets, content, ContentHint.RESENDABLE, null, timestamp, authorization, isOnline = false, urgent = true)
  }

  private fun SenderKeySendResult.resultFor(serviceId: ServiceId): MessageService.SendError {
    return (results.single { it.serviceId == serviceId }.result as Either.Left).value
  }

  private fun device1(serviceId: ServiceId): SignalProtocolAddress = SignalProtocolAddress(serviceId.toString(), SignalServiceAddress.DEFAULT_DEVICE_ID)

  private fun mismatched(account: ServiceId, missing: IntArray = intArrayOf(), extra: IntArray = intArrayOf()): MismatchedDeviceException {
    val entry = MismatchedDeviceException.Entry(account = account.libSignalServiceId, missingDevices = missing, extraDevices = extra)
    return MismatchedDeviceException("mismatched", arrayOf(entry))
  }

  private fun newService(): GroupMessageService {
    val dependencies: MessageSendDependencies = mockk {
      every { this@mockk.localAci } returns this@GroupMessageServiceSenderKeyTest.localAci
      every { localDeviceId } returns 1
      every { maxContentSizeBytes } returns 0L
    }
    val individual: IndividualMessageService = spyk(IndividualMessageService(messageApi, keysApi, protocolStore, sessionLock, cipher, dependencies))
    every { with(individual) { any<Raise<MessageService.SendError>>().createSessionFromPreKeys(any(), any(), any()) } } just Runs

    val group: GroupMessageService = spyk(GroupMessageService(individual, messageApi, protocolStore, sessionLock, cipher, dependencies))
    every { group.createSenderKeyDistributionMessage(any()) } returns mockk { every { serialize() } returns byteArrayOf(1) }
    return group
  }
}

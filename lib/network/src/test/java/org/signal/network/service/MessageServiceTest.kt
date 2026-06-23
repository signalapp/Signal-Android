/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import arrow.core.Either
import arrow.core.raise.Raise
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.signal.core.models.ServiceId
import org.signal.core.util.Base64
import org.signal.libsignal.net.MismatchedDeviceException
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.RequestUnauthorizedException
import org.signal.libsignal.net.ServiceIdNotFoundException
import org.signal.libsignal.net.UserBasedAuthorization
import org.signal.libsignal.net.UserBasedSendAuthorization
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.impl.InMemorySignalProtocolStore
import org.signal.network.api.KeysApiV2
import org.signal.network.api.MessageApiV2
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore
import org.whispersystems.signalservice.api.SignalSessionLock
import org.whispersystems.signalservice.api.crypto.EnvelopeContent
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage
import java.io.IOException
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class MessageServiceTest {

  private val messageApi: MessageApiV2 = mockk()
  private val keysApi: KeysApiV2 = mockk()
  private val protocolStore: SignalServiceAccountDataStore = mockk(relaxUnitFun = true)
  private val sessionLock: SignalSessionLock = mockk()
  private val cipher: SignalServiceCipher = mockk()

  private val localAci = ServiceId.ACI.from(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"))
  private val localAddress = SignalServiceAddress(localAci)

  private val recipientAci = ServiceId.ACI.from(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002"))
  private val recipient = SignalServiceAddress(recipientAci)

  private val timestamp = 1_700_000_000L
  private val envelopeContent: EnvelopeContent = mockk {
    every { size() } returns 0
  }

  @Test
  fun `happy path with existing session returns Success`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipientAci.toString()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")
    coEvery { messageApi.sendSealedSenderMessage(eq(recipientAci), any(), any(), any(), any(), any()) } returns
      RequestResult.Success(Unit)

    val result = service.sendMessage(recipientAci, envelopeContent, timestamp, sealedSenderAccess = null, story = true, isOnline = false)

    val success = (result as Either.Right).value
    assertThat(success.sentSealedSender).isEqualTo(true)
    assertThat(success.devices).isEqualTo(listOf(1))
  }

  @Test
  fun `missing default-device session initializes session before sending`() = runTest {
    val service = newService()
    val defaultAddress = SignalProtocolAddress(recipientAci.libSignalServiceId, SignalServiceAddress.DEFAULT_DEVICE_ID)
    every { protocolStore.getSubDeviceSessions(recipientAci.toString()) } returns emptyList()
    every { protocolStore.containsSession(defaultAddress) } returns false
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")
    coEvery { keysApi.getPreKey(recipientAci.toString(), SignalServiceAddress.DEFAULT_DEVICE_ID, null) } returns
      RequestResult.Success(KeysApiV2.PreKeyResponse(identityKey = ByteArray(0), devices = emptyList()))
    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.Success(Unit)

    val result = service.sendMessage(recipientAci, envelopeContent, timestamp, sealedSenderAccess = null, story = true, isOnline = false)

    val success = (result as Either.Right).value
    assertThat(success.devices).isEqualTo(listOf(SignalServiceAddress.DEFAULT_DEVICE_ID))
    coVerifyOrder {
      keysApi.getPreKey(recipientAci.toString(), SignalServiceAddress.DEFAULT_DEVICE_ID, null)
      messageApi.sendSealedSenderMessage(eq(recipientAci), any(), any(), any(), any(), any())
    }
    verify {
      cipher.encrypt(defaultAddress, null, envelopeContent)
    }
  }

  @Test
  fun `isOnline true is forwarded to the send request`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipientAci.toString()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")
    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.Success(Unit)

    service.sendMessage(recipientAci, envelopeContent, timestamp, sealedSenderAccess = null, story = true, isOnline = true)

    coVerify {
      messageApi.sendSealedSenderMessage(eq(recipientAci), any(), any(), any(), eq(true), any())
    }
  }

  @Test
  fun `urgent false is forwarded to the send request`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipientAci.toString()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")
    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.Success(Unit)

    service.sendMessage(recipientAci, envelopeContent, timestamp, sealedSenderAccess = null, story = true, isOnline = false, urgent = false)

    coVerify {
      messageApi.sendSealedSenderMessage(eq(recipientAci), any(), any(), any(), any(), eq(false))
    }
  }

  @Test
  fun `story uses Story auth`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipientAci.toString()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")
    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.Success(Unit)

    service.sendMessage(recipientAci, envelopeContent, timestamp, sealedSenderAccess = null, story = true, isOnline = false)

    coVerify {
      messageApi.sendSealedSenderMessage(any(), any(), any(), eq(UserBasedSendAuthorization.Story), any(), any())
    }
  }

  @Test
  fun `non-story sealed send with non-zero access key uses AccessKey auth`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipientAci.toString()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")
    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.Success(Unit)

    val accessKey = ByteArray(16) { 1 }
    val sealed = individualUnidentifiedAccessFirst(accessKey)

    service.sendMessage(recipientAci, envelopeContent, timestamp, sealedSenderAccess = sealed, story = false, isOnline = false)

    coVerify {
      messageApi.sendSealedSenderMessage(any(), any(), any(), eq(UserBasedAuthorization.AccessKey(accessKey)), any(), any())
    }
  }

  @Test
  fun `non-story sealed send with zero access key uses UnrestrictedUnauthenticatedAccess auth`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipientAci.toString()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")
    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.Success(Unit)

    val sealed = individualUnidentifiedAccessFirst(ByteArray(16))

    service.sendMessage(recipientAci, envelopeContent, timestamp, sealedSenderAccess = sealed, story = false, isOnline = false)

    coVerify {
      messageApi.sendSealedSenderMessage(any(), any(), any(), eq(UserBasedAuthorization.UnrestrictedUnauthenticatedAccess), any(), any())
    }
  }

  @Test
  fun `non-story sealed send with unsupported access type raises ApplicationError`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipientAci.toString()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")

    val unsupportedAccess = mockk<SealedSenderAccess.IndividualGroupSendTokenFirst>()

    val result = service.sendMessage(recipientAci, envelopeContent, timestamp, sealedSenderAccess = unsupportedAccess, story = false, isOnline = false)

    val app = (result as Either.Left).value as MessageService.SendError.ApplicationError
    assertThat(app.exception).isInstanceOf(IllegalArgumentException::class)
  }

  @Test
  fun `sub-device without session is excluded from target devices`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipientAci.toString()) } returns listOf(2, 3)
    every { protocolStore.containsSession(SignalProtocolAddress(recipientAci.libSignalServiceId, 2)) } returns true
    every { protocolStore.containsSession(SignalProtocolAddress(recipientAci.libSignalServiceId, 3)) } returns false
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")

    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.Success(Unit)

    val result = service.sendMessage(recipientAci, envelopeContent, timestamp, sealedSenderAccess = null, story = true, isOnline = false)

    assertThat(result).isInstanceOf(Either.Right::class)
    verify { cipher.encrypt(SignalProtocolAddress(recipientAci.libSignalServiceId, 1), any(), any()) }
    verify { cipher.encrypt(SignalProtocolAddress(recipientAci.libSignalServiceId, 2), any(), any()) }
    verify(exactly = 0) { cipher.encrypt(SignalProtocolAddress(recipientAci.libSignalServiceId, 3), any(), any()) }
  }

  @Test
  fun `local send with no other devices sends to own device`() = runTest {
    val service = newService()
    val localProtocolAddress = SignalProtocolAddress(localAci.libSignalServiceId, SignalServiceAddress.DEFAULT_DEVICE_ID)
    every { protocolStore.getSubDeviceSessions(localAci.toString()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")

    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.Success(Unit)

    val result = service.sendMessage(localAci, envelopeContent, timestamp, sealedSenderAccess = null, story = true, isOnline = false)

    val success = (result as Either.Right).value
    assertThat(success.devices).isEqualTo(listOf(SignalServiceAddress.DEFAULT_DEVICE_ID))
    verify { cipher.encrypt(localProtocolAddress, null, envelopeContent) }
  }

  @Test
  fun `MismatchedDeviceException archives extras and fetches missing prekeys`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipientAci.toString()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")

    val mismatch = mismatchedException(missing = intArrayOf(2), extra = intArrayOf(5))
    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(mismatch)
    coEvery { keysApi.getPreKey(recipientAci.toString(), 2, null) } returns
      RequestResult.Success(KeysApiV2.PreKeyResponse(identityKey = ByteArray(0), devices = emptyList()))

    service.sendMessage(recipientAci, envelopeContent, timestamp, sealedSenderAccess = null, story = true, isOnline = false)

    verify { protocolStore.archiveSession(SignalProtocolAddress(recipientAci.libSignalServiceId, 5)) }
    coVerify { keysApi.getPreKey(recipientAci.toString(), 2, null) }
  }

  @Test
  fun `MismatchedDeviceException archives stale devices`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipientAci.toString()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")

    val mismatch = mismatchedException(stale = intArrayOf(3))
    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(mismatch)

    service.sendMessage(recipientAci, envelopeContent, timestamp, sealedSenderAccess = null, story = true, isOnline = false)

    verify { protocolStore.archiveSession(SignalProtocolAddress(recipientAci.libSignalServiceId, 3)) }
  }

  @Test
  fun `single mismatched device recovers and the next send attempt succeeds`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipientAci.toString()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")

    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(mismatchedException(missing = intArrayOf(2))) andThen
      RequestResult.Success(Unit)
    coEvery { keysApi.getPreKey(recipientAci.toString(), 2, null) } returns
      RequestResult.Success(KeysApiV2.PreKeyResponse(identityKey = ByteArray(0), devices = emptyList()))

    val result = service.sendMessage(recipientAci, envelopeContent, timestamp, sealedSenderAccess = null, story = true, isOnline = false)

    val success = (result as Either.Right).value
    assertThat(success.devices).isEqualTo(listOf(1))
    coVerify(exactly = 2) { messageApi.sendSealedSenderMessage(eq(recipientAci), any(), any(), any(), any(), any()) }
    coVerify { keysApi.getPreKey(recipientAci.toString(), 2, null) }
  }

  @Test
  fun `sync send to self with no other devices stops instead of looping`() = runTest {
    val service = newService()
    every { protocolStore.isMultiDevice } returns true
    every { protocolStore.getSubDeviceSessions(localAci.toString()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, validSerializedSignalMessageBase64())

    coEvery { messageApi.sendSyncMessage(any(), any(), any()) } returns
      RequestResult.NonSuccess(mismatchedException(extra = intArrayOf(1), account = localAci))

    val result = service.sendSyncMessage(timestamp, envelopeContent, urgent = true, onEncrypted = null)

    val success = (result as Either.Right).value
    assertThat(success.devices).isEqualTo(emptyList<Int>())
    verify { protocolStore.archiveSession(SignalProtocolAddress(localAci.libSignalServiceId, 1)) }
    verify(exactly = 1) { protocolStore.setMultiDevice(false) }
    coVerify(exactly = 1) { messageApi.sendSyncMessage(any(), any(), any()) }
  }

  @Test
  fun `sync send that reached a real linked device is not treated as no other devices`() = runTest {
    val service = newService()
    every { protocolStore.isMultiDevice } returns true
    every { protocolStore.getSubDeviceSessions(localAci.toString()) } returns listOf(2)
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 2, 100, validSerializedSignalMessageBase64())

    coEvery { messageApi.sendSyncMessage(any(), any(), any()) } returns
      RequestResult.NonSuccess(mismatchedException(extra = intArrayOf(2), account = localAci))

    val result = service.sendSyncMessage(timestamp, envelopeContent, urgent = true, onEncrypted = null)

    assertThat(result).isInstanceOf(Either.Left::class)
    verify(exactly = 0) { protocolStore.setMultiDevice(any()) }
    coVerify(atLeast = 2) { messageApi.sendSyncMessage(any(), any(), any()) }
  }

  @Test
  fun `sync send is skipped entirely when not multi-device`() = runTest {
    val service = newService()
    every { protocolStore.isMultiDevice } returns false

    val result = service.sendSyncMessage(timestamp, envelopeContent, urgent = true, onEncrypted = null)

    val success = (result as Either.Right).value
    assertThat(success.devices).isEqualTo(emptyList<Int>())
    coVerify(exactly = 0) { messageApi.sendSyncMessage(any(), any(), any()) }
  }

  @Test
  fun `ServiceIdNotFoundException maps to NotRegistered`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipientAci.toString()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")
    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(ServiceIdNotFoundException("not registered"))

    val result = service.sendMessage(recipientAci, envelopeContent, timestamp, sealedSenderAccess = null, story = true, isOnline = false)

    val left = (result as Either.Left).value
    assertThat(left).isInstanceOf(MessageService.SendError.NotRegistered::class)
  }

  @Test
  fun `RequestUnauthorizedException from sealed send is retried and exhausts attempts`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipientAci.toString()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")
    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(RequestUnauthorizedException("bad access"))

    val result = service.sendMessage(recipientAci, envelopeContent, timestamp, sealedSenderAccess = null, story = true, isOnline = false)

    val left = (result as Either.Left).value
    assertThat(left).isInstanceOf(MessageService.SendError.SessionAttemptsExhausted::class)
    coVerify(exactly = 3) { messageApi.sendSealedSenderMessage(eq(recipientAci), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `RequestUnauthorizedException from sealed send falls back to an unsealed send`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipientAci.toString()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, validSerializedSignalMessageBase64())
    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(RequestUnauthorizedException("bad access"))
    coEvery { messageApi.sendUnsealedSenderMessage(any(), any(), any(), any(), any()) } returns
      RequestResult.Success(Unit)

    val sealed = individualUnidentifiedAccessFirst(ByteArray(16) { 1 })

    val result = service.sendMessage(recipientAci, envelopeContent, timestamp, sealedSenderAccess = sealed, story = false, isOnline = false)

    val success = (result as Either.Right).value
    assertThat(success.sentSealedSender).isEqualTo(false)
    coVerifyOrder {
      messageApi.sendSealedSenderMessage(eq(recipientAci), any(), any(), any(), any(), any())
      messageApi.sendUnsealedSenderMessage(eq(recipientAci), any(), any(), any(), any())
    }
  }

  @Test
  fun `RetryableNetworkError maps to NetworkError`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipientAci.toString()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")
    val ioError = IOException("down")
    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.RetryableNetworkError(ioError)

    val result = service.sendMessage(recipientAci, envelopeContent, timestamp, sealedSenderAccess = null, story = true, isOnline = false)

    val network = (result as Either.Left).value as MessageService.SendError.NetworkError
    assertThat(network.exception).isEqualTo(ioError)
  }

  @Test
  fun `ApplicationError from send is propagated`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipientAci.toString()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")
    val cause = IllegalStateException("boom")
    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.ApplicationError(cause)

    val result = service.sendMessage(recipientAci, envelopeContent, timestamp, sealedSenderAccess = null, story = true, isOnline = false)

    val app = (result as Either.Left).value as MessageService.SendError.ApplicationError
    assertThat(app.exception).isEqualTo(cause)
  }

  @Test
  fun `UntrustedIdentityException during encryption maps to IdentityMismatch`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipientAci.toString()) } returns emptyList()
    val untrusted = UntrustedIdentityException(recipient.identifier)
    every { cipher.encrypt(any(), any(), any()) } throws untrusted

    val result = service.sendMessage(recipientAci, envelopeContent, timestamp, sealedSenderAccess = null, story = true, isOnline = false)

    val mismatch = (result as Either.Left).value as MessageService.SendError.IdentityMismatch
    assertThat(mismatch.exception).isEqualTo(untrusted)
  }

  @Test
  fun `content larger than max returns ContentTooLarge before encryption`() = runTest {
    val largeContent: EnvelopeContent = mockk {
      every { size() } returns 1024
    }
    val service = newService(maxContentSizeBytes = 100)

    val result = service.sendMessage(recipientAci, largeContent, timestamp, sealedSenderAccess = null, story = true, isOnline = false)

    val tooLarge = (result as Either.Left).value as MessageService.SendError.ContentTooLarge
    assertThat(tooLarge.size).isEqualTo(1024)
    assertThat(tooLarge.maxAllowed).isEqualTo(100)
    verify(exactly = 0) { cipher.encrypt(any(), any(), any()) }
  }

  @Test
  fun `prekey fetch 404 during mismatched-device recovery propagates as PreKeyUnavailable`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipientAci.toString()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")

    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(mismatchedException(missing = intArrayOf(2)))
    coEvery { keysApi.getPreKey(recipientAci.toString(), 2, null) } returns
      RequestResult.NonSuccess(KeysApiV2.GetPreKeysError.NotFound)

    val result = service.sendMessage(recipientAci, envelopeContent, timestamp, sealedSenderAccess = null, story = true, isOnline = false)

    val left = (result as Either.Left).value
    assertThat(left).isInstanceOf(MessageService.SendError.PreKeyUnavailable::class)
  }

  @Test
  fun `prekey 429 during mismatched-device recovery propagates retry-after as RateLimited`() = runTest {
    val service = newService()
    every { protocolStore.getSubDeviceSessions(recipientAci.toString()) } returns emptyList()
    every { cipher.encrypt(any(), any(), any()) } returns OutgoingPushMessage(1, 1, 100, "AAAA")

    coEvery { messageApi.sendSealedSenderMessage(any(), any(), any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(mismatchedException(missing = intArrayOf(2)))
    coEvery { keysApi.getPreKey(recipientAci.toString(), 2, null) } returns
      RequestResult.NonSuccess(KeysApiV2.GetPreKeysError.RateLimited(retryAfter = 60.seconds))

    val result = service.sendMessage(recipientAci, envelopeContent, timestamp, sealedSenderAccess = null, story = true, isOnline = false)

    assertThat(result).isEqualTo(Either.Left(MessageService.SendError.RateLimited(retryAfter = 60.seconds)))
  }

  private fun individualUnidentifiedAccessFirst(accessKey: ByteArray): SealedSenderAccess.IndividualUnidentifiedAccessFirst {
    val ua = mockk<UnidentifiedAccess>()
    every { ua.unidentifiedAccessKey } returns accessKey
    return SealedSenderAccess.IndividualUnidentifiedAccessFirst(ua)
  }

  private fun mismatchedException(
    missing: IntArray = intArrayOf(),
    extra: IntArray = intArrayOf(),
    stale: IntArray = intArrayOf(),
    account: ServiceId = recipientAci
  ): MismatchedDeviceException {
    val entry = MismatchedDeviceException.Entry(
      account = account.libSignalServiceId,
      missingDevices = missing,
      extraDevices = extra,
      staleDevices = stale
    )
    return MismatchedDeviceException("mismatched", arrayOf(entry))
  }

  /**
   * Produces a genuinely valid, native-parseable serialized [org.signal.libsignal.protocol.message.SignalMessage]
   * (a WHISPER_TYPE message) encoded as base64. The unsealed send path wraps the encrypted bytes in
   * `SignalMessage(bytes)`, whose native constructor rejects arbitrary input — so the fallback test needs real
   * ciphertext here rather than a placeholder like "AAAA". We get one by establishing a real session between two
   * in-memory stores and capturing a post-handshake reply (the initiator's first message is a PreKeySignalMessage,
   * so we round-trip once to reach a plain SignalMessage).
   */
  private fun validSerializedSignalMessageBase64(): String {
    val aliceAddress = SignalProtocolAddress("alice", 1)
    val bobAddress = SignalProtocolAddress("bob", 1)

    val aliceStore = InMemorySignalProtocolStore(IdentityKeyPair.generate(), 1)
    val bobStore = InMemorySignalProtocolStore(IdentityKeyPair.generate(), 2)

    SessionBuilder(aliceStore, bobAddress, aliceAddress).process(createPreKeyBundle(bobStore, deviceId = bobAddress.deviceId))

    val aliceCipher = SessionCipher(aliceStore, aliceAddress, bobAddress)
    val preKeyMessage = aliceCipher.encrypt("hello".toByteArray())

    val bobCipher = SessionCipher(bobStore, bobAddress, aliceAddress)
    bobCipher.decrypt(PreKeySignalMessage(preKeyMessage.serialize()))

    val reply = bobCipher.encrypt("reply".toByteArray())
    check(reply.type == CiphertextMessage.WHISPER_TYPE) { "Expected a WHISPER_TYPE SignalMessage but got ${reply.type}" }

    return Base64.encodeWithPadding(reply.serialize())
  }

  private fun createPreKeyBundle(store: InMemorySignalProtocolStore, deviceId: Int): PreKeyBundle {
    val preKeyPair = ECKeyPair.generate()
    val signedPreKeyPair = ECKeyPair.generate()
    val signedPreKeySignature = store.identityKeyPair.privateKey.calculateSignature(signedPreKeyPair.publicKey.serialize())
    val kyberPreKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
    val kyberPreKeySignature = store.identityKeyPair.privateKey.calculateSignature(kyberPreKeyPair.publicKey.serialize())

    val preKeyId = 1
    val signedPreKeyId = 2
    val kyberPreKeyId = 3

    store.storePreKey(preKeyId, PreKeyRecord(preKeyId, preKeyPair))
    store.storeSignedPreKey(signedPreKeyId, SignedPreKeyRecord(signedPreKeyId, 1L, signedPreKeyPair, signedPreKeySignature))
    store.storeKyberPreKey(kyberPreKeyId, KyberPreKeyRecord(kyberPreKeyId, 1L, kyberPreKeyPair, kyberPreKeySignature))

    return PreKeyBundle(
      store.localRegistrationId,
      deviceId,
      preKeyId,
      preKeyPair.publicKey,
      signedPreKeyId,
      signedPreKeyPair.publicKey,
      signedPreKeySignature,
      store.identityKeyPair.publicKey,
      kyberPreKeyId,
      kyberPreKeyPair.publicKey,
      kyberPreKeySignature
    )
  }

  /**
   * Spy with `initializeSession` stubbed so tests don't exercise real crypto / native session building.
   * The stub still invokes [KeysApiV2.getPreKey] and forwards non-success [RequestResult]s as the real
   * implementation would; happy path is a no-op.
   */
  private fun newService(maxContentSizeBytes: Long = 0L): MessageService {
    every { protocolStore.containsSession(any()) } returns true

    val spy: MessageService = spyk(
      MessageService(
        localAddress = localAddress,
        localDeviceId = 1,
        messageApi = messageApi,
        keysApi = keysApi,
        protocolStore = protocolStore,
        sessionLock = sessionLock,
        cipher = cipher,
        maxContentSizeBytes = maxContentSizeBytes
      )
    )
    coEvery {
      with(spy) {
        any<Raise<MessageService.SendError>>().initializeSession(any(), any(), any())
      }
    } coAnswers {
      val raiseArg = arg<Raise<MessageService.SendError>>(0)
      val addressArg = arg<SignalProtocolAddress>(2)
      val sealedArg = arg<SealedSenderAccess?>(3)
      when (val r = keysApi.getPreKey(addressArg.name, addressArg.deviceId, sealedArg)) {
        is RequestResult.Success -> Unit
        is RequestResult.NonSuccess -> raiseArg.raise(
          when (val e = r.error) {
            KeysApiV2.GetPreKeysError.Unauthorized -> MessageService.SendError.Unauthorized()
            KeysApiV2.GetPreKeysError.NotFound -> MessageService.SendError.PreKeyUnavailable("No prekeys found for $addressArg")
            is KeysApiV2.GetPreKeysError.RateLimited -> MessageService.SendError.RateLimited(e.retryAfter)
          }
        )
        is RequestResult.RetryableNetworkError -> raiseArg.raise(MessageService.SendError.NetworkError(r.networkError))
        is RequestResult.ApplicationError -> raiseArg.raise(MessageService.SendError.ApplicationError(r.cause))
      }
    }
    return spy
  }
}

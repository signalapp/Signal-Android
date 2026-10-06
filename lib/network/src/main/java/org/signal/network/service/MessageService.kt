/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import org.signal.core.models.ServiceId
import org.signal.core.util.Base64.decodeBase64OrThrow
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.ChallengeOption
import org.signal.libsignal.net.MismatchedDeviceException
import org.signal.libsignal.net.RateLimitChallengeException
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.RequestUnauthorizedException
import org.signal.libsignal.net.SealedSendFailure
import org.signal.libsignal.net.ServiceIdNotFoundException
import org.signal.libsignal.net.SingleOutboundSealedSenderMessage
import org.signal.libsignal.net.SingleOutboundUnsealedMessage
import org.signal.libsignal.net.SyncSendFailure
import org.signal.libsignal.net.UnsealedSendFailure
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.InvalidSessionException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.message.PlaintextContent
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.network.api.KeysApiV2
import org.signal.network.api.MessageApiV2
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore
import org.whispersystems.signalservice.api.SignalSessionLock
import org.whispersystems.signalservice.api.crypto.EnvelopeContent
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher
import org.whispersystems.signalservice.api.crypto.SignalSessionBuilder
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.Envelope
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

/**
 * Sends an [EnvelopeContent] to a single recipient, driving the full one-to-one flow:
 * encrypt-per-device, send, recover mismatched / stale devices by fetching prekeys and rebuilding sessions.
 *
 * All server interaction is delegated to [MessageApiV2] and [KeysApiV2]. Encryption is delegated to
 * [cipher]. Session state is read from (and archived via) [protocolStore] under [sessionLock].
 *
 * Internal helpers return [Either] of [SendError] so orchestration is driven entirely by return
 * values rather than exceptions. Libsignal's checked exceptions (from `cipher.encrypt` and session
 * building) are caught at the single point they can be raised and `raise`d into the matching
 * [SendError] variant.
 *
 * Sync transcripts are the caller's responsibility — issue a second [sendMessage] to the local address
 * with a SyncMessage.Sent payload after a successful primary send.
 */
open class MessageService(
  private val localAddress: SignalServiceAddress,
  private val localDeviceId: Int,
  private val messageApi: MessageApiV2,
  private val keysApi: KeysApiV2,
  private val protocolStore: SignalServiceAccountDataStore,
  private val sessionLock: SignalSessionLock,
  private val cipher: SignalServiceCipher,
  private val maxContentSizeBytes: Long = 0L
) {

  companion object {
    private val TAG = Log.tag(MessageService::class)

    private const val MAX_DEVICE_RECOVERY_ATTEMPTS = 3
  }

  private val localProtocolAddress: SignalProtocolAddress = SignalProtocolAddress(localAddress.identifier, localDeviceId)

  /**
   * Sends [envelopeContent] to [serviceId]. Handles things like establishing sessions with newly-discovered linked devices.
   *
   * Sends sealed when given a [sealedSenderAccess], falling back to unsealed if the server rejects it.
   *
   * Story sends are not fully supported yet. A story whose access is only [SealedSenderAccess.isUnrestrictedForStory] fails with
   * [SendError.Unauthorized] when it needs a new session, because the server usually rejects that access for prekey fetches. The legacy
   * sender fetches those prekeys authenticated instead. A story with no [sealedSenderAccess] is also encrypted unsealed.
   */
  suspend fun sendMessage(
    serviceId: ServiceId,
    envelopeContent: EnvelopeContent,
    timestamp: Long,
    sealedSenderAccess: SealedSenderAccess?,
    story: Boolean,
    isOnline: Boolean,
    urgent: Boolean = true,
    onEncrypted: (() -> Unit)? = null
  ): Either<SendError, SendSuccess> = withContext(Dispatchers.IO) {
    either {
      val contentSize = envelopeContent.size().toLong()
      if (maxContentSizeBytes > 0 && contentSize > maxContentSizeBytes) {
        Log.w(TAG, "[$timestamp] Content size $contentSize exceeds limit of $maxContentSizeBytes bytes; aborting send.")
        raise(SendError.ContentTooLarge(size = contentSize, maxAllowed = maxContentSizeBytes))
      }

      var encryptedReported = false
      val reportEncrypted = {
        if (!encryptedReported) {
          encryptedReported = true
          onEncrypted?.invoke()
        }
      }

      if (story) {
        return@either when (val outcome = sendSealedWithDeviceRecovery(serviceId, envelopeContent, sealedSenderAccess, story = true, timestamp, isOnline, urgent, reportEncrypted)) {
          is SealedSendOutcome.Sent -> outcome.success
          SealedSendOutcome.SealedSenderAccessRejected -> {
            // TODO [stories] Retry rejected story prekey fetches authenticated and keep the story sealed, like the legacy sender, before routing story sends here.
            Log.w(TAG, "[$timestamp] Story send was rejected as unauthorized for $serviceId. unrestrictedForStory: ${SealedSenderAccess.isUnrestrictedForStory(sealedSenderAccess)}")
            raise(SendError.Unauthorized())
          }
        }
      }

      if (sealedSenderAccess != null) {
        when (val outcome = sendSealedWithDeviceRecovery(serviceId, envelopeContent, sealedSenderAccess, story = false, timestamp, isOnline, urgent, reportEncrypted)) {
          is SealedSendOutcome.Sent -> return@either outcome.success
          SealedSendOutcome.SealedSenderAccessRejected -> Log.w(TAG, "[$timestamp] Sealed sender access was rejected for $serviceId. Falling back to an unsealed send.")
        }
      }

      sendUnsealedWithDeviceRecovery(serviceId, envelopeContent, timestamp, isOnline, urgent, reportEncrypted)
    }
  }

  /** Encrypts and sends sealed, rebuilding sessions and retrying when the server reports mismatched devices. */
  private suspend fun Raise<SendError>.sendSealedWithDeviceRecovery(
    serviceId: ServiceId,
    envelopeContent: EnvelopeContent,
    sealedSenderAccess: SealedSenderAccess?,
    story: Boolean,
    timestamp: Long,
    isOnline: Boolean,
    urgent: Boolean,
    reportEncrypted: () -> Unit
  ): SealedSendOutcome {
    // Certain errors self-resolve by mutating external state, like creating new sessions.
    // Trying several times in a loop lets us re-read that external state and use it in the next attempt.
    for (attempt in 0 until MAX_DEVICE_RECOVERY_ATTEMPTS) {
      Log.d(TAG, "[$timestamp] Starting sealed send attempt ${attempt + 1} to $serviceId")
      val encryptedMessages = when (val encrypted = encryptSealedForAllDevices(serviceId, envelopeContent, sealedSenderAccess, timestamp)) {
        is EncryptResult.Success -> encrypted.messages
        EncryptResult.InvalidAccessKeyForPreKeyFetch -> return SealedSendOutcome.SealedSenderAccessRejected
      }
      reportEncrypted()

      val contents = encryptedMessages.toSealedSenderContents()
      val response = if (story) {
        messageApi.sendStoryMessage(serviceId, timestamp, contents, isOnline, urgent)
      } else {
        val access = sealedSenderAccess ?: raise(SendError.ApplicationError(IllegalArgumentException("Non-story sealed sends need a sealed sender access")))
        messageApi.sendSealedSenderMessage(serviceId, timestamp, contents, access, isOnline, urgent)
      }

      when (handleSealedSendResult(response, sealedSenderAccess, timestamp)) {
        SealedSendResult.Success -> {
          val devices = encryptedMessages.map { it.destinationDeviceId }
          Log.d(TAG, "[$timestamp] Successfully sent a sealed message to $serviceId, devices: $devices")
          return SealedSendOutcome.Sent(SendSuccess(envelopeContent = envelopeContent, sentSealedSender = true, devices = devices))
        }
        SealedSendResult.MismatchedDevices -> continue
        SealedSendResult.InvalidAccessKeyForMessageSend,
        SealedSendResult.InvalidAccessKeyForPreKeyFetch -> return SealedSendOutcome.SealedSenderAccessRejected
      }
    }

    Log.w(TAG, "[$timestamp] Exhausted device-recovery attempts for $serviceId")
    raise(SendError.SessionAttemptsExhausted())
  }

  /** Encrypts and sends unsealed, rebuilding sessions and retrying when the server reports mismatched devices. */
  private suspend fun Raise<SendError>.sendUnsealedWithDeviceRecovery(
    serviceId: ServiceId,
    envelopeContent: EnvelopeContent,
    timestamp: Long,
    isOnline: Boolean,
    urgent: Boolean,
    reportEncrypted: () -> Unit
  ): SendSuccess {
    for (attempt in 0 until MAX_DEVICE_RECOVERY_ATTEMPTS) {
      Log.d(TAG, "[$timestamp] Starting unsealed send attempt ${attempt + 1} to $serviceId")
      val encryptedMessages = encryptUnsealedForAllDevices(serviceId, envelopeContent, timestamp)
      reportEncrypted()

      when (sendUnsealed(serviceId, timestamp, encryptedMessages, isOnline, urgent)) {
        UnsealedSendResult.Success -> {
          val devices = encryptedMessages.map { it.destinationDeviceId }
          Log.d(TAG, "[$timestamp] Successfully sent an unsealed message to $serviceId, devices: $devices")
          return SendSuccess(envelopeContent = envelopeContent, sentSealedSender = false, devices = devices)
        }
        UnsealedSendResult.MismatchedDevices -> continue
      }
    }

    Log.w(TAG, "[$timestamp] Exhausted device-recovery attempts for $serviceId")
    raise(SendError.SessionAttemptsExhausted())
  }

  /**
   * Sends a sync message to your other devices.
   */
  suspend fun sendSyncMessage(
    timestamp: Long,
    envelopeContent: EnvelopeContent,
    urgent: Boolean,
    onEncrypted: (() -> Unit)?
  ): Either<SendError, SendSuccess> = withContext(Dispatchers.IO) {
    either {
      val contentSize = envelopeContent.size().toLong()
      if (maxContentSizeBytes > 0 && contentSize > maxContentSizeBytes) {
        Log.w(TAG, "[$timestamp] Content size $contentSize exceeds limit of $maxContentSizeBytes bytes; aborting send.")
        raise(SendError.ContentTooLarge(size = contentSize, maxAllowed = maxContentSizeBytes))
      }

      if (!protocolStore.isMultiDevice) {
        Log.d(TAG, "[$timestamp] We do not have any linked devices. Skipping sync message send.")
        return@either SendSuccess(envelopeContent = envelopeContent, sentSealedSender = false, devices = emptyList())
      }

      var encryptedReported = false

      // Certain errors self-resolve by mutating external state, like creating new sessions.
      // Trying several times in a loop lets us re-read that external state and use it in the next attempt.
      for (attempt in 0 until MAX_DEVICE_RECOVERY_ATTEMPTS) {
        Log.d(TAG, "[$timestamp] Starting sync message send attempt ${attempt + 1}")
        val encryptedMessages = encryptUnsealedForAllDevices(localAddress.serviceId, envelopeContent, timestamp)
        if (!encryptedReported) {
          onEncrypted?.invoke()
          encryptedReported = true
        }

        val result = messageApi.sendSyncMessage(
          timestamp = timestamp,
          contents = encryptedMessages.map { it.toUnsealedMessage() },
          urgent = urgent
        )

        when (result) {
          is RequestResult.Success -> {
            val devices = encryptedMessages.map { it.destinationDeviceId }

            Log.d(TAG, "[$timestamp] Successfully sent sync message to devices: $devices")
            return@either SendSuccess(
              envelopeContent = envelopeContent,
              sentSealedSender = false,
              devices = devices
            )
          }
          is RequestResult.RetryableNetworkError -> {
            raise(result.toSendError())
          }
          is RequestResult.ApplicationError -> {
            raise(SendError.ApplicationError(result.cause))
          }
          is RequestResult.NonSuccess<SyncSendFailure> -> {
            when (val error = result.error) {
              is MismatchedDeviceException -> {
                handleMismatched(error, sealedSenderAccess = null, timestamp)
                val sentOnlyToSelf = encryptedMessages.map { it.destinationDeviceId } == listOf(localDeviceId)
                if (sentOnlyToSelf && error.entries.all { it.missingDevices.isEmpty() }) {
                  Log.w(TAG, "[$timestamp] Sent only to our own device and the server reports no other devices. Marking as no longer multi-device and skipping send.")
                  protocolStore.setMultiDevice(false)
                  return@either SendSuccess(envelopeContent = envelopeContent, sentSealedSender = false, devices = emptyList())
                }
              }
              is RateLimitChallengeException -> {
                raise(SendError.ChallengeRequired(error.token, error.options, error.retryLater?.toKotlinDuration()))
              }
            }
          }
        }
      }

      Log.w(TAG, "[$timestamp] Exhausted device-recovery attempts for sync message")
      raise(SendError.SessionAttemptsExhausted())
    }
  }

  private suspend fun Raise<SendError>.handleSealedSendResult(result: RequestResult<Unit, SealedSendFailure>, sealedSenderAccess: SealedSenderAccess?, timestamp: Long): SealedSendResult {
    return when (result) {
      is RequestResult.Success -> {
        SealedSendResult.Success
      }
      is RequestResult.RetryableNetworkError -> {
        raise(result.toSendError())
      }
      is RequestResult.ApplicationError -> {
        raise(SendError.ApplicationError(result.cause))
      }
      is RequestResult.NonSuccess<SealedSendFailure> -> {
        when (val error = result.error) {
          is MismatchedDeviceException -> {
            when (handleMismatched(error, sealedSenderAccess, timestamp)) {
              SessionInitResult.Initialized -> SealedSendResult.MismatchedDevices
              SessionInitResult.InvalidAccessKeyForPreKeyFetch -> SealedSendResult.InvalidAccessKeyForPreKeyFetch
            }
          }
          is RequestUnauthorizedException -> {
            SealedSendResult.InvalidAccessKeyForMessageSend
          }
          is ServiceIdNotFoundException -> {
            raise(SendError.NotRegistered())
          }
        }
      }
    }
  }

  private suspend fun Raise<SendError>.sendUnsealed(
    serviceId: ServiceId,
    timestamp: Long,
    encryptedMessages: List<OutgoingPushMessage>,
    online: Boolean,
    urgent: Boolean
  ): UnsealedSendResult {
    val result = messageApi.sendUnsealedSenderMessage(
      serviceId = serviceId,
      timestamp = timestamp,
      contents = encryptedMessages.map { it.toUnsealedMessage() },
      onlineOnly = online,
      urgent = urgent
    )

    return when (result) {
      is RequestResult.Success -> {
        UnsealedSendResult.Success
      }
      is RequestResult.RetryableNetworkError -> {
        raise(result.toSendError())
      }
      is RequestResult.ApplicationError -> {
        raise(SendError.ApplicationError(result.cause))
      }
      is RequestResult.NonSuccess<UnsealedSendFailure> -> {
        when (val error = result.error) {
          is MismatchedDeviceException -> {
            handleMismatched(error, sealedSenderAccess = null, timestamp)
            UnsealedSendResult.MismatchedDevices
          }
          is ServiceIdNotFoundException -> {
            raise(SendError.NotRegistered())
          }
          is RateLimitChallengeException -> {
            raise(SendError.ChallengeRequired(error.token, error.options, error.retryLater?.toKotlinDuration()))
          }
        }
      }
    }
  }

  private suspend fun Raise<SendError>.handleMismatched(error: MismatchedDeviceException, sealedSenderAccess: SealedSenderAccess?, timestamp: Long): SessionInitResult {
    Log.w(TAG, "[$timestamp] Handling mismatched devices: ${error.entries.contentToString()}")

    for (entry in error.entries) {
      for (staleDeviceId in entry.staleDevices) {
        Log.w(TAG, "[$timestamp] Archiving stale session: (${entry.account}, $staleDeviceId)")
        protocolStore.archiveSession(SignalProtocolAddress(entry.account, staleDeviceId))
      }

      for (extraDeviceId in entry.extraDevices) {
        Log.w(TAG, "[$timestamp] Archiving extra session: (${entry.account}, $extraDeviceId)")
        protocolStore.archiveSession(SignalProtocolAddress(entry.account, extraDeviceId))
      }

      for (missingDeviceId in entry.missingDevices) {
        Log.w(TAG, "[$timestamp] Initializing session for missing device: (${entry.account}, $missingDeviceId)")
        val address = SignalProtocolAddress(entry.account, missingDeviceId)
        if (initializeSession(ServiceId.fromLibSignal(entry.account), address, sealedSenderAccess) == SessionInitResult.InvalidAccessKeyForPreKeyFetch) {
          return SessionInitResult.InvalidAccessKeyForPreKeyFetch
        }
      }
    }

    return SessionInitResult.Initialized
  }

  private suspend fun Raise<SendError>.encryptSealedForAllDevices(
    serviceId: ServiceId,
    envelopeContent: EnvelopeContent,
    sealedSenderAccess: SealedSenderAccess?,
    timestamp: Long
  ): EncryptResult {
    val messages = targetDeviceIds(serviceId).map { deviceId ->
      val address = SignalProtocolAddress(serviceId.libSignalServiceId, deviceId)
      if (!protocolStore.containsSession(address) && initializeSession(serviceId, address, sealedSenderAccess) == SessionInitResult.InvalidAccessKeyForPreKeyFetch) {
        return EncryptResult.InvalidAccessKeyForPreKeyFetch
      }
      encryptContent(serviceId, address, envelopeContent, sealedSenderAccess, timestamp)
    }

    return EncryptResult.Success(messages)
  }

  private suspend fun Raise<SendError>.encryptUnsealedForAllDevices(serviceId: ServiceId, envelopeContent: EnvelopeContent, timestamp: Long): List<OutgoingPushMessage> {
    return targetDeviceIds(serviceId).map { deviceId ->
      val address = SignalProtocolAddress(serviceId.libSignalServiceId, deviceId)
      if (!protocolStore.containsSession(address)) {
        initializeSession(serviceId, address, sealedSenderAccess = null)
      }
      encryptContent(serviceId, address, envelopeContent, sealedSenderAccess = null, timestamp)
    }
  }

  private suspend fun Raise<SendError>.encryptContent(
    serviceId: ServiceId,
    address: SignalProtocolAddress,
    envelopeContent: EnvelopeContent,
    sealedSenderAccess: SealedSenderAccess?,
    timestamp: Long
  ): OutgoingPushMessage = try {
    cipher.encrypt(address, sealedSenderAccess, envelopeContent)
  } catch (e: UntrustedIdentityException) {
    raise(SendError.IdentityMismatch(serviceId, e))
  } catch (e: InvalidKeyException) {
    raise(SendError.ApplicationError(e))
  } catch (e: NoSessionException) {
    Log.w(TAG, "[$timestamp] Missing or corrupt session for $address. Archiving so the next attempt rebuilds it.", e)
    protocolStore.archiveSession(address)
    raise(SendError.ApplicationError(e))
  } catch (e: InvalidSessionException) {
    Log.w(TAG, "[$timestamp] Invalid session for $address. Archiving so the next attempt rebuilds it.", e)
    protocolStore.archiveSession(address)
    raise(SendError.ApplicationError(e))
  }

  private fun List<OutgoingPushMessage>.toSealedSenderContents(): List<SingleOutboundSealedSenderMessage> {
    return map {
      SingleOutboundSealedSenderMessage(
        deviceId = it.destinationDeviceId,
        registrationId = it.destinationRegistrationId,
        message = it.content.decodeBase64OrThrow()
      )
    }
  }

  private fun OutgoingPushMessage.toUnsealedMessage(): SingleOutboundUnsealedMessage {
    val bytes = content.decodeBase64OrThrow()
    val message = when (type) {
      Envelope.Type.PREKEY_MESSAGE.value -> PreKeySignalMessage(bytes)
      Envelope.Type.DOUBLE_RATCHET.value -> SignalMessage(bytes)
      Envelope.Type.PLAINTEXT_CONTENT.value -> PlaintextContent(bytes)
      else -> throw AssertionError("Bad unsealed message type: $type")
    }

    return SingleOutboundUnsealedMessage(
      deviceId = destinationDeviceId,
      registrationId = destinationRegistrationId,
      message = message
    )
  }

  private fun targetDeviceIds(serviceId: ServiceId): List<Int> {
    val devices: MutableSet<Int> = protocolStore.getSubDeviceSessions(serviceId.toString())
      .filter { protocolStore.containsSession(SignalProtocolAddress(serviceId.libSignalServiceId, it)) }
      .toMutableSet()

    devices += SignalServiceAddress.DEFAULT_DEVICE_ID

    if (serviceId == localAddress.serviceId) {
      devices -= localDeviceId
      if (devices.isEmpty()) {
        devices += localDeviceId
      }
    }

    return devices.sorted()
  }

  /**
   * Initialize a session with the target address, which requires fetching a prekey bundle.
   */
  @VisibleForTesting
  internal open suspend fun Raise<SendError>.initializeSession(
    serviceId: ServiceId,
    address: SignalProtocolAddress,
    sealedSenderAccess: SealedSenderAccess?
  ): SessionInitResult {
    val response = when (val result = keysApi.getPreKey(address.serviceId.toServiceIdString(), address.deviceId, sealedSenderAccess)) {
      is RequestResult.Success -> result.result
      is RequestResult.NonSuccess -> {
        when (val e = result.error) {
          KeysApiV2.GetPreKeysError.Unauthorized -> {
            if (sealedSenderAccess != null) {
              return SessionInitResult.InvalidAccessKeyForPreKeyFetch
            }
            raise(SendError.Unauthorized())
          }
          KeysApiV2.GetPreKeysError.NotFound -> {
            if (sealedSenderAccess == null && address.deviceId == SignalServiceAddress.DEFAULT_DEVICE_ID) {
              raise(SendError.NotRegistered())
            }
            raise(SendError.PreKeyUnavailable("No prekeys found for $address"))
          }
          is KeysApiV2.GetPreKeysError.RateLimited -> raise(SendError.RateLimited(e.retryAfter))
        }
      }
      is RequestResult.RetryableNetworkError -> raise(result.toSendError())
      is RequestResult.ApplicationError -> raise(SendError.ApplicationError(result.cause))
    }

    createSessionFromPreKeys(serviceId, address, response)
    return SessionInitResult.Initialized
  }

  /** Builds a session with [address] from a fetched prekey [response]. */
  @VisibleForTesting
  internal open fun Raise<SendError>.createSessionFromPreKeys(serviceId: ServiceId, address: SignalProtocolAddress, response: KeysApiV2.PreKeyResponse) {
    val item = response.devices.firstOrNull { it.deviceId == address.deviceId }
      ?: raise(SendError.PreKeyUnavailable("No prekey for $address"))

    val bundle = buildPreKeyBundle(response.identityKey, item, address)

    try {
      SignalSessionBuilder(sessionLock, SessionBuilder(protocolStore, address, localProtocolAddress)).process(bundle)
    } catch (e: UntrustedIdentityException) {
      raise(SendError.IdentityMismatch(serviceId, e))
    } catch (e: InvalidKeyException) {
      raise(SendError.ApplicationError(e))
    }
  }

  private fun Raise<SendError>.buildPreKeyBundle(
    identityKey: ByteArray,
    item: KeysApiV2.PreKeyResponseItem,
    address: SignalProtocolAddress
  ): PreKeyBundle {
    val signedPreKey = item.signedPreKey ?: raise(SendError.PreKeyUnavailable("No signed prekey for $address"))
    val kyberPreKey = item.pqPreKey ?: raise(SendError.PreKeyUnavailable("No kyber prekey for $address"))

    return try {
      PreKeyBundle(
        item.registrationId,
        item.deviceId,
        item.preKey?.keyId?.toInt() ?: PreKeyBundle.NULL_PRE_KEY_ID,
        item.preKey?.let { ECPublicKey(it.publicKey) },
        signedPreKey.keyId.toInt(),
        ECPublicKey(signedPreKey.publicKey),
        signedPreKey.signature,
        IdentityKey(identityKey),
        kyberPreKey.keyId.toInt(),
        KEMPublicKey(kyberPreKey.publicKey, 0, kyberPreKey.publicKey.size),
        kyberPreKey.signature
      )
    } catch (e: InvalidKeyException) {
      raise(SendError.ApplicationError(e))
    }
  }

  private fun RequestResult.RetryableNetworkError.toSendError(): SendError {
    val retryAfter = retryAfter
    return if (retryAfter != null) {
      SendError.RateLimited(retryAfter.toKotlinDuration())
    } else {
      SendError.NetworkError(networkError)
    }
  }

  /**
   * Send completed successfully.
   *
   * [devices] is the set of recipient devices the encrypted payload was delivered to. Callers persisting
   * a [org.thoughtcrime.securesms.database.MessageSendLogTables] entry (or a pending PNI signature record)
   * need this to know which sessions the recipient may later reference in a retry receipt.
   */
  data class SendSuccess(
    val envelopeContent: EnvelopeContent,
    val sentSealedSender: Boolean,
    val devices: List<Int>
  )

  /** Result of [sendSealedWithDeviceRecovery]. */
  private sealed interface SealedSendOutcome {
    data class Sent(val success: SendSuccess) : SealedSendOutcome

    /** Every sealed sender access was rejected, for the send or for a prekey fetch. */
    data object SealedSenderAccessRejected : SealedSendOutcome
  }

  /** Result of a single sealed send request. */
  private enum class SealedSendResult {
    Success,
    MismatchedDevices,

    /** Every sealed sender access was rejected for the send. See [MessageApiV2.sendSealedSenderMessage]. */
    InvalidAccessKeyForMessageSend,

    /** A prekey fetch during mismatched-device recovery was rejected. See [SessionInitResult.InvalidAccessKeyForPreKeyFetch]. */
    InvalidAccessKeyForPreKeyFetch
  }

  /** Result of a single unsealed send request. */
  private enum class UnsealedSendResult {
    Success,
    MismatchedDevices
  }

  /** Result of [initializeSession]. Failures other than a rejected sealed sender access are raised. */
  @VisibleForTesting
  internal enum class SessionInitResult {
    Initialized,

    /** The prekey fetch rejected every sealed sender access ([KeysApiV2.getPreKey] already tries the fallbacks). Stop using sealed sender. */
    InvalidAccessKeyForPreKeyFetch
  }

  /** Result of [encryptSealedForAllDevices]. */
  private sealed interface EncryptResult {
    data class Success(val messages: List<OutgoingPushMessage>) : EncryptResult

    /** A prekey fetch for a missing session was rejected. See [SessionInitResult.InvalidAccessKeyForPreKeyFetch]. */
    data object InvalidAccessKeyForPreKeyFetch : EncryptResult
  }

  sealed class SendError : Exception() {
    /** You discovered a safety number change during sending. */
    data class IdentityMismatch(val serviceId: ServiceId, val exception: UntrustedIdentityException) : SendError()

    /** The recipient is no longer registered. */
    class NotRegistered : SendError()

    /** Invalid credentials. You are likely no longer registered. */
    class Unauthorized : SendError()

    /**
     * The server wants you to complete a push challenge/captcha before continuing.
     * [token] is the challenge token; [options] enumerates the supported challenge mechanisms
     * (e.g. "captcha", "pushChallenge"). [retryAfter] is the Retry-After hint, if provided.
     */
    data class ChallengeRequired(val token: String, val options: Set<ChallengeOption>, val retryAfter: Duration?) : SendError()

    /**
     * The encoded content exceeded the configured size cap. Permanent failure for this message —
     * retrying with the same content won't help.
     */
    data class ContentTooLarge(val size: Long, val maxAllowed: Long) : SendError()

    /**
     * Each send attempt may result in us having to establish sessions with linked devices and such. This indicates that we hit our max attempt count while
     * trying to handle these situations. It should be safe to retry with normal backoff.
     */
    class SessionAttemptsExhausted : SendError()

    /** We needed to establish a session, but the server was missing either a signed or kyber prekey for the user. */
    data class PreKeyUnavailable(val reason: String) : SendError()

    /** You're rate-limited. Use the [retryAfter] for your backoff. */
    data class RateLimited(val retryAfter: Duration?) : SendError()

    /** A generic, retryable network error. */
    data class NetworkError(val exception: IOException) : SendError()

    /** An unexpected error. You should likely crash. */
    data class ApplicationError(val exception: Throwable) : SendError()
  }
}

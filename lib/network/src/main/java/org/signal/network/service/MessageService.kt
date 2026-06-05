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
import org.signal.libsignal.net.UserBasedAuthorization
import org.signal.libsignal.net.UserBasedSendAuthorization
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.InvalidKeyException
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
        Log.w(TAG, "Content size $contentSize exceeds limit of $maxContentSizeBytes bytes; aborting send.")
        raise(SendError.ContentTooLarge(size = contentSize, maxAllowed = maxContentSizeBytes))
      }

      var encryptedReported = false
      var activeSealedSenderAccess = sealedSenderAccess
      var sealedSender = sealedSenderAccess != null || story

      // Certain errors self-resolve by mutating external state, like creating new sessions.
      // Trying several times in a loop lets us re-read that external state and use it in the next attempt.
      for (attempt in 0 until MAX_DEVICE_RECOVERY_ATTEMPTS) {
        Log.d(TAG, "Starting message send attempt ${attempt + 1} to $serviceId")
        val encryptedMessages = encryptForAllDevices(serviceId, envelopeContent, activeSealedSenderAccess)
        if (!encryptedReported) {
          onEncrypted?.invoke()
          encryptedReported = true
        }

        if (sealedSender) {
          val result = sendSealed(serviceId, encryptedMessages, timestamp, isOnline, urgent, activeSealedSenderAccess, story)
          when (result) {
            SealedSendResult.Success -> {}
            SealedSendResult.MismatchedDevices -> { continue }
            SealedSendResult.InvalidAccessKey -> {
              Log.w(TAG, "Sealed sender access was rejected for $serviceId. Falling back to an unsealed send.")
              activeSealedSenderAccess = null
              sealedSender = story
              continue
            }
          }
        } else {
          val result = sendUnsealed(serviceId, timestamp, encryptedMessages, isOnline, urgent)
          when (result) {
            UnsealedSendResult.Success -> {}
            UnsealedSendResult.MismatchedDevices -> { continue }
          }
        }

        val devices = encryptedMessages.map { it.destinationDeviceId }

        Log.d(TAG, "Successfully sent ${if (sealedSender) "a sealed" else "an unsealed"} message to $serviceId, devices: $devices")
        return@either SendSuccess(
          envelopeContent = envelopeContent,
          sentSealedSender = sealedSender,
          devices = devices
        )
      }

      Log.w(TAG, "Exhausted device-recovery attempts for $serviceId")
      raise(SendError.SessionAttemptsExhausted())
    }
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
        Log.w(TAG, "Content size $contentSize exceeds limit of $maxContentSizeBytes bytes; aborting send.")
        raise(SendError.ContentTooLarge(size = contentSize, maxAllowed = maxContentSizeBytes))
      }

      var encryptedReported = false

      // Certain errors self-resolve by mutating external state, like creating new sessions.
      // Trying several times in a loop lets us re-read that external state and use it in the next attempt.
      for (attempt in 0 until MAX_DEVICE_RECOVERY_ATTEMPTS) {
        Log.d(TAG, "Starting sync message send attempt ${attempt + 1}")
        val encryptedMessages = encryptForAllDevices(localAddress.serviceId, envelopeContent, sealedSenderAccess = null)
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

            Log.d(TAG, "Successfully sent sync message to devices: $devices")
            return@either SendSuccess(
              envelopeContent = envelopeContent,
              sentSealedSender = false,
              devices = devices
            )
          }
          is RequestResult.RetryableNetworkError -> {
            raise(SendError.NetworkError(result.networkError))
          }
          is RequestResult.ApplicationError -> {
            raise(SendError.ApplicationError(result.cause))
          }
          is RequestResult.NonSuccess<SyncSendFailure> -> {
            when (val error = result.error) {
              is MismatchedDeviceException -> {
                handleMismatched(error, sealedSenderAccess = null)
              }
              is RateLimitChallengeException -> {
                raise(SendError.ChallengeRequired(error.token, error.options, error.retryLater?.toKotlinDuration()))
              }
            }
          }
        }
      }

      Log.w(TAG, "Exhausted device-recovery attempts for sync message")
      raise(SendError.SessionAttemptsExhausted())
    }
  }

  private suspend fun Raise<SendError>.sendSealed(
    serviceId: ServiceId,
    encryptedMessages: List<OutgoingPushMessage>,
    timestamp: Long,
    online: Boolean,
    urgent: Boolean,
    sealedSenderAccess: SealedSenderAccess?,
    story: Boolean
  ): SealedSendResult {
    val auth = if (story) {
      UserBasedSendAuthorization.Story
    } else if (sealedSenderAccess is SealedSenderAccess.IndividualUnidentifiedAccessFirst) {
      if (sealedSenderAccess.unidentifiedAccess.unidentifiedAccessKey.all { it == 0.toByte() }) {
        UserBasedAuthorization.UnrestrictedUnauthenticatedAccess
      } else {
        UserBasedAuthorization.AccessKey(sealedSenderAccess.unidentifiedAccess.unidentifiedAccessKey)
      }
    } else {
      raise(SendError.ApplicationError(IllegalArgumentException("Bad sealed sender access!")))
    }

    val result = messageApi.sendSealedSenderMessage(
      serviceId = serviceId,
      timestamp = timestamp,
      contents = encryptedMessages.map {
        SingleOutboundSealedSenderMessage(
          deviceId = it.destinationDeviceId,
          registrationId = it.destinationRegistrationId,
          message = it.content.decodeBase64OrThrow()
        )
      },
      auth = auth,
      onlineOnly = online,
      urgent = urgent
    )

    return when (result) {
      is RequestResult.Success -> {
        SealedSendResult.Success
      }
      is RequestResult.RetryableNetworkError -> {
        raise(SendError.NetworkError(result.networkError))
      }
      is RequestResult.ApplicationError -> {
        raise(SendError.ApplicationError(result.cause))
      }
      is RequestResult.NonSuccess<SealedSendFailure> -> {
        when (val error = result.error) {
          is MismatchedDeviceException -> {
            handleMismatched(error, sealedSenderAccess)
            SealedSendResult.MismatchedDevices
          }
          is RequestUnauthorizedException -> {
            SealedSendResult.InvalidAccessKey
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
        raise(SendError.NetworkError(result.networkError))
      }
      is RequestResult.ApplicationError -> {
        raise(SendError.ApplicationError(result.cause))
      }
      is RequestResult.NonSuccess<UnsealedSendFailure> -> {
        when (val error = result.error) {
          is MismatchedDeviceException -> {
            handleMismatched(error, sealedSenderAccess = null)
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

  suspend fun Raise<SendError>.handleMismatched(error: MismatchedDeviceException, sealedSenderAccess: SealedSenderAccess?) {
    Log.w(TAG, "Handling mismatched devices: ${error.entries}")

    for (entry in error.entries) {
      for (staleDeviceId in entry.staleDevices) {
        Log.w(TAG, "Archiving stale session: (${entry.account}, $staleDeviceId)")
        protocolStore.archiveSession(SignalProtocolAddress(entry.account, staleDeviceId))
      }

      for (extraDeviceId in entry.extraDevices) {
        Log.w(TAG, "Archiving extra session: (${entry.account}, $extraDeviceId)")
        protocolStore.archiveSession(SignalProtocolAddress(entry.account, extraDeviceId))
      }

      for (missingDeviceId in entry.missingDevices) {
        Log.w(TAG, "Initializing session for missing device: (${entry.account}, $missingDeviceId)")
        val address = SignalProtocolAddress(entry.account, missingDeviceId)
        initializeSession(ServiceId.fromLibSignal(entry.account), address, sealedSenderAccess)
      }
    }
  }

  private suspend fun Raise<SendError>.encryptForAllDevices(
    serviceId: ServiceId,
    envelopeContent: EnvelopeContent,
    sealedSenderAccess: SealedSenderAccess?
  ): List<OutgoingPushMessage> {
    return targetDeviceIds(serviceId).map { deviceId ->
      val address = SignalProtocolAddress(serviceId.libSignalServiceId, deviceId)
      encryptContent(serviceId, address, envelopeContent, sealedSenderAccess)
    }
  }

  private suspend fun Raise<SendError>.encryptContent(
    serviceId: ServiceId,
    address: SignalProtocolAddress,
    envelopeContent: EnvelopeContent,
    sealedSenderAccess: SealedSenderAccess?
  ): OutgoingPushMessage = try {
    if (!protocolStore.containsSession(address)) {
      initializeSession(serviceId, address, sealedSenderAccess)
    }
    cipher.encrypt(address, sealedSenderAccess, envelopeContent)
  } catch (e: UntrustedIdentityException) {
    raise(SendError.IdentityMismatch(serviceId, e))
  } catch (e: InvalidKeyException) {
    raise(SendError.ApplicationError(e))
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
  ) {
    val response = when (val result = keysApi.getPreKey(address.serviceId.toServiceIdString(), address.deviceId, sealedSenderAccess)) {
      is RequestResult.Success -> result.result
      is RequestResult.NonSuccess -> {
        when (val e = result.error) {
          KeysApiV2.GetPreKeysError.Unauthorized -> raise(SendError.Unauthorized())
          KeysApiV2.GetPreKeysError.NotFound -> raise(SendError.PreKeyUnavailable("No prekeys found for $address"))
          is KeysApiV2.GetPreKeysError.RateLimited -> raise(SendError.RateLimited(e.retryAfter))
        }
      }
      is RequestResult.RetryableNetworkError -> raise(SendError.NetworkError(result.networkError))
      is RequestResult.ApplicationError -> raise(SendError.ApplicationError(result.cause))
    }

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

  private enum class SealedSendResult {
    Success, InvalidAccessKey, MismatchedDevices
  }

  private enum class UnsealedSendResult {
    Success, MismatchedDevices
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

    /** The server has fully rejected your request. This usually only happens during times of turmoil. Fail and require user action to resend. */
    class ServerRejected : SendError()

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

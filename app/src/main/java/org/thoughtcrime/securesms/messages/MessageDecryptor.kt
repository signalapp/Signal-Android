package org.thoughtcrime.securesms.messages

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.squareup.wire.internal.toUnmodifiableList
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.logging.Log
import org.signal.libsignal.metadata.InvalidMetadataMessageException
import org.signal.libsignal.metadata.InvalidMetadataVersionException
import org.signal.libsignal.metadata.ProtocolDuplicateMessageException
import org.signal.libsignal.metadata.ProtocolException
import org.signal.libsignal.metadata.ProtocolInvalidKeyException
import org.signal.libsignal.metadata.ProtocolInvalidKeyIdException
import org.signal.libsignal.metadata.ProtocolInvalidMessageException
import org.signal.libsignal.metadata.ProtocolInvalidVersionException
import org.signal.libsignal.metadata.ProtocolLegacyMessageException
import org.signal.libsignal.metadata.ProtocolNoSessionException
import org.signal.libsignal.metadata.ProtocolUntrustedIdentityException
import org.signal.libsignal.metadata.SelfSendException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.groups.GroupSessionBuilder
import org.signal.libsignal.protocol.groups.state.SenderKeyStore
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.DecryptionErrorMessage
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.groups.BadGroupIdException
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.AutomaticSessionResetJob
import org.thoughtcrime.securesms.jobs.PreKeysSyncJob
import org.thoughtcrime.securesms.jobs.SendRetryReceiptJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import org.thoughtcrime.securesms.messages.MessageDecryptor.FollowUpOperation
import org.thoughtcrime.securesms.messages.protocol.BufferedProtocolStore
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.FeatureFlags
import org.whispersystems.signalservice.api.InvalidMessageStructureException
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.api.crypto.SignalGroupSessionBuilder
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher
import org.whispersystems.signalservice.api.crypto.SignalServiceCipherResult
import org.whispersystems.signalservice.api.messages.EnvelopeContentValidator
import org.whispersystems.signalservice.api.push.PNI
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Content
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Envelope
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.PniSignatureMessage
import java.util.Optional

/**
 * This class is designed to handle everything around the process of taking an [Envelope] and decrypting it into something
 * that you can use (or provide an appropriate error if something goes wrong). We'll also use this space to go over some
 * high-level concepts in message decryption.
 */
object MessageDecryptor {

  private val TAG = Log.tag(MessageDecryptor::class.java)

  /**
   * Decrypts an envelope and provides a [Result]. This method has side effects, but all of them are limited to [SignalDatabase].
   * That means that this operation should be atomic when performed within a transaction.
   * To keep that property, there may be [Result.followUpOperations] you have to perform after your transaction is committed.
   * These can vary from enqueueing jobs to inserting items into the [org.thoughtcrime.securesms.database.PendingRetryReceiptCache].
   */
  fun decrypt(
    context: Context,
    bufferedProtocolStore: BufferedProtocolStore,
    envelope: Envelope,
    serverDeliveredTimestamp: Long
  ): Result {
    val selfAci: ServiceId = SignalStore.account().requireAci()
    val selfPni: ServiceId = SignalStore.account().requirePni()

    val destination: ServiceId = envelope.getDestination(selfAci, selfPni)

    if (destination == selfPni && envelope.hasSourceUuid()) {
      Log.i(TAG, "${logPrefix(envelope)} Received a message at our PNI. Marking as needing a PNI signature.")

      val sourceServiceId = ServiceId.parseOrNull(envelope.sourceUuid)

      if (sourceServiceId != null) {
        val sender = RecipientId.from(sourceServiceId)
        SignalDatabase.recipients.markNeedsPniSignature(sender)
      } else {
        Log.w(TAG, "${logPrefix(envelope)} Could not mark sender as needing a PNI signature because the sender serviceId was invalid!")
      }
    }

    if (destination == selfPni && !envelope.hasSourceUuid()) {
      Log.w(TAG, "${logPrefix(envelope)} Got a sealed sender message to our PNI? Invalid message, ignoring.")
      return Result.Ignore(envelope, serverDeliveredTimestamp, emptyList())
    }

    val followUpOperations: MutableList<FollowUpOperation> = mutableListOf()

    if (envelope.type == Envelope.Type.PREKEY_BUNDLE) {
      followUpOperations += FollowUpOperation {
        PreKeysSyncJob.create()
      }
    }

    val bufferedStore = bufferedProtocolStore.get(destination)
    val localAddress = SignalServiceAddress(selfAci, SignalStore.account().e164)
    val cipher = SignalServiceCipher(localAddress, SignalStore.account().deviceId, bufferedStore, ReentrantSessionLock.INSTANCE, UnidentifiedAccessUtil.getCertificateValidator())

    return try {
      val cipherResult: SignalServiceCipherResult? = cipher.decrypt(envelope, serverDeliveredTimestamp)

      if (cipherResult == null) {
        Log.w(TAG, "${logPrefix(envelope)} Decryption resulted in a null result!", true)
        return Result.Ignore(envelope, serverDeliveredTimestamp, followUpOperations.toUnmodifiableList())
      }

      Log.d(TAG, "${logPrefix(envelope, cipherResult)} Successfully decrypted the envelope.")

      val validationResult: EnvelopeContentValidator.Result = EnvelopeContentValidator.validate(envelope, cipherResult.content)

      if (validationResult is EnvelopeContentValidator.Result.Invalid) {
        Log.w(TAG, "${logPrefix(envelope, cipherResult)} Invalid content! ${validationResult.reason}", validationResult.throwable)
        return Result.Ignore(envelope, serverDeliveredTimestamp, followUpOperations.toUnmodifiableList())
      }

      if (validationResult is EnvelopeContentValidator.Result.UnsupportedDataMessage) {
        Log.w(TAG, "${logPrefix(envelope, cipherResult)} Unsupported DataMessage! Our version: ${validationResult.ourVersion}, their version: ${validationResult.theirVersion}")
        return Result.UnsupportedDataMessage(envelope, serverDeliveredTimestamp, cipherResult.toErrorMetadata(), followUpOperations.toUnmodifiableList())
      }

      // Must handle SKDM's immediately, because subsequent decryptions could rely on it
      if (cipherResult.content.hasSenderKeyDistributionMessage()) {
        handleSenderKeyDistributionMessage(
          envelope,
          cipherResult.metadata.sourceServiceId,
          cipherResult.metadata.sourceDeviceId,
          SenderKeyDistributionMessage(cipherResult.content.senderKeyDistributionMessage.toByteArray()),
          bufferedProtocolStore.getAciStore()
        )
      }

      if (FeatureFlags.phoneNumberPrivacy() && cipherResult.content.hasPniSignatureMessage()) {
        handlePniSignatureMessage(
          envelope,
          cipherResult.metadata.sourceServiceId,
          cipherResult.metadata.sourceE164,
          cipherResult.metadata.sourceDeviceId,
          cipherResult.content.pniSignatureMessage
        )
      } else if (cipherResult.content.hasPniSignatureMessage()) {
        Log.w(TAG, "${logPrefix(envelope)} Ignoring PNI signature because the feature flag is disabled!")
      }

      // TODO We can move this to the "message processing" stage once we give it access to the envelope. But for now it'll stay here.
      if (envelope.hasReportingToken() && envelope.reportingToken != null && envelope.reportingToken.size() > 0) {
        val sender = RecipientId.from(cipherResult.metadata.sourceServiceId)
        SignalDatabase.recipients.setReportingToken(sender, envelope.reportingToken.toByteArray())
      }

      Result.Success(envelope, serverDeliveredTimestamp, cipherResult.content, cipherResult.metadata, followUpOperations.toUnmodifiableList())
    } catch (e: Exception) {
      when (e) {
        is ProtocolInvalidKeyIdException,
        is ProtocolInvalidKeyException,
        is ProtocolUntrustedIdentityException,
        is ProtocolNoSessionException,
        is ProtocolInvalidMessageException -> {
          check(e is ProtocolException)
          Log.w(TAG, "${logPrefix(envelope, e)} Decryption error!", e, true)

          if (FeatureFlags.internalUser()) {
            postErrorNotification(context)
          }

          if (FeatureFlags.retryReceipts()) {
            buildResultForDecryptionError(context, envelope, serverDeliveredTimestamp, followUpOperations, e)
          } else {
            Log.w(TAG, "${logPrefix(envelope, e)} Retry receipts disabled! Enqueuing a session reset job, which will also insert an error message.", e, true)

            followUpOperations += FollowUpOperation {
              val sender: Recipient = Recipient.external(context, e.sender)
              AutomaticSessionResetJob(sender.id, e.senderDevice, envelope.timestamp)
            }

            Result.Ignore(envelope, serverDeliveredTimestamp, followUpOperations.toUnmodifiableList())
          }
        }

        is ProtocolDuplicateMessageException -> {
          Log.w(TAG, "${logPrefix(envelope, e)} Duplicate message!", e)
          Result.Ignore(envelope, serverDeliveredTimestamp, followUpOperations.toUnmodifiableList())
        }

        is InvalidMetadataVersionException,
        is InvalidMetadataMessageException,
        is InvalidMessageStructureException -> {
          Log.w(TAG, "${logPrefix(envelope)} Invalid message structure!", e, true)
          Result.Ignore(envelope, serverDeliveredTimestamp, followUpOperations.toUnmodifiableList())
        }

        is SelfSendException -> {
          Log.i(TAG, "[${envelope.timestamp}] Dropping sealed sender message from self!", e)
          Result.Ignore(envelope, serverDeliveredTimestamp, followUpOperations.toUnmodifiableList())
        }

        is ProtocolInvalidVersionException -> {
          Log.w(TAG, "${logPrefix(envelope, e)} Invalid version!", e, true)
          Result.InvalidVersion(envelope, serverDeliveredTimestamp, e.toErrorMetadata(), followUpOperations.toUnmodifiableList())
        }

        is ProtocolLegacyMessageException -> {
          Log.w(TAG, "${logPrefix(envelope, e)} Legacy message!", e, true)
          Result.LegacyMessage(envelope, serverDeliveredTimestamp, e.toErrorMetadata(), followUpOperations)
        }

        else -> {
          Log.w(TAG, "Encountered an unexpected exception! Throwing!", e, true)
          throw e
        }
      }
    }
  }

  private fun buildResultForDecryptionError(
    context: Context,
    envelope: Envelope,
    serverDeliveredTimestamp: Long,
    followUpOperations: MutableList<FollowUpOperation>,
    protocolException: ProtocolException
  ): Result {
    val contentHint: ContentHint = ContentHint.fromType(protocolException.contentHint)
    val senderDevice: Int = protocolException.senderDevice
    val receivedTimestamp: Long = System.currentTimeMillis()
    val sender: Recipient = Recipient.external(context, protocolException.sender)

    followUpOperations += FollowUpOperation {
      buildSendRetryReceiptJob(envelope, protocolException, sender)
    }

    return when (contentHint) {
      ContentHint.DEFAULT -> {
        Log.w(TAG, "${logPrefix(envelope)} The content hint is $contentHint, so we need to insert an error right away.", true)
        Result.DecryptionError(envelope, serverDeliveredTimestamp, protocolException.toErrorMetadata(), followUpOperations.toUnmodifiableList())
      }

      ContentHint.RESENDABLE -> {
        Log.w(TAG, "${logPrefix(envelope)} The content hint is $contentHint, so we can try to resend the message.", true)

        followUpOperations += FollowUpOperation {
          val groupId: GroupId? = protocolException.parseGroupId(envelope)
          val threadId: Long = if (groupId != null) {
            val groupRecipient: Recipient = Recipient.externalPossiblyMigratedGroup(groupId)
            SignalDatabase.threads.getOrCreateThreadIdFor(groupRecipient)
          } else {
            SignalDatabase.threads.getOrCreateThreadIdFor(sender)
          }

          ApplicationDependencies.getPendingRetryReceiptCache().insert(sender.id, senderDevice, envelope.timestamp, receivedTimestamp, threadId)
          ApplicationDependencies.getPendingRetryReceiptManager().scheduleIfNecessary()
          null
        }

        Result.Ignore(envelope, serverDeliveredTimestamp, followUpOperations)
      }

      ContentHint.IMPLICIT -> {
        Log.w(TAG, "${logPrefix(envelope)} The content hint is $contentHint, so no error message is needed.", true)
        Result.Ignore(envelope, serverDeliveredTimestamp, followUpOperations)
      }
    }
  }

  private fun handleSenderKeyDistributionMessage(envelope: Envelope, serviceId: ServiceId, deviceId: Int, message: SenderKeyDistributionMessage, senderKeyStore: SenderKeyStore) {
    Log.i(TAG, "${logPrefix(envelope, serviceId)} Processing SenderKeyDistributionMessage for distributionId ${message.distributionId}")

    val sender = SignalProtocolAddress(serviceId.toString(), deviceId)
    SignalGroupSessionBuilder(ReentrantSessionLock.INSTANCE, GroupSessionBuilder(senderKeyStore)).process(sender, message)
  }

  private fun handlePniSignatureMessage(envelope: Envelope, serviceId: ServiceId, e164: String?, deviceId: Int, pniSignatureMessage: PniSignatureMessage) {
    Log.i(TAG, "${logPrefix(envelope, serviceId)} Processing PniSignatureMessage")

    val pni: PNI = PNI.parseOrThrow(pniSignatureMessage.pni.toByteArray())

    if (SignalDatabase.recipients.isAssociated(serviceId, pni)) {
      Log.i(TAG, "${logPrefix(envelope, serviceId)}[handlePniSignatureMessage] ACI ($serviceId) and PNI ($pni) are already associated.")
      return
    }

    val identityStore = ApplicationDependencies.getProtocolStore().aci().identities()
    val aciAddress = SignalProtocolAddress(serviceId.toString(), deviceId)
    val pniAddress = SignalProtocolAddress(pni.toString(), deviceId)
    val aciIdentity = identityStore.getIdentity(aciAddress)
    val pniIdentity = identityStore.getIdentity(pniAddress)

    if (aciIdentity == null) {
      Log.w(TAG, "${logPrefix(envelope, serviceId)}[validatePniSignature] No identity found for ACI address $aciAddress")
      return
    }

    if (pniIdentity == null) {
      Log.w(TAG, "${logPrefix(envelope, serviceId)}[validatePniSignature] No identity found for PNI address $pniAddress")
      return
    }

    if (pniIdentity.verifyAlternateIdentity(aciIdentity, pniSignatureMessage.signature.toByteArray())) {
      Log.i(TAG, "${logPrefix(envelope, serviceId)}[validatePniSignature] PNI signature is valid. Associating ACI ($serviceId) with PNI ($pni)")
      SignalDatabase.recipients.getAndPossiblyMergePnpVerified(serviceId, pni, e164)
    } else {
      Log.w(TAG, "${logPrefix(envelope, serviceId)}[validatePniSignature] Invalid PNI signature! Cannot associate ACI ($serviceId) with PNI ($pni)")
    }
  }

  private fun postErrorNotification(context: Context) {
    val notification: Notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(context.getString(R.string.MessageDecryptionUtil_failed_to_decrypt_message))
      .setContentText(context.getString(R.string.MessageDecryptionUtil_tap_to_send_a_debug_log))
      .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, SubmitDebugLogActivity::class.java), PendingIntentFlags.mutable()))
      .build()

    NotificationManagerCompat.from(context).notify(NotificationIds.INTERNAL_ERROR, notification)
  }

  private fun logPrefix(envelope: Envelope): String {
    return logPrefix(envelope.timestamp, envelope.sourceUuid ?: "<sealed>", envelope.sourceDevice)
  }

  private fun logPrefix(envelope: Envelope, sender: ServiceId): String {
    return logPrefix(envelope.timestamp, sender.toString(), envelope.sourceDevice)
  }

  private fun logPrefix(envelope: Envelope, cipherResult: SignalServiceCipherResult): String {
    return logPrefix(envelope.timestamp, cipherResult.metadata.sourceServiceId.toString(), cipherResult.metadata.sourceDeviceId)
  }

  private fun logPrefix(envelope: Envelope, exception: ProtocolException): String {
    return if (exception.sender != null) {
      logPrefix(envelope.timestamp, exception.sender, exception.senderDevice)
    } else {
      logPrefix(envelope.timestamp, envelope.sourceUuid, envelope.sourceDevice)
    }
  }

  private fun logPrefix(timestamp: Long, sender: String?, deviceId: Int): String {
    val senderString = sender ?: "null"
    return "[$timestamp] $senderString:$deviceId |"
  }

  private fun buildSendRetryReceiptJob(envelope: Envelope, protocolException: ProtocolException, sender: Recipient): SendRetryReceiptJob {
    val originalContent: ByteArray
    val envelopeType: Int

    if (protocolException.unidentifiedSenderMessageContent.isPresent) {
      originalContent = protocolException.unidentifiedSenderMessageContent.get().content
      envelopeType = protocolException.unidentifiedSenderMessageContent.get().type
    } else {
      originalContent = envelope.content.toByteArray()
      envelopeType = envelope.type.number.toCiphertextMessageType()
    }

    val decryptionErrorMessage: DecryptionErrorMessage = DecryptionErrorMessage.forOriginalMessage(originalContent, envelopeType, envelope.timestamp, protocolException.senderDevice)
    val groupId: GroupId? = protocolException.parseGroupId(envelope)
    return SendRetryReceiptJob(sender.id, Optional.ofNullable(groupId), decryptionErrorMessage)
  }

  private fun ProtocolException.parseGroupId(envelope: Envelope): GroupId? {
    return if (this.groupId.isPresent) {
      try {
        GroupId.push(this.groupId.get())
      } catch (e: BadGroupIdException) {
        Log.w(TAG, "[${envelope.timestamp}] Bad groupId!", true)
        null
      }
    } else {
      null
    }
  }

  private fun Envelope.getDestination(selfAci: ServiceId, selfPni: ServiceId): ServiceId {
    return if (!FeatureFlags.phoneNumberPrivacy()) {
      selfAci
    } else if (this.hasDestinationUuid()) {
      val serviceId = ServiceId.parseOrThrow(this.destinationUuid)
      if (serviceId == selfAci || serviceId == selfPni) {
        serviceId
      } else {
        Log.w(TAG, "Destination of $serviceId does not match our ACI ($selfAci) or PNI ($selfPni)! Defaulting to ACI.")
        selfAci
      }
    } else {
      Log.w(TAG, "No destinationUuid set! Defaulting to ACI.")
      selfAci
    }
  }

  private fun Int.toCiphertextMessageType(): Int {
    return when (this) {
      Envelope.Type.CIPHERTEXT_VALUE -> CiphertextMessage.WHISPER_TYPE
      Envelope.Type.PREKEY_BUNDLE_VALUE -> CiphertextMessage.PREKEY_TYPE
      Envelope.Type.UNIDENTIFIED_SENDER_VALUE -> CiphertextMessage.SENDERKEY_TYPE
      Envelope.Type.PLAINTEXT_CONTENT_VALUE -> CiphertextMessage.PLAINTEXT_CONTENT_TYPE
      else -> CiphertextMessage.WHISPER_TYPE
    }
  }

  private fun ProtocolException.toErrorMetadata(): ErrorMetadata {
    return ErrorMetadata(
      sender = this.sender,
      senderDevice = this.senderDevice,
      groupId = if (this.groupId.isPresent) GroupId.v2(GroupMasterKey(this.groupId.get())) else null
    )
  }

  private fun SignalServiceCipherResult.toErrorMetadata(): ErrorMetadata {
    return ErrorMetadata(
      sender = this.metadata.sourceServiceId.toString(),
      senderDevice = this.metadata.sourceDeviceId,
      groupId = null
    )
  }

  sealed interface Result {
    val envelope: Envelope
    val serverDeliveredTimestamp: Long
    val followUpOperations: List<FollowUpOperation>

    /** Successfully decrypted the envelope content. The plaintext [Content] is available. */
    data class Success(
      override val envelope: Envelope,
      override val serverDeliveredTimestamp: Long,
      val content: Content,
      val metadata: EnvelopeMetadata,
      override val followUpOperations: List<FollowUpOperation>
    ) : Result

    /** We could not decrypt the message, and an error should be inserted into the user's chat history. */
    class DecryptionError(
      override val envelope: Envelope,
      override val serverDeliveredTimestamp: Long,
      override val errorMetadata: ErrorMetadata,
      override val followUpOperations: List<FollowUpOperation>
    ) : Result, Error

    /** The envelope used an invalid version of the Signal protocol. */
    class InvalidVersion(
      override val envelope: Envelope,
      override val serverDeliveredTimestamp: Long,
      override val errorMetadata: ErrorMetadata,
      override val followUpOperations: List<FollowUpOperation>
    ) : Result, Error

    /** The envelope used an old format that hasn't been used since 2015. This shouldn't be happening. */
    class LegacyMessage(
      override val envelope: Envelope,
      override val serverDeliveredTimestamp: Long,
      override val errorMetadata: ErrorMetadata,
      override val followUpOperations: List<FollowUpOperation>
    ) : Result, Error

    /**
     * Indicates the that the [org.whispersystems.signalservice.internal.push.SignalServiceProtos.DataMessage.getRequiredProtocolVersion]
     * is higher than we support.
     */
    class UnsupportedDataMessage(
      override val envelope: Envelope,
      override val serverDeliveredTimestamp: Long,
      override val errorMetadata: ErrorMetadata,
      override val followUpOperations: List<FollowUpOperation>
    ) : Result, Error

    /** There are no further results from this envelope that need to be processed. There may still be [followUpOperations]. */
    class Ignore(
      override val envelope: Envelope,
      override val serverDeliveredTimestamp: Long,
      override val followUpOperations: List<FollowUpOperation>
    ) : Result

    interface Error {
      val errorMetadata: ErrorMetadata
    }
  }

  data class ErrorMetadata(
    val sender: String,
    val senderDevice: Int,
    val groupId: GroupId?
  )

  fun interface FollowUpOperation {
    fun run(): Job?
  }
}

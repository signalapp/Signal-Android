package org.thoughtcrime.securesms.jobs

import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.signal.core.util.PendingIntentFlags.mutable
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messages.MessageContentProcessor.ExceptionMetadata
import org.thoughtcrime.securesms.messages.MessageContentProcessor.MessageState
import org.thoughtcrime.securesms.messages.MessageDecryptor
import org.thoughtcrime.securesms.messages.protocol.BufferedProtocolStore
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.transport.RetryLaterException
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope
import org.whispersystems.signalservice.api.messages.SignalServiceMetadata
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer
import org.whispersystems.signalservice.internal.serialize.SignalServiceMetadataProtobufSerializer
import org.whispersystems.signalservice.internal.serialize.protos.SignalServiceContentProto
import java.util.Optional

/**
 * Decrypts an envelope. Enqueues a separate job, [PushProcessMessageJob], to actually insert
 * the result into our database.
 */
class PushDecryptMessageJob private constructor(
  parameters: Parameters,
  private val envelope: SignalServiceEnvelope,
  private val smsMessageId: Long
) : BaseJob(parameters) {

  companion object {
    val TAG = Log.tag(PushDecryptMessageJob::class.java)

    const val KEY = "PushDecryptJob"
    const val QUEUE = "__PUSH_DECRYPT_JOB__"

    private const val KEY_SMS_MESSAGE_ID = "sms_message_id"
    private const val KEY_ENVELOPE = "envelope"
  }

  @Deprecated("No more jobs of this type should be enqueued. Decryptions now happen as things come off of the websocket.")
  @JvmOverloads
  constructor(envelope: SignalServiceEnvelope, smsMessageId: Long = -1) : this(
    Parameters.Builder()
      .setQueue(QUEUE)
      .setMaxAttempts(Parameters.UNLIMITED)
      .build(),
    envelope,
    smsMessageId
  )

  override fun shouldTrace() = true

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putBlobAsString(KEY_ENVELOPE, envelope.serialize())
      .putLong(KEY_SMS_MESSAGE_ID, smsMessageId)
      .serialize()
  }

  override fun getFactoryKey() = KEY

  @Throws(RetryLaterException::class)
  public override fun onRun() {
    if (needsMigration()) {
      Log.w(TAG, "Migration is still needed.")
      postMigrationNotification()
      throw RetryLaterException()
    }

    val bufferedProtocolStore = BufferedProtocolStore.create()
    val result = MessageDecryptor.decrypt(context, bufferedProtocolStore, envelope.proto, envelope.serverDeliveredTimestamp)
    bufferedProtocolStore.flushToDisk()

    when (result) {
      is MessageDecryptor.Result.Success -> {
        ApplicationDependencies.getJobManager().add(
          PushProcessMessageJob(
            result.toMessageState(),
            result.toSignalServiceContent(),
            null,
            smsMessageId,
            result.envelope.timestamp
          )
        )
      }

      is MessageDecryptor.Result.Error -> {
        ApplicationDependencies.getJobManager().add(
          PushProcessMessageJob(
            result.toMessageState(),
            null,
            result.errorMetadata.toExceptionMetadata(),
            smsMessageId,
            result.envelope.timestamp
          )
        )
      }

      is MessageDecryptor.Result.Ignore -> {
        // No action needed
      }

      else -> {
        throw AssertionError("Unexpected result! ${result.javaClass.simpleName}")
      }
    }

    result.followUpOperations.forEach { it.run() }
  }

  public override fun onShouldRetry(exception: Exception): Boolean {
    return exception is RetryLaterException
  }

  override fun onFailure() = Unit

  private fun needsMigration(): Boolean {
    return TextSecurePreferences.getNeedsSqlCipherMigration(context)
  }

  private fun MessageDecryptor.Result.toMessageState(): MessageState {
    return when (this) {
      is MessageDecryptor.Result.DecryptionError -> MessageState.DECRYPTION_ERROR
      is MessageDecryptor.Result.Ignore -> MessageState.NOOP
      is MessageDecryptor.Result.InvalidVersion -> MessageState.INVALID_VERSION
      is MessageDecryptor.Result.LegacyMessage -> MessageState.LEGACY_MESSAGE
      is MessageDecryptor.Result.Success -> MessageState.DECRYPTED_OK
      is MessageDecryptor.Result.UnsupportedDataMessage -> MessageState.UNSUPPORTED_DATA_MESSAGE
    }
  }

  private fun MessageDecryptor.Result.Success.toSignalServiceContent(): SignalServiceContent {
    val localAddress = SignalServiceAddress(this.metadata.destinationServiceId, Optional.ofNullable(SignalStore.account().e164))
    val metadata = SignalServiceMetadata(
      SignalServiceAddress(this.metadata.sourceServiceId, Optional.ofNullable(this.metadata.sourceE164)),
      this.metadata.sourceDeviceId,
      this.envelope.timestamp,
      this.envelope.serverTimestamp,
      this.serverDeliveredTimestamp,
      this.metadata.sealedSender,
      this.envelope.serverGuid,
      Optional.ofNullable(this.metadata.groupId),
      this.metadata.destinationServiceId.toString()
    )

    val contentProto = SignalServiceContentProto.newBuilder()
      .setLocalAddress(SignalServiceAddressProtobufSerializer.toProtobuf(localAddress))
      .setMetadata(SignalServiceMetadataProtobufSerializer.toProtobuf(metadata))
      .setContent(content)
      .build()

    return SignalServiceContent.createFromProto(contentProto)!!
  }

  private fun MessageDecryptor.ErrorMetadata.toExceptionMetadata(): ExceptionMetadata {
    return ExceptionMetadata(
      this.sender,
      this.senderDevice,
      this.groupId
    )
  }

  private fun postMigrationNotification() {
    val notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().messagesChannel)
      .setSmallIcon(R.drawable.ic_notification)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setContentTitle(context.getString(R.string.PushDecryptJob_new_locked_message))
      .setContentText(context.getString(R.string.PushDecryptJob_unlock_to_view_pending_messages))
      .setContentIntent(PendingIntent.getActivity(context, 0, MainActivity.clearTop(context), mutable()))
      .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
      .build()

    NotificationManagerCompat.from(context).notify(NotificationIds.LEGACY_SQLCIPHER_MIGRATION, notification)
  }

  class Factory : Job.Factory<PushDecryptMessageJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): PushDecryptMessageJob {
      val data = JsonJobData.deserialize(serializedData)
      return PushDecryptMessageJob(
        parameters,
        SignalServiceEnvelope.deserialize(data.getStringAsBlob(KEY_ENVELOPE)),
        data.getLong(KEY_SMS_MESSAGE_ID)
      )
    }
  }
}

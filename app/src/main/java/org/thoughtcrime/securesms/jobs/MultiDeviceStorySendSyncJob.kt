package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.OutgoingContent
import org.whispersystems.signalservice.api.messages.SignalServiceStoryMessageRecipient
import org.whispersystems.signalservice.internal.push.SyncMessage
import java.util.concurrent.TimeUnit

/**
 * Transmits a sent sync transcript to linked devices containing the story sync manifest for the given sent timestamp.
 * The transmitted message will contain all current recipients of a given story.
 */
class MultiDeviceStorySendSyncJob private constructor(parameters: Parameters, private val sentTimestamp: Long, private val deletedMessageId: Long) : CoroutineJob(parameters) {

  companion object {
    const val KEY = "MultiDeviceStorySendSyncJob"

    private val TAG = Log.tag(MultiDeviceStorySendSyncJob::class.java)

    private const val DATA_SENT_TIMESTAMP = "sent.timestamp"
    private const val DATA_DELETED_MESSAGE_ID = "deleted.message.id"

    @JvmStatic
    fun create(sentTimestamp: Long, deletedMessageId: Long): MultiDeviceStorySendSyncJob {
      return MultiDeviceStorySendSyncJob(
        parameters = Parameters.Builder()
          .addConstraint(NetworkConstraint.KEY)
          .addConstraint(SealedSenderConstraint.KEY)
          .setMaxAttempts(Parameters.UNLIMITED)
          .setLifespan(TimeUnit.DAYS.toMillis(1))
          .setQueue(KEY)
          .build(),
        sentTimestamp = sentTimestamp,
        deletedMessageId = deletedMessageId
      )
    }
  }

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putLong(DATA_SENT_TIMESTAMP, sentTimestamp)
      .putLong(DATA_DELETED_MESSAGE_ID, deletedMessageId)
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    val updateManifest = SignalDatabase.storySends.getLocalManifest(sentTimestamp)
    val recipientsSet: Set<SignalServiceStoryMessageRecipient> = updateManifest.toRecipientsSet()

    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = sentTimestamp,
      envelopeContent = OutgoingContent.syncSent(buildSentTranscript(recipientsSet)),
      urgent = false,
      onEncrypted = null
    )

    return result.fold(
      ifRight = {
        Log.i(TAG, "Sent transcript message with ${recipientsSet.size} recipients")
        SignalDatabase.messages.deleteRemotelyDeletedStory(deletedMessageId)
        Result.success()
      },
      ifLeft = { error ->
        error.toSyncJobResult(TAG, "story recipient update transcript", defaultBackoff())
      }
    )
  }

  private fun buildSentTranscript(recipientsSet: Set<SignalServiceStoryMessageRecipient>): SyncMessage.Sent {
    val self = Recipient.self().requireAci().toByteString()

    return SyncMessage.Sent(
      destinationServiceIdBinary = self,
      timestamp = sentTimestamp,
      unidentifiedStatus = listOf(
        SyncMessage.Sent.UnidentifiedDeliveryStatus(
          destinationServiceIdBinary = self,
          unidentified = false
        )
      ),
      storyMessageRecipients = recipientsSet.map {
        SyncMessage.Sent.StoryMessageRecipient(
          distributionListIds = it.distributionListIds,
          destinationServiceIdBinary = it.signalServiceAddress.serviceId.toByteString(),
          isAllowedToReply = it.isAllowedToReply
        )
      }.distinct(),
      isRecipientUpdate = true
    )
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<MultiDeviceStorySendSyncJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceStorySendSyncJob {
      val data = JsonJobData.deserialize(serializedData)
      return MultiDeviceStorySendSyncJob(
        parameters = parameters,
        sentTimestamp = data.getLong(DATA_SENT_TIMESTAMP),
        deletedMessageId = data.getLong(DATA_DELETED_MESSAGE_ID)
      )
    }
  }
}

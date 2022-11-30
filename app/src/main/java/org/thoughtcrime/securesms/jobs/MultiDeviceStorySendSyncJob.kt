package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Data
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.messages.SignalServiceStoryMessageRecipient
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import java.lang.Exception
import java.util.Optional
import java.util.concurrent.TimeUnit

/**
 * Transmits a sent sync transcript to linked devices containing the story sync manifest for the given sent timestamp.
 * The transmitted message will contain all current recipients of a given story.
 */
class MultiDeviceStorySendSyncJob private constructor(parameters: Parameters, private val sentTimestamp: Long, private val deletedMessageId: Long) : BaseJob(parameters) {

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
          .setMaxAttempts(Parameters.UNLIMITED)
          .setLifespan(TimeUnit.DAYS.toMillis(1))
          .setQueue(KEY)
          .build(),
        sentTimestamp = sentTimestamp,
        deletedMessageId = deletedMessageId
      )
    }
  }

  override fun serialize(): Data {
    return Data.Builder()
      .putLong(DATA_SENT_TIMESTAMP, sentTimestamp)
      .putLong(DATA_DELETED_MESSAGE_ID, deletedMessageId)
      .build()
  }

  override fun getFactoryKey(): String = KEY

  override fun onRun() {
    val updateManifest = SignalDatabase.storySends.getLocalManifest(sentTimestamp)
    val recipientsSet: Set<SignalServiceStoryMessageRecipient> = updateManifest.toRecipientsSet()
    val transcriptMessage: SignalServiceSyncMessage = SignalServiceSyncMessage.forSentTranscript(buildSentTranscript(recipientsSet))
    val sendMessageResult = ApplicationDependencies.getSignalServiceMessageSender().sendSyncMessage(transcriptMessage, Optional.empty())

    Log.i(TAG, "Sent transcript message with ${recipientsSet.size} recipients")

    if (!sendMessageResult.isSuccess) {
      throw RetryableException()
    }

    SignalDatabase.mms.deleteRemotelyDeletedStory(deletedMessageId)
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return e is RetryableException
  }

  private fun buildSentTranscript(recipientsSet: Set<SignalServiceStoryMessageRecipient>): SentTranscriptMessage {
    return SentTranscriptMessage(
      Optional.of(SignalServiceAddress(Recipient.self().requireServiceId())),
      sentTimestamp,
      Optional.empty(),
      0,
      emptyMap(),
      true,
      Optional.empty(),
      recipientsSet
    )
  }

  override fun onFailure() = Unit

  class RetryableException : Exception()

  class Factory : Job.Factory<MultiDeviceStorySendSyncJob> {
    override fun create(parameters: Parameters, data: Data): MultiDeviceStorySendSyncJob {
      return MultiDeviceStorySendSyncJob(
        parameters = parameters,
        sentTimestamp = data.getLong(DATA_SENT_TIMESTAMP),
        deletedMessageId = data.getLong(DATA_DELETED_MESSAGE_ID)
      )
    }
  }
}

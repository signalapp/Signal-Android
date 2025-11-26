package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.PollVoteJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messages.GroupSendUtil
import org.thoughtcrime.securesms.polls.PollRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.util.GroupUtil
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.Companion.newBuilder
import kotlin.time.Duration.Companion.days

/**
 * Sends a poll vote for a given poll in a group. If the vote completely fails to send, we do our best to undo that vote.
 */
class PollVoteJob(
  private val messageId: Long,
  private val recipientIds: MutableList<Long>,
  private val initialRecipientCount: Int,
  private val voteCount: Int,
  private val isRemoval: Boolean,
  private val optionId: Long,
  parameters: Parameters
) : Job(parameters) {

  companion object {
    const val KEY: String = "PollVoteJob"
    private val TAG = Log.tag(PollVoteJob::class.java)

    fun create(messageId: Long, voteCount: Int, isRemoval: Boolean, optionId: Long): PollVoteJob? {
      val message = SignalDatabase.messages.getMessageRecordOrNull(messageId)
      if (message == null) {
        Log.w(TAG, "Unable to find corresponding message")
        return null
      }

      val conversationRecipient = SignalDatabase.threads.getRecipientForThreadId(message.threadId)
      if (conversationRecipient == null) {
        Log.w(TAG, "We have a message, but couldn't find the thread!")
        return null
      }

      val recipients = conversationRecipient.participantIds.filter { it != Recipient.self().id }.map { it.toLong() }

      return PollVoteJob(
        messageId = messageId,
        recipientIds = recipients.toMutableList(),
        initialRecipientCount = recipients.size,
        voteCount = voteCount,
        isRemoval = isRemoval,
        optionId = optionId,
        parameters = Parameters.Builder()
          .setQueue(conversationRecipient.id.toQueueKey())
          .addConstraint(NetworkConstraint.KEY)
          .setMaxAttempts(Parameters.UNLIMITED)
          .setLifespan(1.days.inWholeMilliseconds)
          .build()
      )
    }
  }

  override fun serialize(): ByteArray {
    return PollVoteJobData(messageId, recipientIds, initialRecipientCount, voteCount, isRemoval, optionId).encode()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun run(): Result {
    if (!SignalStore.account.isRegistered) {
      Log.w(TAG, "Not registered. Skipping.")
      return Result.failure()
    }

    val message = SignalDatabase.messages.getMessageRecordOrNull(messageId)
    if (message == null) {
      Log.w(TAG, "Unable to find corresponding message")
      return Result.failure()
    }

    val conversationRecipient = SignalDatabase.threads.getRecipientForThreadId(message.threadId)
    if (conversationRecipient == null) {
      Log.w(TAG, "We have a message, but couldn't find the thread!")
      return Result.failure()
    }

    val poll = SignalDatabase.polls.getPoll(messageId)
    if (poll == null) {
      Log.w(TAG, "Unable to find corresponding poll")
      return Result.failure()
    }

    val targetAuthor = message.fromRecipient
    if (targetAuthor == null || !targetAuthor.hasServiceId) {
      Log.w(TAG, "Unable to find target author")
      return Result.failure()
    }

    val targetSentTimestamp = message.dateSent

    val recipients = Recipient.resolvedList(recipientIds.filter { it != Recipient.self().id.toLong() }.map { RecipientId.from(it) })
    val registered = RecipientUtil.getEligibleForSending(recipients)
    val unregistered = recipients - registered.toSet()
    val completions: List<Recipient> = deliver(conversationRecipient, registered, targetAuthor, targetSentTimestamp, poll)

    recipientIds.removeAll(unregistered.map { it.id.toLong() })
    recipientIds.removeAll(completions.map { it.id.toLong() })

    Log.i(TAG, "Completed now: " + completions.size + ", Remaining: " + recipientIds.size)

    if (recipientIds.isNotEmpty()) {
      Log.w(TAG, "Still need to send to " + recipientIds.size + " recipients. Retrying.")
      return Result.retry(defaultBackoff())
    }

    return Result.success()
  }

  private fun deliver(conversationRecipient: Recipient, destinations: List<Recipient>, targetAuthor: Recipient, targetSentTimestamp: Long, poll: PollRecord): List<Recipient> {
    val votes = SignalDatabase.polls.getVotes(poll.id, poll.allowMultipleVotes, voteCount)

    val dataMessageBuilder = newBuilder()
      .withTimestamp(System.currentTimeMillis())
      .withPollVote(
        buildPollVote(
          targetAuthor = targetAuthor,
          targetSentTimestamp = targetSentTimestamp,
          optionIndexes = votes,
          voteCount = voteCount
        )
      )

    GroupUtil.setDataMessageGroupContext(context, dataMessageBuilder, conversationRecipient.requireGroupId().requirePush())

    val dataMessage = dataMessageBuilder.build()

    val results = GroupSendUtil.sendResendableDataMessage(
      context,
      conversationRecipient.groupId.map { obj: GroupId -> obj.requireV2() }.orElse(null),
      null,
      destinations,
      false,
      ContentHint.RESENDABLE,
      MessageId(messageId),
      dataMessage,
      true,
      false,
      null
    )

    val groupResult = GroupSendJobHelper.getCompletedSends(destinations, results)

    for (unregistered in groupResult.unregistered) {
      SignalDatabase.recipients.markUnregistered(unregistered)
    }

    if (groupResult.completed.isNotEmpty() || destinations.isEmpty()) {
      if (isRemoval) {
        SignalDatabase.polls.markPendingAsRemoved(
          pollId = poll.id,
          voterId = Recipient.self().id.toLong(),
          voteCount = voteCount,
          messageId = poll.messageId,
          optionId = optionId
        )
      } else {
        SignalDatabase.polls.markPendingAsAdded(
          pollId = poll.id,
          voterId = Recipient.self().id.toLong(),
          voteCount = voteCount,
          messageId = poll.messageId,
          optionId = optionId
        )
      }
    }

    return groupResult.completed
  }

  override fun onFailure() {
    if (recipientIds.size < initialRecipientCount) {
      Log.w(TAG, "Only sent vote to " + recipientIds.size + "/" + initialRecipientCount + " recipients. Still, it sent to someone, so it stays.")
      return
    }

    Log.w(TAG, "Failed to send to all recipients!")

    val pollId = SignalDatabase.polls.getPollId(messageId)
    if (pollId == null) {
      Log.w(TAG, "Poll no longer exists")
      return
    }

    SignalDatabase.polls.removePendingVote(pollId, optionId, voteCount, messageId)
  }

  private fun buildPollVote(
    targetAuthor: Recipient,
    targetSentTimestamp: Long,
    optionIndexes: List<Int>,
    voteCount: Int
  ): SignalServiceDataMessage.PollVote {
    return SignalServiceDataMessage.PollVote(
      targetAuthor = targetAuthor.requireServiceId(),
      targetSentTimestamp = targetSentTimestamp,
      optionIndexes = optionIndexes,
      voteCount = voteCount
    )
  }

  class Factory : Job.Factory<PollVoteJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): PollVoteJob {
      val data = PollVoteJobData.ADAPTER.decode(serializedData!!)

      return PollVoteJob(
        messageId = data.messageId,
        recipientIds = data.recipients.toMutableList(),
        initialRecipientCount = data.initialRecipientCount,
        voteCount = data.voteCount,
        isRemoval = data.isRemoval,
        optionId = data.optionId,
        parameters = parameters
      )
    }
  }
}

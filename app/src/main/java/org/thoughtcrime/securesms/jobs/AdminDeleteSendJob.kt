package org.thoughtcrime.securesms.jobs

import org.signal.core.models.ServiceId
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.documents.NetworkFailure
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.jobs.protos.AdminDeleteJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messages.GroupSendUtil
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.util.GroupUtil
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.Companion.newBuilder
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.days

/**
 * Job used when an admin deletes a message in a group
 */
class AdminDeleteSendJob private constructor(
  private val messageId: Long,
  private val recipientIds: MutableList<Long>,
  private val initialRecipientCount: Int,
  parameters: Parameters
) : Job(parameters) {

  companion object {
    const val KEY: String = "AdminDeleteSendJob"

    private val TAG = tag(AdminDeleteSendJob::class.java)

    @JvmStatic
    fun create(messageId: Long, filterRecipients: List<RecipientId>): AdminDeleteSendJob? {
      val message = SignalDatabase.messages.getMessageRecordOrNull(messageId)
      if (message == null) {
        return null
      }

      val conversationRecipient = SignalDatabase.threads.getRecipientForThreadId(message.threadId)

      if (conversationRecipient == null) {
        return null
      }

      val recipientIds = filterRecipients.ifEmpty { conversationRecipient.participantIds }.map { it.toLong() }.toMutableList()

      return AdminDeleteSendJob(
        messageId = messageId,
        recipientIds = recipientIds,
        initialRecipientCount = recipientIds.size,
        parameters = Parameters.Builder()
          .setQueue(conversationRecipient.id.toQueueKey())
          .addConstraint(NetworkConstraint.KEY)
          .addConstraint(SealedSenderConstraint.KEY)
          .setLifespan(1.days.inWholeMilliseconds)
          .setMaxAttempts(Parameters.UNLIMITED)
          .build()
      )
    }
  }

  override fun serialize(): ByteArray? {
    return AdminDeleteJobData(messageId, recipientIds, initialRecipientCount).encode()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun run(): Result {
    if (!SignalStore.account.isRegistered) {
      Log.w(TAG, "Not registered. Skipping.")
      return Result.failure()
    }

    val message = SignalDatabase.messages.getMessageRecord(messageId)
    if (!message.fromRecipient.hasServiceId) {
      Log.w(TAG, "Missing service id for the target author.")
      return Result.failure()
    }

    val existingNetworkFailures = message.networkFailures.toMutableSet()
    val existingIdentityMismatches = message.identityKeyMismatches.toMutableSet()
    val targets = (recipientIds + existingIdentityMismatches.map { it.recipientId.toLong() } + existingNetworkFailures.map { it.recipientId.toLong() }).toSet()

    val recipients = targets.map { Recipient.resolved(RecipientId.from(it)) }.toMutableList()
    val targetSentTimestamp = message.dateSent
    val targetAuthor = message.fromRecipient.requireServiceId()

    val conversationRecipient = SignalDatabase.threads.getRecipientForThreadId(message.threadId)
    if (conversationRecipient == null) {
      Log.w(TAG, "We have a message, but couldn't find the thread!")
      return Result.failure()
    }

    if (!conversationRecipient.isPushV2Group) {
      Log.w(TAG, "Cannot admin delete in a non V2 group.")
      return Result.failure()
    }

    val groupRecord = SignalDatabase.groups.getGroup(conversationRecipient.requireGroupId())
    if (groupRecord.isPresent && groupRecord.get().isTerminated) {
      Log.w(TAG, "Cannot admin delete in a terminated group.")
      return Result.failure()
    }

    if (groupRecord.isEmpty || !groupRecord.get().isAdmin(Recipient.self())) {
      Log.w(TAG, "Cannot delete because you are not an admin.")
      return Result.failure()
    }

    val eligible = RecipientUtil.getEligibleForSending(recipients.filter { it.hasServiceId })
    val ineligibleRecipients = recipients - eligible
    val sendResult = deliver(conversationRecipient, eligible, targetAuthor, targetSentTimestamp)

    val completedIds = sendResult.completed.map { it.id }.toSet()
    existingNetworkFailures.removeAll { completedIds.contains(it.recipientId) }
    existingIdentityMismatches.removeAll { completedIds.contains(it.recipientId) }

    val ineligibleIds = (ineligibleRecipients.map { it.id } + sendResult.unregistered).toSet()
    existingNetworkFailures.removeAll { ineligibleIds.contains(it.recipientId) }
    existingIdentityMismatches.removeAll { ineligibleIds.contains(it.recipientId) }

    existingIdentityMismatches.addAll(sendResult.identityMismatch)

    SignalDatabase.messages.setNetworkFailures(messageId, existingNetworkFailures)
    SignalDatabase.messages.setMismatchedIdentities(messageId, existingIdentityMismatches)

    for (completion in sendResult.completed) {
      recipientIds.remove(completion.id.toLong())
    }

    for (unregistered in sendResult.unregistered) {
      SignalDatabase.recipients.markUnregistered(unregistered)
    }

    for (recipient in ineligibleRecipients) {
      recipientIds.remove(recipient.id.toLong())
    }

    Log.i(TAG, "Completed now: ${sendResult.completed.size} Skipped: ${ineligibleRecipients.size + sendResult.skipped.size} Remaining: ${recipientIds.size}")

    if (existingNetworkFailures.isEmpty() && existingIdentityMismatches.isEmpty() && recipientIds.isEmpty()) {
      SignalDatabase.messages.markAsSentAdminDelete(messageId)
      return Result.success()
    } else if (existingIdentityMismatches.isNotEmpty()) {
      Log.w(TAG, "Failing because there were ${existingIdentityMismatches.size} identity mismatches.")
      return Result.failure()
    } else {
      Log.w(TAG, "Still need to send to ${recipientIds.size} recipients. Retrying.")
      return Result.retry(defaultBackoff())
    }
  }

  override fun onFailure() {
    Log.w(TAG, "Failed to send admin delete to all recipients! ${initialRecipientCount - recipientIds.size} /  $initialRecipientCount. Marking remaining non-identity mismatched failures as network failure.")
    val message = SignalDatabase.messages.getMessageRecordOrNull(messageId)
    if (message == null) {
      Log.w(TAG, "Message no longer exists, ignoring.")
    } else {
      val existingIdentityMismatches = message.identityKeyMismatches.map { it.recipientId.toLong() }
      recipientIds.removeAll { existingIdentityMismatches.contains(it) }
      SignalDatabase.messages.setNetworkFailures(messageId, recipientIds.map { NetworkFailure(RecipientId.from(it)) }.toSet())
      SignalDatabase.messages.markAsFailedAdminDelete(messageId)
    }
  }

  private fun deliver(
    conversationRecipient: Recipient,
    destinations: MutableList<Recipient>,
    targetAuthor: ServiceId,
    targetSentTimestamp: Long
  ): GroupSendJobHelper.SendResult {
    val dataMessageBuilder = newBuilder()
      .withTimestamp(System.currentTimeMillis())
      .withAdminDelete(SignalServiceDataMessage.AdminDelete(targetAuthor, targetSentTimestamp))

    GroupUtil.setDataMessageGroupContext(context, dataMessageBuilder, conversationRecipient.requireGroupId().requirePush())

    val nonSelfDestinations = destinations.filterNot { it.isSelf }
    val includeSelf = destinations.size != nonSelfDestinations.size

    val dataMessage = dataMessageBuilder.build()

    val results = GroupSendUtil.sendResendableDataMessage(
      context,
      conversationRecipient.groupId.map { it.requireV2() }.getOrNull(),
      null,
      nonSelfDestinations,
      false,
      ContentHint.RESENDABLE,
      MessageId(messageId),
      dataMessage,
      true,
      false,
      null,
      null
    ).toMutableList()

    if (includeSelf) {
      results.add(AppDependencies.signalServiceMessageSender.sendSyncMessage(dataMessage))
    }

    return GroupSendJobHelper.getCompletedSends(destinations, results)
  }

  class Factory : Job.Factory<AdminDeleteSendJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): AdminDeleteSendJob {
      val data = AdminDeleteJobData.ADAPTER.decode(serializedData!!)

      return AdminDeleteSendJob(
        messageId = data.messageId,
        recipientIds = data.recipientIds.toMutableList(),
        initialRecipientCount = data.initialRecipientCount,
        parameters = parameters
      )
    }
  }
}

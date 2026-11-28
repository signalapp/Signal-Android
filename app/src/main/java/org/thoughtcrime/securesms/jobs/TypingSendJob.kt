package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.libsignal.zkgroup.groups.GroupIdentifier
import org.signal.network.service.GroupSendDestination
import org.signal.network.service.SendRequest
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messages.AppMessageSendDependencies
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.util.OutgoingContent
import org.whispersystems.signalservice.internal.push.TypingMessage
import java.util.concurrent.TimeUnit

/** Sends a best-effort, online-only typing indicator to the recipient of [threadId]. Results are not tracked. */
class TypingSendJob private constructor(
  parameters: Parameters,
  private val threadId: Long,
  private val typing: Boolean
) : CoroutineJob(parameters) {

  companion object {
    const val KEY: String = "TypingSendJob"

    private val TAG = Log.tag(TypingSendJob::class.java)

    private const val KEY_THREAD_ID = "thread_id"
    private const val KEY_TYPING = "typing"

    @JvmStatic
    fun getQueue(threadId: Long): String {
      return "TYPING_$threadId"
    }
  }

  constructor(threadId: Long, typing: Boolean) : this(
    Parameters.Builder()
      .setQueue(getQueue(threadId))
      .setMaxAttempts(1)
      .setLifespan(TimeUnit.SECONDS.toMillis(5))
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setMemoryOnly(true)
      .build(),
    threadId,
    typing
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putLong(KEY_THREAD_ID, threadId)
      .putBoolean(KEY_TYPING, typing)
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  override val cancelCoroutineOnJobCancel: Boolean = true

  override fun onFailure() = Unit

  override suspend fun doRun(): Result {
    if (!Recipient.self().isRegistered) {
      Log.w(TAG, "Not registered.")
      return Result.failure()
    }

    if (!SignalStore.settings.isTypingIndicatorsEnabled) {
      return Result.success()
    }

    Log.d(TAG, "Sending typing " + (if (typing) "started" else "stopped") + " for thread " + threadId)

    val recipient = SignalDatabase.threads.getRecipientForThreadId(threadId)

    if (recipient == null) {
      Log.w(TAG, "Tried to send a typing indicator to a non-existent thread.")
      return Result.success()
    }

    if (recipient.isBlocked) {
      Log.w(TAG, "Not sending typing indicators to blocked recipients.")
      return Result.success()
    }

    if (recipient.isSelf) {
      Log.w(TAG, "Not sending typing indicators to self.")
      return Result.success()
    }

    if (recipient.isPushV1Group || recipient.isMmsGroup) {
      Log.w(TAG, "Not sending typing indicators to unsupported groups.")
      return Result.success()
    }

    if (recipient.isPushV2Group && !SignalDatabase.groups.isActive(recipient.requireGroupId())) {
      Log.w(TAG, "Not sending typing indicators to terminated or inactive groups.")
      return Result.success()
    }

    if (!recipient.isRegistered) {
      Log.w(TAG, "Not sending typing indicators to non-Signal recipients.")
      return Result.success()
    }

    val groupId = recipient.groupId.orNull()?.requireV2()

    val timestamp = System.currentTimeMillis()
    val action = if (typing) TypingMessage.Action.STARTED else TypingMessage.Action.STOPPED
    val request = SendRequest(OutgoingContent.typing(action, timestamp, groupId), timestamp = timestamp, urgent = false, isOnline = true)

    val error = if (groupId != null) {
      val members = SignalDatabase.groups.getGroupMembers(groupId, memberSet = GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF)
      val targets = RecipientUtil.getEligibleForSending(members)

      if (targets.isEmpty()) {
        Log.w(TAG, "No eligible recipients.")
        return Result.success()
      }

      SignalNetwork.messageService.sendGroupMessage(
        destination = GroupSendDestination.Group(GroupIdentifier(groupId.decodedId)),
        recipients = AppMessageSendDependencies.toGroupSendRecipients(targets),
        request = request
      ).leftOrNull()
    } else {
      SignalNetwork.messageService.sendMessage(
        serviceId = recipient.requireServiceId(),
        sealedSenderAccess = SealedSenderAccessUtil.getSealedSenderAccessFor(recipient),
        request = request
      ).leftOrNull()
    }

    if (error != null) {
      Log.w(TAG, "[$timestamp] Failed to send typing indicator: $error")
    }

    return Result.success()
  }

  class Factory : Job.Factory<TypingSendJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): TypingSendJob {
      val data = JsonJobData.deserialize(serializedData)
      return TypingSendJob(parameters, data.getLong(KEY_THREAD_ID), data.getBoolean(KEY_TYPING))
    }
  }
}

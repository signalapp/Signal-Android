package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messages.GroupSendUtil
import org.thoughtcrime.securesms.net.NotPushRegisteredException
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage
import java.util.Optional
import java.util.concurrent.TimeUnit

class TypingSendJob private constructor(
  parameters: Parameters,
  private val threadId: Long,
  private val typing: Boolean
) : BaseJob(parameters) {

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

  @Throws(Exception::class)
  override fun onRun() {
    if (!Recipient.self().isRegistered) {
      throw NotPushRegisteredException()
    }

    if (!SignalStore.settings.isTypingIndicatorsEnabled) {
      return
    }

    Log.d(TAG, "Sending typing " + (if (typing) "started" else "stopped") + " for thread " + threadId)

    val recipient = SignalDatabase.threads.getRecipientForThreadId(threadId)

    if (recipient == null) {
      Log.w(TAG, "Tried to send a typing indicator to a non-existent thread.")
      return
    }

    if (recipient.isBlocked) {
      Log.w(TAG, "Not sending typing indicators to blocked recipients.")
      return
    }

    if (recipient.isSelf) {
      Log.w(TAG, "Not sending typing indicators to self.")
      return
    }

    if (recipient.isPushV1Group || recipient.isMmsGroup) {
      Log.w(TAG, "Not sending typing indicators to unsupported groups.")
      return
    }

    if (recipient.isPushV2Group && !SignalDatabase.groups.isActive(recipient.requireGroupId())) {
      Log.w(TAG, "Not sending typing indicators to terminated or inactive groups.")
      return
    }

    if (!recipient.isRegistered) {
      Log.w(TAG, "Not sending typing indicators to non-Signal recipients.")
      return
    }

    var recipients: List<Recipient> = listOf(recipient)
    var groupId: Optional<ByteArray> = Optional.empty()

    if (recipient.isGroup) {
      recipients = SignalDatabase.groups.getGroupMembers(recipient.requireGroupId(), GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF)
      groupId = Optional.of(recipient.requireGroupId().decodedId)
    }

    recipients = RecipientUtil.getEligibleForSending(recipients.map { it.resolve() })

    val typingMessage = SignalServiceTypingMessage(if (typing) SignalServiceTypingMessage.Action.STARTED else SignalServiceTypingMessage.Action.STOPPED, System.currentTimeMillis(), groupId)

    GroupSendUtil.sendTypingMessage(
      context,
      recipient.groupId.map(GroupId::requireV2).orElse(null),
      recipients,
      typingMessage,
      this::isCanceled
    )
  }

  override fun onFailure() = Unit

  override fun onShouldRetry(exception: Exception): Boolean = false

  class Factory : Job.Factory<TypingSendJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): TypingSendJob {
      val data = JsonJobData.deserialize(serializedData)
      return TypingSendJob(parameters, data.getLong(KEY_THREAD_ID), data.getBoolean(KEY_TYPING))
    }
  }
}

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.signal.ringrtc.CallId
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.ringrtc.RemotePeer
import org.thoughtcrime.securesms.service.webrtc.CallEventSyncMessageUtil
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage
import java.util.Optional
import java.util.concurrent.TimeUnit

/**
 * Sends a sync event for the given call when the user first joins.
 */
class CallSyncEventJob private constructor(
  parameters: Parameters,
  private val conversationRecipientId: RecipientId,
  private val callId: Long,
  private val direction: CallTable.Direction,
  private val event: CallTable.Event
) : BaseJob(parameters) {

  companion object {
    private val TAG = Log.tag(CallSyncEventJob::class.java)

    const val KEY = "CallSyncEventJob"

    private const val KEY_CALL_ID = "call_id"
    private const val KEY_CONVERSATION_ID = "conversation_id"
    private const val KEY_DIRECTION = "direction"
    private const val KEY_EVENT = "event"

    @JvmStatic
    fun createForJoin(conversationRecipientId: RecipientId, callId: Long, isIncoming: Boolean): CallSyncEventJob {
      return CallSyncEventJob(
        getParameters(conversationRecipientId),
        conversationRecipientId,
        callId,
        if (isIncoming) CallTable.Direction.INCOMING else CallTable.Direction.OUTGOING,
        CallTable.Event.ACCEPTED
      )
    }

    @JvmStatic
    fun createForDelete(conversationRecipientId: RecipientId, callId: Long, isIncoming: Boolean): CallSyncEventJob {
      return CallSyncEventJob(
        getParameters(conversationRecipientId),
        conversationRecipientId,
        callId,
        if (isIncoming) CallTable.Direction.INCOMING else CallTable.Direction.OUTGOING,
        CallTable.Event.DELETE
      )
    }

    @JvmStatic
    fun enqueueDeleteSyncEvents(deletedCalls: Set<CallTable.Call>) {
      for (call in deletedCalls) {
        ApplicationDependencies.getJobManager().add(
          createForDelete(
            call.peer,
            call.callId,
            call.direction == CallTable.Direction.INCOMING
          )
        )
      }
    }

    private fun getParameters(conversationRecipientId: RecipientId): Parameters {
      return Parameters.Builder()
        .setQueue(conversationRecipientId.toQueueKey())
        .setLifespan(TimeUnit.MINUTES.toMillis(5))
        .setMaxAttempts(3)
        .addConstraint(NetworkConstraint.KEY)
        .build()
    }
  }

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putLong(KEY_CALL_ID, callId)
      .putString(KEY_CONVERSATION_ID, conversationRecipientId.serialize())
      .putInt(KEY_EVENT, CallTable.Event.serialize(event))
      .putInt(KEY_DIRECTION, CallTable.Direction.serialize(direction))
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override fun onRun() {
    val inputTimestamp = JsonJobData.deserialize(inputData).getLongOrDefault(GroupCallUpdateSendJob.KEY_SYNC_TIMESTAMP, System.currentTimeMillis())
    val syncTimestamp = if (inputTimestamp == 0L) System.currentTimeMillis() else inputTimestamp
    val syncMessage = CallEventSyncMessageUtil.createAcceptedSyncMessage(
      RemotePeer(conversationRecipientId, CallId(callId)),
      syncTimestamp,
      direction == CallTable.Direction.OUTGOING,
      true
    )

    try {
      ApplicationDependencies.getSignalServiceMessageSender().sendSyncMessage(SignalServiceSyncMessage.forCallEvent(syncMessage), Optional.empty())
    } catch (e: Exception) {
      Log.w(TAG, "Unable to send call event sync message for $callId", e)
    }
  }

  override fun onShouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<CallSyncEventJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): CallSyncEventJob {
      val data = JsonJobData.deserialize(serializedData)

      return CallSyncEventJob(
        parameters,
        RecipientId.from(data.getString(KEY_CONVERSATION_ID)),
        data.getLong(KEY_CALL_ID),
        CallTable.Direction.deserialize(data.getInt(KEY_DIRECTION)),
        CallTable.Event.deserialize(data.getInt(KEY_EVENT))
      )
    }
  }
}

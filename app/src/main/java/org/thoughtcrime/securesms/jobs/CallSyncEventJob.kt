package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.signal.ringrtc.CallId
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.CallSyncEventJobData
import org.thoughtcrime.securesms.jobs.protos.CallSyncEventJobRecord
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.ringrtc.RemotePeer
import org.thoughtcrime.securesms.service.webrtc.CallEventSyncMessageUtil
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage
import org.whispersystems.signalservice.internal.push.SyncMessage
import java.util.concurrent.TimeUnit

/**
 * Sends a sync event for the given call when the user first joins.
 */
class CallSyncEventJob private constructor(
  parameters: Parameters,
  private var events: List<CallSyncEventJobRecord>
) : BaseJob(parameters) {

  companion object {
    private val TAG = Log.tag(CallSyncEventJob::class.java)

    const val KEY = "CallSyncEventJob2"

    @JvmStatic
    fun createForJoin(conversationRecipientId: RecipientId, callId: Long, isIncoming: Boolean): CallSyncEventJob {
      return CallSyncEventJob(
        getParameters(),
        listOf(
          CallSyncEventJobRecord(
            recipientId = conversationRecipientId.toLong(),
            callId = callId,
            direction = CallTable.Direction.serialize(if (isIncoming) CallTable.Direction.INCOMING else CallTable.Direction.OUTGOING),
            callEvent = CallSyncEventJobRecord.Event.ACCEPTED
          )
        )
      )
    }

    @JvmStatic
    fun createForNotAccepted(conversationRecipientId: RecipientId, callId: Long, isIncoming: Boolean): CallSyncEventJob {
      return CallSyncEventJob(
        getParameters(),
        listOf(
          CallSyncEventJobRecord(
            recipientId = conversationRecipientId.toLong(),
            callId = callId,
            direction = CallTable.Direction.serialize(if (isIncoming) CallTable.Direction.INCOMING else CallTable.Direction.OUTGOING),
            callEvent = CallSyncEventJobRecord.Event.NOT_ACCEPTED
          )
        )
      )
    }

    @JvmStatic
    fun createForObserved(conversationRecipientId: RecipientId, callId: Long): CallSyncEventJob {
      return CallSyncEventJob(
        getParameters(),
        listOf(
          CallSyncEventJobRecord(
            recipientId = conversationRecipientId.toLong(),
            callId = callId,
            direction = CallTable.Direction.serialize(CallTable.Direction.INCOMING),
            callEvent = CallSyncEventJobRecord.Event.OBSERVED
          )
        )
      )
    }

    private fun createForDelete(calls: List<CallTable.Call>): CallSyncEventJob {
      return CallSyncEventJob(
        getParameters(),
        calls.map {
          CallSyncEventJobRecord(
            recipientId = it.peer.toLong(),
            callId = it.callId,
            direction = CallTable.Direction.serialize(it.direction),
            callEvent = CallSyncEventJobRecord.Event.DELETE
          )
        }
      )
    }

    fun enqueueDeleteSyncEvents(deletedCalls: Set<CallTable.Call>) {
      deletedCalls.chunked(50).forEach {
        AppDependencies.jobManager.add(
          createForDelete(it)
        )
      }
    }

    private fun getParameters(): Parameters {
      return Parameters.Builder()
        .setQueue("CallSyncEventJob")
        .setLifespan(TimeUnit.DAYS.toMillis(1))
        .setMaxAttempts(Parameters.UNLIMITED)
        .addConstraint(NetworkConstraint.KEY)
        .build()
    }
  }

  override fun serialize(): ByteArray {
    return CallSyncEventJobData(events).encodeByteString().toByteArray()
  }

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override fun onShouldRetry(e: Exception): Boolean = e is RetryableException

  override fun onRun() {
    val remainingEvents = events.mapNotNull(this::processEvent)

    if (remainingEvents.isEmpty()) {
      Log.i(TAG, "Successfully sent all sync messages.")
    } else {
      warn(TAG, "Failed to send sync messages for ${remainingEvents.size} events.")
      events = remainingEvents
      throw RetryableException()
    }
  }

  private fun processEvent(callSyncEvent: CallSyncEventJobRecord): CallSyncEventJobRecord? {
    val call = SignalDatabase.calls.getCallById(callSyncEvent.callId, callSyncEvent.deserializeRecipientId())
    if (call == null) {
      Log.w(TAG, "Cannot process event for call that does not exist. Dropping.")
      return null
    }

    val inputTimestamp = JsonJobData.deserialize(inputData).getLongOrDefault(GroupCallUpdateSendJob.KEY_SYNC_TIMESTAMP, System.currentTimeMillis())
    val syncTimestamp = if (inputTimestamp == 0L) System.currentTimeMillis() else inputTimestamp
    val syncMessage = createSyncMessage(syncTimestamp, callSyncEvent, call.type)

    return try {
      AppDependencies.signalServiceMessageSender.sendSyncMessage(SignalServiceSyncMessage.forCallEvent(syncMessage))
      null
    } catch (e: Exception) {
      Log.w(TAG, "Unable to send call event sync message for ${callSyncEvent.callId}", e)
      callSyncEvent
    }
  }

  private fun createSyncMessage(syncTimestamp: Long, callSyncEvent: CallSyncEventJobRecord, callType: CallTable.Type): SyncMessage.CallEvent {
    return when (callSyncEvent.resolveEvent()) {
      CallSyncEventJobRecord.Event.ACCEPTED -> CallEventSyncMessageUtil.createAcceptedSyncMessage(
        remotePeer = RemotePeer(callSyncEvent.deserializeRecipientId(), CallId(callSyncEvent.callId)),
        timestamp = syncTimestamp,
        isOutgoing = callSyncEvent.deserializeDirection() == CallTable.Direction.OUTGOING,
        isVideoCall = callType != CallTable.Type.AUDIO_CALL
      )

      CallSyncEventJobRecord.Event.NOT_ACCEPTED -> CallEventSyncMessageUtil.createNotAcceptedSyncMessage(
        remotePeer = RemotePeer(callSyncEvent.deserializeRecipientId(), CallId(callSyncEvent.callId)),
        timestamp = syncTimestamp,
        isOutgoing = callSyncEvent.deserializeDirection() == CallTable.Direction.OUTGOING,
        isVideoCall = callType != CallTable.Type.AUDIO_CALL
      )

      CallSyncEventJobRecord.Event.DELETE -> CallEventSyncMessageUtil.createDeleteCallEvent(
        remotePeer = RemotePeer(callSyncEvent.deserializeRecipientId(), CallId(callSyncEvent.callId)),
        timestamp = syncTimestamp,
        isOutgoing = callSyncEvent.deserializeDirection() == CallTable.Direction.OUTGOING,
        isVideoCall = callType != CallTable.Type.AUDIO_CALL
      )

      CallSyncEventJobRecord.Event.OBSERVED -> CallEventSyncMessageUtil.createObservedCallEvent(
        remotePeer = RemotePeer(callSyncEvent.deserializeRecipientId(), CallId(callSyncEvent.callId)),
        timestamp = syncTimestamp,
        isOutgoing = false,
        isVideoCall = callType != CallTable.Type.AUDIO_CALL
      )

      else -> throw Exception("Unsupported event: ${callSyncEvent.deprecatedEvent}")
    }
  }

  private fun CallSyncEventJobRecord.deserializeRecipientId(): RecipientId = RecipientId.from(recipientId)

  private fun CallSyncEventJobRecord.deserializeDirection(): CallTable.Direction = CallTable.Direction.deserialize(direction)

  private fun CallSyncEventJobRecord.resolveEvent(): CallSyncEventJobRecord.Event {
    return if (callEvent != CallSyncEventJobRecord.Event.UNKNOWN_ACTION) {
      callEvent
    } else {
      when (CallTable.Event.deserialize(deprecatedEvent)) {
        CallTable.Event.ACCEPTED -> CallSyncEventJobRecord.Event.ACCEPTED
        CallTable.Event.NOT_ACCEPTED -> CallSyncEventJobRecord.Event.NOT_ACCEPTED
        CallTable.Event.DELETE -> CallSyncEventJobRecord.Event.DELETE
        else -> CallSyncEventJobRecord.Event.UNKNOWN_ACTION
      }
    }
  }

  class Factory : Job.Factory<CallSyncEventJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): CallSyncEventJob {
      val events = CallSyncEventJobData.ADAPTER.decode(serializedData!!).records

      return CallSyncEventJob(
        parameters,
        events
      )
    }
  }

  private class RetryableException : Exception()
}

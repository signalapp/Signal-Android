package org.thoughtcrime.securesms.jobs

import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.logging.Log
import org.signal.network.service.IndividualMessageService
import org.signal.ringrtc.CallId
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.jobs.protos.CallSyncEventJobData
import org.thoughtcrime.securesms.jobs.protos.CallSyncEventJobRecord
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.ringrtc.RemotePeer
import org.thoughtcrime.securesms.service.webrtc.CallEventSyncMessageUtil
import org.thoughtcrime.securesms.util.OutgoingContent
import org.whispersystems.signalservice.internal.push.SyncMessage
import java.util.concurrent.TimeUnit

/**
 * Sends a sync event for the given call when the user first joins.
 */
class CallSyncEventJob private constructor(
  parameters: Parameters,
  private var events: List<CallSyncEventJobRecord>
) : CoroutineJob(parameters) {

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
        .addConstraint(SealedSenderConstraint.KEY)
        .build()
    }
  }

  override fun serialize(): ByteArray {
    return CallSyncEventJobData(events).encodeByteString().toByteArray()
  }

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override suspend fun doRun(): Result {
    val remainingEvents = mutableListOf<CallSyncEventJobRecord>()

    for ((index, event) in events.withIndex()) {
      when (val outcome = processEvent(event)) {
        EventResult.Done -> Unit
        EventResult.Retry -> remainingEvents += event
        is EventResult.StopAndRetry -> {
          Log.w(TAG, "Stopping after ${index + 1} of ${events.size} events, keeping the rest for the retry.")
          events = remainingEvents + events.drop(index)
          return outcome.result
        }
      }
    }

    return if (remainingEvents.isEmpty()) {
      Log.i(TAG, "Successfully sent all sync messages.")
      Result.success()
    } else {
      Log.w(TAG, "Failed to send sync messages for ${remainingEvents.size} events.")
      events = remainingEvents
      Result.retry(defaultBackoff())
    }
  }

  private suspend fun processEvent(callSyncEvent: CallSyncEventJobRecord): EventResult {
    val call = SignalDatabase.calls.getCallById(callSyncEvent.callId, callSyncEvent.deserializeRecipientId())
    if (call == null) {
      Log.w(TAG, "Cannot process event for call that does not exist. Dropping.")
      return EventResult.Done
    }

    val inputTimestamp = JsonJobData.deserialize(inputData).getLongOrDefault(GroupCallUpdateSendJob.KEY_SYNC_TIMESTAMP, System.currentTimeMillis())
    val syncTimestamp = if (inputTimestamp == 0L) System.currentTimeMillis() else inputTimestamp
    val syncMessage = createSyncMessage(syncTimestamp, callSyncEvent, call.type)

    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = System.currentTimeMillis(),
      envelopeContent = OutgoingContent.syncCallEvent(syncMessage),
      urgent = false,
      onEncrypted = null
    )

    return result.fold(
      ifRight = { EventResult.Done },
      ifLeft = { error ->
        when (error) {
          is IndividualMessageService.SendError.IdentityMismatch,
          is IndividualMessageService.SendError.NotRegistered,
          is IndividualMessageService.SendError.Unauthorized,
          is IndividualMessageService.SendError.ContentTooLarge -> {
            Log.w(TAG, "Failed to send call event sync message for ${callSyncEvent.callId}. Dropping.", error)
            EventResult.Done
          }

          is IndividualMessageService.SendError.ChallengeRequired -> EventResult.StopAndRetry(error.tryPushChallengeAndToJobResult(TAG, defaultBackoff()))
          is IndividualMessageService.SendError.RateLimited -> EventResult.StopAndRetry(error.toJobResult(TAG, defaultBackoff()))

          is IndividualMessageService.SendError.PreKeyUnavailable,
          is IndividualMessageService.SendError.SessionAttemptsExhausted,
          is IndividualMessageService.SendError.NetworkError,
          is IndividualMessageService.SendError.ApplicationError -> {
            Log.w(TAG, "Unable to send call event sync message for ${callSyncEvent.callId}", error)
            EventResult.Retry
          }
        }
      }
    )
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

  private sealed interface EventResult {
    data object Done : EventResult
    data object Retry : EventResult
    data class StopAndRetry(val result: Result) : EventResult
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
}

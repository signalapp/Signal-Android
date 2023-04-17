package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.ringrtc.CallId
import org.signal.ringrtc.CallManager
import org.thoughtcrime.securesms.testing.SignalActivityRule

@RunWith(AndroidJUnit4::class)
class CallTableTest {

  @get:Rule
  val harness = SignalActivityRule()

  @Test
  fun givenACall_whenISetTimestamp_thenIExpectUpdatedTimestamp() {
    val callId = 1L
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    val now = System.currentTimeMillis()
    SignalDatabase.calls.insertAcceptedGroupCall(
      callId,
      harness.others[0],
      CallTable.Direction.INCOMING,
      now
    )

    SignalDatabase.calls.setTimestamp(callId, conversationId, -1L)
    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(-1L, call?.timestamp)

    val messageRecord = SignalDatabase.messages.getMessageRecord(call!!.messageId!!)
    assertEquals(-1L, messageRecord.dateReceived)
    assertEquals(-1L, messageRecord.dateSent)
  }

  @Test
  fun givenPreExistingEvent_whenIDeleteGroupCall_thenIMarkDeletedAndSetTimestamp() {
    val callId = 1L
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    val now = System.currentTimeMillis()
    SignalDatabase.calls.insertAcceptedGroupCall(
      callId,
      harness.others[0],
      CallTable.Direction.INCOMING,
      now
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    SignalDatabase.calls.deleteGroupCall(call!!)

    val deletedCall = SignalDatabase.calls.getCallById(callId, conversationId)
    val oldestDeletionTimestamp = SignalDatabase.calls.getOldestDeletionTimestamp()

    assertEquals(CallTable.Event.DELETE, deletedCall?.event)
    assertNotEquals(0L, oldestDeletionTimestamp)
    assertNull(deletedCall!!.messageId)
  }

  @Test
  fun givenNoPreExistingEvent_whenIDeleteGroupCall_thenIInsertAndMarkCallDeleted() {
    val callId = 1L
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertDeletedGroupCallFromSyncEvent(
      callId,
      harness.others[0],
      CallTable.Direction.OUTGOING,
      System.currentTimeMillis()
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)

    val oldestDeletionTimestamp = SignalDatabase.calls.getOldestDeletionTimestamp()

    assertEquals(CallTable.Event.DELETE, call?.event)
    assertNotEquals(oldestDeletionTimestamp, 0)
    assertNull(call?.messageId)
  }

  @Test
  fun givenNoPriorEvent_whenIInsertAcceptedOutgoingGroupCall_thenIExpectLocalRingerAndOutgoingRing() {
    val callId = 1L
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertAcceptedGroupCall(
      callId,
      harness.others[0],
      CallTable.Direction.OUTGOING,
      System.currentTimeMillis()
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.OUTGOING_RING, call?.event)
    assertEquals(harness.self.id, call?.ringerRecipient)
    assertNotNull(call?.messageId)
  }

  @Test
  fun givenNoPriorEvent_whenIInsertAcceptedIncomingGroupCall_thenIExpectJoined() {
    val callId = 1L
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertAcceptedGroupCall(
      callId,
      harness.others[0],
      CallTable.Direction.INCOMING,
      System.currentTimeMillis()
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.JOINED, call?.event)
    assertNull(call?.ringerRecipient)
    assertNotNull(call?.messageId)
  }

  @Test
  fun givenARingingCall_whenIAcceptedIncomingGroupCall_thenIExpectAccepted() {
    val callId = 1L
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      ringId = callId,
      groupRecipientId = harness.others[0],
      ringerRecipient = harness.others[1],
      dateReceived = System.currentTimeMillis(),
      ringState = CallManager.RingUpdate.REQUESTED
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.RINGING, call?.event)

    SignalDatabase.calls.acceptIncomingGroupCall(
      call!!
    )

    val acceptedCall = SignalDatabase.calls.getCallById(callId, conversationId)
    assertEquals(CallTable.Event.ACCEPTED, acceptedCall?.event)
  }

  @Test
  fun givenAMissedCall_whenIAcceptedIncomingGroupCall_thenIExpectAccepted() {
    val callId = 1L
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      ringId = callId,
      groupRecipientId = harness.others[0],
      ringerRecipient = harness.others[1],
      dateReceived = System.currentTimeMillis(),
      ringState = CallManager.RingUpdate.EXPIRED_REQUEST
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.MISSED, call?.event)

    SignalDatabase.calls.acceptIncomingGroupCall(
      call!!
    )

    val acceptedCall = SignalDatabase.calls.getCallById(callId, conversationId)
    assertEquals(CallTable.Event.ACCEPTED, acceptedCall?.event)
  }

  @Test
  fun givenADeclinedCall_whenIAcceptedIncomingGroupCall_thenIExpectAccepted() {
    val callId = 1L
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      ringId = callId,
      groupRecipientId = harness.others[0],
      ringerRecipient = harness.others[1],
      dateReceived = System.currentTimeMillis(),
      ringState = CallManager.RingUpdate.DECLINED_ON_ANOTHER_DEVICE
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.DECLINED, call?.event)

    SignalDatabase.calls.acceptIncomingGroupCall(
      call!!
    )

    val acceptedCall = SignalDatabase.calls.getCallById(callId, conversationId)
    assertEquals(CallTable.Event.ACCEPTED, acceptedCall?.event)
  }

  @Test
  fun givenAGenericGroupCall_whenIAcceptedIncomingGroupCall_thenIExpectAccepted() {
    val era = "aaa"
    val callId = CallId.fromEra(era).longValue()
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertOrUpdateGroupCallFromLocalEvent(
      groupRecipientId = harness.others[0],
      sender = harness.others[1],
      timestamp = System.currentTimeMillis(),
      peekGroupCallEraId = "aaa",
      peekJoinedUuids = emptyList(),
      isCallFull = false
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.GENERIC_GROUP_CALL, call?.event)

    SignalDatabase.calls.acceptIncomingGroupCall(
      call!!
    )

    val acceptedCall = SignalDatabase.calls.getCallById(callId, conversationId)
    assertEquals(CallTable.Event.JOINED, acceptedCall?.event)
  }

  @Test
  fun givenNoPriorCallEvent_whenIReceiveAGroupCallUpdateMessage_thenIExpectAGenericGroupCall() {
    val era = "aaa"
    val callId = CallId.fromEra(era).longValue()
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertOrUpdateGroupCallFromLocalEvent(
      groupRecipientId = harness.others[0],
      sender = harness.others[1],
      timestamp = System.currentTimeMillis(),
      peekGroupCallEraId = "aaa",
      peekJoinedUuids = emptyList(),
      isCallFull = false
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.GENERIC_GROUP_CALL, call?.event)
  }

  @Test
  fun givenAPriorCallEventWithNewerTimestamp_whenIReceiveAGroupCallUpdateMessage_thenIExpectAnUpdatedTimestamp() {
    val era = "aaa"
    val callId = CallId.fromEra(era).longValue()
    val now = System.currentTimeMillis()
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertOrUpdateGroupCallFromLocalEvent(
      groupRecipientId = harness.others[0],
      sender = harness.others[1],
      timestamp = now,
      peekGroupCallEraId = "aaa",
      peekJoinedUuids = emptyList(),
      isCallFull = false
    )

    SignalDatabase.calls.getCallById(callId, conversationId).let {
      assertNotNull(it)
      assertEquals(now, it?.timestamp)
    }

    SignalDatabase.calls.insertOrUpdateGroupCallFromLocalEvent(
      groupRecipientId = harness.others[0],
      sender = harness.others[1],
      timestamp = 1L,
      peekGroupCallEraId = "aaa",
      peekJoinedUuids = emptyList(),
      isCallFull = false
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.GENERIC_GROUP_CALL, call?.event)
    assertEquals(1L, call?.timestamp)
  }

  @Test
  fun givenADeletedCallEvent_whenIReceiveARingUpdate_thenIIgnoreTheRingUpdate() {
    val callId = 1L
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertDeletedGroupCallFromSyncEvent(
      callId = callId,
      recipientId = harness.others[0],
      direction = CallTable.Direction.INCOMING,
      timestamp = System.currentTimeMillis()
    )

    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      ringId = callId,
      groupRecipientId = harness.others[0],
      ringerRecipient = harness.others[1],
      dateReceived = System.currentTimeMillis(),
      ringState = CallManager.RingUpdate.REQUESTED
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.DELETE, call?.event)
  }

  @Test
  fun givenAGenericCallEvent_whenRingRequested_thenISetRingerAndMoveToRingingState() {
    val era = "aaa"
    val callId = CallId.fromEra(era).longValue()
    val now = System.currentTimeMillis()
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertOrUpdateGroupCallFromLocalEvent(
      groupRecipientId = harness.others[0],
      sender = harness.others[1],
      timestamp = now,
      peekGroupCallEraId = "aaa",
      peekJoinedUuids = emptyList(),
      isCallFull = false
    )

    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.REQUESTED
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.RINGING, call?.event)
    assertEquals(harness.others[1], call?.ringerRecipient)
  }

  @Test
  fun givenAJoinedCallEvent_whenRingRequested_thenISetRingerAndMoveToRingingState() {
    val era = "aaa"
    val callId = CallId.fromEra(era).longValue()
    val now = System.currentTimeMillis()
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertAcceptedGroupCall(
      callId,
      harness.others[0],
      CallTable.Direction.INCOMING,
      now
    )

    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.REQUESTED
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.ACCEPTED, call?.event)
    assertEquals(harness.others[1], call?.ringerRecipient)
  }

  @Test
  fun givenAGenericCallEvent_whenRingExpired_thenISetRingerAndMoveToMissedState() {
    val era = "aaa"
    val callId = CallId.fromEra(era).longValue()
    val now = System.currentTimeMillis()
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertOrUpdateGroupCallFromLocalEvent(
      groupRecipientId = harness.others[0],
      sender = harness.others[1],
      timestamp = now,
      peekGroupCallEraId = "aaa",
      peekJoinedUuids = emptyList(),
      isCallFull = false
    )

    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.EXPIRED_REQUEST
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.MISSED, call?.event)
    assertEquals(harness.others[1], call?.ringerRecipient)
  }

  @Test
  fun givenARingingCallEvent_whenRingExpired_thenISetRingerAndMoveToMissedState() {
    val era = "aaa"
    val callId = CallId.fromEra(era).longValue()
    val now = System.currentTimeMillis()
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertOrUpdateGroupCallFromLocalEvent(
      groupRecipientId = harness.others[0],
      sender = harness.others[1],
      timestamp = now,
      peekGroupCallEraId = "aaa",
      peekJoinedUuids = emptyList(),
      isCallFull = false
    )

    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.REQUESTED
    )

    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.EXPIRED_REQUEST
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.MISSED, call?.event)
    assertEquals(harness.others[1], call?.ringerRecipient)
  }

  @Test
  fun givenAJoinedCallEvent_whenRingIsCancelledBecauseUserIsBusyLocally_thenIMoveToAcceptedState() {
    val era = "aaa"
    val callId = CallId.fromEra(era).longValue()
    val now = System.currentTimeMillis()
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertAcceptedGroupCall(
      callId,
      harness.others[0],
      CallTable.Direction.INCOMING,
      now
    )

    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.BUSY_LOCALLY
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.ACCEPTED, call?.event)
  }

  @Test
  fun givenAJoinedCallEvent_whenRingIsCancelledBecauseUserIsBusyOnAnotherDevice_thenIMoveToAcceptedState() {
    val era = "aaa"
    val callId = CallId.fromEra(era).longValue()
    val now = System.currentTimeMillis()
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertAcceptedGroupCall(
      callId,
      harness.others[0],
      CallTable.Direction.INCOMING,
      now
    )

    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.BUSY_ON_ANOTHER_DEVICE
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.ACCEPTED, call?.event)
  }

  @Test
  fun givenARingingCallEvent_whenRingCancelledBecauseUserIsBusyLocally_thenIMoveToMissedState() {
    val era = "aaa"
    val callId = CallId.fromEra(era).longValue()
    val now = System.currentTimeMillis()
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertOrUpdateGroupCallFromLocalEvent(
      groupRecipientId = harness.others[0],
      sender = harness.others[1],
      timestamp = now,
      peekGroupCallEraId = "aaa",
      peekJoinedUuids = emptyList(),
      isCallFull = false
    )

    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.REQUESTED
    )

    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.BUSY_LOCALLY
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.MISSED, call?.event)
  }

  @Test
  fun givenARingingCallEvent_whenRingCancelledBecauseUserIsBusyOnAnotherDevice_thenIMoveToMissedState() {
    val era = "aaa"
    val callId = CallId.fromEra(era).longValue()
    val now = System.currentTimeMillis()
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertOrUpdateGroupCallFromLocalEvent(
      groupRecipientId = harness.others[0],
      sender = harness.others[1],
      timestamp = now,
      peekGroupCallEraId = "aaa",
      peekJoinedUuids = emptyList(),
      isCallFull = false
    )

    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.REQUESTED
    )

    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.BUSY_ON_ANOTHER_DEVICE
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.MISSED, call?.event)
  }

  @Test
  fun givenACallEvent_whenRingIsAcceptedOnAnotherDevice_thenIMoveToAcceptedState() {
    val era = "aaa"
    val callId = CallId.fromEra(era).longValue()
    val now = System.currentTimeMillis()
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertOrUpdateGroupCallFromLocalEvent(
      groupRecipientId = harness.others[0],
      sender = harness.others[1],
      timestamp = now,
      peekGroupCallEraId = "aaa",
      peekJoinedUuids = emptyList(),
      isCallFull = false
    )

    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.ACCEPTED_ON_ANOTHER_DEVICE
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.ACCEPTED, call?.event)
  }

  @Test
  fun givenARingingCallEvent_whenRingDeclinedOnAnotherDevice_thenIMoveToDeclinedState() {
    val era = "aaa"
    val callId = CallId.fromEra(era).longValue()
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])

    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.REQUESTED
    )

    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.DECLINED_ON_ANOTHER_DEVICE
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.DECLINED, call?.event)
  }

  @Test
  fun givenAMissedCallEvent_whenRingDeclinedOnAnotherDevice_thenIMoveToDeclinedState() {
    val era = "aaa"
    val callId = CallId.fromEra(era).longValue()
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])

    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.EXPIRED_REQUEST
    )

    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.DECLINED_ON_ANOTHER_DEVICE
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.DECLINED, call?.event)
  }

  @Test
  fun givenAnOutgoingRingCallEvent_whenRingDeclinedOnAnotherDevice_thenIDoNotChangeState() {
    val era = "aaa"
    val callId = CallId.fromEra(era).longValue()
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])

    SignalDatabase.calls.insertAcceptedGroupCall(
      callId,
      harness.others[0],
      CallTable.Direction.OUTGOING,
      System.currentTimeMillis()
    )

    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.DECLINED_ON_ANOTHER_DEVICE
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.OUTGOING_RING, call?.event)
  }

  @Test
  fun givenNoPriorEvent_whenRingRequested_thenICreateAnEventInTheRingingStateAndSetRinger() {
    val callId = 1L
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.REQUESTED
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.RINGING, call?.event)
    assertEquals(harness.others[1], call?.ringerRecipient)
    assertNotNull(call?.messageId)
  }

  @Test
  fun givenNoPriorEvent_whenRingExpired_thenICreateAnEventInTheMissedStateAndSetRinger() {
    val callId = 1L
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.EXPIRED_REQUEST
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.MISSED, call?.event)
    assertEquals(harness.others[1], call?.ringerRecipient)
    assertNotNull(call?.messageId)
  }

  @Test
  fun givenNoPriorEvent_whenRingCancelledByRinger_thenICreateAnEventInTheMissedStateAndSetRinger() {
    val callId = 1L
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.CANCELLED_BY_RINGER
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.MISSED, call?.event)
    assertEquals(harness.others[1], call?.ringerRecipient)
    assertNotNull(call?.messageId)
  }

  @Test
  fun givenNoPriorEvent_whenRingCancelledBecauseUserIsBusyLocally_thenICreateAnEventInTheMissedState() {
    val callId = 1L
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.BUSY_LOCALLY
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.MISSED, call?.event)
    assertNotNull(call?.messageId)
  }

  @Test
  fun givenNoPriorEvent_whenRingCancelledBecauseUserIsBusyOnAnotherDevice_thenICreateAnEventInTheMissedState() {
    val callId = 1L
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.BUSY_ON_ANOTHER_DEVICE
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.MISSED, call?.event)
    assertNotNull(call?.messageId)
  }

  @Test
  fun givenNoPriorEvent_whenRingAcceptedOnAnotherDevice_thenICreateAnEventInTheAcceptedState() {
    val callId = 1L
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.ACCEPTED_ON_ANOTHER_DEVICE
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.ACCEPTED, call?.event)
    assertNotNull(call?.messageId)
  }

  @Test
  fun givenNoPriorEvent_whenRingDeclinedOnAnotherDevice_thenICreateAnEventInTheDeclinedState() {
    val callId = 1L
    val conversationId = CallTable.CallConversationId.Peer(harness.others[0])
    SignalDatabase.calls.insertOrUpdateGroupCallFromRingState(
      callId,
      harness.others[0],
      harness.others[1],
      System.currentTimeMillis(),
      CallManager.RingUpdate.DECLINED_ON_ANOTHER_DEVICE
    )

    val call = SignalDatabase.calls.getCallById(callId, conversationId)
    assertNotNull(call)
    assertEquals(CallTable.Event.DECLINED, call?.event)
    assertNotNull(call?.messageId)
  }
}

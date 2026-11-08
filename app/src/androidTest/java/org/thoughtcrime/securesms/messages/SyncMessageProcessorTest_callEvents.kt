/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.whispersystems.signalservice.internal.push.SyncMessage

@Suppress("ClassName")
@RunWith(AndroidJUnit4::class)
class SyncMessageProcessorTest_callEvents {

  @get:Rule
  val harness = SignalActivityRule(createGroup = true)

  private lateinit var messageHelper: MessageHelper

  @Before
  fun setUp() {
    messageHelper = MessageHelper(harness)
  }

  @After
  fun tearDown() {
    messageHelper.tearDown()
  }

  @Test
  fun givenADeletedCall_whenAcceptedSyncArrives_thenCallStaysDeleted() {
    val callId = 1L
    val peer = messageHelper.alice
    SignalDatabase.calls.insertDeletedCallFromSyncEvent(callId, peer, CallTable.Type.VIDEO_CALL, CallTable.Direction.INCOMING, System.currentTimeMillis())

    messageHelper.syncCallEvent(callId, System.currentTimeMillis(), peer, SyncMessage.CallEvent.Type.VIDEO_CALL, SyncMessage.CallEvent.Direction.INCOMING, SyncMessage.CallEvent.Event.ACCEPTED)

    val call = SignalDatabase.calls.getCallById(callId, peer)!!
    assertThat(call.event).isEqualTo(CallTable.Event.DELETE)
    assertThat(call.messageId).isNull()
  }

  @Test
  fun givenADeletedCall_whenNotAcceptedSyncArrives_thenCallStaysDeleted() {
    val callId = 1L
    val peer = messageHelper.alice
    SignalDatabase.calls.insertDeletedCallFromSyncEvent(callId, peer, CallTable.Type.VIDEO_CALL, CallTable.Direction.INCOMING, System.currentTimeMillis())

    messageHelper.syncCallEvent(callId, System.currentTimeMillis(), peer, SyncMessage.CallEvent.Type.VIDEO_CALL, SyncMessage.CallEvent.Direction.INCOMING, SyncMessage.CallEvent.Event.NOT_ACCEPTED)

    val call = SignalDatabase.calls.getCallById(callId, peer)!!
    assertThat(call.event).isEqualTo(CallTable.Event.DELETE)
    assertThat(call.messageId).isNull()
  }

  @Test
  fun givenADeletedCall_whenDeleteSyncArrives_thenCallStaysDeleted() {
    val callId = 1L
    val peer = messageHelper.alice
    SignalDatabase.calls.insertDeletedCallFromSyncEvent(callId, peer, CallTable.Type.VIDEO_CALL, CallTable.Direction.INCOMING, System.currentTimeMillis())

    messageHelper.syncCallEvent(callId, System.currentTimeMillis(), peer, SyncMessage.CallEvent.Type.VIDEO_CALL, SyncMessage.CallEvent.Direction.INCOMING, SyncMessage.CallEvent.Event.DELETE)

    val call = SignalDatabase.calls.getCallById(callId, peer)!!
    assertThat(call.event).isEqualTo(CallTable.Event.DELETE)
    assertThat(SignalDatabase.calls.getOldestDeletionTimestamp()).isGreaterThan(0L)
  }

  @Test
  fun givenAnAcceptedCall_whenNotAcceptedSyncArrives_thenCallStaysAccepted() {
    val callId = 1L
    val peer = messageHelper.alice
    SignalDatabase.calls.insertOneToOneCall(callId, System.currentTimeMillis(), peer, CallTable.Type.VIDEO_CALL, CallTable.Direction.INCOMING, CallTable.Event.ACCEPTED)

    messageHelper.syncCallEvent(callId, System.currentTimeMillis(), peer, SyncMessage.CallEvent.Type.VIDEO_CALL, SyncMessage.CallEvent.Direction.INCOMING, SyncMessage.CallEvent.Event.NOT_ACCEPTED)

    val call = SignalDatabase.calls.getCallById(callId, peer)!!
    assertThat(call.event).isEqualTo(CallTable.Event.ACCEPTED)
  }

  @Test
  fun givenAMissedCall_whenAcceptedSyncArrives_thenCallIsAccepted() {
    val callId = 1L
    val peer = messageHelper.alice
    SignalDatabase.calls.insertOneToOneCall(callId, System.currentTimeMillis(), peer, CallTable.Type.VIDEO_CALL, CallTable.Direction.INCOMING, CallTable.Event.MISSED)

    messageHelper.syncCallEvent(callId, System.currentTimeMillis(), peer, SyncMessage.CallEvent.Type.VIDEO_CALL, SyncMessage.CallEvent.Direction.INCOMING, SyncMessage.CallEvent.Event.ACCEPTED)

    val call = SignalDatabase.calls.getCallById(callId, peer)!!
    assertThat(call.event).isEqualTo(CallTable.Event.ACCEPTED)
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation

import org.junit.Assert.assertEquals
import org.junit.Test
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.recipients.RecipientId

class CallRowResourcesTest {

  @Test
  fun `incoming audio call`() {
    val call = call(CallTable.Type.AUDIO_CALL, CallTable.Direction.INCOMING, CallTable.Event.ACCEPTED)

    assertEquals(R.drawable.symbol_arrow_downleft_24, CallRowResources.iconRes(call))
    assertEquals(R.string.MessageRecord_incoming_voice_call, CallRowResources.typeStringRes(call))
  }

  @Test
  fun `incoming video call`() {
    val call = call(CallTable.Type.VIDEO_CALL, CallTable.Direction.INCOMING, CallTable.Event.ACCEPTED)

    assertEquals(R.drawable.symbol_arrow_downleft_24, CallRowResources.iconRes(call))
    assertEquals(R.string.MessageRecord_incoming_video_call, CallRowResources.typeStringRes(call))
  }

  @Test
  fun `outgoing audio call`() {
    val call = call(CallTable.Type.AUDIO_CALL, CallTable.Direction.OUTGOING, CallTable.Event.ACCEPTED)

    assertEquals(R.drawable.symbol_arrow_upright_24, CallRowResources.iconRes(call))
    assertEquals(R.string.MessageRecord_outgoing_voice_call, CallRowResources.typeStringRes(call))
  }

  @Test
  fun `outgoing video call`() {
    val call = call(CallTable.Type.VIDEO_CALL, CallTable.Direction.OUTGOING, CallTable.Event.ACCEPTED)

    assertEquals(R.drawable.symbol_arrow_upright_24, CallRowResources.iconRes(call))
    assertEquals(R.string.MessageRecord_outgoing_video_call, CallRowResources.typeStringRes(call))
  }

  @Test
  fun `unanswered outgoing calls read as declined`() {
    val audio = call(CallTable.Type.AUDIO_CALL, CallTable.Direction.OUTGOING, CallTable.Event.NOT_ACCEPTED)
    val video = call(CallTable.Type.VIDEO_CALL, CallTable.Direction.OUTGOING, CallTable.Event.NOT_ACCEPTED)

    assertEquals(R.string.MessageRecord_unanswered_voice_call, CallRowResources.typeStringRes(audio))
    assertEquals(R.string.MessageRecord_unanswered_video_call, CallRowResources.typeStringRes(video))
  }

  @Test
  fun `missed calls`() {
    val audio = call(CallTable.Type.AUDIO_CALL, CallTable.Direction.INCOMING, CallTable.Event.MISSED)
    val video = call(CallTable.Type.VIDEO_CALL, CallTable.Direction.INCOMING, CallTable.Event.MISSED)

    assertEquals(R.drawable.symbol_missed_incoming_24, CallRowResources.iconRes(audio))
    assertEquals(R.string.MessageRecord_missed_voice_call, CallRowResources.typeStringRes(audio))
    assertEquals(R.string.MessageRecord_missed_video_call, CallRowResources.typeStringRes(video))
  }

  @Test
  fun `missed calls declined by a notification profile get their own label`() {
    val audio = call(CallTable.Type.AUDIO_CALL, CallTable.Direction.INCOMING, CallTable.Event.MISSED_NOTIFICATION_PROFILE)
    val video = call(CallTable.Type.VIDEO_CALL, CallTable.Direction.INCOMING, CallTable.Event.MISSED_NOTIFICATION_PROFILE)

    assertEquals(R.string.MessageRecord_missed_voice_call_notification_profile, CallRowResources.typeStringRes(audio))
    assertEquals(R.string.MessageRecord_missed_video_call_notification_profile, CallRowResources.typeStringRes(video))
  }

  @Test
  fun `incoming calls we declined read as missed`() {
    val audio = call(CallTable.Type.AUDIO_CALL, CallTable.Direction.INCOMING, CallTable.Event.DECLINED)
    val video = call(CallTable.Type.VIDEO_CALL, CallTable.Direction.INCOMING, CallTable.Event.DECLINED)

    assertEquals(R.drawable.symbol_missed_incoming_24, CallRowResources.iconRes(audio))
    assertEquals(R.string.MessageRecord_missed_voice_call, CallRowResources.typeStringRes(audio))
    assertEquals(R.string.MessageRecord_missed_video_call, CallRowResources.typeStringRes(video))
  }

  @Test
  fun `incoming calls the caller gave up on read as declined`() {
    val audio = call(CallTable.Type.AUDIO_CALL, CallTable.Direction.INCOMING, CallTable.Event.NOT_ACCEPTED)
    val video = call(CallTable.Type.VIDEO_CALL, CallTable.Direction.INCOMING, CallTable.Event.NOT_ACCEPTED)

    assertEquals(R.drawable.symbol_missed_incoming_24, CallRowResources.iconRes(audio))
    assertEquals(R.string.MessageRecord_declined_voice_call, CallRowResources.typeStringRes(audio))
    assertEquals(R.string.MessageRecord_declined_video_call, CallRowResources.typeStringRes(video))
  }

  @Test
  fun `group call that we joined`() {
    val call = call(CallTable.Type.GROUP_CALL, CallTable.Direction.INCOMING, CallTable.Event.JOINED)

    assertEquals(R.drawable.symbol_group_24, CallRowResources.iconRes(call))
    assertEquals(R.string.CallPreference__group_call, CallRowResources.typeStringRes(call))
  }

  @Test
  fun `generic group call we never joined reads as missed`() {
    val call = call(CallTable.Type.GROUP_CALL, CallTable.Direction.INCOMING, CallTable.Event.GENERIC_GROUP_CALL)

    assertEquals(R.drawable.symbol_missed_incoming_24, CallRowResources.iconRes(call))
    assertEquals(R.string.CallPreference__missed_group_call, CallRowResources.typeStringRes(call))
  }

  @Test
  fun `generic group call we joined reads as a group call`() {
    val call = call(CallTable.Type.GROUP_CALL, CallTable.Direction.INCOMING, CallTable.Event.GENERIC_GROUP_CALL, didLocalUserJoin = true)

    assertEquals(R.drawable.symbol_group_24, CallRowResources.iconRes(call))
    assertEquals(R.string.CallPreference__group_call, CallRowResources.typeStringRes(call))
  }

  @Test
  fun `missed group call declined by a notification profile gets its own label`() {
    val call = call(CallTable.Type.GROUP_CALL, CallTable.Direction.INCOMING, CallTable.Event.MISSED_NOTIFICATION_PROFILE)

    assertEquals(R.drawable.symbol_missed_incoming_24, CallRowResources.iconRes(call))
    assertEquals(R.string.CallPreference__missed_group_call_notification_profile, CallRowResources.typeStringRes(call))
  }

  @Test
  fun `incoming group call ring we accepted`() {
    val call = call(CallTable.Type.GROUP_CALL, CallTable.Direction.INCOMING, CallTable.Event.ACCEPTED)

    assertEquals(R.drawable.symbol_arrow_downleft_24, CallRowResources.iconRes(call))
    assertEquals(R.string.CallPreference__incoming_group_call, CallRowResources.typeStringRes(call))
  }

  @Test
  fun `outgoing group call`() {
    val call = call(CallTable.Type.GROUP_CALL, CallTable.Direction.OUTGOING, CallTable.Event.ACCEPTED)

    assertEquals(R.drawable.symbol_arrow_upright_24, CallRowResources.iconRes(call))
    assertEquals(R.string.CallPreference__outgoing_group_call, CallRowResources.typeStringRes(call))
  }

  private fun call(
    type: CallTable.Type,
    direction: CallTable.Direction,
    event: CallTable.Event,
    didLocalUserJoin: Boolean = false
  ): CallTable.Call {
    return CallTable.Call(
      callId = 1L,
      peer = RecipientId.from(1L),
      type = type,
      direction = direction,
      event = event,
      messageId = 1L,
      timestamp = 1000L,
      ringerRecipient = null,
      isGroupCallActive = false,
      didLocalUserJoin = didLocalUserJoin,
      read = true
    )
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.MessageTypes

/**
 * Maps a call to the icon and label that [callLogSection] shows for it.
 */
object CallRowResources {

  @DrawableRes
  fun iconRes(call: CallTable.Call): Int {
    return when (call.messageType) {
      MessageTypes.MISSED_VIDEO_CALL_TYPE, MessageTypes.MISSED_AUDIO_CALL_TYPE -> {
        R.drawable.symbol_missed_incoming_24
      }
      MessageTypes.INCOMING_AUDIO_CALL_TYPE, MessageTypes.INCOMING_VIDEO_CALL_TYPE -> {
        if (call.isDisplayedAsMissedCallInUi) R.drawable.symbol_missed_incoming_24 else R.drawable.symbol_arrow_downleft_24
      }
      MessageTypes.OUTGOING_AUDIO_CALL_TYPE, MessageTypes.OUTGOING_VIDEO_CALL_TYPE -> {
        R.drawable.symbol_arrow_upright_24
      }
      MessageTypes.GROUP_CALL_TYPE -> {
        when {
          call.isDisplayedAsMissedCallInUi -> R.drawable.symbol_missed_incoming_24
          call.event == CallTable.Event.GENERIC_GROUP_CALL || call.event == CallTable.Event.JOINED -> R.drawable.symbol_group_24
          call.direction == CallTable.Direction.INCOMING -> R.drawable.symbol_arrow_downleft_24
          call.direction == CallTable.Direction.OUTGOING -> R.drawable.symbol_arrow_upright_24
          else -> error("Unexpected group call state: event=${call.event}, direction=${call.direction}")
        }
      }
      else -> {
        error("Unexpected type ${call.type}")
      }
    }
  }

  @StringRes
  fun typeStringRes(call: CallTable.Call): Int {
    return when (call.messageType) {
      MessageTypes.MISSED_AUDIO_CALL_TYPE -> {
        missedCallStringRes(isVideo = false, callEvent = call.event)
      }
      MessageTypes.MISSED_VIDEO_CALL_TYPE -> {
        missedCallStringRes(isVideo = true, callEvent = call.event)
      }
      MessageTypes.INCOMING_AUDIO_CALL_TYPE -> {
        if (call.isDisplayedAsMissedCallInUi) missedCallStringRes(false, call.event) else R.string.MessageRecord_incoming_voice_call
      }
      MessageTypes.INCOMING_VIDEO_CALL_TYPE -> {
        if (call.isDisplayedAsMissedCallInUi) missedCallStringRes(true, call.event) else R.string.MessageRecord_incoming_video_call
      }
      MessageTypes.OUTGOING_AUDIO_CALL_TYPE -> {
        if (call.event == CallTable.Event.NOT_ACCEPTED) R.string.MessageRecord_unanswered_voice_call else R.string.MessageRecord_outgoing_voice_call
      }
      MessageTypes.OUTGOING_VIDEO_CALL_TYPE -> {
        if (call.event == CallTable.Event.NOT_ACCEPTED) R.string.MessageRecord_unanswered_video_call else R.string.MessageRecord_outgoing_video_call
      }
      MessageTypes.GROUP_CALL_TYPE -> {
        when {
          call.isDisplayedAsMissedCallInUi -> {
            if (call.event == CallTable.Event.MISSED_NOTIFICATION_PROFILE) {
              R.string.CallPreference__missed_group_call_notification_profile
            } else {
              R.string.CallPreference__missed_group_call
            }
          }
          call.event == CallTable.Event.GENERIC_GROUP_CALL || call.event == CallTable.Event.JOINED -> R.string.CallPreference__group_call
          call.direction == CallTable.Direction.INCOMING -> R.string.CallPreference__incoming_group_call
          call.direction == CallTable.Direction.OUTGOING -> R.string.CallPreference__outgoing_group_call
          else -> error("Unexpected group call state: event=${call.event}, direction=${call.direction}")
        }
      }
      else -> {
        error("Unexpected type ${call.messageType}")
      }
    }
  }

  @StringRes
  private fun missedCallStringRes(isVideo: Boolean, callEvent: CallTable.Event): Int {
    return when (callEvent) {
      CallTable.Event.MISSED_NOTIFICATION_PROFILE -> {
        if (isVideo) R.string.MessageRecord_missed_video_call_notification_profile else R.string.MessageRecord_missed_voice_call_notification_profile
      }
      CallTable.Event.NOT_ACCEPTED -> {
        if (isVideo) R.string.MessageRecord_declined_video_call else R.string.MessageRecord_declined_voice_call
      }
      else -> {
        if (isVideo) R.string.MessageRecord_missed_video_call else R.string.MessageRecord_missed_voice_call
      }
    }
  }
}

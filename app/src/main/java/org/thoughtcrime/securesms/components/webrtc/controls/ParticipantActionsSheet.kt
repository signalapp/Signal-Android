/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.controls

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.toLiveData
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Observable
import org.signal.core.ui.compose.AllNightPreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.SignalE164Util

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantActionsSheet(
  callParticipant: CallParticipant,
  isSelfAdmin: Boolean,
  isCallLink: Boolean,
  onDismiss: () -> Unit,
  onMuteAudio: (CallParticipant) -> Unit,
  onRemoveFromCall: (CallParticipant) -> Unit,
  onContactDetails: (CallParticipant) -> Unit,
  onViewSafetyNumber: (CallParticipant) -> Unit,
  onGoToChat: (CallParticipant) -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState
  ) {
    val recipient by (
      (if (LocalInspectionMode.current) Observable.just(Recipient.UNKNOWN) else Recipient.observable(callParticipant.recipient.id))
        .toFlowable(BackpressureStrategy.LATEST)
        .toLiveData()
        .observeAsState(initial = callParticipant.recipient)
      )

    ParticipantActionsSheetContent(
      recipient = recipient,
      callParticipant = callParticipant,
      isSelfAdmin = isSelfAdmin,
      isCallLink = isCallLink,
      onDismiss = onDismiss,
      onMuteAudio = onMuteAudio,
      onRemoveFromCall = onRemoveFromCall,
      onContactDetails = onContactDetails,
      onViewSafetyNumber = onViewSafetyNumber,
      onGoToChat = onGoToChat
    )
  }
}

@Composable
private fun ParticipantActionsSheetContent(
  recipient: Recipient,
  callParticipant: CallParticipant,
  isSelfAdmin: Boolean,
  isCallLink: Boolean,
  onDismiss: () -> Unit,
  onMuteAudio: (CallParticipant) -> Unit,
  onRemoveFromCall: (CallParticipant) -> Unit,
  onContactDetails: (CallParticipant) -> Unit,
  onViewSafetyNumber: (CallParticipant) -> Unit,
  onGoToChat: (CallParticipant) -> Unit
) {
  ParticipantHeader(recipient = recipient)

  val hasAdminActions = isSelfAdmin && (callParticipant.isMicrophoneEnabled || isCallLink)

  if (hasAdminActions) {
    Dividers.Default()

    if (callParticipant.isMicrophoneEnabled) {
      Rows.TextRow(
        text = stringResource(id = R.string.CallParticipantSheet__mute_audio),
        icon = painterResource(id = R.drawable.symbol_mic_slash_24),
        onClick = {
          onMuteAudio(callParticipant)
          onDismiss()
        }
      )
    }

    if (isCallLink) {
      Rows.TextRow(
        text = stringResource(id = R.string.CallParticipantSheet__remove_from_call),
        icon = painterResource(id = R.drawable.symbol_minus_circle_24),
        onClick = {
          onRemoveFromCall(callParticipant)
          onDismiss()
        }
      )
    }
  }

  Dividers.Default()

  Rows.TextRow(
    text = stringResource(id = R.string.CallParticipantSheet__contact_details),
    icon = painterResource(id = R.drawable.symbol_person_24),
    onClick = {
      onContactDetails(callParticipant)
      onDismiss()
    }
  )

  Rows.TextRow(
    text = stringResource(id = R.string.ConversationSettingsFragment__view_safety_number),
    icon = painterResource(id = R.drawable.symbol_safety_number_24),
    onClick = {
      onViewSafetyNumber(callParticipant)
      onDismiss()
    }
  )

  Rows.TextRow(
    text = stringResource(id = R.string.CallContextMenu__go_to_chat),
    icon = painterResource(id = R.drawable.symbol_open_24),
    onClick = {
      onGoToChat(callParticipant)
      onDismiss()
    }
  )

  Spacer(modifier = Modifier.size(48.dp))
}

@Composable
private fun ParticipantHeader(recipient: Recipient) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 16.dp)
  ) {
    if (LocalInspectionMode.current) {
      Spacer(modifier = Modifier.size(64.dp))
    } else {
      AndroidView(
        factory = ::AvatarImageView,
        modifier = Modifier.size(64.dp)
      ) {
        it.setAvatarUsingProfile(recipient)
      }
    }

    Spacer(modifier = Modifier.size(12.dp))

    Text(
      text = recipient.getDisplayName(androidx.compose.ui.platform.LocalContext.current),
      style = MaterialTheme.typography.titleLarge
    )

    if (recipient.shouldShowE164) {
      Spacer(modifier = Modifier.size(2.dp))
      Text(
        text = SignalE164Util.prettyPrint(recipient.requireE164()),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@AllNightPreviews
@Composable
private fun ParticipantActionsSheetAdminPreview() {
  Previews.BottomSheetPreview {
    ParticipantActionsSheetContent(
      recipient = Recipient(isResolving = false, systemContactName = "Peter Parker"),
      callParticipant = CallParticipant(
        recipient = Recipient(isResolving = false, systemContactName = "Peter Parker"),
        isMicrophoneEnabled = true
      ),
      isSelfAdmin = true,
      isCallLink = true,
      onDismiss = {},
      onMuteAudio = {},
      onRemoveFromCall = {},
      onContactDetails = {},
      onViewSafetyNumber = {},
      onGoToChat = {}
    )
  }
}

@AllNightPreviews
@Composable
private fun ParticipantActionsSheetNonAdminPreview() {
  Previews.BottomSheetPreview {
    ParticipantActionsSheetContent(
      recipient = Recipient(isResolving = false, systemContactName = "Gwen Stacy"),
      callParticipant = CallParticipant(
        recipient = Recipient(isResolving = false, systemContactName = "Gwen Stacy")
      ),
      isSelfAdmin = false,
      isCallLink = false,
      onDismiss = {},
      onMuteAudio = {},
      onRemoveFromCall = {},
      onContactDetails = {},
      onViewSafetyNumber = {},
      onGoToChat = {}
    )
  }
}

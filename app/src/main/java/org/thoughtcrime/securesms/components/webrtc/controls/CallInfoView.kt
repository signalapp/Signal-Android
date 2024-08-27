/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.controls

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rxjava3.subscribeAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.toLiveData
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Observable
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Dividers
import org.signal.core.ui.Previews
import org.signal.core.ui.Rows
import org.signal.core.ui.theme.LocalExtendedColors
import org.signal.core.ui.theme.SignalTheme
import org.signal.ringrtc.CallLinkState
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatar
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatarImage
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.components.webrtc.WebRtcCallViewModel
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.events.GroupCallRaiseHandEvent
import org.thoughtcrime.securesms.events.WebRtcViewModel
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Renders information about a call (1:1, group, or call link) and provides actions available for
 * said call (e.g., raise hand, kick, etc)
 */
object CallInfoView {

  @Composable
  fun View(
    webRtcCallViewModel: WebRtcCallViewModel,
    controlsAndInfoViewModel: ControlsAndInfoViewModel,
    callbacks: Callbacks,
    modifier: Modifier
  ) {
    val participantsState: ParticipantsState by webRtcCallViewModel.callParticipantsState
      .toFlowable(BackpressureStrategy.LATEST)
      .map { state ->
        ParticipantsState(
          inCallLobby = state.callState == WebRtcViewModel.State.CALL_PRE_JOIN,
          ringGroup = state.ringGroup,
          includeSelf = state.groupCallState === WebRtcViewModel.GroupCallState.CONNECTED_AND_JOINED || state.groupCallState === WebRtcViewModel.GroupCallState.IDLE,
          participantCount = if (state.participantCount.isPresent) state.participantCount.asLong.toInt() else 0,
          remoteParticipants = state.allRemoteParticipants.sortedBy { it.callParticipantId.recipientId },
          localParticipant = state.localParticipant,
          groupMembers = state.groupMembers.filterNot { it.member.isSelf },
          callRecipient = state.recipient,
          raisedHands = state.raisedHands
        )
      }
      .subscribeAsState(ParticipantsState())

    val controlAndInfoState: ControlAndInfoState by controlsAndInfoViewModel.state

    val onEditNameClicked: () -> Unit = remember(controlAndInfoState) {
      {
        callbacks.onEditNameClicked(controlAndInfoState.callLink?.state?.name ?: "")
      }
    }

    SignalTheme(
      isDarkMode = true
    ) {
      Surface {
        CallInfo(
          participantsState = participantsState,
          controlAndInfoState = controlAndInfoState,
          onShareLinkClicked = callbacks::onShareLinkClicked,
          onEditNameClicked = onEditNameClicked,
          onToggleAdminApprovalClicked = callbacks::onToggleAdminApprovalClicked,
          onBlock = callbacks::onBlock,
          modifier = modifier
        )
      }
    }
  }

  interface Callbacks {
    fun onShareLinkClicked()
    fun onEditNameClicked(name: String)
    fun onToggleAdminApprovalClicked(checked: Boolean)
    fun onBlock(callParticipant: CallParticipant)
  }
}

@Preview
@Composable
private fun CallInfoPreview() {
  SignalTheme(isDarkMode = true) {
    Surface {
      val remoteParticipants = listOf(CallParticipant(recipient = Recipient.UNKNOWN))
      CallInfo(
        participantsState = ParticipantsState(remoteParticipants = remoteParticipants, raisedHands = remoteParticipants.map { GroupCallRaiseHandEvent(it, System.currentTimeMillis()) }),
        controlAndInfoState = ControlAndInfoState(),
        onShareLinkClicked = { },
        onEditNameClicked = { },
        onToggleAdminApprovalClicked = { },
        onBlock = { }
      )
    }
  }
}

@Composable
private fun CallInfo(
  participantsState: ParticipantsState,
  controlAndInfoState: ControlAndInfoState,
  onShareLinkClicked: () -> Unit,
  onEditNameClicked: () -> Unit,
  onToggleAdminApprovalClicked: (Boolean) -> Unit,
  onBlock: (CallParticipant) -> Unit,
  modifier: Modifier = Modifier
) {
  val listState = rememberLazyListState()

  LaunchedEffect(controlAndInfoState.resetScrollState) {
    listState.scrollToItem(0)
  }

  LazyColumn(
    state = listState,
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
  ) {
    item {
      val text = if (controlAndInfoState.callLink == null) {
        stringResource(id = R.string.CallLinkInfoSheet__call_info)
      } else if (controlAndInfoState.callLink.state.name.isNotEmpty()) {
        controlAndInfoState.callLink.state.name
      } else {
        stringResource(id = R.string.Recipient_signal_call)
      }

      Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = 24.dp)
      )
    }

    if (controlAndInfoState.callLink != null) {
      item {
        Rows.TextRow(
          text = stringResource(id = R.string.CallLinkDetailsFragment__share_link),
          icon = painterResource(id = R.drawable.symbol_link_24),
          iconModifier = Modifier
            .background(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = CircleShape
            )
            .size(42.dp)
            .padding(9.dp),
          onClick = onShareLinkClicked,
          modifier = Modifier
            .defaultMinSize(minHeight = 64.dp)
        )

        Dividers.Default()
      }
    }

    if (participantsState.raisedHands.isNotEmpty()) {
      item {
        Box(
          modifier = Modifier
            .padding(horizontal = 24.dp)
            .defaultMinSize(minHeight = 52.dp)
            .fillMaxWidth(),
          contentAlignment = Alignment.CenterStart
        ) {
          Text(
            text = pluralStringResource(id = R.plurals.CallParticipantsListDialog__raised_hands, count = participantsState.raisedHands.size, participantsState.raisedHands.size),
            style = MaterialTheme.typography.titleSmall
          )
        }
      }

      items(
        items = participantsState.raisedHands.map { it.sender },
        key = {
          val key: Long = it.callParticipantId.demuxId // Due to a bug in how the Compose toolchain inlines saveable states, this Long needs to be set into its own variable within the lambda before being returned.
          key
        }
      ) {
        HandRaisedRow(recipient = it.recipient, it.getShortRecipientDisplayName(LocalContext.current), it.isSelf && it.isPrimary)
      }

      item {
        Dividers.Default()
      }
    }

    if (!participantsState.inCallLobby || participantsState.isOngoing()) {
      item {
        Box(
          modifier = Modifier
            .padding(horizontal = 24.dp)
            .defaultMinSize(minHeight = 52.dp)
            .fillMaxWidth(),
          contentAlignment = Alignment.CenterStart
        ) {
          Text(
            text = getCallSheetLabel(participantsState),
            style = MaterialTheme.typography.titleSmall
          )
        }
      }
    }

    if (!participantsState.inCallLobby || participantsState.isOngoing()) {
      items(
        items = participantsState.participantsForList.distinctBy { it.callParticipantId },
        key = { it.callParticipantId },
        contentType = { null }
      ) {
        CallParticipantRow(
          callParticipant = it,
          isSelfAdmin = controlAndInfoState.isSelfAdmin() && !participantsState.inCallLobby,
          onBlockClicked = onBlock
        )
      }

      if (participantsState.inCallLobby && participantsState.unknownParticipantCount > 0) {
        item {
          UnknownMembersRow(
            unknownMemberCount = participantsState.unknownParticipantCount,
            allCallMembersAreUnknown = participantsState.participantsForList.isEmpty()
          )
        }
      }
    } else if (participantsState.isGroupCall()) {
      items(
        items = participantsState.groupMembers,
        key = { it.member.id.toLong() },
        contentType = { null }
      ) {
        GroupMemberRow(
          groupMember = it,
          isSelfAdmin = false
        )
      }
    } else if (controlAndInfoState.callLink == null) {
      item {
        CallParticipantRow(
          initialRecipient = participantsState.callRecipient,
          name = participantsState.callRecipient.getShortDisplayName(LocalContext.current),
          showIcons = false,
          isVideoEnabled = false,
          isMicrophoneEnabled = false,
          showHandRaised = false,
          canLowerHand = false,
          isSelfAdmin = false,
          onBlockClicked = {}
        )
      }
    }

    if (controlAndInfoState.callLink?.credentials?.adminPassBytes != null) {
      item {
        if (!participantsState.inCallLobby) {
          Dividers.Default()
        }

        Rows.TextRow(
          text = if (controlAndInfoState.callLink.state.name.isNotEmpty()) {
            stringResource(id = R.string.CallLinkDetailsFragment__edit_call_name)
          } else {
            stringResource(id = R.string.CallLinkDetailsFragment__add_call_name)
          },
          onClick = onEditNameClicked
        )
        Rows.ToggleRow(
          checked = controlAndInfoState.callLink.state.restrictions == CallLinkState.Restrictions.ADMIN_APPROVAL,
          text = stringResource(id = R.string.CallLinkDetailsFragment__approve_all_members),
          onCheckChanged = onToggleAdminApprovalClicked
        )
      }
    }

    item {
      Spacer(modifier = Modifier.size(48.dp))
    }
  }
}

@Composable
private fun getCallSheetLabel(state: ParticipantsState): String {
  return if (!state.inCallLobby || state.isOngoing()) {
    pluralStringResource(id = R.plurals.CallParticipantsListDialog_in_this_call, count = state.participantCountForDisplay, state.participantCountForDisplay)
  } else if (state.isGroupCall()) {
    val groupSize = state.groupMembers.size
    if (state.ringGroup) {
      pluralStringResource(id = R.plurals.CallParticipantsListDialog__signal_will_ring, count = groupSize, groupSize)
    } else {
      pluralStringResource(id = R.plurals.CallParticipantsListDialog__signal_will_notify, count = groupSize, groupSize)
    }
  } else {
    pluralStringResource(id = R.plurals.CallParticipantsListDialog__signal_will_ring, count = 1, 1)
  }
}

@Preview
@Composable
private fun CallParticipantRowPreview() {
  SignalTheme(isDarkMode = true) {
    Surface {
      CallParticipantRow(
        CallParticipant(recipient = Recipient.UNKNOWN),
        isSelfAdmin = true
      ) {}
    }
  }
}

@Preview
@Composable
private fun HandRaisedRowPreview() {
  SignalTheme(isDarkMode = true) {
    Surface {
      HandRaisedRow(Recipient.UNKNOWN, "Peter Parker", canLowerHand = true)
    }
  }
}

@Composable
private fun CallParticipantRow(
  callParticipant: CallParticipant,
  isSelfAdmin: Boolean,
  onBlockClicked: (CallParticipant) -> Unit
) {
  CallParticipantRow(
    initialRecipient = callParticipant.recipient,
    name = callParticipant.getShortRecipientDisplayName(LocalContext.current),
    showIcons = true,
    isVideoEnabled = callParticipant.isVideoEnabled,
    isMicrophoneEnabled = callParticipant.isMicrophoneEnabled,
    showHandRaised = false,
    canLowerHand = false,
    isSelfAdmin = isSelfAdmin,
    onBlockClicked = { onBlockClicked(callParticipant) }
  )
}

@Composable
private fun HandRaisedRow(recipient: Recipient, name: String, canLowerHand: Boolean) {
  CallParticipantRow(
    initialRecipient = recipient,
    name = name,
    showIcons = true,
    isVideoEnabled = true,
    isMicrophoneEnabled = true,
    showHandRaised = true,
    canLowerHand = canLowerHand,
    isSelfAdmin = false,
    onBlockClicked = {}
  )
}

@Composable
private fun CallParticipantRow(
  initialRecipient: Recipient,
  name: String,
  showIcons: Boolean,
  isVideoEnabled: Boolean,
  isMicrophoneEnabled: Boolean,
  showHandRaised: Boolean,
  canLowerHand: Boolean,
  isSelfAdmin: Boolean,
  onBlockClicked: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(Rows.defaultPadding())
  ) {
    val recipient by ((if (LocalInspectionMode.current) Observable.just(Recipient.UNKNOWN) else Recipient.observable(initialRecipient.id)))
      .toFlowable(BackpressureStrategy.LATEST)
      .toLiveData()
      .observeAsState(initial = initialRecipient)

    if (LocalInspectionMode.current) {
      Spacer(
        modifier = Modifier
          .size(40.dp)
          .background(color = Color.Red, shape = CircleShape)
      )
    } else {
      AndroidView(
        factory = ::AvatarImageView,
        modifier = Modifier.size(40.dp)
      ) {
        it.setAvatarUsingProfile(recipient)
      }
    }

    Spacer(modifier = Modifier.width(24.dp))

    Text(
      text = name,
      modifier = Modifier
        .weight(1f)
        .align(Alignment.CenterVertically)
    )

    if (showIcons && showHandRaised && canLowerHand) {
      val context = LocalContext.current
      TextButton(onClick = {
        if (recipient.isSelf) {
          showLowerHandDialog(context)
        }
      }) {
        Text(text = stringResource(id = R.string.CallOverflowPopupWindow__lower_hand))
      }
      Spacer(modifier = Modifier.width(16.dp))
    }

    if (showIcons && showHandRaised) {
      Icon(
        painter = painterResource(id = R.drawable.symbol_raise_hand_24),
        contentDescription = null,
        modifier = Modifier.align(Alignment.CenterVertically)
      )
    }

    if (showIcons && !isVideoEnabled) {
      Icon(
        painter = painterResource(id = R.drawable.symbol_video_slash_24),
        contentDescription = null,
        modifier = Modifier.align(Alignment.CenterVertically)
      )
    }

    if (showIcons && !isMicrophoneEnabled) {
      if (!isVideoEnabled) {
        Spacer(modifier = Modifier.width(16.dp))
      }

      Icon(
        painter = painterResource(id = R.drawable.symbol_mic_slash_24),
        contentDescription = null,
        modifier = Modifier.align(Alignment.CenterVertically)
      )
    }

    if (showIcons && isSelfAdmin && !recipient.isSelf) {
      if (!isMicrophoneEnabled) {
        Spacer(modifier = Modifier.width(16.dp))
      }

      Icon(
        painter = painterResource(id = R.drawable.symbol_minus_circle_24),
        contentDescription = null,
        modifier = Modifier
          .clickable(onClick = onBlockClicked)
          .align(Alignment.CenterVertically)
      )
    }
  }
}

private fun showLowerHandDialog(context: Context) {
  MaterialAlertDialogBuilder(context)
    .setTitle(R.string.CallOverflowPopupWindow__lower_your_hand)
    .setPositiveButton(
      R.string.CallOverflowPopupWindow__lower_hand
    ) { _, _ -> AppDependencies.signalCallManager.raiseHand(false) }
    .setNegativeButton(R.string.CallOverflowPopupWindow__cancel, null)
    .show()
}

@Composable
private fun GroupMemberRow(
  groupMember: GroupMemberEntry.FullMember,
  isSelfAdmin: Boolean
) {
  CallParticipantRow(
    initialRecipient = groupMember.member,
    name = groupMember.member.getShortDisplayName(LocalContext.current),
    showIcons = false,
    isVideoEnabled = false,
    isMicrophoneEnabled = false,
    showHandRaised = false,
    canLowerHand = false,
    isSelfAdmin = isSelfAdmin
  ) {}
}

@Composable
private fun UnknownMembersRow(
  unknownMemberCount: Int,
  allCallMembersAreUnknown: Boolean
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(Rows.defaultPadding())
  ) {
    when (unknownMemberCount) {
      1 -> SingleUnknownAvatar()
      2 -> TwoUnknownAvatars()
      else -> ThreeUnknownAvatars()
    }

    val textResId = if (allCallMembersAreUnknown) {
      R.plurals.CallInfoView__d_people
    } else {
      R.plurals.CallInfoView__plus_d_people
    }

    Text(
      text = pluralStringResource(
        id = textResId,
        count = unknownMemberCount,
        unknownMemberCount
      ),
      modifier = Modifier
        .weight(1f)
        .align(Alignment.CenterVertically)
        .padding(horizontal = 24.dp)
    )

    var displayDialog by remember { mutableStateOf(false) }

    Icon(
      painter = painterResource(id = R.drawable.symbol_info_24),
      contentDescription = stringResource(id = R.string.CallInfoView__more_information),
      modifier = Modifier.clickable(onClick = {
        displayDialog = true
      })
    )

    if (displayDialog) {
      Dialogs.SimpleMessageDialog(
        message = stringResource(id = R.string.CallInfoView__before_joining_a_call),
        dismiss = stringResource(id = R.string.CallInfoView__got_it),
        onDismiss = { displayDialog = false }
      )
    }
  }
}

@Composable
private fun SingleUnknownAvatar() {
  FallbackAvatarImage(
    fallbackAvatar = FallbackAvatar.Resource.Person(AvatarColor.random()),
    modifier = Modifier.size(40.dp)
  )
}

@Composable
private fun TwoUnknownAvatars() {
  Box(modifier = Modifier.width(40.dp)) {
    FallbackAvatarImage(
      fallbackAvatar = FallbackAvatar.Resource.Person(AvatarColor.random()),
      modifier = Modifier
        .size(34.dp)
        .align(Alignment.CenterStart)
    )

    FallbackAvatarImage(
      fallbackAvatar = FallbackAvatar.Resource.Person(AvatarColor.random()),
      modifier = Modifier
        .size(38.dp)
        .align(Alignment.CenterEnd)
        .border(width = 2.dp, color = LocalExtendedColors.current.colorSurface1, shape = CircleShape)
    )
  }
}

@Composable
private fun ThreeUnknownAvatars() {
  Box(modifier = Modifier.width(40.dp)) {
    FallbackAvatarImage(
      fallbackAvatar = FallbackAvatar.Resource.Person(AvatarColor.random()),
      modifier = Modifier
        .size(27.dp)
        .align(Alignment.CenterStart)
    )

    FallbackAvatarImage(
      fallbackAvatar = FallbackAvatar.Resource.Person(AvatarColor.random()),
      modifier = Modifier
        .size(31.dp)
        .align(Alignment.Center)
        .border(width = 2.dp, color = SignalTheme.colors.colorSurface1, shape = CircleShape)
    )

    FallbackAvatarImage(
      fallbackAvatar = FallbackAvatar.Resource.Person(AvatarColor.random()),
      modifier = Modifier
        .size(31.dp)
        .align(Alignment.CenterEnd)
        .border(width = 2.dp, color = SignalTheme.colors.colorSurface1, shape = CircleShape)
    )
  }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UnknownMembersRowPreview() {
  Previews.BottomSheetPreview {
    Column {
      UnknownMembersRow(unknownMemberCount = 1, allCallMembersAreUnknown = true)
      UnknownMembersRow(unknownMemberCount = 1, allCallMembersAreUnknown = false)
      UnknownMembersRow(unknownMemberCount = 2, allCallMembersAreUnknown = false)
      UnknownMembersRow(unknownMemberCount = 3, allCallMembersAreUnknown = false)
    }
  }
}

private data class ParticipantsState(
  val inCallLobby: Boolean = false,
  val ringGroup: Boolean = true,
  val includeSelf: Boolean = false,
  val participantCount: Int = 0,
  val remoteParticipants: List<CallParticipant> = emptyList(),
  val localParticipant: CallParticipant? = null,
  val groupMembers: List<GroupMemberEntry.FullMember> = emptyList(),
  val callRecipient: Recipient = Recipient.UNKNOWN,
  val raisedHands: List<GroupCallRaiseHandEvent> = emptyList()
) {

  val participantsForList: List<CallParticipant> = if (includeSelf && localParticipant != null) {
    listOf(localParticipant) + remoteParticipants
  } else {
    remoteParticipants
  }.filter { it.recipient.isProfileSharing }

  val unknownParticipantCount = remoteParticipants.count { !it.recipient.isProfileSharing }

  val participantCountForDisplay: Int = if (participantCount == 0) {
    participantsForList.size
  } else {
    participantCount
  }

  fun isGroupCall(): Boolean {
    return groupMembers.isNotEmpty()
  }

  fun isOngoing(): Boolean {
    return remoteParticipants.isNotEmpty()
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.individual

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Texts
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.conversation.individual.IndividualSettingsState.Dialog
import org.thoughtcrime.securesms.components.settings.conversation.shared.BlockRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.CallBar
import org.thoughtcrime.securesms.components.settings.conversation.shared.CallBarState
import org.thoughtcrime.securesms.components.settings.conversation.shared.ConversationHeader
import org.thoughtcrime.securesms.components.settings.conversation.shared.ConversationSettingsScaffold
import org.thoughtcrime.securesms.components.settings.conversation.shared.InternalDetailsButton
import org.thoughtcrime.securesms.components.settings.conversation.shared.SoundsAndNotificationsRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.UnmuteDialog
import org.thoughtcrime.securesms.components.settings.conversation.shared.previewRecipient
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Settings for the Signal release notes chat.
 */
@Composable
fun ReleaseNotesSettingsScreen(
  state: IndividualSettingsState,
  onEvent: (IndividualSettingsEvent) -> Unit,
  onNavigationClick: () -> Unit,
  onAvatarViewCreated: (View) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  ConversationSettingsScaffold(
    title = state.recipient.getDisplayName(context),
    recipient = state.recipient,
    onNavigationClick = onNavigationClick,
    modifier = modifier
  ) {
    if (state.recipient == Recipient.UNKNOWN) {
      return@ConversationSettingsScaffold
    }

    item {
      ConversationHeader(
        recipient = state.recipient,
        name = state.recipient.getDisplayName(context),
        showVerifiedBadge = state.recipient.showVerified,
        onAvatarClick = { onEvent(IndividualSettingsEvent.AvatarClicked) },
        onAvatarViewCreated = onAvatarViewCreated
      )
    }

    if (state.displayInternalRecipientDetails) {
      item {
        InternalDetailsButton(onClick = { onEvent(IndividualSettingsEvent.InternalDetailsClicked) })
      }
    }

    item {
      CallBar(
        state = state.callBar,
        enabled = !state.isDeprecatedOrUnregistered,
        isMuteMenuShown = state.dialog == Dialog.MuteMenu,
        onAddToStoryClick = {},
        onMessageClick = {},
        onVideoCallClick = {},
        onAudioCallClick = {},
        onMuteClick = { onEvent(IndividualSettingsEvent.MuteClicked) },
        onMuteDurationSelected = { onEvent(IndividualSettingsEvent.MuteDurationSelected(it)) },
        onMuteUntilCustomTimeClick = { onEvent(IndividualSettingsEvent.MuteUntilCustomTimeClicked) },
        onMuteMenuDismissed = { onEvent(IndividualSettingsEvent.DialogDismissed) },
        onSearchClick = { onEvent(IndividualSettingsEvent.SearchClicked) }
      )
    }

    item { Dividers.Default() }

    item {
      Rows.TextRow(
        text = stringResource(R.string.ReleaseNotes__this_is_official_chat),
        icon = painterResource(R.drawable.symbol_official_20)
      )
    }

    item {
      Rows.TextRow(
        text = stringResource(R.string.ReleaseNotes__keep_up_to_date),
        icon = painterResource(R.drawable.symbol_bell_20)
      )
    }

    item { Dividers.Default() }

    item {
      SoundsAndNotificationsRow(
        enabled = !state.isDeprecatedOrUnregistered,
        onClick = { onEvent(IndividualSettingsEvent.SoundsAndNotificationsClicked) }
      )
    }

    item { Dividers.Default() }

    item { Texts.SectionHeader(text = stringResource(R.string.preferences__help)) }

    item {
      Rows.TextRow(
        text = stringResource(R.string.HelpSettingsFragment__support_center),
        icon = painterResource(R.drawable.symbol_help_24),
        onClick = { onEvent(IndividualSettingsEvent.SupportCenterClicked) }
      )
    }

    item {
      Rows.TextRow(
        text = stringResource(R.string.HelpSettingsFragment__contact_us),
        icon = painterResource(R.drawable.symbol_invite_24),
        onClick = { onEvent(IndividualSettingsEvent.ContactUsClicked) }
      )
    }

    item {
      Rows.TextRow(
        text = stringResource(R.string.preferences__donate_to_signal),
        icon = painterResource(R.drawable.symbol_heart_24),
        onClick = { onEvent(IndividualSettingsEvent.DonateClicked) }
      )
    }

    if (state.canModifyBlockedState) {
      item { Dividers.Default() }

      item {
        BlockRow(
          isBlocked = state.recipient.isBlocked,
          enabled = !state.isDeprecatedOrUnregistered,
          onClick = { onEvent(IndividualSettingsEvent.BlockClicked) }
        )
      }
    }
  }

  ReleaseNotesSettingsDialogs(
    state = state,
    onEvent = onEvent
  )
}

@Composable
private fun ReleaseNotesSettingsDialogs(
  state: IndividualSettingsState,
  onEvent: (IndividualSettingsEvent) -> Unit
) {
  when (state.dialog) {
    Dialog.Unmute -> UnmuteDialog(
      recipient = state.recipient,
      onConfirm = { onEvent(IndividualSettingsEvent.UnmuteConfirmed) },
      onDismiss = { onEvent(IndividualSettingsEvent.DialogDismissed) }
    )

    // The mute menu is a dropdown anchored to the call bar, so CallBar renders it rather than us.
    Dialog.MuteMenu, Dialog.None -> Unit
  }
}

@AllDevicePreviews
@Composable
private fun ReleaseNotesSettingsScreenPreview() {
  Previews.Preview {
    ReleaseNotesSettingsScreen(
      state = IndividualSettingsState(
        recipient = previewRecipient(1L, profileName = ProfileName.fromParts("Signal", null), isReleaseNotes = true),
        threadId = 1L,
        canModifyBlockedState = true,
        callBar = CallBarState(isMuteAvailable = true, isSearchAvailable = true)
      ),
      onEvent = {},
      onNavigationClick = {},
      onAvatarViewCreated = {}
    )
  }
}

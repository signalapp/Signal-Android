/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.individual

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.conversation.shared.CallBar
import org.thoughtcrime.securesms.components.settings.conversation.shared.CallBarState
import org.thoughtcrime.securesms.components.settings.conversation.shared.ChatColorAndWallpaperRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.ConversationHeader
import org.thoughtcrime.securesms.components.settings.conversation.shared.ConversationSettingsScaffold
import org.thoughtcrime.securesms.components.settings.conversation.shared.DisappearingMessagesRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.InternalDetailsButton
import org.thoughtcrime.securesms.components.settings.conversation.shared.StarredMessagesRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.previewRecipient
import org.thoughtcrime.securesms.components.settings.conversation.shared.sharedMediaSection
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Settings for the note to self chat.
 */
@Composable
fun NoteToSelfSettingsScreen(
  state: IndividualSettingsState,
  onEvent: (IndividualSettingsEvent) -> Unit,
  onNavigationClick: () -> Unit,
  onAvatarViewCreated: (View) -> Unit,
  modifier: Modifier = Modifier
) {
  ConversationSettingsScaffold(
    title = stringResource(R.string.note_to_self),
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
        name = stringResource(R.string.note_to_self),
        storyViewState = state.storyViewState,
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
        onAddToStoryClick = {},
        onMessageClick = {},
        onVideoCallClick = {},
        onAudioCallClick = {},
        onMuteClick = {},
        onMuteDurationSelected = {},
        onMuteUntilCustomTimeClick = {},
        onMuteMenuDismissed = {},
        onSearchClick = { onEvent(IndividualSettingsEvent.SearchClicked) }
      )
    }

    item { Dividers.Default() }

    item {
      DisappearingMessagesRow(
        lifespanSeconds = state.disappearingMessagesLifespan,
        enabled = !state.isDeprecatedOrUnregistered,
        onClick = { onEvent(IndividualSettingsEvent.DisappearingMessagesClicked) }
      )
    }

    item {
      ChatColorAndWallpaperRow(onClick = { onEvent(IndividualSettingsEvent.ChatColorAndWallpaperClicked) })
    }

    if (state.starredMessagesEnabled) {
      item {
        StarredMessagesRow(onClick = { onEvent(IndividualSettingsEvent.StarredMessagesClicked) })
      }
    }

    sharedMediaSection(
      state = state.mediaRail,
      onEvent = { onEvent(IndividualSettingsEvent.MediaRailEvent(it)) }
    )
  }
}

@AllDevicePreviews
@Composable
private fun NoteToSelfSettingsScreenPreview() {
  Previews.Preview {
    NoteToSelfSettingsScreen(
      state = IndividualSettingsState(
        recipient = previewRecipient(1L, isSelf = true),
        threadId = 1L,
        starredMessagesEnabled = true,
        callBar = CallBarState(isSearchAvailable = true)
      ),
      onEvent = {},
      onNavigationClick = {},
      onAvatarViewCreated = {}
    )
  }
}

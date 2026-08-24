/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.individual

import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Texts
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.conversation.individual.IndividualSettingsState.Dialog
import org.thoughtcrime.securesms.components.settings.conversation.shared.BlockRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.CallBar
import org.thoughtcrime.securesms.components.settings.conversation.shared.CallBarState
import org.thoughtcrime.securesms.components.settings.conversation.shared.ChatColorAndWallpaperRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.ConversationHeader
import org.thoughtcrime.securesms.components.settings.conversation.shared.ConversationSettingsScaffold
import org.thoughtcrime.securesms.components.settings.conversation.shared.DisappearingMessagesRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.InternalDetailsButton
import org.thoughtcrime.securesms.components.settings.conversation.shared.LargeIconRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.RecipientRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.ReportSpamRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.SoundsAndNotificationsRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.StarredMessagesRow
import org.thoughtcrime.securesms.components.settings.conversation.shared.UnmuteDialog
import org.thoughtcrime.securesms.components.settings.conversation.shared.callLogSection
import org.thoughtcrime.securesms.components.settings.conversation.shared.previewRecipient
import org.thoughtcrime.securesms.components.settings.conversation.shared.sharedMediaSection
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.signal.core.ui.R as CoreUiR

private val BADGE_SIZE = 64.dp

/**
 * Settings for a 1:1 conversation with another person.
 */
@Composable
fun IndividualSettingsScreen(
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
        storyViewState = state.storyViewState,
        subhead = state.recipient.combinedAboutAndEmoji,
        showVerifiedBadge = state.recipient.showVerified,
        showSystemContactBadge = state.recipient.isSystemContact,
        badges = state.recipient.badges,
        onAvatarClick = { onEvent(IndividualSettingsEvent.AvatarClicked) },
        onBadgeClick = { onEvent(IndividualSettingsEvent.BadgeClicked(it)) },
        onNameClick = { onEvent(IndividualSettingsEvent.HeadlineClicked) },
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
        onMessageClick = { onEvent(IndividualSettingsEvent.MessageClicked) },
        onVideoCallClick = { onEvent(IndividualSettingsEvent.VideoCallClicked) },
        onAudioCallClick = { onEvent(IndividualSettingsEvent.AudioCallClicked) },
        onMuteClick = { onEvent(IndividualSettingsEvent.MuteClicked) },
        onMuteDurationSelected = { onEvent(IndividualSettingsEvent.MuteDurationSelected(it)) },
        onMuteUntilCustomTimeClick = { onEvent(IndividualSettingsEvent.MuteUntilCustomTimeClicked) },
        onMuteMenuDismissed = { onEvent(IndividualSettingsEvent.DialogDismissed) },
        onSearchClick = { onEvent(IndividualSettingsEvent.SearchClicked) }
      )
    }

    item { Dividers.Default() }

    callLogSection(state.calls)

    if (!state.recipient.isBlocked) {
      item {
        DisappearingMessagesRow(
          lifespanSeconds = state.disappearingMessagesLifespan,
          enabled = !state.isDeprecatedOrUnregistered,
          onClick = { onEvent(IndividualSettingsEvent.DisappearingMessagesClicked) }
        )
      }
    }

    item {
      Rows.TextRow(
        text = stringResource(R.string.NicknameActivity__nickname),
        icon = painterResource(CoreUiR.drawable.symbol_edit_24),
        onClick = { onEvent(IndividualSettingsEvent.NicknameClicked) }
      )
    }

    item {
      ChatColorAndWallpaperRow(onClick = { onEvent(IndividualSettingsEvent.ChatColorAndWallpaperClicked) })
    }

    item {
      SoundsAndNotificationsRow(
        enabled = !state.isDeprecatedOrUnregistered,
        onClick = { onEvent(IndividualSettingsEvent.SoundsAndNotificationsClicked) }
      )
    }

    if (state.starredMessagesEnabled) {
      item {
        StarredMessagesRow(onClick = { onEvent(IndividualSettingsEvent.StarredMessagesClicked) })
      }
    }

    when (state.contactLinkState) {
      ContactLinkState.OPEN -> item {
        Rows.TextRow(
          text = stringResource(R.string.ConversationSettingsFragment__contact_details),
          icon = painterResource(R.drawable.ic_profile_circle_24),
          onClick = { onEvent(IndividualSettingsEvent.ContactDetailsClicked) }
        )
      }

      ContactLinkState.ADD -> item {
        Rows.TextRow(
          text = stringResource(R.string.ConversationSettingsFragment__add_as_a_contact),
          icon = painterResource(R.drawable.ic_plus_24),
          onClick = { onEvent(IndividualSettingsEvent.AddAsContactClicked) }
        )
      }

      ContactLinkState.NONE -> Unit
    }

    item {
      Rows.TextRow(
        text = stringResource(R.string.ConversationSettingsFragment__view_safety_number),
        icon = painterResource(R.drawable.symbol_safety_number_24),
        enabled = !state.isDeprecatedOrUnregistered,
        onClick = { onEvent(IndividualSettingsEvent.ViewSafetyNumberClicked) }
      )
    }

    sharedMediaSection(
      state = state.mediaRail,
      onEvent = { onEvent(IndividualSettingsEvent.MediaRailEvent(it)) }
    )

    badgeSection(state, onEvent)
    groupsInCommonSection(state, onEvent)

    if (state.canModifyBlockedState) {
      item { Dividers.Default() }

      item {
        BlockRow(
          isBlocked = state.recipient.isBlocked,
          enabled = !state.isDeprecatedOrUnregistered,
          onClick = { onEvent(IndividualSettingsEvent.BlockClicked) }
        )
      }

      item {
        ReportSpamRow(
          enabled = !state.isDeprecatedOrUnregistered,
          onClick = { onEvent(IndividualSettingsEvent.ReportSpamClicked) }
        )
      }
    }
  }

  IndividualSettingsDialogs(
    state = state,
    onEvent = onEvent
  )
}

/** The recipient's badges, and a nudge toward getting your own. */
private fun LazyListScope.badgeSection(
  state: IndividualSettingsState,
  onEvent: (IndividualSettingsEvent) -> Unit
) {
  if (state.recipient.badges.isEmpty()) {
    return
  }

  item { Dividers.Default() }

  item { Texts.SectionHeader(text = stringResource(R.string.ManageProfileFragment_badges)) }

  item {
    BadgeRow(
      badges = state.recipient.badges,
      onBadgeClick = { onEvent(IndividualSettingsEvent.BadgeClicked(it)) }
    )
  }

  item {
    Text(
      text = stringResource(R.string.ConversationSettingsFragment__get_badges),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
  }
}

/** Groups the two of you are both in. */
private fun LazyListScope.groupsInCommonSection(
  state: IndividualSettingsState,
  onEvent: (IndividualSettingsEvent) -> Unit
) {
  if (!state.selfHasGroups) {
    return
  }

  item { Dividers.Default() }

  item {
    val count = state.allGroupsInCommon.size
    Texts.SectionHeader(
      text = if (count == 0) {
        stringResource(R.string.ManageRecipientActivity_no_groups_in_common)
      } else {
        pluralStringResource(R.plurals.ManageRecipientActivity_d_groups_in_common, count, count)
      }
    )
  }

  if (!state.recipient.isBlocked) {
    item {
      LargeIconRow(
        text = stringResource(R.string.ConversationSettingsFragment__add_to_a_group),
        icon = R.drawable.ic_plus_24,
        enabled = !state.isDeprecatedOrUnregistered,
        onClick = { onEvent(IndividualSettingsEvent.AddToAGroupClicked) }
      )
    }
  }

  items(
    items = state.groupsInCommon,
    key = { it.id.toLong() }
  ) { group ->
    RecipientRow(
      recipient = group,
      onClick = { onEvent(IndividualSettingsEvent.GroupInCommonClicked(group.id)) }
    )
  }

  if (state.canShowMoreGroupsInCommon) {
    item {
      LargeIconRow(
        text = stringResource(R.string.ConversationSettingsFragment__see_all),
        icon = R.drawable.ic_chevron_down_icon_20,
        onClick = { onEvent(IndividualSettingsEvent.RevealAllGroupsInCommonClicked) }
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgeRow(
  badges: List<Badge>,
  onBadgeClick: (Badge) -> Unit,
  modifier: Modifier = Modifier
) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 8.dp)
  ) {
    badges.forEach { badge ->
      AndroidView(
        factory = { context -> BadgeImageView(context, null) },
        modifier = Modifier.size(BADGE_SIZE)
      ) { badgeView ->
        badgeView.setBadge(badge)
        badgeView.setOnClickListener { onBadgeClick(badge) }
      }
    }
  }
}

@Composable
private fun IndividualSettingsDialogs(
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
private fun IndividualSettingsScreenPreview() {
  Previews.Preview {
    IndividualSettingsScreen(
      state = IndividualSettingsState(
        recipient = previewRecipient(1L, profileName = ProfileName.fromParts("Miles", "Morales"), about = "Just hanging around"),
        threadId = 1L,
        canModifyBlockedState = true,
        starredMessagesEnabled = true,
        selfHasGroups = true,
        allGroupsInCommon = listOf(previewRecipient(2L, groupName = "Spider Society")),
        callBar = CallBarState(
          isVideoAvailable = true,
          isAudioAvailable = true,
          isAudioSecure = true,
          isMuteAvailable = true,
          isSearchAvailable = true
        )
      ),
      onEvent = {},
      onNavigationClick = {},
      onAvatarViewCreated = {}
    )
  }
}

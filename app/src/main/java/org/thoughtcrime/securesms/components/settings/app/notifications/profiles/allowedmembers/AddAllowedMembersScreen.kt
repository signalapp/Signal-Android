/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.allowedmembers

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.NotificationProfileAddMembers
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.NotificationProfileRecipient
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.recipients.Recipient
import java.util.UUID

@VisibleForTesting
object AddAllowedMembersTestTags {
  const val LIST = "list"
  const val ROW_ADD_MEMBERS = "row-add-members"
  const val ROW_ALLOW_ALL_CALLS = "row-allow-all-calls"
  const val ROW_NOTIFY_FOR_ALL_MENTIONS = "row-notify-for-all-mentions"
  const val BUTTON_NEXT = "button-next"
  const val MEMBER_PREFIX = "member-"

  fun member(recipientId: RecipientId): String = MEMBER_PREFIX + recipientId.toLong()
}

/**
 * Shows and allows adding the people and groups a profile lets through, during the create flow.
 */
@Composable
fun AddAllowedMembersScreen(
  state: AddAllowedMembersState,
  onEvent: (AddAllowedMembersEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = "",
    onNavigationClick = { onEvent(AddAllowedMembersEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    snackbarHost = { RemovedMemberSnackbar(snackbar = state.snackbar, onEvent = onEvent) },
    modifier = modifier
  ) { contentPadding ->
    val profile = state.profile ?: return@Settings

    Column(
      modifier = Modifier.padding(contentPadding)
    ) {
      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .testTag(AddAllowedMembersTestTags.LIST)
      ) {
        item {
          Text(
            text = stringResource(R.string.AddAllowedMembers__allowed_notifications),
            style = MaterialTheme.typography.headlineMedium.copy(hyphens = Hyphens.Auto),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
              .horizontalGutters()
              .padding(top = 20.dp)
              .fillMaxWidth()
          )

          Text(
            text = stringResource(R.string.AddAllowedMembers__add_people_and_groups_you_want_notifications_and_calls_from_when_this_profile_is_on),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .horizontalGutters()
              .padding(top = 12.dp, bottom = 24.dp)
              .fillMaxWidth()
          )
        }

        item {
          Texts.SectionHeader(
            text = stringResource(R.string.AddAllowedMembers__allowed_notifications)
          )
        }

        item {
          NotificationProfileAddMembers(
            onClick = { onEvent(AddAllowedMembersEvent.AddMembersClicked) },
            modifier = Modifier.testTag(AddAllowedMembersTestTags.ROW_ADD_MEMBERS)
          )
        }

        items(state.members, key = { it.id.toLong() }) { member ->
          NotificationProfileRecipient(
            recipient = member,
            onRemoveClick = { onEvent(AddAllowedMembersEvent.RemoveMemberClicked(it)) },
            modifier = Modifier.testTag(AddAllowedMembersTestTags.member(member.id))
          )
        }

        item {
          Texts.SectionHeader(
            text = stringResource(R.string.AddAllowedMembers__exceptions)
          )
        }

        item {
          Rows.ToggleRow(
            checked = profile.allowAllCalls,
            text = stringResource(R.string.AddAllowedMembers__allow_all_calls),
            icon = SignalIcons.Phone.imageVector,
            onCheckChanged = { onEvent(AddAllowedMembersEvent.AllowAllCallsToggled(it)) },
            modifier = Modifier.testTag(AddAllowedMembersTestTags.ROW_ALLOW_ALL_CALLS)
          )
        }

        item {
          Rows.ToggleRow(
            checked = profile.allowAllMentions,
            text = stringResource(R.string.AddAllowedMembers__notify_for_all_mentions),
            icon = SignalIcons.At.imageVector,
            onCheckChanged = { onEvent(AddAllowedMembersEvent.NotifyForAllMentionsToggled(it)) },
            modifier = Modifier.testTag(AddAllowedMembersTestTags.ROW_NOTIFY_FOR_ALL_MENTIONS)
          )
        }
      }

      Buttons.LargeTonal(
        onClick = { onEvent(AddAllowedMembersEvent.NextClicked) },
        modifier = Modifier
          .align(Alignment.End)
          .padding(16.dp)
          .testTag(AddAllowedMembersTestTags.BUTTON_NEXT)
      ) {
        Text(text = stringResource(R.string.EditNotificationProfileFragment__next))
      }
    }
  }
}

@Composable
private fun RemovedMemberSnackbar(
  snackbar: AddAllowedMembersState.Snackbar,
  onEvent: (AddAllowedMembersEvent) -> Unit
) {
  val removed = (snackbar as? AddAllowedMembersState.Snackbar.MemberRemoved)?.recipient
  val message = removed?.let { stringResource(R.string.NotificationProfileDetails__s_removed, it.getDisplayName(LocalContext.current)) }

  Snackbars.Host(
    message = message,
    onDismissed = { onEvent(AddAllowedMembersEvent.SnackbarDismissed) },
    duration = Snackbars.Duration.LONG,
    actionLabel = stringResource(R.string.NotificationProfileDetails__undo),
    onActionPerformed = { removed?.let { onEvent(AddAllowedMembersEvent.UndoRemoveMemberClicked(it.id)) } }
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun AddAllowedMembersScreenPreview() {
  AddAllowedMembersScreen(
    state = AddAllowedMembersState(
      profile = NotificationProfile(
        id = 0L,
        name = "Test Profile",
        emoji = "",
        createdAt = 0L,
        schedule = NotificationProfileSchedule(id = 0L),
        notificationProfileId = NotificationProfileId(UUID(0L, 0L))
      ),
      members = (1..3).map {
        Recipient(
          id = RecipientId.from(it.toLong()),
          isResolving = false,
          registeredValue = RecipientTable.RegisteredState.REGISTERED,
          systemContactName = "Test User $it"
        )
      }
    ),
    onEvent = {}
  )
}

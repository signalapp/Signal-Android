/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.details

import android.text.format.DateFormat
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.Texts
import org.signal.core.util.orderOfDaysInWeek
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.NotificationProfileAddMembers
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.NotificationProfileCircleIconRow
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.NotificationProfileRecipient
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.models.NotificationProfileRow
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.formatHours
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.UUID

@VisibleForTesting
object NotificationProfileDetailsTestTags {
  const val LIST = "list"
  const val ROW_PROFILE = "row-profile"
  const val ROW_ADD_MEMBERS = "row-add-members"
  const val ROW_SEE_ALL = "row-see-all"
  const val ROW_SCHEDULE = "row-schedule"
  const val ROW_ALLOW_ALL_CALLS = "row-allow-all-calls"
  const val ROW_NOTIFY_FOR_ALL_MENTIONS = "row-notify-for-all-mentions"
  const val ROW_DELETE = "row-delete"
  const val BUTTON_EDIT = "button-edit"
  const val MEMBER_PREFIX = "member-"

  fun member(recipientId: RecipientId): String = MEMBER_PREFIX + recipientId.toLong()
}

/**
 * Shows a notification profile and lets the user change who it allows, its schedule and exceptions, or delete it.
 */
@Composable
fun NotificationProfileDetailsScreen(
  state: NotificationProfileDetailsState,
  onEvent: (NotificationProfileDetailsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = state.profile?.name.orEmpty(),
    onNavigationClick = { onEvent(NotificationProfileDetailsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    snackbarHost = { RemovedMemberSnackbar(snackbar = state.snackbar, onEvent = onEvent) },
    actions = {
      if (state.profile != null) {
        IconButton(
          onClick = { onEvent(NotificationProfileDetailsEvent.EditClicked) },
          modifier = Modifier.testTag(NotificationProfileDetailsTestTags.BUTTON_EDIT)
        ) {
          Icon(
            imageVector = SignalIcons.Edit.imageVector,
            contentDescription = stringResource(R.string.NotificationProfileDetails__edit_notification_profile)
          )
        }
      }
    },
    modifier = modifier
  ) { contentPadding ->
    val profile = state.profile ?: return@Settings

    LazyColumn(
      modifier = Modifier
        .padding(contentPadding)
        .testTag(NotificationProfileDetailsTestTags.LIST)
    ) {
      item {
        NotificationProfileRow(
          profile = profile,
          activeStatus = state.activeStatus,
          showSwitch = true,
          onClick = { onEvent(NotificationProfileDetailsEvent.ProfileToggled) },
          modifier = Modifier.testTag(NotificationProfileDetailsTestTags.ROW_PROFILE)
        )
      }

      item { Dividers.Default() }

      item {
        Texts.SectionHeader(text = stringResource(R.string.AddAllowedMembers__allowed_notifications))
      }

      item {
        NotificationProfileAddMembers(
          onClick = { onEvent(NotificationProfileDetailsEvent.AddMembersClicked) },
          modifier = Modifier.testTag(NotificationProfileDetailsTestTags.ROW_ADD_MEMBERS)
        )
      }

      items(state.visibleMembers, key = { it.id.toLong() }) { member ->
        NotificationProfileRecipient(
          recipient = member,
          onRemoveClick = { onEvent(NotificationProfileDetailsEvent.RemoveMemberClicked(it)) },
          modifier = Modifier
            .animateItem()
            .testTag(NotificationProfileDetailsTestTags.member(member.id))
        )
      }

      if (state.showSeeAll) {
        item(key = "see-all") {
          NotificationProfileCircleIconRow(
            icon = ImageVector.vectorResource(R.drawable.symbol_chevron_down_24),
            text = stringResource(R.string.NotificationProfileDetails__see_all),
            onClick = { onEvent(NotificationProfileDetailsEvent.SeeAllMembersClicked) },
            modifier = Modifier
              .animateItem()
              .testTag(NotificationProfileDetailsTestTags.ROW_SEE_ALL)
          )
        }
      }

      item(key = "divider-schedule") { Dividers.Default(modifier = Modifier.animateItem()) }

      item(key = "header-schedule") {
        Texts.SectionHeader(text = stringResource(R.string.NotificationProfileDetails__schedule), modifier = Modifier.animateItem())
      }

      item(key = "schedule") {
        Rows.TextRow(
          text = describeSchedule(profile.schedule),
          label = stringResource(if (profile.schedule.enabled) R.string.NotificationProfileDetails__on else R.string.NotificationProfileDetails__off),
          icon = SignalIcons.Recent.imageVector,
          onClick = { onEvent(NotificationProfileDetailsEvent.ScheduleClicked) },
          modifier = Modifier
            .animateItem()
            .testTag(NotificationProfileDetailsTestTags.ROW_SCHEDULE)
        )
      }

      item(key = "divider-exceptions") { Dividers.Default(modifier = Modifier.animateItem()) }

      item(key = "header-exceptions") {
        Texts.SectionHeader(text = stringResource(R.string.NotificationProfileDetails__exceptions), modifier = Modifier.animateItem())
      }

      item(key = "allow-all-calls") {
        Rows.ToggleRow(
          checked = profile.allowAllCalls,
          text = stringResource(R.string.NotificationProfileDetails__allow_all_calls),
          icon = SignalIcons.Phone.imageVector,
          onCheckChanged = { onEvent(NotificationProfileDetailsEvent.AllowAllCallsToggled(it)) },
          modifier = Modifier
            .animateItem()
            .testTag(NotificationProfileDetailsTestTags.ROW_ALLOW_ALL_CALLS)
        )
      }

      item(key = "notify-for-all-mentions") {
        Rows.ToggleRow(
          checked = profile.allowAllMentions,
          text = stringResource(R.string.NotificationProfileDetails__notify_for_all_mentions),
          icon = SignalIcons.At.imageVector,
          onCheckChanged = { onEvent(NotificationProfileDetailsEvent.NotifyForAllMentionsToggled(it)) },
          modifier = Modifier
            .animateItem()
            .testTag(NotificationProfileDetailsTestTags.ROW_NOTIFY_FOR_ALL_MENTIONS)
        )
      }

      item(key = "divider-delete") { Dividers.Default(modifier = Modifier.animateItem()) }

      item(key = "delete") {
        Rows.TextRow(
          text = stringResource(R.string.NotificationProfileDetails__delete_profile),
          icon = SignalIcons.Trash.imageVector,
          foregroundTint = MaterialTheme.colorScheme.error,
          onClick = { onEvent(NotificationProfileDetailsEvent.DeleteClicked) },
          modifier = Modifier
            .animateItem()
            .testTag(NotificationProfileDetailsTestTags.ROW_DELETE)
        )
      }
    }
  }

  when (state.dialog) {
    NotificationProfileDetailsState.Dialog.None -> Unit
    NotificationProfileDetailsState.Dialog.ConfirmDelete -> {
      Dialogs.SimpleAlertDialog(
        title = "",
        body = stringResource(R.string.NotificationProfileDetails__permanently_delete_profile),
        confirm = stringResource(R.string.NotificationProfileDetails__delete),
        dismiss = stringResource(android.R.string.cancel),
        confirmColor = MaterialTheme.colorScheme.error,
        onConfirm = { onEvent(NotificationProfileDetailsEvent.DeleteConfirmed) },
        onDeny = { onEvent(NotificationProfileDetailsEvent.DialogDismissed) },
        onDismissRequest = { onEvent(NotificationProfileDetailsEvent.DialogDismissed) }
      )
    }
  }
}

@Composable
private fun describeSchedule(schedule: NotificationProfileSchedule): String {
  if (!schedule.enabled) {
    return stringResource(R.string.NotificationProfileDetails__schedule)
  }

  val locale = LocalLocale.current.platformLocale
  val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
  val hours = stringResource(
    R.string.NotificationProfileDetails__s_to_s,
    schedule.startTime().formatHours(is24Hour, locale),
    schedule.endTime().formatHours(is24Hour, locale)
  )

  val days = if (schedule.daysEnabled.size == DayOfWeek.entries.size) {
    stringResource(R.string.NotificationProfileDetails__everyday)
  } else {
    locale.orderOfDaysInWeek()
      .filter { it in schedule.daysEnabled }
      .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
  }

  return if (days.isNotEmpty()) "$hours\n$days" else hours
}

@Composable
private fun RemovedMemberSnackbar(
  snackbar: NotificationProfileDetailsState.Snackbar,
  onEvent: (NotificationProfileDetailsEvent) -> Unit
) {
  val removed = (snackbar as? NotificationProfileDetailsState.Snackbar.MemberRemoved)?.recipient
  val message = removed?.let { stringResource(R.string.NotificationProfileDetails__s_removed, it.getDisplayName(LocalContext.current)) }

  Snackbars.Host(
    message = message,
    onDismissed = { onEvent(NotificationProfileDetailsEvent.SnackbarDismissed) },
    duration = Snackbars.Duration.LONG,
    actionLabel = stringResource(R.string.NotificationProfileDetails__undo),
    onActionPerformed = { removed?.let { onEvent(NotificationProfileDetailsEvent.UndoRemoveMemberClicked(it.id)) } }
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun NotificationProfileDetailsScreenPreview() {
  NotificationProfileDetailsScreen(
    state = previewState(),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun NotificationProfileDetailsScreenConfirmDeletePreview() {
  NotificationProfileDetailsScreen(
    state = previewState().copy(dialog = NotificationProfileDetailsState.Dialog.ConfirmDelete),
    onEvent = {}
  )
}

private fun previewState(): NotificationProfileDetailsState {
  return NotificationProfileDetailsState(
    profile = NotificationProfile(
      id = 1L,
      name = "Work",
      emoji = "",
      createdAt = 0L,
      schedule = NotificationProfileSchedule(id = 1L, enabled = true, start = 900, end = 1700, daysEnabled = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)),
      allowAllCalls = true,
      notificationProfileId = NotificationProfileId(UUID(0L, 1L))
    ),
    members = (1..7).map {
      Recipient(
        id = RecipientId.from(it.toLong()),
        isResolving = false,
        registeredValue = RecipientTable.RegisteredState.REGISTERED,
        systemContactName = "Test User $it"
      )
    },
    activeStatus = NotificationProfiles.ActiveStatus.OnUntil(LocalTime.of(17, 0))
  )
}

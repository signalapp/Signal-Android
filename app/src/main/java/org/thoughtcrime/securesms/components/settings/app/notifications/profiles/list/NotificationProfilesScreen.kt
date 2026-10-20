/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.list

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.compose.horizontalGutters
import org.signal.emoji.EmojiStrings
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.models.NotificationProfileRow
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import java.time.LocalTime
import java.util.UUID
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object NotificationProfilesTestTags {
  const val LIST = "list"
  const val ROW_NEW_PROFILE = "row-new-profile"
  const val BUTTON_CREATE_PROFILE = "button-create-profile"
  const val PROFILE_PREFIX = "profile-"

  fun profile(profileId: Long): String = PROFILE_PREFIX + profileId
}

/**
 * Primary entry point for Notification Profiles. When the user has no profiles, shows an empty state, otherwise shows
 * all current profiles.
 */
@Composable
fun NotificationProfilesScreen(
  state: NotificationProfilesState,
  onEvent: (NotificationProfilesEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val profiles = state.profiles

  Scaffolds.Settings(
    title = if (profiles.isNullOrEmpty()) "" else stringResource(R.string.NotificationsSettingsFragment__notification_profiles),
    onNavigationClick = { onEvent(NotificationProfilesEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(CoreUiR.string.CloseButton__content_description),
    modifier = modifier
  ) { paddingValues ->
    when {
      profiles == null -> Unit
      profiles.isEmpty() -> NoNotificationProfilesEmpty(onEvent = onEvent, modifier = Modifier.padding(paddingValues))
      else -> ProfileList(profiles = profiles, activeProfileId = state.activeProfileId, activeStatus = state.activeStatus, onEvent = onEvent, modifier = Modifier.padding(paddingValues))
    }
  }
}

@Composable
private fun ProfileList(
  profiles: List<NotificationProfile>,
  activeProfileId: Long?,
  activeStatus: NotificationProfiles.ActiveStatus?,
  onEvent: (NotificationProfilesEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  LazyColumn(
    modifier = modifier.testTag(NotificationProfilesTestTags.LIST)
  ) {
    item {
      Texts.SectionHeader(
        text = stringResource(R.string.NotificationProfilesFragment__profiles)
      )
    }

    item {
      Rows.TextRow(
        text = {
          Text(text = stringResource(R.string.NotificationProfilesFragment__new_profile))
        },
        icon = {
          Icon(
            imageVector = SignalIcons.Plus.imageVector,
            contentDescription = null,
            modifier = Modifier
              .size(40.dp)
              .background(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
              .padding(8.dp)
          )
        },
        onClick = { onEvent(NotificationProfilesEvent.NewProfileClicked) },
        modifier = Modifier.testTag(NotificationProfilesTestTags.ROW_NEW_PROFILE)
      )
    }

    items(profiles, key = { it.id }) { profile ->
      NotificationProfileRow(
        profile = profile,
        activeStatus = if (profile.id == activeProfileId) activeStatus else null,
        onClick = { onEvent(NotificationProfilesEvent.ProfileClicked(it)) },
        modifier = Modifier.testTag(NotificationProfilesTestTags.profile(profile.id))
      )
    }
  }
}

@Composable
private fun NoNotificationProfilesEmpty(
  onEvent: (NotificationProfilesEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .horizontalGutters(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
    ) {
      Spacer(modifier = Modifier.height(96.dp))

      Box(
        modifier = Modifier
          .size(88.dp)
          .background(
            color = Color(AvatarColor.A100.colorInt()),
            shape = CircleShape
          )
          .padding(20.dp),
        contentAlignment = Alignment.Center
      ) {
        Image(
          painter = painterResource(R.drawable.ic_sleeping_face),
          contentDescription = null,
          modifier = Modifier.fillMaxSize()
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text = stringResource(R.string.NotificationProfilesFragment__notification_profiles),
        style = MaterialTheme.typography.headlineMedium.copy(hyphens = Hyphens.Auto),
        textAlign = TextAlign.Center,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp)
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = stringResource(R.string.NotificationProfilesFragment__create_a_profile_to_receive_notifications_and_calls_only_from_the_people_and_groups_you_want_to_hear_from),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp)
      )
    }

    Button(
      onClick = { onEvent(NotificationProfilesEvent.NewProfileClicked) },
      modifier = Modifier
        .fillMaxWidth()
        .testTag(NotificationProfilesTestTags.BUTTON_CREATE_PROFILE)
    ) {
      Text(text = stringResource(R.string.NotificationProfilesFragment__create_profile))
    }
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun NotificationProfilesScreenPreview() {
  NotificationProfilesScreen(
    state = NotificationProfilesState(
      profiles = listOf(
        NotificationProfile(
          id = 1L,
          name = "Test Profile",
          emoji = EmojiStrings.AUDIO,
          createdAt = 0L,
          schedule = NotificationProfileSchedule(id = 1),
          notificationProfileId = NotificationProfileId(UUID(0L, 1L))
        )
      ),
      activeProfileId = 1L,
      activeStatus = NotificationProfiles.ActiveStatus.OnUntil(LocalTime.of(17, 0))
    ),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun NotificationProfilesScreenEmptyPreview() {
  NotificationProfilesScreen(state = NotificationProfilesState(profiles = emptyList()), onEvent = {})
}

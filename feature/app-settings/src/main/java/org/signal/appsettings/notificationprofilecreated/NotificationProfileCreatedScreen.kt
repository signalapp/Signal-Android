/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.notificationprofilecreated

import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.appsettings.R
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.theme.SignalTheme

@VisibleForTesting
object NotificationProfileCreatedTestTags {
  const val SCROLLER = "scroller"
  const val BUTTON_DONE = "button-done"
}

/**
 * Shown at the end of the notification profile creation flow, explaining how the new profile gets turned on and off.
 */
@Composable
fun NotificationProfileCreatedScreen(
  state: NotificationProfileCreatedState,
  onEvent: (NotificationProfileCreatedEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffold(
    bottomBar = {
      Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(24.dp)
      ) {
        Buttons.LargeTonal(
          onClick = { onEvent(NotificationProfileCreatedEvent.DoneClicked) },
          modifier = Modifier.testTag(NotificationProfileCreatedTestTags.BUTTON_DONE)
        ) {
          Text(text = stringResource(R.string.NotificationProfileCreated__done))
        }
      }
    },
    modifier = modifier
  ) { contentPadding ->
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .padding(contentPadding)
        .verticalScroll(rememberScrollState())
        .testTag(NotificationProfileCreatedTestTags.SCROLLER)
    ) {
      Spacer(modifier = Modifier.height(176.dp))

      Image(
        painter = painterResource(R.drawable.ic_notification_profile_done),
        contentDescription = null,
        modifier = Modifier.size(88.dp)
      )

      Text(
        text = stringResource(R.string.NotificationProfileCreated__profile_created),
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        textAlign = TextAlign.Center,
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 24.dp, top = 20.dp, end = 24.dp)
      )

      when (state.hasSchedule) {
        null -> Unit
        true -> {
          Tip(icon = SignalIcons.Recent, text = R.string.NotificationProfileCreated__your_profile_will_turn_on_and_off_automatically_according_to_your_schedule)
          Tip(icon = SignalIcons.MoreVertical, text = R.string.NotificationProfileCreated__you_can_turn_your_profile_on_or_off_manually_via_the_menu_on_the_chat_list)
        }
        false -> {
          Tip(icon = SignalIcons.MoreVertical, text = R.string.NotificationProfileCreated__you_can_turn_your_profile_on_or_off_manually_via_the_menu_on_the_chat_list)
          Tip(icon = SignalIcons.Recent, text = R.string.NotificationProfileCreated__add_a_schedule_in_settings_to_automate_your_profile)
        }
      }
    }
  }
}

@Composable
private fun Tip(icon: SignalIcons, @StringRes text: Int) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 32.dp, top = 24.dp, end = 32.dp)
  ) {
    Icon(
      imageVector = icon.imageVector,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .size(36.dp)
        .background(color = SignalTheme.colors.colorSurface1, shape = CircleShape)
        .padding(8.dp)
    )

    Text(
      text = stringResource(text),
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(start = 12.dp)
    )
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun NotificationProfileCreatedScreenWithSchedulePreview() {
  NotificationProfileCreatedScreen(state = NotificationProfileCreatedState(hasSchedule = true), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun NotificationProfileCreatedScreenWithoutSchedulePreview() {
  NotificationProfileCreatedScreen(state = NotificationProfileCreatedState(hasSchedule = false), onEvent = {})
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.shared

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.thoughtcrime.securesms.R
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Which buttons the call bar under the header should offer. Not every conversation type can do every one of these --
 * note to self, for instance, only ever gets search.
 */
data class CallBarState(
  val isMessageAvailable: Boolean = false,
  val isVideoAvailable: Boolean = false,
  val isAudioAvailable: Boolean = false,
  val isAudioSecure: Boolean = false,
  val isMuteAvailable: Boolean = false,
  val isMuted: Boolean = false,
  val isSearchAvailable: Boolean = false,
  val isAddToStoryAvailable: Boolean = false
)

/**
 * The strip of story/message/call/mute/search buttons that sits under the header.
 *
 * Callbacks rather than a shared event type, so that each screen can route them into its own events.
 */
@Composable
fun CallBar(
  state: CallBarState,
  onAddToStoryClick: () -> Unit,
  onMessageClick: () -> Unit,
  onVideoCallClick: () -> Unit,
  onAudioCallClick: () -> Unit,
  onMuteClick: () -> Unit,
  onMuteDurationSelected: (Long) -> Unit,
  onMuteUntilCustomTimeClick: () -> Unit,
  onMuteMenuDismissed: () -> Unit,
  onSearchClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  isMuteMenuShown: Boolean = false
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp)
      .padding(top = 24.dp, bottom = 16.dp)
  ) {
    if (state.isAddToStoryAvailable) {
      Buttons.ActionButton(
        onClick = onAddToStoryClick,
        imageVector = SignalIcons.AddToStory.imageVector,
        label = stringResource(R.string.ConversationSettingsFragment__story),
        enabled = enabled
      )
    }

    if (state.isMessageAvailable) {
      Buttons.ActionButton(
        onClick = onMessageClick,
        imageVector = SignalIcons.ChatMessage.imageVector,
        label = stringResource(R.string.ConversationSettingsFragment__message),
        enabled = enabled
      )
    }

    if (state.isVideoAvailable) {
      Buttons.ActionButton(
        onClick = onVideoCallClick,
        imageVector = SignalIcons.Video.imageVector,
        label = stringResource(R.string.ConversationSettingsFragment__video),
        enabled = enabled
      )
    }

    if (state.isAudioAvailable) {
      Buttons.ActionButton(
        onClick = onAudioCallClick,
        imageVector = if (state.isAudioSecure) SignalIcons.Phone.imageVector else SignalIcons.PhoneUnlock.imageVector,
        label = stringResource(if (state.isAudioSecure) R.string.ConversationSettingsFragment__audio else R.string.ConversationSettingsFragment__call),
        enabled = enabled
      )
    }

    if (state.isMuteAvailable) {
      MuteButton(
        isMuted = state.isMuted,
        isMenuShown = isMuteMenuShown,
        enabled = enabled,
        onClick = onMuteClick,
        onDurationSelected = onMuteDurationSelected,
        onCustomTimeClick = onMuteUntilCustomTimeClick,
        onMenuDismissed = onMuteMenuDismissed
      )
    }

    if (state.isSearchAvailable) {
      Buttons.ActionButton(
        onClick = onSearchClick,
        imageVector = SignalIcons.Search.imageVector,
        label = stringResource(R.string.ConversationSettingsFragment__search),
        enabled = enabled
      )
    }
  }
}

@Composable
private fun MuteButton(
  isMuted: Boolean,
  isMenuShown: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
  onDurationSelected: (Long) -> Unit,
  onCustomTimeClick: () -> Unit,
  onMenuDismissed: () -> Unit,
  modifier: Modifier = Modifier
) {
  val controller = remember { DropdownMenus.MenuController() }
  val controllerShown = controller.isShown()

  LaunchedEffect(isMenuShown) {
    if (isMenuShown) {
      controller.show()
    } else {
      controller.hide()
    }
  }

  // The menu hides itself when dismissed, so tell the view model to drop the dialog from state to match.
  LaunchedEffect(controllerShown) {
    if (!controllerShown && isMenuShown) {
      onMenuDismissed()
    }
  }

  Box(modifier = modifier) {
    Buttons.ActionButton(
      onClick = onClick,
      imageVector = if (isMuted) SignalIcons.BellSlash.imageVector else SignalIcons.Bell.imageVector,
      label = stringResource(if (isMuted) R.string.ConversationSettingsFragment__muted else R.string.ConversationSettingsFragment__mute),
      enabled = enabled
    )

    DropdownMenus.Menu(controller = controller) { menuController ->
      MUTE_DURATIONS.forEach { duration ->
        DropdownMenus.ItemWithIcon(
          menuController = menuController,
          imageVector = duration.icon.imageVector,
          stringResId = duration.labelResId,
          onClick = { onDurationSelected(System.currentTimeMillis() + duration.durationMillis) }
        )
      }

      DropdownMenus.ItemWithIcon(
        menuController = menuController,
        imageVector = SignalIcons.Calendar.imageVector,
        stringResId = R.string.MuteDialog__mute_until,
        onClick = onCustomTimeClick
      )

      DropdownMenus.ItemWithIcon(
        menuController = menuController,
        imageVector = SignalIcons.BellSlash.imageVector,
        stringResId = R.string.arrays__always,
        onClick = { onDurationSelected(Long.MAX_VALUE) }
      )
    }
  }
}

@Composable
private fun CallBarPreview(state: CallBarState, enabled: Boolean = true) {
  Previews.Preview {
    CallBar(
      state = state,
      enabled = enabled,
      onAddToStoryClick = {},
      onMessageClick = {},
      onVideoCallClick = {},
      onAudioCallClick = {},
      onMuteClick = {},
      onMuteDurationSelected = {},
      onMuteUntilCustomTimeClick = {},
      onMuteMenuDismissed = {},
      onSearchClick = {}
    )
  }
}

/** What a 1:1 with a registered contact offers. */
@DayNightPreviews
@Composable
private fun CallBarIndividualPreview() {
  CallBarPreview(
    CallBarState(
      isVideoAvailable = true,
      isAudioAvailable = true,
      isAudioSecure = true,
      isMuteAvailable = true,
      isSearchAvailable = true
    )
  )
}

/** Groups trade the audio call button for the add-to-story button. */
@DayNightPreviews
@Composable
private fun CallBarGroupPreview() {
  CallBarPreview(
    CallBarState(
      isVideoAvailable = true,
      isMuteAvailable = true,
      isSearchAvailable = true,
      isAddToStoryAvailable = true
    )
  )
}

/** Note to self, which has nobody to call or mute. */
@DayNightPreviews
@Composable
private fun CallBarNoteToSelfPreview() {
  CallBarPreview(CallBarState(isSearchAvailable = true))
}

/** The release notes chat, which can be muted but not called. */
@DayNightPreviews
@Composable
private fun CallBarReleaseNotesPreview() {
  CallBarPreview(CallBarState(isMuteAvailable = true, isSearchAvailable = true))
}

@DayNightPreviews
@Composable
private fun CallBarMutedPreview() {
  CallBarPreview(
    CallBarState(
      isVideoAvailable = true,
      isAudioAvailable = true,
      isAudioSecure = true,
      isMuteAvailable = true,
      isMuted = true,
      isSearchAvailable = true
    )
  )
}

/** An unregistered peer, where the audio button falls back to an insecure "Call" that dials out. */
@DayNightPreviews
@Composable
private fun CallBarInsecureAudioPreview() {
  CallBarPreview(
    CallBarState(
      isAudioAvailable = true,
      isAudioSecure = false,
      isMuteAvailable = true,
      isSearchAvailable = true
    )
  )
}

/** Opened as call info, where the message button takes the place of search. */
@DayNightPreviews
@Composable
private fun CallBarCallInfoPreview() {
  CallBarPreview(
    CallBarState(
      isMessageAvailable = true,
      isVideoAvailable = true,
      isAudioAvailable = true,
      isAudioSecure = true,
      isMuteAvailable = true
    )
  )
}

@DayNightPreviews
@Composable
private fun CallBarDisabledPreview() {
  CallBarPreview(
    CallBarState(
      isVideoAvailable = true,
      isAudioAvailable = true,
      isAudioSecure = true,
      isMuteAvailable = true,
      isSearchAvailable = true
    ),
    enabled = false
  )
}

private data class MuteDuration(
  val icon: SignalIcons,
  @StringRes val labelResId: Int,
  val durationMillis: Long
)

private val MUTE_DURATIONS = listOf(
  MuteDuration(SignalIcons.Daytime, R.string.arrays__mute_for_one_hour, 1.hours.inWholeMilliseconds),
  MuteDuration(SignalIcons.Nighttime, R.string.arrays__mute_for_eight_hours, 8.hours.inWholeMilliseconds),
  MuteDuration(SignalIcons.CalendarOne, R.string.arrays__mute_for_one_day, 1.days.inWholeMilliseconds),
  MuteDuration(SignalIcons.CalendarWeek, R.string.arrays__mute_for_seven_days, 7.days.inWholeMilliseconds)
)

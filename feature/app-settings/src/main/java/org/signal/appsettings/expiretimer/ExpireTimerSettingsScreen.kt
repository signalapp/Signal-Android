/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.expiretimer

import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.appsettings.AppSettingsLazyColumn
import org.signal.appsettings.R
import org.signal.appsettings.appSettingsScreen
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.CircularProgressWrapper
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Pickers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.util.ExpirationUtil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object ExpireTimerSettingsTestTags {
  const val SCROLLER = "scroller"
  const val ROW_CUSTOM_TIME = "row-custom-time"
  const val BUTTON_SAVE = "button-save"

  fun preset(timer: Duration): String = "row-preset-${timer.inWholeSeconds}"
}

/** The preset timers offered, in the order they're listed. */
private val ExpireTimerPresets: List<Duration> = listOf(
  Duration.ZERO,
  28.days,
  7.days,
  1.days,
  8.hours,
  1.hours,
  5.minutes,
  30.seconds
)

/**
 * Picks a disappearing messages timer, either the default for new chats, the timer for one chat, or one for a group
 * that's being created.
 */
@Composable
fun ExpireTimerSettingsScreen(
  state: ExpireTimerSettingsState,
  onEvent: (ExpireTimerSettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = stringResource(R.string.ExpireTimerSettingsScreen__disappearing_messages),
    onNavigationClick = { onEvent(ExpireTimerSettingsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(CoreUiR.string.CloseButton__content_description),
    snackbarHost = {
      Snackbars.Host(
        message = state.snackbar.message(),
        onDismissed = { onEvent(ExpireTimerSettingsEvent.SnackbarDismissed) }
      )
    },
    modifier = modifier,
    expandableTitle = true
  ) { contentPadding ->
    Column(modifier = Modifier.padding(contentPadding)) {
      Box(modifier = Modifier.weight(1f)) {
        AppSettingsLazyColumn(
          modifier = Modifier.testTag(ExpireTimerSettingsTestTags.SCROLLER)
        ) {
          item {
            Rows.TextRow(
              label = stringResource(
                if (state.mode == ExpireTimerSettingsState.Mode.UNIVERSAL) {
                  R.string.ExpireTimerSettingsFragment__when_enabled_new_messages_sent_and_received_in_new_chats_started_by_you_will_disappear_after_they_have_been_seen
                } else {
                  R.string.ExpireTimerSettingsFragment__when_enabled_new_messages_sent_and_received_in_this_chat_will_disappear_after_they_have_been_seen
                }
              )
            )
          }

          ExpireTimerPresets.forEach { timer ->
            item(key = timer.inWholeSeconds) {
              Rows.RadioRow(
                selected = state.currentTimer == timer,
                text = stringResource(timer.presetLabel()),
                modifier = Modifier
                  .clickable { onEvent(ExpireTimerSettingsEvent.TimerSelected(timer)) }
                  .testTag(ExpireTimerSettingsTestTags.preset(timer))
              )
            }
          }

          item {
            val isCustom = state.currentTimer !in ExpireTimerPresets
            Rows.RadioRow(
              selected = isCustom,
              text = stringResource(R.string.ExpireTimerSettingsFragment__custom_time),
              label = if (isCustom) ExpirationUtil.getExpirationDisplayValue(LocalContext.current, state.currentTimer.inWholeSeconds.toInt()) else null,
              modifier = Modifier
                .clickable { onEvent(ExpireTimerSettingsEvent.CustomTimeClicked) }
                .testTag(ExpireTimerSettingsTestTags.ROW_CUSTOM_TIME)
            )
          }
        }
      }

      Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = Modifier.appSettingsScreen()
      ) {
        CircularProgressWrapper(
          isLoading = state.isSaving,
          modifier = Modifier.padding(16.dp)
        ) {
          Buttons.LargeTonal(
            onClick = { onEvent(ExpireTimerSettingsEvent.SaveClicked) },
            enabled = !state.isSaving,
            modifier = Modifier.testTag(ExpireTimerSettingsTestTags.BUTTON_SAVE)
          ) {
            Text(text = stringResource(R.string.ExpireTimerSettingsFragment__save))
          }
        }
      }
    }
  }

  when (state.dialog) {
    ExpireTimerSettingsState.Dialog.None -> Unit
    ExpireTimerSettingsState.Dialog.CustomTimer -> {
      Pickers.DurationDialog(
        title = stringResource(R.string.ExpireTimerSettingsFragment__custom_time),
        initialDuration = state.currentTimer.takeIf { it > Duration.ZERO } ?: 1.minutes,
        onConfirm = { onEvent(ExpireTimerSettingsEvent.CustomTimerSelected(it)) },
        onDismiss = { onEvent(ExpireTimerSettingsEvent.DialogDismissed) }
      )
    }
  }
}

@StringRes
private fun Duration.presetLabel(): Int {
  return when (this) {
    Duration.ZERO -> R.string.ExpireTimerSettingsFragment__off
    28.days -> R.string.ExpireTimerSettingsFragment__4_weeks
    7.days -> R.string.ExpireTimerSettingsFragment__1_week
    1.days -> R.string.ExpireTimerSettingsFragment__1_day
    8.hours -> R.string.ExpireTimerSettingsFragment__8_hours
    1.hours -> R.string.ExpireTimerSettingsFragment__1_hour
    5.minutes -> R.string.ExpireTimerSettingsFragment__5_minutes
    30.seconds -> R.string.ExpireTimerSettingsFragment__30_seconds
    else -> error("Not a preset timer: $this")
  }
}

@Composable
private fun ExpireTimerSettingsState.Snackbar.message(): String? {
  return when (this) {
    ExpireTimerSettingsState.Snackbar.None -> null
    is ExpireTimerSettingsState.Snackbar.SaveFailed -> stringResource(message)
  }
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun ExpireTimerSettingsScreenPreview() {
  ExpireTimerSettingsScreen(
    state = ExpireTimerSettingsState(initialTimer = 1.days),
    onEvent = {}
  )
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun ExpireTimerSettingsScreenCustomSavingPreview() {
  ExpireTimerSettingsScreen(
    state = ExpireTimerSettingsState(
      mode = ExpireTimerSettingsState.Mode.RECIPIENT,
      initialTimer = 3.hours,
      isSaving = true
    ),
    onEvent = {}
  )
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun ExpireTimerSettingsScreenCustomDialogPreview() {
  ExpireTimerSettingsScreen(
    state = ExpireTimerSettingsState(dialog = ExpireTimerSettingsState.Dialog.CustomTimer),
    onEvent = {}
  )
}

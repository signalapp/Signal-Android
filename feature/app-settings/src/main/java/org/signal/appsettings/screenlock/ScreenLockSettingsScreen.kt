/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.screenlock

import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.signal.appsettings.R
import org.signal.core.ui.biometrics.rememberBiometricsAuthentication
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.DurationPickerUnit
import org.signal.core.ui.compose.Pickers
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.util.ExpirationUtil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object ScreenLockSettingsTestTags {
  const val SCROLLER = "scroller"
  const val SWITCH_SCREEN_LOCK = "switch-screen-lock"
  const val ROW_TIMEOUT_PREFIX = "row-timeout-"
  const val ROW_CUSTOM_TIMEOUT = "row-custom-timeout"
}

private val CUSTOM_TIMEOUT_UNITS = listOf(DurationPickerUnit.MINUTES, DurationPickerUnit.HOURS, DurationPickerUnit.DAYS)

/**
 * Lets the user turn on Signal's screen lock, which requires their device credential to open the app, and choose how
 * long the app can be in the background before it locks.
 */
@Composable
fun ScreenLockSettingsScreen(
  state: ScreenLockSettingsState,
  onEvent: (ScreenLockSettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val enableBiometrics = rememberBiometricsAuthentication(
    promptTitle = stringResource(R.string.ScreenLockSettingsFragment__use_signal_screen_lock),
    deviceCredentialTitle = stringResource(R.string.ScreenLockSettingsFragment__use_screen_lock),
    proceedWhenUnavailable = false
  )
  val disableBiometrics = rememberBiometricsAuthentication(
    promptTitle = stringResource(R.string.ScreenLockSettingsFragment__turn_off_signal_lock),
    proceedWhenUnavailable = false
  )

  LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
    enableBiometrics.cancelAuthentication()
    disableBiometrics.cancelAuthentication()
  }

  Scaffolds.Settings(
    title = stringResource(R.string.preferences_app_protection__screen_lock),
    onNavigationClick = { onEvent(ScreenLockSettingsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(CoreUiR.string.CloseButton__content_description),
    modifier = modifier
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .padding(paddingValues)
        .testTag(ScreenLockSettingsTestTags.SCROLLER)
    ) {
      item {
        Icon(
          painter = painterResource(R.drawable.ic_screen_lock),
          contentDescription = null,
          tint = Color.Unspecified,
          modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth()
            .padding(vertical = 24.dp)
        )
      }

      item {
        Text(
          text = stringResource(R.string.ScreenLockSettingsFragment__your_android_device),
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, end = 40.dp, bottom = 24.dp)
        )
      }

      item {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .padding(horizontal = 24.dp)
            .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(24.dp))
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
          Text(
            text = stringResource(R.string.ScreenLockSettingsFragment__use_screen_lock),
            modifier = Modifier.weight(1f)
          )
          Spacer(Modifier.width(16.dp))
          Switch(
            checked = state.isScreenLockEnabled,
            enabled = state.canAuthenticate,
            onCheckedChange = { enabled ->
              val biometrics = if (enabled) enableBiometrics else disableBiometrics
              biometrics.withBiometricsAuthentication { onEvent(ScreenLockSettingsEvent.ScreenLockToggled(enabled)) }
            },
            modifier = Modifier.testTag(ScreenLockSettingsTestTags.SWITCH_SCREEN_LOCK)
          )
        }
      }

      if (state.isScreenLockEnabled) {
        item {
          Text(
            text = stringResource(R.string.ScreenLockSettingsFragment__start_screen_lock),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 24.dp, bottom = 16.dp, start = 24.dp)
          )

          Column(Modifier.selectableGroup()) {
            ScreenLockSettingsState.PRESET_TIMEOUTS.forEach { timeout ->
              TimeoutRow(
                selected = state.timeout == timeout,
                onClick = { onEvent(ScreenLockSettingsEvent.TimeoutSelected(timeout)) },
                modifier = Modifier.testTag(ScreenLockSettingsTestTags.ROW_TIMEOUT_PREFIX + timeout.inWholeSeconds)
              ) {
                Text(
                  text = stringResource(timeout.presetLabelRes),
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
            }

            TimeoutRow(
              selected = state.isCustomTimeout,
              onClick = { onEvent(ScreenLockSettingsEvent.CustomTimeoutClicked) },
              modifier = Modifier.testTag(ScreenLockSettingsTestTags.ROW_CUSTOM_TIMEOUT)
            ) {
              Column {
                Text(
                  text = stringResource(R.string.ScreenLockSettingsFragment__custom_time),
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onSurface
                )

                if (state.isCustomTimeout && state.timeout > Duration.ZERO) {
                  Text(
                    text = ExpirationUtil.getExpirationDisplayValue(LocalContext.current, state.timeout.inWholeSeconds.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }
      }
    }

    when (state.dialog) {
      ScreenLockSettingsState.Dialog.None -> Unit
      ScreenLockSettingsState.Dialog.CustomTimeout -> {
        Pickers.DurationDialog(
          title = stringResource(R.string.ScreenLockSettingsFragment__custom_time),
          initialDuration = state.timeout,
          units = CUSTOM_TIMEOUT_UNITS,
          onConfirm = { onEvent(ScreenLockSettingsEvent.CustomTimeoutSelected(it)) },
          onDismiss = { onEvent(ScreenLockSettingsEvent.DialogDismissed) }
        )
      }
    }
  }
}

private val Duration.presetLabelRes: Int
  @StringRes get() = when (this) {
    1.minutes -> R.string.ScreenLockSettingsFragment__after_1_min
    5.minutes -> R.string.ScreenLockSettingsFragment__after_5_min
    30.minutes -> R.string.ScreenLockSettingsFragment__after_30_min
    else -> error("Not a preset timeout: $this")
  }

@Composable
private fun TimeoutRow(
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .fillMaxWidth()
      .defaultMinSize(minHeight = 56.dp)
      .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
      .padding(horizontal = 24.dp)
  ) {
    RadioButton(selected = selected, onClick = null)
    Spacer(Modifier.width(16.dp))
    content()
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ScreenLockSettingsScreenOffPreview() {
  ScreenLockSettingsScreen(state = ScreenLockSettingsState(), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ScreenLockSettingsScreenPresetPreview() {
  ScreenLockSettingsScreen(state = ScreenLockSettingsState(isScreenLockEnabled = true, timeout = 5.minutes), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ScreenLockSettingsScreenCustomPreview() {
  ScreenLockSettingsScreen(state = ScreenLockSettingsState(isScreenLockEnabled = true, timeout = 3.hours), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ScreenLockSettingsScreenCustomDialogPreview() {
  ScreenLockSettingsScreen(
    state = ScreenLockSettingsState(isScreenLockEnabled = true, timeout = 3.hours, dialog = ScreenLockSettingsState.Dialog.CustomTimeout),
    onEvent = {}
  )
}

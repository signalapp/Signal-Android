/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.updates

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.signal.appsettings.R
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@VisibleForTesting
object AppUpdatesSettingsTestTags {
  const val SCROLLER = "scroller"
  const val ROW_AUTO_UPDATE = "row-auto-update"
  const val ROW_CHECK_FOR_UPDATES = "row-check-for-updates"
}

/**
 * Settings around app updates. Only shown for builds that manage their own app updates.
 */
@Composable
fun AppUpdatesSettingsScreen(
  state: AppUpdatesSettingsState,
  onEvent: (AppUpdatesSettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  LifecycleResumeEffect(Unit) {
    onEvent(AppUpdatesSettingsEvent.ScreenResumed)
    onPauseOrDispose {}
  }

  Scaffolds.Settings(
    title = stringResource(R.string.preferences_app_updates__title),
    onNavigationClick = { onEvent(AppUpdatesSettingsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    modifier = modifier
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .padding(paddingValues)
        .testTag(AppUpdatesSettingsTestTags.SCROLLER)
    ) {
      if (state.isAutoUpdateSupported) {
        item {
          Rows.ToggleRow(
            checked = state.autoUpdateEnabled,
            text = "Automatic updates",
            label = "Automatically download and install app updates",
            onCheckChanged = { onEvent(AppUpdatesSettingsEvent.AutoUpdateToggled(it)) },
            modifier = Modifier.testTag(AppUpdatesSettingsTestTags.ROW_AUTO_UPDATE)
          )
        }
      }

      item {
        Rows.TextRow(
          text = "Check for updates",
          label = "Last checked on: ${rememberLastSuccessfulUpdateString(state.lastCheckedTime)}",
          onClick = { onEvent(AppUpdatesSettingsEvent.CheckForUpdatesClicked) },
          modifier = Modifier.testTag(AppUpdatesSettingsTestTags.ROW_CHECK_FOR_UPDATES)
        )
      }
    }
  }
}

@Composable
private fun rememberLastSuccessfulUpdateString(lastUpdateTime: Duration): String {
  return remember(lastUpdateTime) {
    if (lastUpdateTime > Duration.ZERO) {
      val dateFormat = SimpleDateFormat("MMMM dd, yyyy 'at' h:mma", Locale.US)
      dateFormat.format(Date(lastUpdateTime.inWholeMilliseconds))
    } else {
      "Never"
    }
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun AppUpdatesSettingsScreenPreview() {
  AppUpdatesSettingsScreen(
    state = AppUpdatesSettingsState(
      lastCheckedTime = System.currentTimeMillis().milliseconds,
      autoUpdateEnabled = true,
      isAutoUpdateSupported = true
    ),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun AppUpdatesSettingsScreenNeverCheckedPreview() {
  AppUpdatesSettingsScreen(
    state = AppUpdatesSettingsState(),
    onEvent = {}
  )
}

/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.updates

import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.rememberStatusBarColorNestedScrollModifier
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.ApkUpdateJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Settings around app updates. Only shown for builds that manage their own app updates.
 */
class AppUpdatesSettingsFragment : ComposeFragment() {

  private val viewModel: AppUpdatesSettingsViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppUpdatesSettingsScreen(
      state = state,
      callbacks = remember { Callbacks() }
    )
  }

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  private inner class Callbacks : AppUpdatesSettingsCallbacks {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onAutoUpdateChanged(enabled: Boolean) {
      SignalStore.apkUpdate.autoUpdate = enabled
      viewModel.refresh()
    }

    override fun onCheckForUpdatesClick() {
      AppDependencies.jobManager.add(ApkUpdateJob())
    }
  }
}

private interface AppUpdatesSettingsCallbacks {
  fun onNavigationClick() = Unit
  fun onAutoUpdateChanged(enabled: Boolean) = Unit
  fun onCheckForUpdatesClick() = Unit

  object Empty : AppUpdatesSettingsCallbacks
}

@Composable
private fun AppUpdatesSettingsScreen(
  state: AppUpdatesSettingsState,
  callbacks: AppUpdatesSettingsCallbacks
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences_app_updates__title),
    onNavigationClick = callbacks::onNavigationClick,
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24)
  ) { paddingValues ->

    LazyColumn(
      modifier = Modifier
        .padding(paddingValues)
        .then(rememberStatusBarColorNestedScrollModifier())
    ) {
      if (Build.VERSION.SDK_INT >= 31) {
        item {
          Rows.ToggleRow(
            checked = state.autoUpdateEnabled,
            text = "Automatic updates",
            label = "Automatically download and install app updates",
            onCheckChanged = callbacks::onAutoUpdateChanged
          )
        }
      }

      item {
        Rows.TextRow(
          text = "Check for updates",
          label = "Last checked on: ${rememberLastSuccessfulUpdateString(state.lastCheckedTime)}",
          onClick = callbacks::onCheckForUpdatesClick
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

@SignalPreview
@Composable
private fun AppUpdatesSettingsScreenPreview() {
  Previews.Preview {
    AppUpdatesSettingsScreen(
      state = AppUpdatesSettingsState(
        lastCheckedTime = System.currentTimeMillis().milliseconds,
        autoUpdateEnabled = true
      ),
      callbacks = AppUpdatesSettingsCallbacks.Empty
    )
  }
}

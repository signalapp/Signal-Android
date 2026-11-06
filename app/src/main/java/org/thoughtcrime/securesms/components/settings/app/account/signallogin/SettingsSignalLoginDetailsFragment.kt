/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.signallogin

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.navigation.LocalResultEventBus
import org.signal.core.ui.navigation.ResultEventBus
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.components.settings.app.backups.remote.BackupKeyDisplayFragment
import org.thoughtcrime.securesms.components.settings.app.backups.remote.BackupKeyRotated
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Shows the account and recovery keys that make up the user's Signal Login, under the legacy nav graph.
 */
class SettingsSignalLoginDetailsFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  /** Stands in for the Nav3 host's bus, so the backup key screen's fragment result reaches the Entry the same way. */
  private val resultEventBus = ResultEventBus()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setFragmentResultListener(BackupKeyDisplayFragment.AEP_ROTATION_KEY) { _, bundle ->
      if (bundle.getBoolean(BackupKeyDisplayFragment.AEP_ROTATION_KEY, false)) {
        resultEventBus.sendResult(result = BackupKeyRotated)
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    CompositionLocalProvider(LocalResultEventBus provides resultEventBus) {
      SettingsSignalLoginDetailsEntry(parentEventEmitter = appSettingsRouter::onEvent)
    }
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      is AppSettingsRoute.BackupsRoute.DisplayKey -> findNavController().safeNavigate(
        SettingsSignalLoginDetailsFragmentDirections.actionSettingsSignalLoginDetailsFragmentToBackupKeyDisplayFragment().setStartWithKeyRotation(route.startWithKeyRotation)
      )
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }
}

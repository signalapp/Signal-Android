/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.components.settings.app.backups.local

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.navigation.LocalResultEventBus
import org.signal.core.ui.navigation.ResultEventBus
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.components.settings.app.backups.remote.BackupKeyDisplayFragment
import org.thoughtcrime.securesms.components.settings.app.backups.remote.BackupKeyRotated
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Hosts [LocalBackupsEntry] under the legacy nav graph.
 */
class LocalBackupsFragment : ComposeFragment() {

  private val args: LocalBackupsFragmentArgs by navArgs()

  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  /** Stands in for the Nav3 host's bus, so the key screen's fragment result reaches the Entry the same way. */
  private val resultEventBus = ResultEventBus()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    setFragmentResultListener(BackupKeyDisplayFragment.AEP_ROTATION_KEY) { _, bundle ->
      if (bundle.getBoolean(BackupKeyDisplayFragment.AEP_ROTATION_KEY, false)) {
        resultEventBus.sendResult(result = BackupKeyRotated)
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    CompositionLocalProvider(
      LocalNavigationEventDispatcherOwner provides requireActivity(),
      LocalResultEventBus provides resultEventBus
    ) {
      LocalBackupsEntry(
        parentEventEmitter = appSettingsRouter::onEvent,
        triggerUpdateFlow = args.triggerUpdateFlow
      )
    }
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      AppSettingsRoute.AccountRoute.SignalLoginDetails -> findNavController().safeNavigate(R.id.action_localBackupsFragment_to_settingsSignalLoginDetailsFragment)
      is AppSettingsRoute.BackupsRoute.DisplayKey -> findNavController().safeNavigate(R.id.action_backupsPreferenceFragment_to_backupKeyDisplayFragment)
      AppSettingsRoute.DataAndStorageRoute.Storage -> findNavController().safeNavigate(R.id.action_direct_to_storagePreferenceFragment)
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }
}

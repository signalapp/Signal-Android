/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.managestorage.ManageStorageSettingsAction
import org.signal.appsettings.managestorage.ManageStorageSettingsEvent
import org.signal.appsettings.managestorage.ManageStorageSettingsScreen
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.viewModel
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.billing.upgrade.UpgradeToEnableOptimizedStorageSheet
import org.thoughtcrime.securesms.billing.upgrade.UpgradeToPaidTierBottomSheet
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.mediaoverview.MediaOverviewActivity
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Manage settings related to on-device storage including viewing usage and auto-delete settings.
 */
class ManageStorageSettingsFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  private val viewModel: ManageStorageSettingsViewModel by viewModel {
    ManageStorageSettingsViewModel(parentEventEmitter = appSettingsRouter::onEvent)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    UpgradeToPaidTierBottomSheet.addResultListener(this) {
      viewModel.onEvent(ManageStorageSettingsEvent.PaidTierUpgradeCompleted)
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.onEvent(ManageStorageSettingsEvent.ScreenResumed)
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      AppSettingsRoute.DataAndStorageRoute.KeepMessages -> findNavController().safeNavigate(R.id.action_storagePreferenceFragment_to_keepMessagesSettingsFragment)
      AppSettingsRoute.DataAndStorageRoute.ChatLengthLimit -> findNavController().safeNavigate(R.id.action_storagePreferenceFragment_to_chatLengthLimitSettingsFragment)
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { handleAction(it) }

    ManageStorageSettingsScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: ManageStorageSettingsAction) {
    when (action) {
      ManageStorageSettingsAction.NavigateToReviewStorage -> startActivity(MediaOverviewActivity.forAll(requireContext()))
      ManageStorageSettingsAction.ShowUpgradeToEnableOptimizedStorage -> UpgradeToEnableOptimizedStorageSheet().show(parentFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }
}

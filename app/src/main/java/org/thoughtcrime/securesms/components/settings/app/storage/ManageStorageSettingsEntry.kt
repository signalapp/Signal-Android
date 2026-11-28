/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.managestorage.ManageStorageSettingsAction
import org.signal.appsettings.managestorage.ManageStorageSettingsEvent
import org.signal.appsettings.managestorage.ManageStorageSettingsScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.CollectActions
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.billing.upgrade.UpgradeToEnableOptimizedStorageSheet
import org.thoughtcrime.securesms.billing.upgrade.UpgradeToPaidTierBottomSheet
import org.thoughtcrime.securesms.mediaoverview.MediaOverviewActivity

private val TAG = Log.tag(ManageStorageSettingsViewModel::class)

/**
 * The manage storage screen, wired to its view model, the media overview, and the sheet for upgrading to optimized storage.
 */
@Composable
fun ManageStorageSettingsEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val activity = LocalActivity.current as? FragmentActivity
  val lifecycleOwner = LocalLifecycleOwner.current
  val viewModel: ManageStorageSettingsViewModel = viewModel { ManageStorageSettingsViewModel(parentEventEmitter = parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  DisposableEffect(activity, lifecycleOwner) {
    val fragmentManager = activity?.supportFragmentManager
    fragmentManager?.let {
      UpgradeToPaidTierBottomSheet.addResultListener(it, lifecycleOwner) {
        viewModel.onEvent(ManageStorageSettingsEvent.PaidTierUpgradeCompleted)
      }
    }
    onDispose { fragmentManager?.clearFragmentResultListener(UpgradeToPaidTierBottomSheet.RESULT_KEY) }
  }

  CollectActions(viewModel.actions) { action ->
    when (action) {
      ManageStorageSettingsAction.NavigateToReviewStorage -> activity?.startActivity(MediaOverviewActivity.forAll(activity))
      ManageStorageSettingsAction.ShowUpgradeToEnableOptimizedStorage -> {
        if (activity == null) {
          Log.w(TAG, "Unable to show the optimized storage upgrade without a FragmentActivity.")
        } else {
          UpgradeToEnableOptimizedStorageSheet().show(activity.supportFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
        }
      }
    }
  }

  ManageStorageSettingsScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}

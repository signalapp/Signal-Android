/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import android.app.Activity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.Nav
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.viewModel

/**
 * Handles the selection, payment, and changing of a user's backup tier.
 */
class MessageBackupsFlowFragment : ComposeFragment() {

  private val viewModel: MessageBackupsFlowViewModel by viewModel { MessageBackupsFlowViewModel() }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.stateFlow.collectAsState()
    val navController = rememberNavController()

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
      navController.setLifecycleOwner(this@MessageBackupsFlowFragment)

      requireActivity().onBackPressedDispatcher.addCallback(
        lifecycleOwner,
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            viewModel.goToPreviousStage()
          }
        }
      )
    }

    Nav.Host(
      navController = navController,
      startDestination = state.startScreen.name
    ) {
      composable(route = MessageBackupsStage.Route.EDUCATION.name) {
        MessageBackupsEducationScreen(
          onNavigationClick = viewModel::goToPreviousStage,
          onEnableBackups = viewModel::goToNextStage,
          onLearnMore = {}
        )
      }

      composable(route = MessageBackupsStage.Route.BACKUP_KEY_EDUCATION.name) {
        MessageBackupsKeyEducationScreen(
          onNavigationClick = viewModel::goToPreviousStage,
          onNextClick = viewModel::goToNextStage
        )
      }

      composable(route = MessageBackupsStage.Route.BACKUP_KEY_RECORD.name) {
        val context = LocalContext.current

        MessageBackupsKeyRecordScreen(
          backupKey = state.backupKey,
          onNavigationClick = viewModel::goToPreviousStage,
          onNextClick = viewModel::goToNextStage,
          onCopyToClipboardClick = {
            Util.copyToClipboard(context, it)
          }
        )
      }

      composable(route = MessageBackupsStage.Route.TYPE_SELECTION.name) {
        MessageBackupsTypeSelectionScreen(
          currentBackupTier = state.currentMessageBackupTier,
          selectedBackupTier = state.selectedMessageBackupTier,
          availableBackupTypes = state.availableBackupTypes.filter { it.tier == MessageBackupTier.FREE || state.hasBackupSubscriberAvailable },
          onMessageBackupsTierSelected = { tier ->
            val type = state.availableBackupTypes.first { it.tier == tier }
            val label = when (type) {
              is MessageBackupsType.Free -> requireContext().resources.getQuantityString(R.plurals.MessageBackupsTypeSelectionScreen__text_plus_d_days_of_media, type.mediaRetentionDays, type.mediaRetentionDays)
              is MessageBackupsType.Paid -> requireContext().getString(R.string.MessageBackupsTypeSelectionScreen__text_plus_all_your_media)
            }

            viewModel.onMessageBackupTierUpdated(tier, label)
          },
          onNavigationClick = viewModel::goToPreviousStage,
          onReadMoreClicked = {},
          onNextClicked = viewModel::goToNextStage
        )
      }
    }

    LaunchedEffect(state.stage) {
      val newRoute = state.stage.route.name
      val currentRoute = navController.currentDestination?.route
      if (currentRoute != newRoute) {
        if (currentRoute != null && MessageBackupsStage.Route.valueOf(currentRoute).isAfter(state.stage.route)) {
          navController.popBackStack()
        } else {
          navController.navigate(newRoute)
        }
      }

      if (state.stage == MessageBackupsStage.CHECKOUT_SHEET) {
        AppDependencies.billingApi.launchBillingFlow(requireActivity())
      }

      if (state.stage == MessageBackupsStage.COMPLETED) {
        requireActivity().setResult(Activity.RESULT_OK)
        requireActivity().finishAfterTransition()
      }
    }
  }
}

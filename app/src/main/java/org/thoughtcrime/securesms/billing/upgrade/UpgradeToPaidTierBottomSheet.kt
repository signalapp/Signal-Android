/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.billing.upgrade

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import kotlinx.coroutines.rx3.asFlowable
import org.signal.core.ui.Dialogs
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsFlowViewModel
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsStage
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentCheckoutDelegate
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.util.viewModel

/**
 * BottomSheet that encapsulates the common logic for updating someone to paid tier.
 */
abstract class UpgradeToPaidTierBottomSheet : ComposeBottomSheetDialogFragment(), InAppPaymentCheckoutDelegate.ErrorHandlerCallback {

  companion object {
    const val RESULT_KEY = "UpgradeToPaidTierBottomSheet.RESULT_KEY"

    fun addResultListener(fragment: Fragment, onUpgradedToPaidTier: () -> Unit) {
      fragment.setFragmentResultListener(RESULT_KEY) { key, bundle ->
        if (RESULT_KEY == key) {
          val didCompleteSignup = bundle.getBoolean(RESULT_KEY, false)

          if (didCompleteSignup) {
            onUpgradedToPaidTier()
          }
        }
      }
    }
  }

  private val viewModel: MessageBackupsFlowViewModel by viewModel {
    MessageBackupsFlowViewModel(
      initialTierSelection = MessageBackupTier.PAID,
      startScreen = MessageBackupsStage.TYPE_SELECTION
    )
  }

  private val errorHandler = InAppPaymentCheckoutDelegate.ErrorHandler()

  override val peekHeightPercentage: Float = 1f

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    errorHandler.attach(
      fragment = this,
      errorHandlerCallback = this,
      inAppPaymentIdSource = viewModel.stateFlow.asFlowable()
        .filter { it.inAppPayment != null }
        .map { it.inAppPayment!!.id }
    )
  }

  @Composable
  override fun SheetContent() {
    val state by viewModel.stateFlow.collectAsState()

    val paidBackupType = state.availableBackupTypes.firstOrNull { it.tier == MessageBackupTier.PAID } as? MessageBackupsType.Paid
    val freeBackupType = state.availableBackupTypes.firstOrNull { it.tier == MessageBackupTier.FREE } as? MessageBackupsType.Free

    if (paidBackupType != null && freeBackupType != null) {
      UpgradeSheetContent(
        paidBackupType = paidBackupType,
        freeBackupType = freeBackupType,
        isSubscribeEnabled = state.stage == MessageBackupsStage.TYPE_SELECTION,
        onSubscribeClick = viewModel::goToNextStage
      )
    }

    when (state.stage) {
      MessageBackupsStage.CREATING_IN_APP_PAYMENT -> Dialogs.IndeterminateProgressDialog()
      MessageBackupsStage.PROCESS_PAYMENT -> Dialogs.IndeterminateProgressDialog()
      MessageBackupsStage.PROCESS_FREE -> Dialogs.IndeterminateProgressDialog()
      else -> Unit
    }

    LaunchedEffect(state.stage) {
      if (state.stage == MessageBackupsStage.CHECKOUT_SHEET) {
        AppDependencies.billingApi.launchBillingFlow(requireActivity())
      }

      if (state.stage == MessageBackupsStage.COMPLETED) {
        dismissAllowingStateLoss()
        setFragmentResult(RESULT_KEY, bundleOf(RESULT_KEY to true))
      }
    }
  }

  /**
   * This is responsible for displaying the normal upgrade sheet content.
   */
  @Composable
  abstract fun UpgradeSheetContent(
    paidBackupType: MessageBackupsType.Paid,
    freeBackupType: MessageBackupsType.Free,
    isSubscribeEnabled: Boolean,
    onSubscribeClick: () -> Unit
  )

  override fun onUserLaunchedAnExternalApplication() = error("Unsupported.")

  override fun navigateToDonationPending(inAppPayment: InAppPaymentTable.InAppPayment) = error("Unsupported.")

  override fun exitCheckoutFlow() {
    dismissAllowingStateLoss()
  }
}

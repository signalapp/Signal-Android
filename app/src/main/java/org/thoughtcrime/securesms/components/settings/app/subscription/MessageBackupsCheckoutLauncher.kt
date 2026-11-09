/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.requireFragmentActivity
import org.signal.core.util.getSerializableCompat
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.CreateBackupBottomSheet
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsCheckoutActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentProcessorAction

object MessageBackupsCheckoutLauncher {

  fun Fragment.createBackupsCheckoutLauncher(
    onCreateBackupBottomSheetResultListener: OnCreateBackupBottomSheetResultListener = {} as OnCreateBackupBottomSheetResultListener
  ): ActivityResultLauncher<MessageBackupTier?> {
    childFragmentManager.setFragmentResultListener(CreateBackupBottomSheet.REQUEST_KEY, viewLifecycleOwner) { requestKey, bundle ->
      if (requestKey == CreateBackupBottomSheet.REQUEST_KEY) {
        val result = bundle.getSerializableCompat(CreateBackupBottomSheet.REQUEST_KEY, CreateBackupBottomSheet.Result::class.java)
        onCreateBackupBottomSheetResultListener.onCreateBackupBottomSheetResult(result != CreateBackupBottomSheet.Result.BACKUP_STARTED)
      }
    }

    return registerForActivityResult(MessageBackupsCheckoutActivity.Contract()) { result ->
      if (result?.action == InAppPaymentProcessorAction.PROCESS_NEW_IN_APP_PAYMENT || result?.action == InAppPaymentProcessorAction.UPDATE_SUBSCRIPTION) {
        CreateBackupBottomSheet().show(childFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
      }
    }
  }

  /**
   * The Compose counterpart of [createBackupsCheckoutLauncher], for screens that aren't fragments. The sheet that follows
   * a successful checkout is shown on the activity, and [onCreateBackupBottomSheetResult] hears back from it while this
   * is in the composition.
   */
  @Composable
  fun rememberBackupsCheckoutLauncher(
    onCreateBackupBottomSheetResult: (backUpLater: Boolean) -> Unit = {}
  ): ActivityResultLauncher<MessageBackupTier?> {
    val fragmentManager = requireFragmentActivity().supportFragmentManager
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResult by rememberUpdatedState(onCreateBackupBottomSheetResult)

    LaunchedEffect(fragmentManager, lifecycleOwner) {
      fragmentManager.setFragmentResultListener(CreateBackupBottomSheet.REQUEST_KEY, lifecycleOwner) { _, bundle ->
        val result = bundle.getSerializableCompat(CreateBackupBottomSheet.REQUEST_KEY, CreateBackupBottomSheet.Result::class.java)
        currentOnResult(result != CreateBackupBottomSheet.Result.BACKUP_STARTED)
      }
    }

    return rememberLauncherForActivityResult(MessageBackupsCheckoutActivity.Contract()) { result ->
      if (result?.action == InAppPaymentProcessorAction.PROCESS_NEW_IN_APP_PAYMENT || result?.action == InAppPaymentProcessorAction.UPDATE_SUBSCRIPTION) {
        CreateBackupBottomSheet().show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
      }
    }
  }

  fun interface OnCreateBackupBottomSheetResultListener {
    fun onCreateBackupBottomSheetResult(backUpLater: Boolean)
  }
}

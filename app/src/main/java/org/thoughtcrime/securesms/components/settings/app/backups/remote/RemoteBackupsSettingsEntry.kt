/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.navigateTo
import org.signal.core.ui.compose.requireFragmentActivity
import org.signal.core.ui.navigation.ResultEffect
import org.thoughtcrime.securesms.backup.ArchiveUploadProgress
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.BackupAlert
import org.thoughtcrime.securesms.backup.v2.ui.BackupAlertBottomSheet
import org.thoughtcrime.securesms.billing.launchManageBackupsSubscription
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.MessageBackupsCheckoutLauncher.rememberBackupsCheckoutLauncher
import org.thoughtcrime.securesms.help.HelpFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediasend.MediaSendLauncher
import org.thoughtcrime.securesms.util.CommunicationActions

/**
 * Remote backups settings, wired to their view model and to the checkout, subscription and other activities they open.
 * A finished backup key rotation comes back as a [BackupKeyRotated] result.
 *
 * @param forQuickRestore True when the user arrived here from quick restore, which changes what backing up now does.
 */
@Composable
fun RemoteBackupsSettingsEntry(
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit,
  forQuickRestore: Boolean = false
) {
  val activity = requireFragmentActivity()
  val scope = rememberCoroutineScope()
  val viewModel: RemoteBackupsSettingsViewModel = viewModel { RemoteBackupsSettingsViewModel() }

  val state by viewModel.state.collectAsState()
  val backupProgress by ArchiveUploadProgress.progress.collectAsStateWithLifecycle(initialValue = null)
  val restoreState by viewModel.restoreState.collectAsState()
  val deleteState by SignalStore.backup.deletionStateFlow.collectAsStateWithLifecycle(initialValue = SignalStore.backup.deletionState)

  LifecycleResumeEffect(viewModel) {
    viewModel.refresh()
    onPauseOrDispose {}
  }

  ResultEffect<BackupKeyRotated> {
    viewModel.getKeyRotationLimit()
    viewModel.requestSnackbar(RemoteBackupsSettingsState.Snackbar.AEP_KEY_ROTATED)
  }

  val checkoutLauncher = rememberBackupsCheckoutLauncher { backUpLater ->
    if (backUpLater) {
      viewModel.requestSnackbar(RemoteBackupsSettingsState.Snackbar.BACKUP_WILL_BE_CREATED_OVERNIGHT)
    }
  }

  val callbacks = remember(activity, viewModel, checkoutLauncher, scope, forQuickRestore, parentEventEmitter) {
    EntryCallbacks(activity, viewModel, checkoutLauncher, scope, forQuickRestore, parentEventEmitter)
  }

  RemoteBackupsSettingsContent(
    state = state,
    backupRestoreState = restoreState,
    backupDeleteState = deleteState,
    contentCallbacks = callbacks,
    backupProgress = backupProgress
  )
}

@Stable
private class EntryCallbacks(
  private val activity: FragmentActivity,
  private val viewModel: RemoteBackupsSettingsViewModel,
  private val checkoutLauncher: ActivityResultLauncher<MessageBackupTier?>,
  private val scope: CoroutineScope,
  private val forQuickRestore: Boolean,
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) : ContentCallbacks {

  override fun onNavigationClick() {
    parentEventEmitter.navigateBack()
  }

  override fun onBackupTypeActionClick(tier: MessageBackupTier) {
    when (tier) {
      MessageBackupTier.FREE -> checkoutLauncher.launch(MessageBackupTier.PAID)
      MessageBackupTier.PAID -> scope.launch { launchManageBackupsSubscription(activity) }
    }
  }

  override fun onLaunchBackupsCheckoutFlow() {
    checkoutLauncher.launch(null)
  }

  override fun onBackUpUsingCellularClick(canUseCellular: Boolean) {
    viewModel.setCanBackUpUsingCellular(canUseCellular)
  }

  override fun onBackupNowClick() {
    viewModel.onBackupNowClick(forQuickRestore)
  }

  override fun onTurnOffAndDeleteBackupsClick() {
    viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.TURN_OFF_AND_DELETE_BACKUPS)
  }

  override fun onBackupFrequencyClick() {
    viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.BACKUP_FREQUENCY)
  }

  override fun onDialogDismissed() {
    viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.NONE)
  }

  override fun onSnackbarDismissed() {
    viewModel.requestSnackbar(RemoteBackupsSettingsState.Snackbar.NONE)
  }

  override fun onTurnOffAndDeleteBackupsConfirm() {
    viewModel.turnOffAndDeleteBackups()
  }

  override fun onViewBackupKeyClick() {
    if (SignalStore.account.isPhoneNumberless) {
      parentEventEmitter.navigateTo(AppSettingsRoute.AccountRoute.SignalLoginDetails)
    } else {
      parentEventEmitter.navigateTo(AppSettingsRoute.BackupsRoute.DisplayKey())
    }
  }

  override fun onStartMediaRestore() {
    viewModel.beginMediaRestore()
  }

  override fun onCancelMediaRestore() {
    viewModel.cancelMediaRestore()
  }

  override fun onDisplaySkipMediaRestoreProtectionDialog() {
    viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.SKIP_MEDIA_RESTORE_PROTECTION)
  }

  override fun onSkipMediaRestore() {
    viewModel.skipMediaRestore()
  }

  override fun onLearnMoreAboutLostSubscription() {
    viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.SUBSCRIPTION_NOT_FOUND)
  }

  override fun onRenewLostSubscription() {
    checkoutLauncher.launch(MessageBackupTier.PAID)
  }

  override fun onCancelUploadClick() {
    viewModel.cancelUpload()
  }

  override fun onContactSupport() {
    activity.finish()
    activity.startActivity(AppSettingsActivity.help(activity, HelpFragment.REMOTE_BACKUPS_INDEX))
  }

  override fun onLearnMoreAboutBackupFailure() {
    BackupAlertBottomSheet.create(BackupAlert.BackupFailed).show(activity.supportFragmentManager, null)
  }

  override fun onRestoreUsingCellularConfirm() {
    viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.RESTORE_OVER_CELLULAR_PROTECTION)
  }

  override fun onRestoreUsingCellularClick() {
    viewModel.setCanRestoreUsingCellular()
  }

  override fun onRedemptionErrorDetailsClick() {
    BackupAlertBottomSheet.create(BackupAlert.CouldNotRedeemBackup).show(activity.supportFragmentManager, null)
  }

  override fun onDisplayProgressDialog() {
    viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.PROGRESS_SPINNER)
  }

  override fun onDisplayDownloadingBackupDialog() {
    viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.DOWNLOADING_YOUR_BACKUP)
  }

  override fun onManageStorageClick() {
    activity.finish()
    activity.startActivity(AppSettingsActivity.manageStorage(activity))
  }

  override fun onIncludeDebuglogClick(newState: Boolean) {
    viewModel.setIncludeDebuglog(newState)
  }

  override fun onMediaBackupSizeClick() {
    viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.FREE_TIER_MEDIA_EXPLAINER)
  }

  override fun onFreeTierBackupSizeLearnMore() {
    CommunicationActions.openBrowserLink(activity, "https://support.signal.org/hc/articles/9708267671322")
  }

  override fun onTransferScanQrCodeClick() {
    activity.startActivity(MediaSendLauncher.camera(activity))
  }
}

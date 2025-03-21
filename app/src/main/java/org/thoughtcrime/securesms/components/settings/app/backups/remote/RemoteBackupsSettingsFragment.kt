/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.bytes
import org.signal.core.util.gibiBytes
import org.signal.core.util.logging.Log
import org.signal.core.util.mebiBytes
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.BiometricDeviceAuthentication
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.ArchiveUploadProgress
import org.thoughtcrime.securesms.backup.v2.BackupFrequency
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.BackupAlert
import org.thoughtcrime.securesms.backup.v2.ui.BackupAlertBottomSheet
import org.thoughtcrime.securesms.backup.v2.ui.status.BackupStatusData
import org.thoughtcrime.securesms.backup.v2.ui.status.BackupStatusRow
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.billing.launchManageBackupsSubscription
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.MessageBackupsCheckoutLauncher.createBackupsCheckoutLauncher
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.fonts.SignalSymbols
import org.thoughtcrime.securesms.fonts.SignalSymbols.SignalSymbol
import org.thoughtcrime.securesms.help.HelpFragment
import org.thoughtcrime.securesms.keyvalue.protos.ArchiveUploadProgressState
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.ServiceUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.viewModel
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.signal.core.ui.R as CoreUiR

/**
 * Remote backups settings fragment.
 */
class RemoteBackupsSettingsFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(RemoteBackupsSettingsFragment::class)
    private const val AUTHENTICATE_REQUEST_CODE = 1
  }

  private val viewModel by viewModel {
    RemoteBackupsSettingsViewModel()
  }

  private val args: RemoteBackupsSettingsFragmentArgs by navArgs()

  private lateinit var checkoutLauncher: ActivityResultLauncher<MessageBackupTier?>
  private lateinit var biometricDeviceAuthentication: BiometricDeviceAuthentication

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsState()
    val backupProgress by ArchiveUploadProgress.progress.collectAsStateWithLifecycle(initialValue = null)
    val restoreState by viewModel.restoreState.collectAsState()
    val callbacks = remember { Callbacks() }

    RemoteBackupsSettingsContent(
      backupsEnabled = state.backupsEnabled,
      lastBackupTimestamp = state.lastBackupTimestamp,
      canBackUpUsingCellular = state.canBackUpUsingCellular,
      canRestoreUsingCellular = state.canRestoreUsingCellular,
      backupsFrequency = state.backupsFrequency,
      requestedDialog = state.dialog,
      requestedSnackbar = state.snackbar,
      contentCallbacks = callbacks,
      backupProgress = backupProgress,
      backupMediaSize = state.backupMediaSize,
      backupState = state.backupState,
      backupRestoreState = restoreState,
      hasRedemptionError = state.hasRedemptionError
    )
  }

  @Stable
  private inner class Callbacks : ContentCallbacks {
    override fun onNavigationClick() {
      findNavController().popBackStack()
    }

    override fun onBackupTypeActionClick(tier: MessageBackupTier) {
      when (tier) {
        MessageBackupTier.FREE -> checkoutLauncher.launch(MessageBackupTier.PAID)
        MessageBackupTier.PAID -> launchManageBackupsSubscription()
      }
    }

    override fun onLaunchBackupsCheckoutFlow() {
      checkoutLauncher.launch(null)
    }

    override fun onBackUpUsingCellularClick(canUseCellular: Boolean) {
      viewModel.setCanBackUpUsingCellular(canUseCellular)
    }

    override fun onBackupNowClick() {
      viewModel.onBackupNowClick()
    }

    override fun onTurnOffAndDeleteBackupsClick() {
      viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.TURN_OFF_AND_DELETE_BACKUPS)
    }

    override fun onChangeBackupFrequencyClick() {
      viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.BACKUP_FREQUENCY)
    }

    override fun onDialogDismissed() {
      viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.NONE)
    }

    override fun onSnackbarDismissed() {
      viewModel.requestSnackbar(RemoteBackupsSettingsState.Snackbar.NONE)
    }

    override fun onSelectBackupsFrequencyChange(newFrequency: BackupFrequency) {
      viewModel.setBackupsFrequency(newFrequency)
    }

    override fun onTurnOffAndDeleteBackupsConfirm() {
      viewModel.turnOffAndDeleteBackups()
    }

    override fun onViewBackupKeyClick() {
      if (!biometricDeviceAuthentication.authenticate(requireContext(), true, this@RemoteBackupsSettingsFragment::showConfirmDeviceCredentialIntent)) {
        displayBackupKey()
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
      // TODO - [backups] Need process here (cancel first?)
    }

    override fun onContactSupport() {
      requireActivity().finish()
      requireActivity().startActivity(AppSettingsActivity.help(requireContext(), HelpFragment.REMOTE_BACKUPS_INDEX))
    }

    override fun onLearnMoreAboutBackupFailure() {
      BackupAlertBottomSheet.create(BackupAlert.BackupFailed).show(parentFragmentManager, null)
    }

    override fun onRestoreUsingCellularClick(canUseCellular: Boolean) {
      viewModel.setCanRestoreUsingCellular(canUseCellular)
    }

    override fun onRedemptionErrorDetailsClick() {
      BackupAlertBottomSheet.create(BackupAlert.CouldNotRedeemBackup).show(parentFragmentManager, null)
    }
  }

  private fun displayBackupKey() {
    findNavController().safeNavigate(R.id.action_remoteBackupsSettingsFragment_to_backupKeyDisplayFragment)
  }

  private fun showConfirmDeviceCredentialIntent() {
    val keyguardManager = ServiceUtil.getKeyguardManager(requireContext())
    val intent = keyguardManager.createConfirmDeviceCredentialIntent(getString(R.string.RemoteBackupsSettingsFragment__unlock_to_view_backup_key), "")

    startActivityForResult(intent, AUTHENTICATE_REQUEST_CODE)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    checkoutLauncher = createBackupsCheckoutLauncher { backUpLater ->
      if (backUpLater) {
        viewModel.requestSnackbar(RemoteBackupsSettingsState.Snackbar.BACKUP_WILL_BE_CREATED_OVERNIGHT)
      }
    }

    if (savedInstanceState == null && args.backupLaterSelected) {
      viewModel.requestSnackbar(RemoteBackupsSettingsState.Snackbar.BACKUP_WILL_BE_CREATED_OVERNIGHT)
    }

    val biometricManager = BiometricManager.from(requireContext())
    val biometricPrompt = BiometricPrompt(this, AuthListener())
    val promptInfo: BiometricPrompt.PromptInfo = BiometricPrompt.PromptInfo.Builder()
      .setAllowedAuthenticators(BiometricDeviceAuthentication.ALLOWED_AUTHENTICATORS)
      .setTitle(getString(R.string.RemoteBackupsSettingsFragment__unlock_to_view_backup_key))
      .build()

    biometricDeviceAuthentication = BiometricDeviceAuthentication(biometricManager, biometricPrompt, promptInfo)
  }

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  private inner class AuthListener : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationFailed() {
      Log.w(TAG, "onAuthenticationFailed")
      Toast.makeText(requireContext(), R.string.RemoteBackupsSettingsFragment__authenticatino_required, Toast.LENGTH_SHORT).show()
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
      Log.i(TAG, "onAuthenticationSucceeded")
      displayBackupKey()
    }

    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
      Log.w(TAG, "onAuthenticationError: $errorCode, $errString")
      onAuthenticationFailed()
    }
  }
}

/**
 * Callback interface for RemoteBackupsSettingsContent composable.
 */
private interface ContentCallbacks {
  fun onNavigationClick() = Unit
  fun onLaunchBackupsCheckoutFlow() = Unit
  fun onBackupTypeActionClick(tier: MessageBackupTier) = Unit
  fun onBackUpUsingCellularClick(canUseCellular: Boolean) = Unit
  fun onBackupNowClick() = Unit
  fun onTurnOffAndDeleteBackupsClick() = Unit
  fun onChangeBackupFrequencyClick() = Unit
  fun onDialogDismissed() = Unit
  fun onSnackbarDismissed() = Unit
  fun onSelectBackupsFrequencyChange(newFrequency: BackupFrequency) = Unit
  fun onTurnOffAndDeleteBackupsConfirm() = Unit
  fun onViewBackupKeyClick() = Unit
  fun onStartMediaRestore() = Unit
  fun onDisplaySkipMediaRestoreProtectionDialog() = Unit
  fun onSkipMediaRestore() = Unit
  fun onCancelMediaRestore() = Unit
  fun onRenewLostSubscription() = Unit
  fun onLearnMoreAboutLostSubscription() = Unit
  fun onContactSupport() = Unit
  fun onLearnMoreAboutBackupFailure() = Unit
  fun onRestoreUsingCellularClick(canUseCellular: Boolean) = Unit
  fun onRedemptionErrorDetailsClick() = Unit
}

@Composable
private fun RemoteBackupsSettingsContent(
  backupsEnabled: Boolean,
  backupState: RemoteBackupsSettingsState.BackupState,
  backupRestoreState: BackupRestoreState,
  lastBackupTimestamp: Long,
  canBackUpUsingCellular: Boolean,
  canRestoreUsingCellular: Boolean,
  backupsFrequency: BackupFrequency,
  requestedDialog: RemoteBackupsSettingsState.Dialog,
  requestedSnackbar: RemoteBackupsSettingsState.Snackbar,
  contentCallbacks: ContentCallbacks,
  backupProgress: ArchiveUploadProgressState?,
  backupMediaSize: Long,
  hasRedemptionError: Boolean
) {
  val snackbarHostState = remember {
    SnackbarHostState()
  }

  Scaffolds.Settings(
    title = stringResource(id = R.string.RemoteBackupsSettingsFragment__signal_backups),
    onNavigationClick = contentCallbacks::onNavigationClick,
    navigationIconPainter = painterResource(id = R.drawable.symbol_arrow_left_24),
    snackbarHost = {
      Snackbars.Host(snackbarHostState = snackbarHostState)
    }
  ) {
    LazyColumn(
      modifier = Modifier
        .padding(it)
    ) {
      if (hasRedemptionError) {
        item {
          RedemptionErrorAlert(onDetailsClick = contentCallbacks::onRedemptionErrorDetailsClick)
        }
      }

      item {
        when (backupState) {
          is RemoteBackupsSettingsState.BackupState.Loading -> {
            LoadingCard()
          }

          is RemoteBackupsSettingsState.BackupState.Error -> {
            ErrorCard()
          }

          is RemoteBackupsSettingsState.BackupState.Pending -> {
            PendingCard(backupState.price)
          }

          is RemoteBackupsSettingsState.BackupState.SubscriptionMismatchMissingGooglePlay -> {
            SubscriptionMismatchMissingGooglePlayCard(
              state = backupState,
              onLearnMoreClick = contentCallbacks::onLearnMoreAboutLostSubscription,
              onRenewClick = contentCallbacks::onRenewLostSubscription
            )
          }

          RemoteBackupsSettingsState.BackupState.None -> Unit

          is RemoteBackupsSettingsState.BackupState.WithTypeAndRenewalTime -> {
            BackupCard(
              backupState = backupState,
              onBackupTypeActionButtonClicked = contentCallbacks::onBackupTypeActionClick
            )
          }
        }
      }

      if (backupsEnabled) {
        if (backupRestoreState !is BackupRestoreState.None) {
          item {
            Dividers.Default()
          }

          if (backupRestoreState is BackupRestoreState.FromBackupStatusData) {
            item {
              BackupStatusRow(
                backupStatusData = backupRestoreState.backupStatusData,
                onCancelClick = contentCallbacks::onCancelMediaRestore,
                onSkipClick = contentCallbacks::onSkipMediaRestore,
                onLearnMoreClick = contentCallbacks::onLearnMoreAboutBackupFailure
              )
            }

            item {
              Rows.ToggleRow(
                checked = canRestoreUsingCellular,
                text = stringResource(id = R.string.RemoteBackupsSettingsFragment__restore_using_cellular),
                onCheckChanged = contentCallbacks::onRestoreUsingCellularClick
              )
            }
          } else if (backupRestoreState is BackupRestoreState.Ready && backupState is RemoteBackupsSettingsState.BackupState.Canceled) {
            item {
              BackupReadyToDownloadRow(
                ready = backupRestoreState,
                endOfSubscription = backupState.renewalTime,
                onDownloadClick = contentCallbacks::onStartMediaRestore
              )
            }
          }
        }

        appendBackupDetailsItems(
          backupState = backupState,
          backupProgress = backupProgress,
          lastBackupTimestamp = lastBackupTimestamp,
          backupMediaSize = backupMediaSize,
          backupsFrequency = backupsFrequency,
          canBackUpUsingCellular = canBackUpUsingCellular,
          canRestoreUsingCellular = canRestoreUsingCellular,
          contentCallbacks = contentCallbacks
        )
      } else {
        if (backupRestoreState is BackupRestoreState.FromBackupStatusData) {
          item {
            BackupStatusRow(
              backupStatusData = backupRestoreState.backupStatusData,
              onCancelClick = contentCallbacks::onCancelMediaRestore,
              onSkipClick = contentCallbacks::onSkipMediaRestore,
              onLearnMoreClick = contentCallbacks::onLearnMoreAboutBackupFailure
            )
          }
        }

        item {
          Text(
            text = stringResource(R.string.RemoteBackupsSettingsFragment__backups_have_been_turned_off),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
              .padding(top = 24.dp, bottom = 20.dp)
              .horizontalGutters()
          )
        }

        item {
          Buttons.LargePrimary(
            onClick = { contentCallbacks.onBackupTypeActionClick(MessageBackupTier.FREE) },
            modifier = Modifier.horizontalGutters()
          ) {
            Text(text = stringResource(R.string.RemoteBackupsSettingsFragment__reenable_backups))
          }
        }
      }
    }
  }

  when (requestedDialog) {
    RemoteBackupsSettingsState.Dialog.NONE -> {}
    RemoteBackupsSettingsState.Dialog.TURN_OFF_FAILED -> {
      FailedToTurnOffBackupDialog(
        onDismiss = contentCallbacks::onDialogDismissed
      )
    }

    RemoteBackupsSettingsState.Dialog.TURN_OFF_AND_DELETE_BACKUPS -> {
      TurnOffAndDeleteBackupsDialog(
        onConfirm = contentCallbacks::onTurnOffAndDeleteBackupsConfirm,
        onDismiss = contentCallbacks::onDialogDismissed
      )
    }

    RemoteBackupsSettingsState.Dialog.BACKUP_FREQUENCY -> {
      BackupFrequencyDialog(
        selected = backupsFrequency,
        onSelected = contentCallbacks::onSelectBackupsFrequencyChange,
        onDismiss = contentCallbacks::onDialogDismissed
      )
    }

    RemoteBackupsSettingsState.Dialog.PROGRESS_SPINNER -> {
      CircularProgressDialog(onDismiss = contentCallbacks::onDialogDismissed)
    }

    RemoteBackupsSettingsState.Dialog.DOWNLOADING_YOUR_BACKUP -> {
      DownloadingYourBackupDialog(onDismiss = contentCallbacks::onDialogDismissed)
    }

    RemoteBackupsSettingsState.Dialog.SUBSCRIPTION_NOT_FOUND -> {
      SubscriptionNotFoundBottomSheet(
        onDismiss = contentCallbacks::onDialogDismissed,
        onContactSupport = contentCallbacks::onContactSupport
      )
    }

    RemoteBackupsSettingsState.Dialog.SKIP_MEDIA_RESTORE_PROTECTION -> {
      SkipDownloadDialog(
        renewalTime = if (backupState is RemoteBackupsSettingsState.BackupState.WithTypeAndRenewalTime) {
          backupState.renewalTime
        } else {
          error("Unexpected dialog display without renewal time.")
        },
        onDismiss = contentCallbacks::onDialogDismissed,
        onSkipClick = contentCallbacks::onSkipMediaRestore
      )
    }
  }

  val snackbarMessageId = remember(requestedSnackbar) {
    when (requestedSnackbar) {
      RemoteBackupsSettingsState.Snackbar.NONE -> -1
      RemoteBackupsSettingsState.Snackbar.BACKUP_DELETED_AND_TURNED_OFF -> R.string.RemoteBackupsSettingsFragment__backup_deleted_and_turned_off
      RemoteBackupsSettingsState.Snackbar.BACKUP_TYPE_CHANGED_AND_SUBSCRIPTION_CANCELLED -> R.string.RemoteBackupsSettingsFragment__backup_type_changed_and_subcription_deleted
      RemoteBackupsSettingsState.Snackbar.SUBSCRIPTION_CANCELLED -> R.string.RemoteBackupsSettingsFragment__subscription_cancelled
      RemoteBackupsSettingsState.Snackbar.DOWNLOAD_COMPLETE -> R.string.RemoteBackupsSettingsFragment__download_complete
      RemoteBackupsSettingsState.Snackbar.BACKUP_WILL_BE_CREATED_OVERNIGHT -> R.string.RemoteBackupsSettingsFragment__backup_will_be_created_overnight
    }
  }

  val snackbarText = if (snackbarMessageId == -1) "" else stringResource(id = snackbarMessageId)

  LaunchedEffect(requestedSnackbar) {
    when (requestedSnackbar) {
      RemoteBackupsSettingsState.Snackbar.NONE -> {
        snackbarHostState.currentSnackbarData?.dismiss()
      }

      else -> {
        snackbarHostState.showSnackbar(snackbarText)
        contentCallbacks.onSnackbarDismissed()
      }
    }
  }
}

private fun LazyListScope.appendBackupDetailsItems(
  backupState: RemoteBackupsSettingsState.BackupState,
  backupProgress: ArchiveUploadProgressState?,
  lastBackupTimestamp: Long,
  backupMediaSize: Long,
  backupsFrequency: BackupFrequency,
  canBackUpUsingCellular: Boolean,
  canRestoreUsingCellular: Boolean,
  contentCallbacks: ContentCallbacks
) {
  item {
    Dividers.Default()
  }

  item {
    Texts.SectionHeader(text = stringResource(id = R.string.RemoteBackupsSettingsFragment__backup_details))
  }

  if (backupProgress == null || backupProgress.state == ArchiveUploadProgressState.State.None) {
    item {
      LastBackupRow(
        lastBackupTimestamp = lastBackupTimestamp,
        onBackupNowClick = contentCallbacks::onBackupNowClick
      )
    }
  } else {
    item {
      InProgressBackupRow(archiveUploadProgressState = backupProgress)
    }
  }

  if (backupState !is RemoteBackupsSettingsState.BackupState.ActiveFree) {
    item {
      Rows.TextRow(text = {
        Column {
          Text(
            text = stringResource(id = R.string.RemoteBackupsSettingsFragment__backup_size),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = backupMediaSize.bytes.toUnitString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      })
    }
  }

  item {
    Rows.TextRow(
      text = {
        Column {
          Text(
            text = stringResource(id = R.string.RemoteBackupsSettingsFragment__backup_frequency),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = getTextForFrequency(backupsFrequency = backupsFrequency),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      },
      onClick = contentCallbacks::onChangeBackupFrequencyClick
    )
  }

  item {
    Rows.ToggleRow(
      checked = canBackUpUsingCellular,
      text = stringResource(id = R.string.RemoteBackupsSettingsFragment__back_up_using_cellular),
      onCheckChanged = contentCallbacks::onBackUpUsingCellularClick
    )
  }

  item {
    Rows.TextRow(
      text = stringResource(R.string.RemoteBackupsSettingsFragment__view_backup_key),
      onClick = contentCallbacks::onViewBackupKeyClick
    )
  }

  item {
    Dividers.Default()
  }

  item {
    Rows.TextRow(
      text = stringResource(id = R.string.RemoteBackupsSettingsFragment__turn_off_and_delete_backup),
      foregroundTint = MaterialTheme.colorScheme.error,
      onClick = contentCallbacks::onTurnOffAndDeleteBackupsClick
    )
  }
}

@Composable
private fun BackupCard(
  backupState: RemoteBackupsSettingsState.BackupState.WithTypeAndRenewalTime,
  onBackupTypeActionButtonClicked: (MessageBackupTier) -> Unit = {}
) {
  val messageBackupsType = backupState.messageBackupsType

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 12.dp)
      .background(color = SignalTheme.colors.colorSurface2, shape = RoundedCornerShape(12.dp))
      .padding(24.dp)
  ) {
    Row(modifier = Modifier.fillMaxWidth()) {
      Column {
        val title = when (messageBackupsType) {
          is MessageBackupsType.Paid -> stringResource(R.string.MessageBackupsTypeSelectionScreen__text_plus_all_your_media)
          is MessageBackupsType.Free -> pluralStringResource(R.plurals.MessageBackupsTypeSelectionScreen__text_plus_d_days_of_media, messageBackupsType.mediaRetentionDays, messageBackupsType.mediaRetentionDays)
        }

        Text(
          text = buildAnnotatedString {
            if (backupState.isActive()) {
              SignalSymbol(SignalSymbols.Weight.REGULAR, SignalSymbols.Glyph.CHECKMARK)
              append(" ")
            }

            append(title)
          },
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium
        )

        when (backupState) {
          is RemoteBackupsSettingsState.BackupState.ActivePaid -> {
            Text(
              text = stringResource(R.string.RemoteBackupsSettingsFragment__s_per_month, FiatMoneyUtil.format(LocalContext.current.resources, backupState.price)),
              modifier = Modifier.padding(top = 12.dp)
            )
          }

          is RemoteBackupsSettingsState.BackupState.ActiveFree -> {
            Text(
              text = stringResource(R.string.RemoteBackupsSettingsFragment__your_backup_plan_is_free),
              modifier = Modifier.padding(top = 12.dp)
            )
          }

          is RemoteBackupsSettingsState.BackupState.Inactive -> {
            val text = when (messageBackupsType) {
              is MessageBackupsType.Paid -> stringResource(R.string.RemoteBackupsSettingsFragment__subscription_inactive)
              is MessageBackupsType.Free -> stringResource(R.string.RemoteBackupsSettingsFragment__you_turned_off_backups)
            }

            Text(
              text = text,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(top = 8.dp)
            )
          }

          is RemoteBackupsSettingsState.BackupState.Canceled -> {
            Text(
              text = stringResource(R.string.RemoteBackupsSettingsFragment__subscription_cancelled),
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.labelLarge,
              modifier = Modifier.padding(top = 8.dp)
            )
          }

          else -> error("Not supported here: $backupState")
        }

        if (messageBackupsType is MessageBackupsType.Paid) {
          val resource = when (backupState) {
            is RemoteBackupsSettingsState.BackupState.ActivePaid -> R.string.RemoteBackupsSettingsFragment__renews_s
            is RemoteBackupsSettingsState.BackupState.Inactive -> R.string.RemoteBackupsSettingsFragment__expired_on_s
            is RemoteBackupsSettingsState.BackupState.Canceled -> R.string.RemoteBackupsSettingsFragment__expires_on_s
            else -> error("Not supported here.")
          }

          if (backupState.renewalTime > 0.seconds) {
            Text(
              text = stringResource(resource, DateUtils.formatDateWithYear(Locale.getDefault(), backupState.renewalTime.inWholeMilliseconds))
            )
          }
        }
      }

      Spacer(modifier = Modifier.weight(1f))

      Image(
        painter = painterResource(R.drawable.image_signal_backups),
        contentDescription = null,
        modifier = Modifier.size(64.dp)
      )
    }

    val buttonText = when (messageBackupsType) {
      is MessageBackupsType.Paid -> stringResource(R.string.RemoteBackupsSettingsFragment__manage_or_cancel)
      is MessageBackupsType.Free -> stringResource(R.string.RemoteBackupsSettingsFragment__upgrade)
    }

    if (backupState.isActive()) {
      Buttons.LargeTonal(
        onClick = { onBackupTypeActionButtonClicked(messageBackupsType.tier) },
        colors = ButtonDefaults.filledTonalButtonColors().copy(
          containerColor = SignalTheme.colors.colorTransparent5,
          contentColor = colorResource(R.color.signal_light_colorOnSurface)
        ),
        modifier = Modifier.padding(top = 12.dp)
      ) {
        Text(
          text = buttonText
        )
      }
    }
  }
}

@Composable
private fun RedemptionErrorAlert(
  onDetailsClick: () -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .padding(horizontal = 16.dp)
      .padding(top = 8.dp, bottom = 4.dp)
      .border(
        width = 1.dp,
        color = colorResource(R.color.signal_colorOutline_38),
        shape = RoundedCornerShape(12.dp)
      )
      .padding(vertical = 16.dp)
      .padding(start = 16.dp, end = 12.dp)
  ) {
    Icon(
      painter = painterResource(R.drawable.symbol_backup_error_24),
      tint = Color(0xFFFF9500),
      contentDescription = null
    )

    Text(
      text = stringResource(R.string.AppSettingsFragment__couldnt_redeem_your_backups_subscription),
      modifier = Modifier.padding(start = 16.dp, end = 4.dp).weight(1f)
    )

    Buttons.Small(onClick = onDetailsClick) {
      Text(
        text = stringResource(R.string.RemoteBackupsSettingsFragment__details)
      )
    }
  }
}

@Composable
private fun BoxCard(content: @Composable () -> Unit) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .fillMaxWidth()
      .defaultMinSize(minHeight = 150.dp)
      .padding(horizontal = 16.dp, vertical = 12.dp)
      .background(color = SignalTheme.colors.colorSurface2, shape = RoundedCornerShape(12.dp))
      .padding(24.dp)
  ) {
    content()
  }
}

@Composable
private fun LoadingCard() {
  BoxCard {
    CircularProgressIndicator()
  }
}

@Composable
private fun ErrorCard() {
  BoxCard {
    Column {
      CircularProgressIndicator(
        strokeWidth = 3.5.dp,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.size(32.dp)
      )

      Text(text = stringResource(R.string.RemoteBackupsSettingsFragment__waiting_for_network))
    }
  }
}

@Composable
private fun PendingCard(
  price: FiatMoney
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 12.dp)
      .background(color = SignalTheme.colors.colorSurface2, shape = RoundedCornerShape(12.dp))
      .padding(24.dp)
  ) {
    Text(
      text = stringResource(R.string.MessageBackupsTypeSelectionScreen__text_plus_all_your_media),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodyMedium
    )

    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(top = 12.dp)
    ) {
      Column(
        modifier = Modifier.weight(1f)
      ) {
        Text(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__s_per_month, FiatMoneyUtil.format(LocalContext.current.resources, price))
        )
        Text(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__payment_pending)
        )
      }

      CircularProgressIndicator(
        strokeWidth = 3.5.dp,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.size(32.dp)
      )
    }
  }
}

@Composable
private fun SubscriptionMismatchMissingGooglePlayCard(
  state: RemoteBackupsSettingsState.BackupState.SubscriptionMismatchMissingGooglePlay,
  onRenewClick: () -> Unit = {},
  onLearnMoreClick: () -> Unit = {}
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 12.dp)
      .background(color = SignalTheme.colors.colorSurface2, shape = RoundedCornerShape(12.dp))
      .padding(24.dp)
  ) {
    val days by rememberUpdatedState((state.renewalTime - System.currentTimeMillis().milliseconds).inWholeDays)

    Row {
      Text(
        text = pluralStringResource(R.plurals.RemoteBackupsSettingsFragment__your_subscription_on_this_device_is_valid, days.toInt(), days),
        modifier = Modifier
          .weight(1f)
          .padding(end = 13.dp)
      )

      Box {
        Image(
          painter = painterResource(R.drawable.image_signal_backups),
          contentDescription = null,
          modifier = Modifier.size(64.dp)
        )

        Box(
          modifier = Modifier
            .size(22.dp)
            .background(
              color = Color(0xFFFFCC00),
              shape = CircleShape
            )
            .border(5.dp, color = SignalTheme.colors.colorSurface2, shape = CircleShape)
            .align(Alignment.TopEnd)
        )
      }
    }

    Row(
      horizontalArrangement = spacedBy(16.dp)
    ) {
      Buttons.LargeTonal(
        onClick = onRenewClick,
        colors = ButtonDefaults.filledTonalButtonColors().copy(
          containerColor = SignalTheme.colors.colorTransparent5,
          contentColor = colorResource(R.color.signal_light_colorOnSurface)
        ),
        modifier = Modifier
          .padding(top = 24.dp)
          .weight(1f)
      ) {
        Text(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__renew)
        )
      }

      Buttons.LargeTonal(
        onClick = onLearnMoreClick,
        colors = ButtonDefaults.filledTonalButtonColors().copy(
          containerColor = SignalTheme.colors.colorTransparent5,
          contentColor = colorResource(R.color.signal_light_colorOnSurface)
        ),
        modifier = Modifier
          .padding(top = 24.dp)
          .weight(1f)
      ) {
        Text(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__learn_more)
        )
      }
    }
  }
}

@Composable
private fun InProgressBackupRow(
  archiveUploadProgressState: ArchiveUploadProgressState
) {
  Row(
    modifier = Modifier
      .padding(horizontal = dimensionResource(id = CoreUiR.dimen.gutter))
      .padding(top = 16.dp, bottom = 14.dp)
  ) {
    Column(
      modifier = Modifier.weight(1f)
    ) {
      when (archiveUploadProgressState.state) {
        ArchiveUploadProgressState.State.None -> {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        ArchiveUploadProgressState.State.Export -> {
          val progressValue by animateFloatAsState(targetValue = archiveUploadProgressState.frameExportProgress(), animationSpec = tween(durationMillis = 250))
          LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            progress = { progressValue },
            drawStopIndicator = {}
          )
        }
        ArchiveUploadProgressState.State.UploadBackupFile, ArchiveUploadProgressState.State.UploadMedia -> {
          val progressValue by animateFloatAsState(targetValue = archiveUploadProgressState.uploadProgress(), animationSpec = tween(durationMillis = 250))
          LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            progress = { progressValue },
            drawStopIndicator = {}
          )
        }
      }

      Text(
        text = getProgressStateMessage(archiveUploadProgressState),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun getProgressStateMessage(archiveUploadProgressState: ArchiveUploadProgressState): String {
  return when (archiveUploadProgressState.state) {
    ArchiveUploadProgressState.State.None -> stringResource(R.string.RemoteBackupsSettingsFragment__processing_backup)
    ArchiveUploadProgressState.State.Export -> getBackupExportPhaseProgressString(archiveUploadProgressState)
    ArchiveUploadProgressState.State.UploadBackupFile, ArchiveUploadProgressState.State.UploadMedia -> getBackupUploadPhaseProgressString(archiveUploadProgressState)
  }
}

@Composable
private fun getBackupExportPhaseProgressString(state: ArchiveUploadProgressState): String {
  return when (state.backupPhase) {
    ArchiveUploadProgressState.BackupPhase.BackupPhaseNone -> stringResource(R.string.RemoteBackupsSettingsFragment__processing_backup)
    ArchiveUploadProgressState.BackupPhase.Message -> {
      pluralStringResource(
        R.plurals.RemoteBackupsSettingsFragment__processing_messages_progress_text,
        state.frameTotalCount.toInt(),
        "%,d".format(state.frameExportCount),
        "%,d".format(state.frameTotalCount),
        (state.frameExportProgress() * 100).toInt()
      )
    }

    else -> stringResource(R.string.RemoteBackupsSettingsFragment__preparing_backup)
  }
}

@Composable
private fun getBackupUploadPhaseProgressString(state: ArchiveUploadProgressState): String {
  val formattedTotalBytes = state.uploadBytesTotal.bytes.toUnitString()
  val formattedUploadedBytes = state.uploadBytesUploaded.bytes.toUnitString()
  val percent = (state.uploadProgress() * 100).toInt()

  return stringResource(R.string.RemoteBackupsSettingsFragment__uploading_s_of_s_d, formattedUploadedBytes, formattedTotalBytes, percent)
}

@Composable
private fun LastBackupRow(
  lastBackupTimestamp: Long,
  onBackupNowClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .padding(horizontal = dimensionResource(id = CoreUiR.dimen.gutter))
      .padding(top = 16.dp, bottom = 14.dp)
  ) {
    Column(
      modifier = Modifier.weight(1f)
    ) {
      Text(
        text = stringResource(id = R.string.RemoteBackupsSettingsFragment__last_backup),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
      )

      if (lastBackupTimestamp > 0) {
        val context = LocalContext.current

        val day = remember(lastBackupTimestamp) {
          DateUtils.getDayPrecisionTimeString(context, Locale.getDefault(), lastBackupTimestamp)
        }

        val time = remember(lastBackupTimestamp) {
          DateUtils.getOnlyTimeString(context, lastBackupTimestamp)
        }

        Text(
          text = stringResource(id = R.string.RemoteBackupsSettingsFragment__s_at_s, day, time),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        Text(
          text = stringResource(id = R.string.RemoteBackupsSettingsFragment__never),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    Buttons.Small(onClick = onBackupNowClick) {
      Text(text = stringResource(id = R.string.RemoteBackupsSettingsFragment__back_up_now))
    }
  }
}

@Composable
private fun FailedToTurnOffBackupDialog(
  onDismiss: () -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.RemoteBackupsSettingsFragment__couldnt_turn_off_and_delete_backups),
    body = stringResource(R.string.RemoteBackupsSettingsFragment__a_network_error_occurred),
    confirm = stringResource(id = android.R.string.ok),
    onConfirm = {},
    onDismiss = onDismiss
  )
}

@Composable
private fun TurnOffAndDeleteBackupsDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(id = R.string.RemoteBackupsSettingsFragment__turn_off_and_delete_backups),
    body = stringResource(id = R.string.RemoteBackupsSettingsFragment__you_will_not_be_charged_again),
    confirm = stringResource(id = R.string.RemoteBackupsSettingsFragment__turn_off_and_delete),
    dismiss = stringResource(id = android.R.string.cancel),
    confirmColor = MaterialTheme.colorScheme.error,
    onConfirm = onConfirm,
    onDismiss = onDismiss
  )
}

@Composable
private fun DownloadingYourBackupDialog(
  onDismiss: () -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.RemoteBackupsSettingsFragment__downloading_your_backup),
    body = stringResource(R.string.RemoteBackupsSettingsFragment__depending_on_the_size),
    confirm = stringResource(android.R.string.ok),
    onConfirm = {},
    onDismiss = onDismiss
  )
}

@Composable
private fun SkipDownloadDialog(
  renewalTime: Duration,
  onSkipClick: () -> Unit = {},
  onDismiss: () -> Unit = {}
) {
  val days = (renewalTime - System.currentTimeMillis().milliseconds).inWholeDays.toInt()

  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.RemoteBackupsSettingsFragment__skip_download_question),
    body = pluralStringResource(R.plurals.RemoteBackupsSettingsFragment__if_you_skip_downloading, days, days),
    confirm = stringResource(R.string.RemoteBackupsSettingsFragment__skip),
    dismiss = stringResource(android.R.string.cancel),
    confirmColor = MaterialTheme.colorScheme.error,
    onConfirm = onSkipClick,
    onDismiss = onDismiss
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CircularProgressDialog(
  onDismiss: () -> Unit
) {
  BasicAlertDialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      dismissOnBackPress = false,
      dismissOnClickOutside = false
    )
  ) {
    Surface(
      shape = AlertDialogDefaults.shape,
      color = AlertDialogDefaults.containerColor
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.aspectRatio(1f)
      ) {
        CircularProgressIndicator(
          modifier = Modifier
            .size(48.dp)
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupFrequencyDialog(
  selected: BackupFrequency,
  onSelected: (BackupFrequency) -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss
  ) {
    Surface {
      Column(
        modifier = Modifier
          .background(
            color = AlertDialogDefaults.containerColor,
            shape = AlertDialogDefaults.shape
          )
          .fillMaxWidth()
      ) {
        Text(
          text = stringResource(id = R.string.RemoteBackupsSettingsFragment__backup_frequency),
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier.padding(24.dp)
        )

        BackupFrequency.entries.forEach {
          Rows.RadioRow(
            selected = selected == it,
            text = getTextForFrequency(backupsFrequency = it),
            label = when (it) {
              BackupFrequency.MANUAL -> stringResource(id = R.string.RemoteBackupsSettingsFragment__by_tapping_back_up_now)
              else -> null
            },
            modifier = Modifier
              .padding(end = 24.dp)
              .clickable(onClick = {
                onSelected(it)
                onDismiss()
              })
          )
        }

        Box(
          contentAlignment = Alignment.CenterEnd,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
        ) {
          TextButton(onClick = onDismiss) {
            Text(text = stringResource(id = android.R.string.cancel))
          }
        }
      }
    }
  }
}

@Composable
private fun BackupReadyToDownloadRow(
  ready: BackupRestoreState.Ready,
  endOfSubscription: Duration,
  onDownloadClick: () -> Unit = {}
) {
  val days = (endOfSubscription - System.currentTimeMillis().milliseconds).inWholeDays.toInt()
  val string = pluralStringResource(R.plurals.RemoteBackupsSettingsFragment__you_have_s_of_backup_data, days, ready.bytes, days)
  val annotated = buildAnnotatedString {
    append(string)
    val startIndex = string.indexOf(ready.bytes)
    val endIndex = startIndex + ready.bytes.length

    addStyle(SpanStyle(fontWeight = FontWeight.Bold), startIndex, endIndex)
  }

  Column {
    Text(
      text = annotated,
      modifier = Modifier
        .horizontalGutters()
        .padding(vertical = 8.dp)
    )

    Rows.TextRow(
      text = stringResource(R.string.RemoteBackupsSettingsFragment__download),
      icon = painterResource(R.drawable.symbol_arrow_circle_down_24),
      onClick = onDownloadClick
    )
  }
}

@Composable
private fun getTextForFrequency(backupsFrequency: BackupFrequency): String {
  return when (backupsFrequency) {
    BackupFrequency.DAILY -> stringResource(id = R.string.RemoteBackupsSettingsFragment__daily)
    BackupFrequency.WEEKLY -> stringResource(id = R.string.RemoteBackupsSettingsFragment__weekly)
    BackupFrequency.MONTHLY -> stringResource(id = R.string.RemoteBackupsSettingsFragment__monthly)
    BackupFrequency.MANUAL -> stringResource(id = R.string.RemoteBackupsSettingsFragment__manually_back_up)
  }
}

@SignalPreview
@Composable
private fun RemoteBackupsSettingsContentPreview() {
  Previews.Preview {
    RemoteBackupsSettingsContent(
      backupsEnabled = true,
      lastBackupTimestamp = -1,
      canBackUpUsingCellular = false,
      canRestoreUsingCellular = false,
      backupsFrequency = BackupFrequency.MANUAL,
      requestedDialog = RemoteBackupsSettingsState.Dialog.NONE,
      requestedSnackbar = RemoteBackupsSettingsState.Snackbar.NONE,
      contentCallbacks = object : ContentCallbacks {},
      backupProgress = null,
      backupMediaSize = 2300000,
      backupState = RemoteBackupsSettingsState.BackupState.ActiveFree(
        messageBackupsType = MessageBackupsType.Free(mediaRetentionDays = 30)
      ),
      backupRestoreState = BackupRestoreState.FromBackupStatusData(BackupStatusData.CouldNotCompleteBackup),
      hasRedemptionError = true
    )
  }
}

@SignalPreview
@Composable
private fun RedemptionErrorAlertPreview() {
  Previews.Preview {
    RedemptionErrorAlert { }
  }
}

@SignalPreview
@Composable
private fun LoadingCardPreview() {
  Previews.Preview {
    LoadingCard()
  }
}

@SignalPreview
@Composable
private fun ErrorCardPreview() {
  Previews.Preview {
    ErrorCard()
  }
}

@SignalPreview
@Composable
private fun PendingCardPreview() {
  Previews.Preview {
    PendingCard(
      price = FiatMoney(BigDecimal.TEN, Currency.getInstance(Locale.getDefault()))
    )
  }
}

@SignalPreview
@Composable
private fun SubscriptionMismatchMissingGooglePlayCardPreview() {
  Previews.Preview {
    SubscriptionMismatchMissingGooglePlayCard(
      state = RemoteBackupsSettingsState.BackupState.SubscriptionMismatchMissingGooglePlay(
        messageBackupsType = MessageBackupsType.Paid(
          pricePerMonth = FiatMoney(BigDecimal.valueOf(3), Currency.getInstance("CAD")),
          storageAllowanceBytes = 100_000_000,
          mediaTtl = 30.days
        ),
        renewalTime = System.currentTimeMillis().milliseconds + 30.days
      )
    )
  }
}

@SignalPreview
@Composable
private fun BackupCardPreview() {
  Previews.Preview {
    Column {
      BackupCard(
        backupState = RemoteBackupsSettingsState.BackupState.ActivePaid(
          messageBackupsType = MessageBackupsType.Paid(
            pricePerMonth = FiatMoney(BigDecimal.valueOf(3), Currency.getInstance("CAD")),
            storageAllowanceBytes = 100_000_000,
            mediaTtl = 30.days
          ),
          renewalTime = 1727193018.seconds,
          price = FiatMoney(BigDecimal.valueOf(3), Currency.getInstance("CAD"))
        )
      )

      BackupCard(
        backupState = RemoteBackupsSettingsState.BackupState.Canceled(
          messageBackupsType = MessageBackupsType.Paid(
            pricePerMonth = FiatMoney(BigDecimal.valueOf(3), Currency.getInstance("CAD")),
            storageAllowanceBytes = 100_000_000,
            mediaTtl = 30.days
          ),
          renewalTime = 1727193018.seconds
        )
      )

      BackupCard(
        backupState = RemoteBackupsSettingsState.BackupState.Inactive(
          messageBackupsType = MessageBackupsType.Paid(
            pricePerMonth = FiatMoney(BigDecimal.valueOf(3), Currency.getInstance("CAD")),
            storageAllowanceBytes = 100_000_000,
            mediaTtl = 30.days
          ),
          renewalTime = 1727193018.seconds
        )
      )

      BackupCard(
        backupState = RemoteBackupsSettingsState.BackupState.ActivePaid(
          messageBackupsType = MessageBackupsType.Paid(
            pricePerMonth = FiatMoney(BigDecimal.valueOf(3), Currency.getInstance("CAD")),
            storageAllowanceBytes = 100_000_000,
            mediaTtl = 30.days
          ),
          renewalTime = 1727193018.seconds,
          price = FiatMoney(BigDecimal.valueOf(3), Currency.getInstance("CAD"))
        )
      )

      BackupCard(
        backupState = RemoteBackupsSettingsState.BackupState.ActiveFree(
          messageBackupsType = MessageBackupsType.Free(
            mediaRetentionDays = 30
          )
        )
      )
    }
  }
}

@SignalPreview
@Composable
private fun BackupReadyToDownloadPreview() {
  Previews.Preview {
    BackupReadyToDownloadRow(
      ready = BackupRestoreState.Ready("12GB"),
      endOfSubscription = System.currentTimeMillis().milliseconds + 30.days
    )
  }
}

@SignalPreview
@Composable
private fun LastBackupRowPreview() {
  Previews.Preview {
    LastBackupRow(
      lastBackupTimestamp = -1,
      onBackupNowClick = {}
    )
  }
}

@SignalPreview
@Composable
private fun InProgressRowPreview() {
  Previews.Preview {
    Column {
      InProgressBackupRow(archiveUploadProgressState = ArchiveUploadProgressState())
      InProgressBackupRow(
        archiveUploadProgressState = ArchiveUploadProgressState(
          state = ArchiveUploadProgressState.State.Export,
          backupPhase = ArchiveUploadProgressState.BackupPhase.BackupPhaseNone
        )
      )
      InProgressBackupRow(
        archiveUploadProgressState = ArchiveUploadProgressState(
          state = ArchiveUploadProgressState.State.Export,
          backupPhase = ArchiveUploadProgressState.BackupPhase.Account
        )
      )
      InProgressBackupRow(
        archiveUploadProgressState = ArchiveUploadProgressState(
          state = ArchiveUploadProgressState.State.Export,
          backupPhase = ArchiveUploadProgressState.BackupPhase.Call
        )
      )
      InProgressBackupRow(
        archiveUploadProgressState = ArchiveUploadProgressState(
          state = ArchiveUploadProgressState.State.Export,
          backupPhase = ArchiveUploadProgressState.BackupPhase.Sticker
        )
      )
      InProgressBackupRow(
        archiveUploadProgressState = ArchiveUploadProgressState(
          state = ArchiveUploadProgressState.State.Export,
          backupPhase = ArchiveUploadProgressState.BackupPhase.Recipient
        )
      )
      InProgressBackupRow(
        archiveUploadProgressState = ArchiveUploadProgressState(
          state = ArchiveUploadProgressState.State.Export,
          backupPhase = ArchiveUploadProgressState.BackupPhase.Thread
        )
      )
      InProgressBackupRow(
        archiveUploadProgressState = ArchiveUploadProgressState(
          state = ArchiveUploadProgressState.State.Export,
          backupPhase = ArchiveUploadProgressState.BackupPhase.Message,
          frameExportCount = 1,
          frameTotalCount = 1
        )
      )
      InProgressBackupRow(
        archiveUploadProgressState = ArchiveUploadProgressState(
          state = ArchiveUploadProgressState.State.Export,
          backupPhase = ArchiveUploadProgressState.BackupPhase.Message,
          frameExportCount = 1000,
          frameTotalCount = 100_000
        )
      )
      InProgressBackupRow(
        archiveUploadProgressState = ArchiveUploadProgressState(
          state = ArchiveUploadProgressState.State.Export,
          backupPhase = ArchiveUploadProgressState.BackupPhase.Message,
          frameExportCount = 1_000_000,
          frameTotalCount = 100_000
        )
      )
      InProgressBackupRow(
        archiveUploadProgressState = ArchiveUploadProgressState(
          state = ArchiveUploadProgressState.State.UploadBackupFile,
          backupPhase = ArchiveUploadProgressState.BackupPhase.BackupPhaseNone,
          backupFileUploadedBytes = 10.mebiBytes.inWholeBytes,
          backupFileTotalBytes = 50.mebiBytes.inWholeBytes,
          mediaUploadedBytes = 0,
          mediaTotalBytes = 0
        )
      )
      InProgressBackupRow(
        archiveUploadProgressState = ArchiveUploadProgressState(
          state = ArchiveUploadProgressState.State.UploadMedia,
          backupPhase = ArchiveUploadProgressState.BackupPhase.BackupPhaseNone,
          backupFileUploadedBytes = 10.mebiBytes.inWholeBytes,
          backupFileTotalBytes = 50.mebiBytes.inWholeBytes,
          mediaUploadedBytes = 100.mebiBytes.inWholeBytes,
          mediaTotalBytes = 1.gibiBytes.inWholeBytes
        )
      )
    }
  }
}

@SignalPreview
@Composable
private fun FailedToTurnOffBackupDialogPreview() {
  Previews.Preview {
    FailedToTurnOffBackupDialog(
      onDismiss = {}
    )
  }
}

@SignalPreview
@Composable
private fun TurnOffAndDeleteBackupsDialogPreview() {
  Previews.Preview {
    TurnOffAndDeleteBackupsDialog(
      onConfirm = {},
      onDismiss = {}
    )
  }
}

@SignalPreview
@Composable
private fun DownloadingYourBackupDialogPreview() {
  Previews.Preview {
    DownloadingYourBackupDialog(
      onDismiss = {}
    )
  }
}

@SignalPreview
@Composable
private fun SkipDownloadDialogPreview() {
  Previews.Preview {
    SkipDownloadDialog(
      renewalTime = System.currentTimeMillis().milliseconds + 30.days
    )
  }
}

@SignalPreview
@Composable
private fun CircularProgressDialogPreview() {
  Previews.Preview {
    CircularProgressDialog(
      onDismiss = {}
    )
  }
}

@SignalPreview
@Composable
private fun BackupFrequencyDialogPreview() {
  Previews.Preview {
    BackupFrequencyDialog(
      selected = BackupFrequency.DAILY,
      onSelected = {},
      onDismiss = {}
    )
  }
}

private data class BackupProgress(
  val completed: Long,
  val total: Long
) {
  val progress: Float = if (total > 0) completed / total.toFloat() else 0f
}

private fun ArchiveUploadProgressState.frameExportProgress(): Float {
  return if (this.frameTotalCount == 0L) {
    0f
  } else {
    this.frameExportCount / this.frameTotalCount.toFloat()
  }
}

private fun ArchiveUploadProgressState.uploadProgress(): Float {
  val current = this.backupFileUploadedBytes + this.mediaUploadedBytes
  val total = this.backupFileTotalBytes + this.mediaTotalBytes

  return if (total == 0L) {
    0f
  } else {
    current / total.toFloat()
  }
}

private val ArchiveUploadProgressState.uploadBytesTotal: Long
  get() = this.backupFileTotalBytes + this.mediaTotalBytes

private val ArchiveUploadProgressState.uploadBytesUploaded: Long
  get() = this.backupFileUploadedBytes + this.mediaUploadedBytes

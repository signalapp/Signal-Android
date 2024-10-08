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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Dividers
import org.signal.core.ui.Previews
import org.signal.core.ui.Rows
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.SignalPreview
import org.signal.core.ui.Snackbars
import org.signal.core.ui.Texts
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.BiometricDeviceAuthentication
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.ArchiveUploadProgress
import org.thoughtcrime.securesms.backup.v2.BackupFrequency
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.billing.launchManageBackupsSubscription
import org.thoughtcrime.securesms.components.settings.app.subscription.MessageBackupsCheckoutLauncher.createBackupsCheckoutLauncher
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.fonts.SignalSymbols
import org.thoughtcrime.securesms.fonts.SignalSymbols.SignalSymbol
import org.thoughtcrime.securesms.keyvalue.protos.ArchiveUploadProgressState
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.ServiceUtil
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.viewModel
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
    val backupProgress by ArchiveUploadProgress.progress.collectAsState(initial = null)
    val callbacks = remember { Callbacks() }

    RemoteBackupsSettingsContent(
      messageBackupsType = state.messageBackupsType,
      lastBackupTimestamp = state.lastBackupTimestamp,
      canBackUpUsingCellular = state.canBackUpUsingCellular,
      backupsFrequency = state.backupsFrequency,
      requestedDialog = state.dialog,
      requestedSnackbar = state.snackbar,
      contentCallbacks = callbacks,
      backupProgress = backupProgress,
      backupSize = state.backupSize,
      renewalTime = state.renewalTime
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
      Toast.makeText(requireContext(), R.string.authentication_required, Toast.LENGTH_SHORT).show()
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
}

@Composable
private fun RemoteBackupsSettingsContent(
  messageBackupsType: MessageBackupsType?,
  renewalTime: Duration,
  lastBackupTimestamp: Long,
  canBackUpUsingCellular: Boolean,
  backupsFrequency: BackupFrequency,
  requestedDialog: RemoteBackupsSettingsState.Dialog,
  requestedSnackbar: RemoteBackupsSettingsState.Snackbar,
  contentCallbacks: ContentCallbacks,
  backupProgress: ArchiveUploadProgressState?,
  backupSize: Long
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
      if (messageBackupsType != null) {
        item {
          BackupTypeRow(
            messageBackupsType = messageBackupsType,
            renewalTime = renewalTime,
            onBackupTypeActionButtonClicked = contentCallbacks::onBackupTypeActionClick
          )
        }
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
          InProgressBackupRow(progress = backupProgress.completedAttachments.toInt(), totalProgress = backupProgress.totalAttachments.toInt())
        }
      }

      item {
        Rows.TextRow(text = {
          Column {
            Text(
              text = stringResource(id = R.string.RemoteBackupsSettingsFragment__backup_size),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = Util.getPrettyFileSize(backupSize),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        })
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
  }

  when (requestedDialog) {
    RemoteBackupsSettingsState.Dialog.NONE -> {}
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

    RemoteBackupsSettingsState.Dialog.DELETING_BACKUP, RemoteBackupsSettingsState.Dialog.BACKUP_DELETED -> {
      DeletingBackupDialog(
        backupDeleted = requestedDialog == RemoteBackupsSettingsState.Dialog.BACKUP_DELETED,
        onDismiss = contentCallbacks::onDialogDismissed
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

@Composable
private fun BackupTypeRow(
  messageBackupsType: MessageBackupsType,
  renewalTime: Duration,
  onBackupTypeActionButtonClicked: (MessageBackupTier) -> Unit = {}
) {
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
            SignalSymbol(SignalSymbols.Weight.REGULAR, SignalSymbols.Glyph.CHECKMARK)
            append(" ")
            append(title)
          },
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium
        )

        val cost = when (messageBackupsType) {
          is MessageBackupsType.Paid -> stringResource(R.string.RemoteBackupsSettingsFragment__s_per_month, FiatMoneyUtil.format(LocalContext.current.resources, messageBackupsType.pricePerMonth))
          is MessageBackupsType.Free -> stringResource(R.string.RemoteBackupsSettingsFragment__your_backup_plan_is_free)
        }

        Text(
          text = cost,
          modifier = Modifier.padding(top = 12.dp)
        )

        if (messageBackupsType is MessageBackupsType.Paid) {
          if (renewalTime > 0.seconds) {
            Text(
              text = stringResource(R.string.RemoteBackupsSettingsFragment__renews_s, DateUtils.formatDateWithYear(Locale.getDefault(), renewalTime.inWholeMilliseconds))
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

@Composable
private fun InProgressBackupRow(
  progress: Int?,
  totalProgress: Int?
) {
  Row(
    modifier = Modifier
      .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
      .padding(top = 16.dp, bottom = 14.dp)
  ) {
    Column(
      modifier = Modifier.weight(1f)
    ) {
      if (totalProgress == null || totalProgress == 0) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      } else {
        LinearProgressIndicator(
          modifier = Modifier.fillMaxWidth(),
          progress = { ((progress ?: 0) / totalProgress).toFloat() }
        )
      }

      Text(
        text = stringResource(R.string.RemoteBackupsSettingsFragment__d_slash_d, progress ?: 0, totalProgress ?: 0),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun LastBackupRow(
  lastBackupTimestamp: Long,
  onBackupNowClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeletingBackupDialog(
  backupDeleted: Boolean,
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
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .defaultMinSize(minWidth = 232.dp)
          .padding(bottom = 60.dp)
      ) {
        if (backupDeleted) {
          Icon(
            painter = painterResource(id = R.drawable.symbol_check_light_24),
            contentDescription = null,
            tint = Color(0xFF09B37B),
            modifier = Modifier
              .padding(top = 58.dp, bottom = 9.dp)
              .size(48.dp)
          )
          Text(
            text = stringResource(id = R.string.RemoteBackupsSettingsFragment__backup_deleted),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else {
          CircularProgressIndicator(
            modifier = Modifier
              .padding(top = 64.dp, bottom = 20.dp)
              .size(48.dp)
          )
          Text(
            text = stringResource(id = R.string.RemoteBackupsSettingsFragment__deleting_backup),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
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

        BackupFrequency.values().forEach {
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
      messageBackupsType = MessageBackupsType.Free(mediaRetentionDays = 30),
      lastBackupTimestamp = -1,
      canBackUpUsingCellular = false,
      backupsFrequency = BackupFrequency.MANUAL,
      requestedDialog = RemoteBackupsSettingsState.Dialog.NONE,
      requestedSnackbar = RemoteBackupsSettingsState.Snackbar.NONE,
      contentCallbacks = object : ContentCallbacks {},
      backupProgress = null,
      renewalTime = 1727193018.seconds,
      backupSize = 2300000
    )
  }
}

@SignalPreview
@Composable
private fun BackupTypeRowPreview() {
  Previews.Preview {
    Column {
      BackupTypeRow(
        messageBackupsType = MessageBackupsType.Paid(
          pricePerMonth = FiatMoney(BigDecimal.valueOf(3), Currency.getInstance("CAD")),
          storageAllowanceBytes = 100_000_000
        ),
        renewalTime = 1727193018.seconds
      )

      BackupTypeRow(
        messageBackupsType = MessageBackupsType.Free(
          mediaRetentionDays = 30
        ),
        renewalTime = 0.seconds
      )
    }
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
    InProgressBackupRow(50, 100)
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
private fun DeleteBackupDialogPreview() {
  Previews.Preview {
    DeletingBackupDialog(
      backupDeleted = true,
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

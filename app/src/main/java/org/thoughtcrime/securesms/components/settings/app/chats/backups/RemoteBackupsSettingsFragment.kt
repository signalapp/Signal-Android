/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats.backups

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.fragment.findNavController
import kotlinx.collections.immutable.persistentListOf
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Dividers
import org.signal.core.ui.Previews
import org.signal.core.ui.Rows
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.SignalPreview
import org.signal.core.ui.Snackbars
import org.signal.core.ui.Texts
import org.signal.core.util.money.FiatMoney
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.BackupFrequency
import org.thoughtcrime.securesms.backup.v2.BackupV2Event
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.CheckoutFlowActivity
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.conversation.v2.registerForLifecycle
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.viewModel
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

/**
 * Remote backups settings fragment.
 */
class RemoteBackupsSettingsFragment : ComposeFragment() {

  private val viewModel by viewModel {
    RemoteBackupsSettingsViewModel()
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsState()
    val callbacks = remember { Callbacks() }

    RemoteBackupsSettingsContent(
      messageBackupsType = state.messageBackupsType,
      lastBackupTimestamp = state.lastBackupTimestamp,
      canBackUpUsingCellular = state.canBackUpUsingCellular,
      backupsFrequency = state.backupsFrequency,
      requestedDialog = state.dialog,
      requestedSnackbar = state.snackbar,
      contentCallbacks = callbacks,
      backupProgress = state.backupProgress,
      backupSize = state.backupSize
    )
  }

  @Stable
  private inner class Callbacks : ContentCallbacks {
    override fun onNavigationClick() {
      findNavController().popBackStack()
    }

    override fun onEnableBackupsClick() {
      startActivity(CheckoutFlowActivity.createIntent(requireContext(), InAppPaymentType.RECURRING_BACKUP))
    }

    override fun onBackUpUsingCellularClick(canUseCellular: Boolean) {
      viewModel.setCanBackUpUsingCellular(canUseCellular)
    }

    override fun onViewPaymentHistory() {
      findNavController().safeNavigate(R.id.action_remoteBackupsSettingsFragment_to_remoteBackupsPaymentHistoryFragment)
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

    override fun onBackupsTypeClick() {
      findNavController().safeNavigate(R.id.action_remoteBackupsSettingsFragment_to_backupsTypeSettingsFragment)
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
  fun onEvent(backupEvent: BackupV2Event) {
    viewModel.updateBackupProgress(backupEvent)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    EventBus.getDefault().registerForLifecycle(subscriber = this, lifecycleOwner = viewLifecycleOwner)
  }

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }
}

/**
 * Callback interface for RemoteBackupsSettingsContent composable.
 */
private interface ContentCallbacks {
  fun onNavigationClick() = Unit
  fun onEnableBackupsClick() = Unit
  fun onBackupsTypeClick() = Unit
  fun onBackUpUsingCellularClick(canUseCellular: Boolean) = Unit
  fun onViewPaymentHistory() = Unit
  fun onBackupNowClick() = Unit
  fun onTurnOffAndDeleteBackupsClick() = Unit
  fun onChangeBackupFrequencyClick() = Unit
  fun onDialogDismissed() = Unit
  fun onSnackbarDismissed() = Unit
  fun onSelectBackupsFrequencyChange(newFrequency: BackupFrequency) = Unit
  fun onTurnOffAndDeleteBackupsConfirm() = Unit
}

@Composable
private fun RemoteBackupsSettingsContent(
  messageBackupsType: MessageBackupsType?,
  lastBackupTimestamp: Long,
  canBackUpUsingCellular: Boolean,
  backupsFrequency: BackupFrequency,
  requestedDialog: RemoteBackupsSettingsState.Dialog,
  requestedSnackbar: RemoteBackupsSettingsState.Snackbar,
  contentCallbacks: ContentCallbacks,
  backupProgress: BackupV2Event?,
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
      item {
        BackupTypeRow(
          messageBackupsType = messageBackupsType,
          onEnableBackupsClick = contentCallbacks::onEnableBackupsClick,
          onChangeBackupsTypeClick = contentCallbacks::onBackupsTypeClick
        )
      }

      if (messageBackupsType == null) {
        item {
          Rows.TextRow(
            text = stringResource(id = R.string.RemoteBackupsSettingsFragment__payment_history),
            onClick = contentCallbacks::onViewPaymentHistory
          )
        }
      } else {
        item {
          Dividers.Default()
        }

        item {
          Texts.SectionHeader(text = stringResource(id = R.string.RemoteBackupsSettingsFragment__backup_details))
        }

        if (backupProgress == null || backupProgress.type == BackupV2Event.Type.FINISHED) {
          item {
            LastBackupRow(
              lastBackupTimestamp = lastBackupTimestamp,
              onBackupNowClick = contentCallbacks::onBackupNowClick
            )
          }
        } else {
          item {
            InProgressBackupRow(progress = backupProgress.count.toInt(), totalProgress = backupProgress.estimatedTotalCount.toInt())
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
      }
    }
    contentCallbacks.onSnackbarDismissed()
  }
}

@Composable
private fun BackupTypeRow(
  messageBackupsType: MessageBackupsType?,
  onEnableBackupsClick: () -> Unit,
  onChangeBackupsTypeClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(enabled = messageBackupsType != null, onClick = onChangeBackupsTypeClick)
      .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
      .padding(top = 16.dp, bottom = 14.dp)
  ) {
    Column(
      modifier = Modifier.weight(1f)
    ) {
      Text(
        text = stringResource(id = R.string.RemoteBackupsSettingsFragment__backup_type),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
      )

      if (messageBackupsType == null) {
        Text(
          text = stringResource(id = R.string.RemoteBackupsSettingsFragment__backups_disabled),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        val localResources = LocalContext.current.resources
        val formattedCurrency = remember(messageBackupsType.pricePerMonth) {
          FiatMoneyUtil.format(localResources, messageBackupsType.pricePerMonth, FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())
        }

        Text(
          text = stringResource(id = R.string.RemoteBackupsSettingsFragment__s_dot_s_per_month, messageBackupsType.title, formattedCurrency)
        )
      }
    }

    if (messageBackupsType == null) {
      Buttons.Small(onClick = onEnableBackupsClick) {
        Text(text = stringResource(id = R.string.RemoteBackupsSettingsFragment__enable_backups))
      }
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
      messageBackupsType = null,
      lastBackupTimestamp = -1,
      canBackUpUsingCellular = false,
      backupsFrequency = BackupFrequency.MANUAL,
      requestedDialog = RemoteBackupsSettingsState.Dialog.NONE,
      requestedSnackbar = RemoteBackupsSettingsState.Snackbar.NONE,
      contentCallbacks = object : ContentCallbacks {},
      backupProgress = null,
      backupSize = 2300000
    )
  }
}

@SignalPreview
@Composable
private fun BackupTypeRowPreview() {
  Previews.Preview {
    BackupTypeRow(
      messageBackupsType = MessageBackupsType(
        tier = MessageBackupTier.FREE,
        title = "Free",
        pricePerMonth = FiatMoney(BigDecimal.ZERO, Currency.getInstance("USD")),
        features = persistentListOf()
      ),
      onChangeBackupsTypeClick = {},
      onEnableBackupsClick = {}
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

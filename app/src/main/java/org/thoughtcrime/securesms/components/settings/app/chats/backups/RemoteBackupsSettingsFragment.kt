/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats.backups

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.fragment.findNavController
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
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.BackupFrequency
import org.thoughtcrime.securesms.backup.v2.BackupV2Event
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsFlowActivity
import org.thoughtcrime.securesms.backup.v2.ui.subscription.getTierDetails
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.conversation.v2.registerForLifecycle
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.viewModel
import java.util.Locale

/**
 * Remote backups settings fragment.
 *
 * TODO [message-backups] -- All copy in this file is non-final
 */
class RemoteBackupsSettingsFragment : ComposeFragment() {

  private val viewModel by viewModel {
    RemoteBackupsSettingsViewModel()
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state
    val callbacks = remember { Callbacks() }

    RemoteBackupsSettingsContent(
      messageBackupTier = state.messageBackupsTier,
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
      startActivity(Intent(requireContext(), MessageBackupsFlowActivity::class.java))
    }

    override fun onBackUpUsingCellularClick(canUseCellular: Boolean) {
      viewModel.setCanBackUpUsingCellular(canUseCellular)
    }

    override fun onViewPaymentHistory() {
      // TODO [message-backups] Navigate to payment history
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
  messageBackupTier: MessageBackupTier?,
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
    title = "Signal Backups",
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
          messageBackupTier = messageBackupTier,
          onEnableBackupsClick = contentCallbacks::onEnableBackupsClick,
          onChangeBackupsTypeClick = contentCallbacks::onBackupsTypeClick
        )
      }

      if (messageBackupTier == null) {
        item {
          Rows.TextRow(
            text = "Payment history",
            onClick = contentCallbacks::onViewPaymentHistory
          )
        }
      } else {
        item {
          Dividers.Default()
        }

        item {
          Texts.SectionHeader(text = "Backup Details")
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
                text = "Backup size",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = Util.getPrettyFileSize(backupSize ?: 0),
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
                  text = "Backup frequency",
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
            text = "Back up using cellular",
            onCheckChanged = contentCallbacks::onBackUpUsingCellularClick
          )
        }

        item {
          Dividers.Default()
        }

        item {
          Rows.TextRow(
            text = "Turn off and delete backup",
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
  }

  LaunchedEffect(requestedSnackbar) {
    when (requestedSnackbar) {
      RemoteBackupsSettingsState.Snackbar.NONE -> {
        snackbarHostState.currentSnackbarData?.dismiss()
      }

      RemoteBackupsSettingsState.Snackbar.BACKUP_DELETED_AND_TURNED_OFF -> {
        snackbarHostState.showSnackbar(
          "Backup deleted and turned off"
        )
      }

      RemoteBackupsSettingsState.Snackbar.BACKUP_TYPE_CHANGED_AND_SUBSCRIPTION_CANCELLED -> {
        snackbarHostState.showSnackbar(
          "Backup type changed and subscription cancelled"
        )
      }

      RemoteBackupsSettingsState.Snackbar.SUBSCRIPTION_CANCELLED -> {
        snackbarHostState.showSnackbar(
          "Subscription cancelled"
        )
      }

      RemoteBackupsSettingsState.Snackbar.DOWNLOAD_COMPLETE -> {
        snackbarHostState.showSnackbar(
          "Download complete"
        )
      }
    }
    contentCallbacks.onSnackbarDismissed()
  }
}

@Composable
private fun BackupTypeRow(
  messageBackupTier: MessageBackupTier?,
  onEnableBackupsClick: () -> Unit,
  onChangeBackupsTypeClick: () -> Unit
) {
  val messageBackupsType = if (messageBackupTier != null) getTierDetails(messageBackupTier) else null

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(enabled = messageBackupTier != null, onClick = onChangeBackupsTypeClick)
      .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
      .padding(top = 16.dp, bottom = 14.dp)
  ) {
    Column(
      modifier = Modifier.weight(1f)
    ) {
      Text(
        text = "Backup type",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
      )

      if (messageBackupsType == null) {
        Text(
          text = "Backups disabled",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        val localResources = LocalContext.current.resources
        val formattedCurrency = remember(messageBackupsType.pricePerMonth) {
          FiatMoneyUtil.format(localResources, messageBackupsType.pricePerMonth, FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())
        }

        Text(
          text = "${messageBackupsType.title} · $formattedCurrency/month"
        )
      }
    }

    if (messageBackupsType == null) {
      Buttons.Small(onClick = onEnableBackupsClick) {
        Text(text = "Enable backups")
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
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), progress = ((progress ?: 0) / totalProgress).toFloat())
      }

      Text(
        text = "$progress/$totalProgress",
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
        text = "Last backup",
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
          text = "$day at $time",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        Text(
          text = "Never",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    Buttons.Small(onClick = onBackupNowClick) {
      Text(text = "Back up now")
    }
  }
}

@Composable
private fun TurnOffAndDeleteBackupsDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = "Turn off and delete backups?",
    body = "You will not be charged again. Your backup will be deleted and no new backups will be created.",
    confirm = "Turn off and delete",
    dismiss = stringResource(id = android.R.string.cancel),
    confirmColor = MaterialTheme.colorScheme.error,
    onConfirm = onConfirm,
    onDismiss = onDismiss
  )
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
          text = "Backup frequency",
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier.padding(24.dp)
        )

        BackupFrequency.values().forEach {
          Rows.RadioRow(
            selected = selected == it,
            text = getTextForFrequency(backupsFrequency = it),
            label = when (it) {
              BackupFrequency.MANUAL -> "By tapping \"Back up now\""
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
    BackupFrequency.DAILY -> "Daily"
    BackupFrequency.WEEKLY -> "Weekly"
    BackupFrequency.MONTHLY -> "Monthly"
    BackupFrequency.MANUAL -> "Manually back up"
  }
}

@SignalPreview
@Composable
private fun RemoteBackupsSettingsContentPreview() {
  Previews.Preview {
    RemoteBackupsSettingsContent(
      messageBackupTier = null,
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
      messageBackupTier = MessageBackupTier.PAID,
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
private fun BackupFrequencyDialogPreview() {
  Previews.Preview {
    BackupFrequencyDialog(
      selected = BackupFrequency.DAILY,
      onSelected = {},
      onDismiss = {}
    )
  }
}

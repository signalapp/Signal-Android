/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.components.settings.app.backups.local

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.Snackbars
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.isIdle
import org.thoughtcrime.securesms.backup.v2.ui.status.BackupCreationProgressRow
import org.thoughtcrime.securesms.components.compose.rememberBiometricsAuthentication
import org.thoughtcrime.securesms.keyvalue.protos.LocalBackupCreationProgress
import org.thoughtcrime.securesms.util.BackupUtil
import org.signal.core.ui.R as CoreUiR
import org.signal.core.ui.compose.DayNightPreviews as DayNightPreview

@Composable
internal fun LocalBackupsSettingsScreen(
  state: LocalBackupsSettingsState,
  callback: LocalBackupsSettingsCallback,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
  val context = LocalContext.current
  var showChooseLocationDialog by rememberSaveable { mutableStateOf(false) }
  var showTurnOffAndDeleteDialog by rememberSaveable { mutableStateOf(false) }

  val learnMore = stringResource(id = R.string.BackupsPreferenceFragment__learn_more)
  val restoreText = stringResource(id = R.string.OnDeviceBackupsScreen__to_restore_a_backup, learnMore).trim()
  val learnMoreColor = MaterialTheme.colorScheme.primary

  val restoreInfo = remember(restoreText, learnMore, learnMoreColor) {
    buildAnnotatedString {
      append(restoreText)
      append(" ")

      withLink(
        LinkAnnotation.Clickable(
          tag = "learn-more",
          linkInteractionListener = { callback.onLearnMoreClick() },
          styles = TextLinkStyles(style = SpanStyle(color = learnMoreColor))
        )
      ) {
        append(learnMore)
      }
    }
  }

  val biometrics = rememberBiometricsAuthentication(
    promptTitle = stringResource(R.string.RemoteBackupsSettingsFragment__unlock_to_view_backup_key),
    educationSheetMessage = stringResource(R.string.RemoteBackupsSettingsFragment__to_view_your_key)
  )

  Scaffolds.Settings(
    title = stringResource(id = R.string.RemoteBackupsSettingsFragment__on_device_backups),
    navigationIcon = ImageVector.vectorResource(CoreUiR.drawable.symbol_arrow_start_24),
    onNavigationClick = callback::onNavigationClick,
    snackbarHost = {
      Snackbars.Host(snackbarHostState)
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier.padding(paddingValues)
    ) {
      if (!state.backupsEnabled) {
        item {
          Rows.TextRow(
            text = {
              Column {
                Text(
                  text = stringResource(id = R.string.BackupsPreferenceFragment__backups_are_encrypted_with_a_passphrase),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  style = MaterialTheme.typography.bodyMedium
                )

                Buttons.MediumTonal(
                  onClick = {
                    // For the SAF-based flow, present an in-screen dialog before launching the picker.
                    if (BackupUtil.isUserSelectionRequired(context)) {
                      showChooseLocationDialog = true
                    } else {
                      callback.onTurnOnClick()
                    }
                  },
                  enabled = state.canTurnOn,
                  modifier = Modifier.padding(top = 12.dp)
                ) {
                  Text(text = stringResource(id = R.string.BackupsPreferenceFragment__turn_on))
                }
              }
            }
          )
        }
      } else {
        val isCreating = !state.progress.isIdle

        if (isCreating) {
          item {
            BackupCreationProgressRow(
              progress = state.progress,
              isRemote = false
            )
          }
        } else {
          item {
            Rows.TextRow(
              text = {
                Column {
                  Text(
                    text = stringResource(id = R.string.BackupsPreferenceFragment__create_backup),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                  )

                  if (state.lastBackupLabel != null) {
                    Text(
                      text = stringResource(R.string.BackupsSettingsFragment_last_backup_s, state.lastBackupLabel),
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(top = 4.dp)
                    )
                  }
                }
              },
              onClick = callback::onCreateBackupClick
            )
          }
        }

        item {
          Rows.TextRow(
            text = stringResource(id = R.string.BackupsPreferenceFragment__backup_time),
            label = state.scheduleTimeLabel,
            onClick = callback::onPickTimeClick
          )
        }

        if (!state.folderDisplayName.isNullOrBlank()) {
          item {
            Rows.TextRow(
              text = stringResource(id = R.string.BackupsPreferenceFragment__backup_folder),
              label = state.folderDisplayName
            )
          }
        }

        item {
          Rows.TextRow(
            text = stringResource(id = R.string.UnifiedOnDeviceBackupsSettingsScreen__view_recovery_key),
            onClick = { biometrics.withBiometricsAuthentication { callback.onViewBackupKeyClick() } }
          )
        }
      }

      if (state.backupsEnabled) {
        item {
          Rows.TextRow(
            text = stringResource(id = R.string.RemoteBackupsSettingsFragment__turn_off_and_delete),
            foregroundTint = MaterialTheme.colorScheme.error,
            onClick = { showTurnOffAndDeleteDialog = true }
          )
        }
      }

      item {
        Dividers.Default()
      }

      item {
        Text(
          text = restoreInfo,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(
            horizontal = dimensionResource(id = CoreUiR.dimen.gutter),
            vertical = 16.dp
          )
        )
      }
    }
  }

  if (showChooseLocationDialog) {
    Dialogs.SimpleAlertDialog(
      title = stringResource(id = R.string.BackupDialog_enable_local_backups),
      body = stringResource(id = R.string.BackupDialog_to_enable_backups_choose_a_folder),
      confirm = stringResource(id = R.string.BackupDialog_choose_folder),
      dismiss = stringResource(id = android.R.string.cancel),
      onConfirm = callback::onLaunchBackupLocationPickerClick,
      onDismiss = { showChooseLocationDialog = false }
    )
  }

  if (showTurnOffAndDeleteDialog) {
    Dialogs.SimpleAlertDialog(
      title = stringResource(id = R.string.BackupDialog_delete_backups),
      body = stringResource(id = R.string.BackupDialog_disable_and_delete_all_local_backups),
      confirm = stringResource(id = R.string.BackupDialog_delete_backups_statement),
      confirmColor = MaterialTheme.colorScheme.error,
      dismiss = stringResource(id = android.R.string.cancel),
      onConfirm = callback::onTurnOffAndDeleteConfirmed,
      onDismiss = { showTurnOffAndDeleteDialog = false }
    )
  }

  if (state.isDeleting) {
    val message = stringResource(id = R.string.BackupDialog_deleting_local_backup)
    if (state.deleteTotal > 0) {
      Dialogs.DeterminateProgressDialog(
        message = message,
        progress = { state.deleteCompleted.toFloat() / state.deleteTotal }
      )
    } else {
      Dialogs.IndeterminateProgressDialog(message = message)
    }
  }
}

@DayNightPreview
@Composable
private fun OnDeviceBackupsDisabledCanTurnOnPreviewSettings() {
  Previews.Preview {
    LocalBackupsSettingsScreen(
      state = LocalBackupsSettingsState(
        backupsEnabled = false,
        canTurnOn = true
      ),
      callback = LocalBackupsSettingsCallback.Empty
    )
  }
}

@DayNightPreview
@Composable
private fun OnDeviceBackupsDisabledCannotTurnOnPreviewSettings() {
  Previews.Preview {
    LocalBackupsSettingsScreen(
      state = LocalBackupsSettingsState(
        backupsEnabled = false,
        canTurnOn = false
      ),
      callback = LocalBackupsSettingsCallback.Empty
    )
  }
}

@DayNightPreview
@Composable
private fun LocalBackupsSettingsEnabledIdlePreview() {
  Previews.Preview {
    LocalBackupsSettingsScreen(
      state = LocalBackupsSettingsState(
        backupsEnabled = true,
        lastBackupLabel = "1 hour ago",
        folderDisplayName = "/storage/emulated/0/Signal/Backups",
        scheduleTimeLabel = "1:00 AM",
        progress = LocalBackupCreationProgress(idle = LocalBackupCreationProgress.Idle())
      ),
      callback = LocalBackupsSettingsCallback.Empty
    )
  }
}

@DayNightPreview
@Composable
private fun LocalBackupsSettingsEnabledExportingIndeterminatePreview() {
  Previews.Preview {
    LocalBackupsSettingsScreen(
      state = LocalBackupsSettingsState(
        backupsEnabled = true,
        lastBackupLabel = "1 hour ago",
        folderDisplayName = "/storage/emulated/0/Signal/Backups",
        scheduleTimeLabel = "1:00 AM",
        progress = LocalBackupCreationProgress(
          exporting = LocalBackupCreationProgress.Exporting(phase = LocalBackupCreationProgress.ExportPhase.ACCOUNT)
        )
      ),
      callback = LocalBackupsSettingsCallback.Empty
    )
  }
}

@DayNightPreview
@Composable
private fun LocalBackupsSettingsEnabledExportingMessagesPreview() {
  Previews.Preview {
    LocalBackupsSettingsScreen(
      state = LocalBackupsSettingsState(
        backupsEnabled = true,
        lastBackupLabel = "1 hour ago",
        folderDisplayName = "/storage/emulated/0/Signal/Backups",
        scheduleTimeLabel = "1:00 AM",
        progress = LocalBackupCreationProgress(
          exporting = LocalBackupCreationProgress.Exporting(
            phase = LocalBackupCreationProgress.ExportPhase.MESSAGE,
            frameExportCount = 42000,
            frameTotalCount = 100000
          )
        )
      ),
      callback = LocalBackupsSettingsCallback.Empty
    )
  }
}

@DayNightPreview
@Composable
private fun LocalBackupsSettingsEnabledTransferringPreview() {
  Previews.Preview {
    LocalBackupsSettingsScreen(
      state = LocalBackupsSettingsState(
        backupsEnabled = true,
        lastBackupLabel = "1 hour ago",
        folderDisplayName = "/storage/emulated/0/Signal/Backups",
        scheduleTimeLabel = "1:00 AM",
        progress = LocalBackupCreationProgress(
          transferring = LocalBackupCreationProgress.Transferring(
            completed = 50,
            total = 200,
            mediaPhase = true
          )
        )
      ),
      callback = LocalBackupsSettingsCallback.Empty
    )
  }
}

@DayNightPreview
@Composable
private fun LocalBackupsSettingsEnabledNonLegacyPreview() {
  Previews.Preview {
    LocalBackupsSettingsScreen(
      state = LocalBackupsSettingsState(
        backupsEnabled = true,
        lastBackupLabel = "1 hour ago",
        folderDisplayName = "Signal Backups",
        scheduleTimeLabel = "1:00 AM",
        progress = LocalBackupCreationProgress(idle = LocalBackupCreationProgress.Idle())
      ),
      callback = LocalBackupsSettingsCallback.Empty
    )
  }
}

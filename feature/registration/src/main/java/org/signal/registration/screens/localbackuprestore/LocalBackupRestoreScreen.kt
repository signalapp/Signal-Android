/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.localbackuprestore

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.util.mebiBytes
import org.signal.registration.R
import org.signal.registration.test.TestTags
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalBackupRestoreScreen(
  state: LocalBackupRestoreState,
  onEvent: (LocalBackupRestoreEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val folderPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree()
  ) { uri: Uri? ->
    if (uri != null) {
      onEvent(LocalBackupRestoreEvents.BackupFolderSelected(uri))
    } else {
      onEvent(LocalBackupRestoreEvents.FolderPickerDismissed)
    }
  }

  LaunchedEffect(state.launchFolderPicker) {
    if (state.launchFolderPicker) {
      folderPickerLauncher.launch(null)
    }
  }

  when (state.restorePhase) {
    LocalBackupRestoreState.RestorePhase.SelectFolder -> {
      SelectFolderContent(onEvent = onEvent, modifier = modifier)
    }
    LocalBackupRestoreState.RestorePhase.Scanning -> {
      ScanningContent(modifier = modifier)
    }
    LocalBackupRestoreState.RestorePhase.BackupFound -> {
      BackupFoundContent(backupInfo = state.backupInfo!!, allBackups = state.allBackups, onEvent = onEvent, modifier = modifier)
    }
    LocalBackupRestoreState.RestorePhase.NoBackupFound -> {
      NoBackupFoundContent(onEvent = onEvent, modifier = modifier)
    }
    LocalBackupRestoreState.RestorePhase.Preparing -> {
      PreparingContent(modifier = modifier)
    }
    LocalBackupRestoreState.RestorePhase.InProgress -> {
      InProgressContent(progressFraction = state.progressFraction, onEvent = onEvent, modifier = modifier)
    }
    LocalBackupRestoreState.RestorePhase.Error -> {
      ErrorContent(errorMessage = state.errorMessage, onEvent = onEvent, modifier = modifier)
    }
  }
}

@Composable
private fun SelectFolderContent(
  onEvent: (LocalBackupRestoreEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(horizontal = 24.dp)
      .testTag(TestTags.LOCAL_BACKUP_RESTORE_SCREEN),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Spacer(modifier = Modifier.height(40.dp))

    Text(
      text = stringResource(R.string.LocalBackupRestoreScreen__restore_on_device_backup),
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = stringResource(R.string.LocalBackupRestoreScreen__select_folder_description),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(28.dp))

    BackupOptionCard(
      icon = {
        Icon(
          painter = painterResource(R.drawable.symbol_folder_24),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(32.dp)
        )
      },
      title = stringResource(R.string.LocalBackupRestoreScreen__choose_backup_folder),
      subtitle = stringResource(R.string.LocalBackupRestoreScreen__choose_folder_subtitle),
      onClick = { onEvent(LocalBackupRestoreEvents.PickBackupFolder) },
      modifier = Modifier.testTag(TestTags.LOCAL_BACKUP_RESTORE_SELECT_FOLDER_BUTTON)
    )

    Spacer(modifier = Modifier.weight(1f))

    TextButton(
      onClick = { onEvent(LocalBackupRestoreEvents.Cancel) },
      modifier = Modifier.padding(bottom = 32.dp)
    ) {
      Text(
        text = stringResource(android.R.string.cancel),
        color = MaterialTheme.colorScheme.primary
      )
    }
  }
}

@Composable
private fun ScanningContent(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 24.dp)
      .testTag(TestTags.LOCAL_BACKUP_RESTORE_SCREEN),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    CircularProgressIndicator(
      modifier = Modifier.size(48.dp)
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = stringResource(R.string.LocalBackupRestoreScreen__scanning_folder),
      style = MaterialTheme.typography.bodyLarge
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupFoundContent(
  backupInfo: LocalBackupInfo,
  allBackups: List<LocalBackupInfo>,
  onEvent: (LocalBackupRestoreEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val coroutineScope = rememberCoroutineScope()
  var showBottomSheet by remember { mutableStateOf(false) }

  if (showBottomSheet) {
    BackupPickerBottomSheet(
      allBackups = allBackups,
      initialSelection = backupInfo,
      sheetState = sheetState,
      onConfirm = { backup ->
        onEvent(LocalBackupRestoreEvents.BackupSelected(backup))
        coroutineScope.launch {
          sheetState.hide()
          showBottomSheet = false
        }
      },
      onDismiss = {
        coroutineScope.launch {
          sheetState.hide()
          showBottomSheet = false
        }
      }
    )
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(horizontal = 24.dp)
      .testTag(TestTags.LOCAL_BACKUP_RESTORE_SCREEN),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Spacer(modifier = Modifier.height(40.dp))

    Text(
      text = stringResource(R.string.LocalBackupRestoreScreen__restore_on_device_backup),
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = stringResource(R.string.LocalBackupRestoreScreen__backup_found_description),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(28.dp))

    BackupInfoCard(backupInfo = backupInfo)

    Spacer(modifier = Modifier.height(16.dp))

    if (allBackups.size > 1) {
      TextButton(
        onClick = { showBottomSheet = true }
      ) {
        Icon(
          painter = SignalIcons.Backup.painter,
          contentDescription = null,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = stringResource(R.string.LocalBackupRestoreScreen__choose_earlier_backup)
        )
      }
    }

    Spacer(modifier = Modifier.weight(1f))

    Buttons.LargeTonal(
      onClick = { onEvent(LocalBackupRestoreEvents.RestoreBackup) },
      modifier = Modifier
        .fillMaxWidth()
        .testTag(TestTags.LOCAL_BACKUP_RESTORE_RESTORE_BUTTON)
    ) {
      Text(text = stringResource(R.string.LocalBackupRestoreScreen__restore_backup))
    }

    Spacer(modifier = Modifier.height(16.dp))

    TextButton(
      onClick = { onEvent(LocalBackupRestoreEvents.Cancel) },
      modifier = Modifier.padding(bottom = 32.dp)
    ) {
      Text(
        text = stringResource(android.R.string.cancel),
        color = MaterialTheme.colorScheme.primary
      )
    }
  }
}

@Composable
private fun BackupInfoCard(
  backupInfo: LocalBackupInfo,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val formattedDate = remember(backupInfo.date) {
    backupInfo.date.format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a"))
  }
  val formattedSize = remember(backupInfo.sizeBytes) {
    backupInfo.sizeBytes?.let { Formatter.formatShortFileSize(context, it) }
  }

  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ),
    modifier = modifier
      .fillMaxWidth()
      .testTag(TestTags.LOCAL_BACKUP_RESTORE_BACKUP_INFO_CARD)
  ) {
    Column(
      modifier = Modifier.padding(16.dp)
    ) {
      Text(
        text = stringResource(R.string.LocalBackupRestoreScreen__your_latest_backup),
        style = MaterialTheme.typography.bodyLarge
      )

      Spacer(modifier = Modifier.height(12.dp))

      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          painter = SignalIcons.Recent.painter,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = formattedDate,
          style = MaterialTheme.typography.bodyMedium
        )
      }

      if (formattedSize != null) {
        Spacer(modifier = Modifier.height(8.dp))

        val sizeIcon = if (backupInfo.type == LocalBackupInfo.BackupType.V1) {
          SignalIcons.File.painter
        } else {
          painterResource(R.drawable.symbol_folder_24)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            painter = sizeIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(12.dp))
          Text(
            text = formattedSize,
            style = MaterialTheme.typography.bodyMedium
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupPickerBottomSheet(
  allBackups: List<LocalBackupInfo>,
  initialSelection: LocalBackupInfo,
  sheetState: androidx.compose.material3.SheetState,
  onConfirm: (LocalBackupInfo) -> Unit,
  onDismiss: () -> Unit
) {
  var selected by remember(initialSelection) { mutableStateOf(initialSelection) }

  BottomSheets.BottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState
  ) {
    BackupPickerSheetContent(
      allBackups = allBackups,
      selected = selected,
      onSelect = { selected = it },
      onConfirm = { onConfirm(selected) }
    )
  }
}

@Composable
private fun BackupPickerSheetContent(
  allBackups: List<LocalBackupInfo>,
  selected: LocalBackupInfo,
  onSelect: (LocalBackupInfo) -> Unit,
  onConfirm: () -> Unit
) {
  val context = LocalContext.current

  Spacer(modifier = Modifier.height(24.dp))

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.padding(horizontal = 24.dp)
  ) {
    Text(
      text = stringResource(R.string.LocalBackupRestoreScreen__choose_a_backup_to_restore),
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = stringResource(R.string.LocalBackupRestoreScreen__choosing_an_older_backup_warning),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(24.dp))

    Card(
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      ),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier.padding(vertical = 4.dp)
          .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp))
      ) {
        allBackups.forEach { backup ->
          val isSelected = backup == selected
          val formattedDate = remember(backup.date) {
            backup.date.format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a"))
          }
          val formattedSize = remember(backup.sizeBytes) {
            backup.sizeBytes?.let { Formatter.formatShortFileSize(context, it) }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onSelect(backup) }
              .padding(horizontal = 16.dp, vertical = 12.dp)
          ) {
            RadioButton(
              selected = isSelected,
              onClick = { onSelect(backup) }
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
              Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodyLarge
              )
              if (formattedSize != null) {
                Text(
                  text = formattedSize,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Buttons.LargeTonal(
      onClick = onConfirm,
      modifier = Modifier.defaultMinSize(220.dp)
    ) {
      Text(text = stringResource(R.string.LocalBackupRestoreScreen__continue_button))
    }

    Spacer(modifier = Modifier.height(32.dp))
  }
}

@Composable
private fun NoBackupFoundContent(
  onEvent: (LocalBackupRestoreEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(horizontal = 24.dp)
      .testTag(TestTags.LOCAL_BACKUP_RESTORE_SCREEN),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Spacer(modifier = Modifier.height(40.dp))

    Text(
      text = stringResource(R.string.LocalBackupRestoreScreen__no_backup_found),
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = stringResource(R.string.LocalBackupRestoreScreen__no_backup_found_description),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(32.dp))

    OutlinedButton(
      onClick = { onEvent(LocalBackupRestoreEvents.PickBackupFolder) },
      modifier = Modifier.fillMaxWidth()
    ) {
      Text(text = stringResource(R.string.LocalBackupRestoreScreen__try_different_folder))
    }

    Spacer(modifier = Modifier.height(16.dp))

    TextButton(
      onClick = { onEvent(LocalBackupRestoreEvents.Cancel) }
    ) {
      Text(text = stringResource(android.R.string.cancel))
    }
  }
}

@Composable
private fun BackupOptionCard(
  icon: @Composable () -> Unit,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ),
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(16.dp)
    ) {
      icon()

      Spacer(modifier = Modifier.width(16.dp))

      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun PreparingContent(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 24.dp)
      .testTag(TestTags.LOCAL_BACKUP_RESTORE_SCREEN),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    CircularProgressIndicator(
      modifier = Modifier.size(48.dp)
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = stringResource(R.string.LocalBackupRestoreScreen__preparing_restore),
      style = MaterialTheme.typography.bodyLarge
    )
  }
}

@Composable
private fun InProgressContent(
  progressFraction: Float,
  onEvent: (LocalBackupRestoreEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 24.dp)
      .testTag(TestTags.LOCAL_BACKUP_RESTORE_SCREEN),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Text(
      text = stringResource(R.string.LocalBackupRestoreScreen__restoring_backup),
      style = MaterialTheme.typography.headlineMedium,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = stringResource(R.string.LocalBackupRestoreScreen__restoring_description),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(32.dp))

    LinearProgressIndicator(
      progress = { progressFraction },
      modifier = Modifier
        .fillMaxWidth()
        .testTag(TestTags.LOCAL_BACKUP_RESTORE_PROGRESS_BAR)
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "${(progressFraction * 100).toInt()}%",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(32.dp))

    TextButton(
      onClick = { onEvent(LocalBackupRestoreEvents.Cancel) }
    ) {
      Text(text = stringResource(android.R.string.cancel))
    }
  }
}

@Composable
private fun ErrorContent(
  errorMessage: String?,
  onEvent: (LocalBackupRestoreEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 24.dp)
      .testTag(TestTags.LOCAL_BACKUP_RESTORE_SCREEN),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Text(
      text = stringResource(R.string.LocalBackupRestoreScreen__restore_failed),
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.error,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = errorMessage ?: stringResource(R.string.LocalBackupRestoreScreen__restore_failed_description),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(32.dp))

    OutlinedButton(
      onClick = { onEvent(LocalBackupRestoreEvents.PickBackupFolder) },
      modifier = Modifier.fillMaxWidth()
    ) {
      Text(text = stringResource(R.string.LocalBackupRestoreScreen__try_again))
    }

    Spacer(modifier = Modifier.height(16.dp))

    TextButton(
      onClick = { onEvent(LocalBackupRestoreEvents.Cancel) }
    ) {
      Text(text = stringResource(android.R.string.cancel))
    }
  }
}

@AllDevicePreviews
@Composable
private fun LocalBackupRestoreScreenSelectFolderPreview() {
  Previews.Preview {
    LocalBackupRestoreScreen(
      state = LocalBackupRestoreState(restorePhase = LocalBackupRestoreState.RestorePhase.SelectFolder),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun LocalBackupRestoreScreenBackupFoundPreview() {
  Previews.Preview {
    LocalBackupRestoreScreen(
      state = LocalBackupRestoreState(
        restorePhase = LocalBackupRestoreState.RestorePhase.BackupFound,
        backupInfo = LocalBackupInfo(
          type = LocalBackupInfo.BackupType.V2,
          date = LocalDateTime.of(2026, 3, 15, 14, 30, 0),
          name = "signal-backup-2026-03-15-14-30-00",
          uri = Uri.EMPTY,
          sizeBytes = 511.mebiBytes.bytes
        )
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun LocalBackupRestoreScreenBackupFoundLegacyPreview() {
  Previews.Preview {
    LocalBackupRestoreScreen(
      state = LocalBackupRestoreState(
        restorePhase = LocalBackupRestoreState.RestorePhase.BackupFound,
        backupInfo = LocalBackupInfo(
          type = LocalBackupInfo.BackupType.V1,
          date = LocalDateTime.of(2026, 3, 15, 14, 30, 0),
          name = "signal-2026-03-15-14-30-00.backup",
          uri = Uri.EMPTY,
          sizeBytes = 1_482_184_499
        )
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun BackupPickerBottomSheetContentPreview() {
  Previews.BottomSheetPreview {
    val backups = listOf(
      LocalBackupInfo(
        type = LocalBackupInfo.BackupType.V2,
        date = LocalDateTime.of(2026, 3, 24, 3, 38, 0),
        name = "signal-backup-2026-03-24-03-38-00",
        uri = Uri.EMPTY,
        sizeBytes = 1_482_184_499
      ),
      LocalBackupInfo(
        type = LocalBackupInfo.BackupType.V2,
        date = LocalDateTime.of(2024, 8, 13, 3, 21, 0),
        name = "signal-backup-2024-08-13-03-21-00",
        uri = Uri.EMPTY,
        sizeBytes = 1_439_432_704
      )
    )
    BackupPickerSheetContent(
      allBackups = backups,
      selected = backups[1],
      onSelect = {},
      onConfirm = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun LocalBackupRestoreScreenNoBackupFoundPreview() {
  Previews.Preview {
    LocalBackupRestoreScreen(
      state = LocalBackupRestoreState(restorePhase = LocalBackupRestoreState.RestorePhase.NoBackupFound),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun LocalBackupRestoreScreenInProgressPreview() {
  Previews.Preview {
    LocalBackupRestoreScreen(
      state = LocalBackupRestoreState(restorePhase = LocalBackupRestoreState.RestorePhase.InProgress, progressFraction = 0.65f),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun LocalBackupRestoreScreenErrorPreview() {
  Previews.Preview {
    LocalBackupRestoreScreen(
      state = LocalBackupRestoreState(restorePhase = LocalBackupRestoreState.RestorePhase.Error, errorMessage = "Backup file is corrupted"),
      onEvent = {}
    )
  }
}

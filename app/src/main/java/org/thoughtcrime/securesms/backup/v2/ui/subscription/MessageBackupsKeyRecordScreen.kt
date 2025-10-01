/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import android.content.Context
import androidx.annotation.UiContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.backups.remote.BackupKeySaveState
import org.thoughtcrime.securesms.fonts.MonoTypeface
import org.thoughtcrime.securesms.util.storage.AndroidCredentialRepository
import org.thoughtcrime.securesms.util.storage.CredentialManagerError
import org.thoughtcrime.securesms.util.storage.CredentialManagerResult
import org.signal.core.ui.R as CoreUiR

@Stable
sealed interface MessageBackupsKeyRecordMode {
  data class Next(val onNextClick: () -> Unit) : MessageBackupsKeyRecordMode
  data class CreateNewKey(
    val onCreateNewKeyClick: () -> Unit,
    val onTurnOffAndDownloadClick: () -> Unit,
    val isOptimizedStorageEnabled: Boolean
  ) : MessageBackupsKeyRecordMode
}

/**
 * Screen displaying the backup key allowing the user to write it down
 * or copy it.
 */
@Composable
fun MessageBackupsKeyRecordScreen(
  backupKey: String,
  keySaveState: BackupKeySaveState?,
  canOpenPasswordManagerSettings: Boolean,
  onNavigationClick: () -> Unit = {},
  onCopyToClipboardClick: (String) -> Unit = {},
  onRequestSaveToPasswordManager: () -> Unit = {},
  onConfirmSaveToPasswordManager: () -> Unit = {},
  onSaveToPasswordManagerComplete: (CredentialManagerResult) -> Unit = {},
  onGoToPasswordManagerSettingsClick: () -> Unit = {},
  mode: MessageBackupsKeyRecordMode = MessageBackupsKeyRecordMode.Next(onNextClick = {})
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val backupKeyString = remember(backupKey) {
    backupKey.chunked(4).joinToString("  ")
  }

  Scaffolds.Settings(
    title = "",
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
    onNavigationClick = onNavigationClick,
    snackbarHost = { Snackbars.Host(snackbarHostState = snackbarHostState) }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .padding(paddingValues)
        .padding(horizontal = dimensionResource(CoreUiR.dimen.gutter))
    ) {
      Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        LazyColumn(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .weight(1f)
            .testTag("message-backups-key-record-screen-lazy-column")
        ) {
          item {
            Image(
              imageVector = ImageVector.vectorResource(R.drawable.image_signal_backups_lock),
              contentDescription = null,
              modifier = Modifier
                .padding(top = 24.dp)
                .size(80.dp)
            )
          }

          item {
            Text(
              text = stringResource(R.string.MessageBackupsKeyRecordScreen__record_your_backup_key),
              style = MaterialTheme.typography.headlineMedium,
              modifier = Modifier.padding(top = 16.dp)
            )
          }

          item {
            Text(
              text = stringResource(R.string.MessageBackupsKeyRecordScreen__this_key_is_required_to_recover),
              textAlign = TextAlign.Center,
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 12.dp)
            )
          }

          item {
            Box(
              modifier = Modifier
                .padding(top = 24.dp, bottom = 16.dp)
                .background(
                  color = SignalTheme.colors.colorSurface1,
                  shape = RoundedCornerShape(10.dp)
                )
                .padding(24.dp)
            ) {
              Text(
                text = backupKeyString,
                style = MaterialTheme.typography.bodyLarge
                  .copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight(400),
                    letterSpacing = 1.44.sp,
                    lineHeight = 36.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = MonoTypeface.fontFamily()
                  )
              )
            }
          }

          item {
            Buttons.Small(
              onClick = { onCopyToClipboardClick(backupKeyString) }
            ) {
              Text(
                text = stringResource(R.string.MessageBackupsKeyRecordScreen__copy_to_clipboard)
              )
            }
          }

          if (AndroidCredentialRepository.isCredentialManagerSupported) {
            item {
              Buttons.Small(
                onClick = { onRequestSaveToPasswordManager() }
              ) {
                Text(
                  text = stringResource(R.string.MessageBackupsKeyRecordScreen__save_to_password_manager)
                )
              }
            }
          }
        }

        if (mode is MessageBackupsKeyRecordMode.Next) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 24.dp)
          ) {
            Buttons.LargeTonal(
              onClick = mode.onNextClick,
              modifier = Modifier.align(Alignment.BottomEnd)
            ) {
              Text(
                text = stringResource(R.string.MessageBackupsKeyRecordScreen__next)
              )
            }
          }
        } else if (mode is MessageBackupsKeyRecordMode.CreateNewKey) {
          CreateNewKeyButton(mode)
        }
      }

      when (keySaveState) {
        is BackupKeySaveState.RequestingConfirmation -> {
          Dialogs.SimpleAlertDialog(
            title = stringResource(R.string.MessageBackupsKeyRecordScreen__confirm_save_to_password_manager_title),
            body = stringResource(R.string.MessageBackupsKeyRecordScreen__confirm_save_to_password_manager_body),
            dismiss = stringResource(android.R.string.cancel),
            onDismiss = { onSaveToPasswordManagerComplete(CredentialManagerResult.UserCanceled) },
            confirm = stringResource(R.string.MessageBackupsKeyRecordScreen__continue),
            onConfirm = onConfirmSaveToPasswordManager
          )
        }

        is BackupKeySaveState.AwaitingCredentialManager -> {
          val context = LocalContext.current
          LaunchedEffect(keySaveState) {
            val result = saveKeyToCredentialManager(context, backupKey)
            onSaveToPasswordManagerComplete(result)
          }
        }

        is BackupKeySaveState.Success -> {
          val snackbarMessage = stringResource(R.string.MessageBackupsKeyRecordScreen__save_to_password_manager_success)
          LaunchedEffect(keySaveState) {
            snackbarHostState.showSnackbar(snackbarMessage)
          }
        }

        is BackupKeySaveState.Error -> BackupKeySaveErrorDialog(
          error = keySaveState,
          showPasswordManagerSettingsButton = canOpenPasswordManagerSettings,
          onGoToPasswordManagerSettingsClick = onGoToPasswordManagerSettingsClick,
          onDismiss = { onSaveToPasswordManagerComplete(CredentialManagerResult.UserCanceled) }
        )

        null -> Unit
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateNewKeyButton(
  mode: MessageBackupsKeyRecordMode.CreateNewKey
) {
  var displayBottomSheet by remember { mutableStateOf(false) }
  var displayDialog by remember { mutableStateOf(false) }

  TextButton(
    onClick = { displayBottomSheet = true },
    modifier = Modifier
      .padding(bottom = 24.dp)
      .horizontalGutters()
      .fillMaxWidth()
      .requiredWidthIn(min = Dp.Unspecified, max = 264.dp)
  ) {
    Text(text = stringResource(R.string.MessageBackupsKeyRecordScreen__create_new_key))
  }

  if (displayDialog) {
    DownloadMediaDialog(
      onTurnOffAndDownloadClick = mode.onTurnOffAndDownloadClick,
      onCancelClick = { displayDialog = false }
    )
  }

  if (displayBottomSheet) {
    ModalBottomSheet(
      onDismissRequest = { displayBottomSheet = false },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
      CreateNewBackupKeySheetContent(
        onContinueClick = {
          if (mode.isOptimizedStorageEnabled) {
            displayDialog = true
          } else {
            mode.onCreateNewKeyClick()
          }
        },
        onCancelClick = { displayBottomSheet = false }
      )
    }
  }
}

@Composable
private fun BackupKeySaveErrorDialog(
  error: BackupKeySaveState.Error,
  showPasswordManagerSettingsButton: Boolean,
  onGoToPasswordManagerSettingsClick: () -> Unit = {},
  onDismiss: () -> Unit = {}
) {
  val title: String
  val message: String
  when (error.errorType) {
    is CredentialManagerError.MissingCredentialManager -> {
      title = stringResource(R.string.MessageBackupsKeyRecordScreen__cant_save_to_password_manager_title)
      message = stringResource(R.string.MessageBackupsKeyRecordScreen__missing_password_manager_message)
    }

    is CredentialManagerError.SavePromptDisabled -> {
      title = stringResource(R.string.MessageBackupsKeyRecordScreen__cant_save_to_password_manager_title)
      message = stringResource(R.string.MessageBackupsKeyRecordScreen__password_save_prompt_disabled_message)
    }

    is CredentialManagerError.Unexpected -> return
  }

  if (showPasswordManagerSettingsButton) {
    Dialogs.SimpleAlertDialog(
      title = title,
      body = message,
      confirm = stringResource(R.string.MessageBackupsKeyRecordScreen__go_to_settings),
      onConfirm = { onGoToPasswordManagerSettingsClick() },
      dismiss = stringResource(android.R.string.cancel),
      onDismiss = onDismiss
    )
  } else {
    Dialogs.SimpleMessageDialog(
      title = title,
      message = message,
      dismiss = stringResource(android.R.string.ok),
      onDismiss = onDismiss
    )
  }
}

@Composable
private fun ColumnScope.CreateNewBackupKeySheetContent(
  onContinueClick: () -> Unit = {},
  onCancelClick: () -> Unit = {}
) {
  Image(
    imageVector = ImageVector.vectorResource(R.drawable.image_signal_backups_key),
    contentDescription = null,
    modifier = Modifier
      .align(Alignment.CenterHorizontally)
      .padding(top = 38.dp, bottom = 18.dp)
      .size(80.dp)
  )

  Text(
    text = stringResource(R.string.MessageBackupsKeyRecordScreen__create_a_new_backup_key),
    style = MaterialTheme.typography.titleLarge,
    textAlign = TextAlign.Center,
    modifier = Modifier
      .align(Alignment.CenterHorizontally)
      .padding(bottom = 12.dp)
      .horizontalGutters()
  )

  Text(
    text = stringResource(R.string.MessageBackupsKeyRecordScreen__creating_a_new_key_is_only_necessary),
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier
      .padding(bottom = 91.dp, start = 36.dp, end = 36.dp)
      .align(Alignment.CenterHorizontally)
  )

  Buttons.LargeTonal(
    onClick = onContinueClick,
    modifier = Modifier
      .padding(bottom = 16.dp)
      .fillMaxWidth()
      .requiredWidthIn(min = Dp.Unspecified, max = 220.dp)
      .align(Alignment.CenterHorizontally)
  ) {
    Text(text = stringResource(R.string.MessageBackupsKeyRecordScreen__continue))
  }

  TextButton(
    onClick = onCancelClick,
    modifier = Modifier
      .padding(bottom = 48.dp)
      .fillMaxWidth()
      .requiredWidthIn(min = Dp.Unspecified, max = 220.dp)
      .align(Alignment.CenterHorizontally)
  ) {
    Text(text = stringResource(android.R.string.cancel))
  }
}

@Composable
private fun DownloadMediaDialog(
  onTurnOffAndDownloadClick: () -> Unit = {},
  onCancelClick: () -> Unit = {}
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.MessageBackupsKeyRecordScreen__download_media),
    body = stringResource(R.string.MessageBackupsKeyRecordScreen__to_create_a_new_backup_key),
    confirm = stringResource(R.string.MessageBackupsKeyRecordScreen__turn_off_and_download),
    dismiss = stringResource(android.R.string.cancel),
    onConfirm = onTurnOffAndDownloadClick,
    onDeny = onCancelClick
  )
}

private suspend fun saveKeyToCredentialManager(
  @UiContext activityContext: Context,
  backupKey: String
): CredentialManagerResult {
  return AndroidCredentialRepository.saveCredential(
    activityContext = activityContext,
    username = activityContext.getString(R.string.MessageBackupsKeyRecordScreen__backup_key_password_manager_id),
    password = backupKey
  )
}

@DayNightPreviews
@Composable
private fun MessageBackupsKeyRecordScreenPreview() {
  Previews.Preview {
    MessageBackupsKeyRecordScreen(
      backupKey = (0 until 63).map { (('A'..'Z') + ('0'..'9')).random() }.joinToString("") + "0",
      keySaveState = null,
      canOpenPasswordManagerSettings = true,
      mode = MessageBackupsKeyRecordMode.CreateNewKey(
        onCreateNewKeyClick = {},
        onTurnOffAndDownloadClick = {},
        isOptimizedStorageEnabled = true
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun SaveKeyConfirmationDialogPreview() {
  Previews.Preview {
    MessageBackupsKeyRecordScreen(
      backupKey = (0 until 63).map { (('A'..'Z') + ('0'..'9')).random() }.joinToString("") + "0",
      keySaveState = BackupKeySaveState.RequestingConfirmation,
      canOpenPasswordManagerSettings = true,
      mode = MessageBackupsKeyRecordMode.Next(onNextClick = {})
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@DayNightPreviews
@Composable
private fun CreateNewBackupKeySheetContentPreview() {
  Previews.BottomSheetPreview {
    Column {
      CreateNewBackupKeySheetContent()
    }
  }
}

@DayNightPreviews
@Composable
private fun DownloadMediaDialogPreview() {
  Previews.Preview {
    DownloadMediaDialog()
  }
}

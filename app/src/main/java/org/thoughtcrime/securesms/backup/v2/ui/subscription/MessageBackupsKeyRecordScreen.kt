/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import android.content.Context
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.backups.remote.BackupKeyCredentialManagerHandler
import org.thoughtcrime.securesms.components.settings.app.backups.remote.BackupKeySaveState
import org.thoughtcrime.securesms.components.settings.app.backups.remote.CredentialManagerError
import org.thoughtcrime.securesms.components.settings.app.backups.remote.CredentialManagerResult
import org.thoughtcrime.securesms.fonts.MonoTypeface
import org.signal.core.ui.R as CoreUiR

private const val TAG = "MessageBackupsKeyRecordScreen"

/**
 * Screen displaying the backup key allowing the user to write it down
 * or copy it.
 */
@Composable
fun MessageBackupsKeyRecordScreen(
  backupKey: String,
  keySaveState: BackupKeySaveState?,
  onNavigationClick: () -> Unit = {},
  onCopyToClipboardClick: (String) -> Unit = {},
  onRequestSaveToPasswordManager: () -> Unit = {},
  onConfirmSaveToPasswordManager: () -> Unit = {},
  onSaveToPasswordManagerComplete: (CredentialManagerResult) -> Unit = {},
  onNextClick: () -> Unit = {},
  onGoToDeviceSettingsClick: () -> Unit = {}
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
              painter = painterResource(R.drawable.image_signal_backups_lock),
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

          if (BackupKeyCredentialManagerHandler.isCredentialManagerSupported) {
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

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
        ) {
          Buttons.LargeTonal(
            onClick = onNextClick,
            modifier = Modifier.align(Alignment.BottomEnd)
          ) {
            Text(
              text = stringResource(R.string.MessageBackupsKeyRecordScreen__next)
            )
          }
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

        is BackupKeySaveState.Error -> {
          when (keySaveState.errorType) {
            is CredentialManagerError.MissingCredentialManager -> {
              Dialogs.SimpleAlertDialog(
                title = stringResource(R.string.MessageBackupsKeyRecordScreen__cant_save_to_password_manager_title),
                body = stringResource(R.string.MessageBackupsKeyRecordScreen__missing_password_manager_message),
                confirm = stringResource(R.string.MessageBackupsKeyRecordScreen__go_to_settings),
                onConfirm = { onGoToDeviceSettingsClick() },
                dismiss = stringResource(android.R.string.cancel),
                onDismiss = { onSaveToPasswordManagerComplete(CredentialManagerResult.UserCanceled) }
              )
            }

            is CredentialManagerError.SavePromptDisabled -> {
              Dialogs.SimpleAlertDialog(
                title = stringResource(R.string.MessageBackupsKeyRecordScreen__cant_save_to_password_manager_title),
                body = stringResource(R.string.MessageBackupsKeyRecordScreen__missing_password_manager_message),
                confirm = stringResource(R.string.MessageBackupsKeyRecordScreen__go_to_settings),
                onConfirm = { onGoToDeviceSettingsClick() },
                dismiss = stringResource(android.R.string.cancel),
                onDismiss = { onSaveToPasswordManagerComplete(CredentialManagerResult.UserCanceled) }
              )
            }

            is CredentialManagerError.Unexpected -> Unit
          }
        }

        null -> Unit
      }
    }
  }
}

private suspend fun saveKeyToCredentialManager(
  context: Context,
  backupKey: String
): CredentialManagerResult {
  return try {
    CredentialManager.create(context)
      .createCredential(
        context = context,
        request = CreatePasswordRequest(
          id = context.getString(R.string.RemoteBackupsSettingsFragment__signal_backups),
          password = backupKey,
          preferImmediatelyAvailableCredentials = false,
          isAutoSelectAllowed = false
        )
      )
    CredentialManagerResult.Success
  } catch (e: Exception) {
    when (e) {
      is CreateCredentialCancellationException -> CredentialManagerResult.UserCanceled
      is CreateCredentialInterruptedException -> CredentialManagerResult.Interrupted(e)
      is CreateCredentialNoCreateOptionException -> CredentialManagerError.MissingCredentialManager(e)
      is CreateCredentialUnknownException -> {
        when {
          Build.VERSION.SDK_INT <= 33 && e.message?.contains("[28431]") == true -> {
            // This error only impacts Android 13 and earlier, when Google is the designated autofill provider. The error can be safely disregarded, since users
            // will receive a save prompt from autofill and the password will be stored in Google Password Manager, which syncs with the Credential Manager API.
            Log.d(TAG, "Disregarding CreateCredentialUnknownException and treating credential creation as success: \"${e.message}\".")
            CredentialManagerResult.Success
          }

          e.message?.contains("[28434]") == true -> {
            Log.w(TAG, "Detected MissingCredentialManager error based on CreateCredentialUnknownException message: \"${e.message}\"")
            CredentialManagerError.MissingCredentialManager(e)
          }

          e.message?.contains("[28435]") == true -> {
            Log.w(TAG, "CreateCredentialUnknownException: \"${e.message}\"")
            CredentialManagerError.SavePromptDisabled(e)
          }

          else -> CredentialManagerError.Unexpected(e)
        }
      }

      else -> CredentialManagerError.Unexpected(e)
    }
  }
}

@SignalPreview
@Composable
private fun MessageBackupsKeyRecordScreenPreview() {
  Previews.Preview {
    MessageBackupsKeyRecordScreen(
      backupKey = (0 until 63).map { (('A'..'Z') + ('0'..'9')).random() }.joinToString("") + "0",
      keySaveState = null
    )
  }
}

@SignalPreview
@Composable
private fun SaveKeyConfirmationDialogPreview() {
  Previews.Preview {
    MessageBackupsKeyRecordScreen(
      backupKey = (0 until 63).map { (('A'..'Z') + ('0'..'9')).random() }.joinToString("") + "0",
      keySaveState = BackupKeySaveState.RequestingConfirmation
    )
  }
}

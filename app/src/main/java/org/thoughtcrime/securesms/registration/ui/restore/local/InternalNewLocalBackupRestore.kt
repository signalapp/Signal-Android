/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import org.signal.core.models.AccountEntropyPool
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.fonts.MonoTypeface
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.restore.AccountEntropyPoolVerification
import org.thoughtcrime.securesms.registration.ui.restore.BackupKeyVisualTransformation
import org.thoughtcrime.securesms.registration.ui.restore.attachBackupKeyAutoFillHelper
import org.thoughtcrime.securesms.registration.ui.restore.backupKeyAutoFillHelper
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen
import org.thoughtcrime.securesms.util.StorageUtil

/**
 * Internal only registration screen to collect backup folder and AEP. Actual restore will happen
 * post-registration when the app re-routes to [org.thoughtcrime.securesms.restore.RestoreActivity] and then
 * [InternalNewLocalRestoreActivity]. Yay implicit navigation!
 */
class InternalNewLocalBackupRestore : ComposeFragment() {

  private val TAG = Log.tag(InternalNewLocalBackupRestore::class)

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()

  private lateinit var chooseBackupLocationLauncher: ActivityResultLauncher<Intent>
  private val directoryFlow = SignalStore.backup.newLocalBackupsDirectoryFlow.map { if (Build.VERSION.SDK_INT >= 24 && it != null) StorageUtil.getDisplayPath(requireContext(), Uri.parse(it)) else it }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    chooseBackupLocationLauncher = registerForActivityResult(
      ActivityResultContracts.StartActivityForResult()
    ) { result ->
      if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
        handleBackupLocationSelected(result.data!!.data!!)
      } else {
        Log.w(TAG, "Backup location selection cancelled or failed")
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val selectedDirectory: String? by directoryFlow.collectAsStateWithLifecycle(SignalStore.backup.newLocalBackupsDirectory)

    InternalNewLocalBackupRestoreScreen(
      selectedDirectory = selectedDirectory,
      callback = CallbackImpl()
    )
  }

  private fun launchBackupDirectoryPicker() {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)

    if (Build.VERSION.SDK_INT >= 26) {
      val currentDirectory = SignalStore.backup.newLocalBackupsDirectory
      if (currentDirectory != null) {
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(currentDirectory))
      }
    }

    intent.addFlags(
      Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_READ_URI_PERMISSION
    )

    try {
      Log.d(TAG, "Launching backup directory picker")
      chooseBackupLocationLauncher.launch(intent)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to launch backup directory picker", e)
      Toast.makeText(requireContext(), R.string.BackupDialog_no_file_picker_available, Toast.LENGTH_LONG).show()
    }
  }

  private fun handleBackupLocationSelected(uri: Uri) {
    Log.i(TAG, "Backup location selected: $uri")

    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)

    SignalStore.backup.newLocalBackupsDirectory = uri.toString()

    Toast.makeText(requireContext(), "Directory selected: $uri", Toast.LENGTH_SHORT).show()
  }

  private inner class CallbackImpl : Callback {
    override fun onSelectDirectoryClick() {
      launchBackupDirectoryPicker()
    }

    override fun onRestoreClick(backupKey: String) {
      sharedViewModel.registerWithBackupKey(
        context = requireContext(),
        backupKey = backupKey,
        e164 = null,
        pin = null,
        aciIdentityKeyPair = null,
        pniIdentityKeyPair = null
      )
    }
  }
}

private interface Callback {
  fun onSelectDirectoryClick()
  fun onRestoreClick(backupKey: String)

  object Empty : Callback {
    override fun onSelectDirectoryClick() = Unit
    override fun onRestoreClick(backupKey: String) = Unit
  }
}

@Composable
private fun InternalNewLocalBackupRestoreScreen(
  selectedDirectory: String? = null,
  callback: Callback
) {
  var backupKey by remember { mutableStateOf("") }
  var isBackupKeyValid by remember { mutableStateOf(false) }
  var aepValidationError by remember { mutableStateOf<AccountEntropyPoolVerification.AEPValidationError?>(null) }

  val visualTransform = remember { BackupKeyVisualTransformation(chunkSize = 4) }
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusRequester = remember { FocusRequester() }
  var requestFocus by remember { mutableStateOf(true) }

  val autoFillHelper = backupKeyAutoFillHelper { newValue ->
    backupKey = newValue
    val (valid, error) = AccountEntropyPoolVerification.verifyAEP(
      backupKey = newValue,
      changed = true,
      previousAEPValidationError = aepValidationError
    )
    isBackupKeyValid = valid
    aepValidationError = error
  }

  RegistrationScreen(
    title = "Local Backup V2 Restore",
    subtitle = null,
    bottomContent = {
      Buttons.LargeTonal(
        onClick = { callback.onRestoreClick(backupKey) },
        enabled = isBackupKeyValid && aepValidationError == null && selectedDirectory != null,
        modifier = Modifier.align(Alignment.CenterEnd)
      ) {
        Text(text = "Restore")
      }
    }
  ) {
    Column(
      modifier = Modifier.fillMaxWidth()
    ) {
      DirectorySelectionRow(
        selectedDirectory = selectedDirectory,
        onClick = callback::onSelectDirectoryClick
      )

      Spacer(modifier = Modifier.height(24.dp))

      TextField(
        value = backupKey,
        onValueChange = { value ->
          val newKey = AccountEntropyPool.removeIllegalCharacters(value).take(AccountEntropyPool.LENGTH + 16).lowercase()
          val (valid, error) = AccountEntropyPoolVerification.verifyAEP(
            backupKey = newKey,
            changed = backupKey != newKey,
            previousAEPValidationError = aepValidationError
          )
          backupKey = newKey
          isBackupKeyValid = valid
          aepValidationError = error
          autoFillHelper.onValueChanged(newKey)
        },
        label = {
          Text(text = stringResource(id = R.string.EnterBackupKey_backup_key))
        },
        textStyle = LocalTextStyle.current.copy(
          fontFamily = MonoTypeface.fontFamily(),
          lineHeight = 36.sp
        ),
        keyboardOptions = KeyboardOptions(
          keyboardType = KeyboardType.Password,
          capitalization = KeyboardCapitalization.None,
          imeAction = ImeAction.Done,
          autoCorrectEnabled = false
        ),
        keyboardActions = KeyboardActions(
          onDone = {
            if (isBackupKeyValid && aepValidationError == null && selectedDirectory != null) {
              keyboardController?.hide()
              callback.onRestoreClick(backupKey)
            }
          }
        ),
        supportingText = { aepValidationError?.ValidationErrorMessage() },
        isError = aepValidationError != null,
        minLines = 4,
        visualTransformation = visualTransform,
        modifier = Modifier
          .fillMaxWidth()
          .focusRequester(focusRequester)
          .attachBackupKeyAutoFillHelper(autoFillHelper)
          .onGloballyPositioned {
            if (requestFocus) {
              focusRequester.requestFocus()
              requestFocus = false
            }
          }
      )
    }
  }
}

@Composable
private fun DirectorySelectionRow(
  selectedDirectory: String?,
  onClick: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp)
  ) {
    Text(
      text = "Select Backup Directory",
      style = MaterialTheme.typography.bodyLarge
    )
    Text(
      text = selectedDirectory ?: "No directory selected",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun AccountEntropyPoolVerification.AEPValidationError.ValidationErrorMessage() {
  when (this) {
    is AccountEntropyPoolVerification.AEPValidationError.TooLong -> Text(text = stringResource(R.string.EnterBackupKey_too_long_error, this.count, this.max))
    AccountEntropyPoolVerification.AEPValidationError.Invalid -> Text(text = stringResource(R.string.EnterBackupKey_invalid_backup_key_error))
    AccountEntropyPoolVerification.AEPValidationError.Incorrect -> Text(text = stringResource(R.string.EnterBackupKey_incorrect_backup_key_error))
  }
}

@DayNightPreviews
@Composable
private fun InternalNewLocalBackupRestoreScreenPreview() {
  Previews.Preview {
    InternalNewLocalBackupRestoreScreen(
      selectedDirectory = "/storage/emulated/0/Signal/Backups",
      callback = Callback.Empty
    )
  }
}

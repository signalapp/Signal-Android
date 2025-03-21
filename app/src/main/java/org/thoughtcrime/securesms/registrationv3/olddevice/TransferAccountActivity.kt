/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.olddevice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BiometricDeviceAuthentication
import org.thoughtcrime.securesms.BiometricDeviceLockContract
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.devicetransfer.olddevice.OldDeviceTransferActivity
import org.thoughtcrime.securesms.fonts.SignalSymbols
import org.thoughtcrime.securesms.fonts.SignalSymbols.SignalSymbol
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registrationv3.data.QuickRegistrationRepository
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.viewModel
import org.whispersystems.signalservice.api.provisioning.RestoreMethod

/**
 * Launched after scanning QR code from new device to start the transfer/reregistration process from
 * old phone to new phone.
 */
class TransferAccountActivity : PassphraseRequiredActivity() {

  companion object {
    private val TAG = Log.tag(TransferAccountActivity::class)

    private const val KEY_URI = "URI"

    const val LEARN_MORE_URL = "https://support.signal.org/hc/articles/360007059752-Backup-and-Restore-Messages"

    fun intent(context: Context, uri: String): Intent {
      return Intent(context, TransferAccountActivity::class.java).apply {
        putExtra(KEY_URI, uri)
      }
    }
  }

  private val theme: DynamicTheme = DynamicNoActionBarTheme()

  private val viewModel: TransferAccountViewModel by viewModel {
    TransferAccountViewModel(intent.getStringExtra(KEY_URI)!!)
  }

  private lateinit var biometricAuth: BiometricDeviceAuthentication
  private lateinit var biometricDeviceLockLauncher: ActivityResultLauncher<String>

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)
    theme.onCreate(this)

    if (!SignalStore.account.isRegistered) {
      finish()
    }

    biometricDeviceLockLauncher = registerForActivityResult(BiometricDeviceLockContract()) { result: Int ->
      if (result == BiometricDeviceAuthentication.AUTHENTICATED) {
        Log.i(TAG, "Device authentication succeeded via contract")
        transferAccount()
      }
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setAllowedAuthenticators(BiometricDeviceAuthentication.ALLOWED_AUTHENTICATORS)
      .setTitle(getString(R.string.TransferAccount_unlock_to_transfer))
      .setConfirmationRequired(true)
      .build()

    biometricAuth = BiometricDeviceAuthentication(
      BiometricManager.from(this),
      BiometricPrompt(this, BiometricAuthenticationListener()),
      promptInfo
    )

    lifecycleScope.launch {
      val restoreMethodSelected = viewModel
        .state
        .mapNotNull { it.restoreMethodSelected }
        .firstOrNull()

      when (restoreMethodSelected) {
        RestoreMethod.DEVICE_TRANSFER -> {
          startActivities(
            arrayOf(
              MainActivity.clearTop(this@TransferAccountActivity),
              Intent(this@TransferAccountActivity, OldDeviceTransferActivity::class.java)
            )
          )
        }

        RestoreMethod.REMOTE_BACKUP,
        RestoreMethod.LOCAL_BACKUP,
        RestoreMethod.DECLINE,
        null -> startActivity(MainActivity.clearTop(this@TransferAccountActivity))
      }
    }

    setContent {
      val state by viewModel.state.collectAsState()

      SignalTheme {
        TransferToNewDevice(
          state = state,
          onTransferAccount = this::authenticate,
          onContinueOnOtherDeviceDismiss = {
            finish()
            viewModel.clearReRegisterResult()
          },
          onErrorDismiss = viewModel::clearReRegisterResult,
          onBackClicked = { finish() }
        )
      }
    }
  }

  override fun onPause() {
    super.onPause()
    biometricAuth.cancelAuthentication()
  }

  override fun onResume() {
    super.onResume()
    theme.onResume(this)
  }

  private fun authenticate() {
    val canAuthenticate = biometricAuth.authenticate(this, true) {
      biometricDeviceLockLauncher.launch(getString(R.string.TransferAccount_unlock_to_transfer))
    }

    if (!canAuthenticate) {
      Log.w(TAG, "Device authentication not available")
      transferAccount()
    }
  }

  private fun transferAccount() {
    Log.d(TAG, "transferAccount()")

    viewModel.transferAccount()
  }

  private inner class BiometricAuthenticationListener : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationError(errorCode: Int, errorString: CharSequence) {
      Log.w(TAG, "Device authentication error: $errorCode")
      onAuthenticationFailed()
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
      Log.i(TAG, "Device authentication succeeded")
      transferAccount()
    }

    override fun onAuthenticationFailed() {
      Log.w(TAG, "Device authentication failed")
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferToNewDevice(
  state: TransferAccountViewModel.TransferAccountState,
  onTransferAccount: () -> Unit = {},
  onContinueOnOtherDeviceDismiss: () -> Unit = {},
  onErrorDismiss: () -> Unit = {},
  onBackClicked: () -> Unit = {}
) {
  Scaffold(
    topBar = { TopAppBarContent(onBackClicked = onBackClicked) }
  ) { contentPadding ->
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .padding(contentPadding)
        .horizontalGutters()
    ) {
      Image(
        painter = painterResource(R.drawable.image_transfer_phones),
        contentDescription = null,
        modifier = Modifier.padding(top = 20.dp, bottom = 28.dp)
      )

      val context = LocalContext.current
      val learnMore = stringResource(id = R.string.TransferAccount_learn_more)
      val fullString = stringResource(id = R.string.TransferAccount_body, learnMore)
      val spanned = SpanUtil.urlSubsequence(fullString, learnMore, TransferAccountActivity.LEARN_MORE_URL)
      Texts.LinkifiedText(
        textWithUrlSpans = spanned,
        onUrlClick = { CommunicationActions.openBrowserLink(context, it) },
        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
      )

      Spacer(modifier = Modifier.height(28.dp))

      AnimatedContent(
        targetState = state.inProgress,
        contentAlignment = Alignment.Center
      ) { inProgress ->
        if (inProgress) {
          CircularProgressIndicator()
        } else {
          Buttons.LargeTonal(
            onClick = onTransferAccount,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(text = stringResource(id = R.string.TransferAccount_button))
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = buildAnnotatedString {
          SignalSymbol(SignalSymbols.Weight.REGULAR, SignalSymbols.Glyph.LOCK)
          append(" ")
          append(stringResource(id = R.string.TransferAccount_messages_e2e))
        },
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
      )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    when (state.reRegisterResult) {
      QuickRegistrationRepository.TransferAccountResult.SUCCESS -> {
        ModalBottomSheet(
          dragHandle = null,
          onDismissRequest = onContinueOnOtherDeviceDismiss,
          sheetState = sheetState
        ) {
          ContinueOnOtherDevice()
        }
      }

      QuickRegistrationRepository.TransferAccountResult.FAILED -> {
        Dialogs.SimpleMessageDialog(
          message = stringResource(R.string.RegistrationActivity_unable_to_connect_to_service),
          dismiss = stringResource(android.R.string.ok),
          onDismiss = onErrorDismiss
        )
      }

      null -> Unit
    }
  }
}

@SignalPreview
@Composable
private fun TransferToNewDevicePreview() {
  Previews.Preview {
    TransferToNewDevice(state = TransferAccountViewModel.TransferAccountState("sgnl://rereg"))
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBarContent(onBackClicked: () -> Unit) {
  TopAppBar(
    title = {
      Text(text = stringResource(R.string.TransferAccount_title))
    },
    navigationIcon = {
      IconButton(onClick = onBackClicked) {
        Icon(
          painter = painterResource(R.drawable.symbol_x_24),
          tint = MaterialTheme.colorScheme.onSurface,
          contentDescription = null
        )
      }
    }
  )
}

/**
 * Shown after successfully sending provisioning message to new device.
 */
@Composable
fun ContinueOnOtherDevice() {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .horizontalGutters()
      .padding(bottom = 54.dp)
  ) {
    BottomSheets.Handle()

    Spacer(modifier = Modifier.height(26.dp))

    Image(
      painter = painterResource(R.drawable.image_other_device),
      contentDescription = null,
      modifier = Modifier.padding(bottom = 20.dp)
    )

    Text(
      text = stringResource(id = R.string.TransferAccount_continue_on_your_other_device),
      style = MaterialTheme.typography.titleLarge
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = stringResource(id = R.string.TransferAccount_continue_on_your_other_device_details),
      style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    )

    Spacer(modifier = Modifier.height(36.dp))

    CircularProgressIndicator(modifier = Modifier.size(44.dp))
  }
}

@SignalPreview
@Composable
private fun ContinueOnOtherDevicePreview() {
  Previews.Preview {
    ContinueOnOtherDevice()
  }
}

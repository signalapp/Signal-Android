/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.olddevice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BiometricDeviceAuthentication
import org.thoughtcrime.securesms.BiometricDeviceLockContract
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.compose.SignalTheme
import org.thoughtcrime.securesms.devicetransfer.olddevice.OldDeviceTransferActivity
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.viewModel
import org.whispersystems.signalservice.api.provisioning.RestoreMethod

/**
 * Launched after scanning QR code from new device to start the transfer/reregistration process from
 * old phone to new phone.
 */
class QuickTransferOldDeviceActivity : PassphraseRequiredActivity() {

  companion object {
    private val TAG = Log.tag(QuickTransferOldDeviceActivity::class)

    private const val KEY_URI = "URI"

    const val LEARN_MORE_URL = "https://support.signal.org/hc/articles/360007059752-Backup-and-Restore-Messages"

    fun intent(context: Context, uri: String): Intent {
      return Intent(context, QuickTransferOldDeviceActivity::class.java).apply {
        putExtra(KEY_URI, uri)
      }
    }
  }

  private val theme: DynamicTheme = DynamicNoActionBarTheme()

  private val viewModel: QuickTransferOldDeviceViewModel by viewModel {
    QuickTransferOldDeviceViewModel(intent.getStringExtra(KEY_URI)!!)
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
        continueTransferOrPromptForBackup()
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
              MainActivity.clearTop(this@QuickTransferOldDeviceActivity),
              Intent(this@QuickTransferOldDeviceActivity, OldDeviceTransferActivity::class.java)
            )
          )
        }

        RestoreMethod.REMOTE_BACKUP,
        RestoreMethod.LOCAL_BACKUP,
        RestoreMethod.DECLINE,
        null -> startActivity(MainActivity.clearTop(this@QuickTransferOldDeviceActivity))
      }
    }

    setContent {
      val state by viewModel.state.collectAsStateWithLifecycle()

      LaunchedEffect(state.performAuthentication) {
        if (state.performAuthentication) {
          authenticate()
          viewModel.clearAttemptAuthentication()
        }
      }

      LaunchedEffect(state.navigateToBackupCreation) {
        if (state.navigateToBackupCreation) {
          startActivity(AppSettingsActivity.remoteBackups(context = this@QuickTransferOldDeviceActivity, forQuickRestore = true))
          viewModel.clearNavigateToBackupCreation()
        }
      }

      SignalTheme {
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides this) {
          TransferAccountNavHost(
            viewModel = viewModel,
            onFinished = { finish() }
          )
        }
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
      continueTransferOrPromptForBackup()
    }
  }

  private fun continueTransferOrPromptForBackup() {
    Log.d(TAG, "transferAccount()")

    viewModel.onTransferAccountAttempted()
  }

  private inner class BiometricAuthenticationListener : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationError(errorCode: Int, errorString: CharSequence) {
      Log.w(TAG, "Device authentication error: $errorCode")
      onAuthenticationFailed()
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
      Log.i(TAG, "Device authentication succeeded")
      continueTransferOrPromptForBackup()
    }

    override fun onAuthenticationFailed() {
      Log.w(TAG, "Device authentication failed")
    }
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.twofactor.verify

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.signal.appsettings.twofactorverify.PasskeyAssertionParameters
import org.signal.appsettings.twofactorverify.TwoFactorVerifyAction
import org.signal.appsettings.twofactorverify.TwoFactorVerifyEvent
import org.signal.appsettings.twofactorverify.TwoFactorVerifyMethod
import org.signal.appsettings.twofactorverify.TwoFactorVerifyScreen
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.util.ViewUtil
import org.signal.core.ui.viewModel
import org.signal.core.util.logging.Log
import org.signal.passwordmanager.PasskeyAssertionResult
import org.signal.passwordmanager.SignalPasskeyManager
import org.thoughtcrime.securesms.PassphraseRequiredActivity

/**
 * Hosts [TwoFactorVerifyScreen] for the two-factor reminder megaphone. Finishes with [RESULT_OK] and the verified
 * method in its result once the user proves they still hold one, and with [RESULT_CANCELED] otherwise.
 */
class TwoFactorVerifyActivity : PassphraseRequiredActivity() {

  companion object {
    private val TAG = Log.tag(TwoFactorVerifyActivity::class)

    const val REQUEST_CODE = 1206

    private const val EXTRA_VERIFIED_METHOD = "verified_method"

    @JvmStatic
    fun createIntent(context: Context): Intent {
      return Intent(context, TwoFactorVerifyActivity::class.java)
    }

    /** The method the user verified, read off a [RESULT_OK] result, or null when the result doesn't name one. */
    fun getVerifiedMethod(data: Intent?): TwoFactorVerifyMethod? {
      val name = data?.getStringExtra(EXTRA_VERIFIED_METHOD) ?: return null
      return TwoFactorVerifyMethod.entries.firstOrNull { it.name == name }
    }
  }

  private val viewModel by viewModel {
    TwoFactorVerifyViewModel(arePasskeysSupported = SignalPasskeyManager.isSupported(this))
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)

    setContent {
      val state by viewModel.state.collectAsStateWithLifecycle()

      CollectActions(viewModel.actions) { action -> handleAction(action) }

      SignalTheme {
        TwoFactorVerifyScreen(
          state = state,
          onEvent = viewModel::onEvent
        )
      }
    }
  }

  private fun handleAction(action: TwoFactorVerifyAction) {
    when (action) {
      is TwoFactorVerifyAction.AuthenticateWithPasskey -> {
        assertPasskey(action.parameters)
      }
      is TwoFactorVerifyAction.Verified -> {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_VERIFIED_METHOD, action.method.name))
        finish()
      }
      TwoFactorVerifyAction.Close -> {
        ViewUtil.hideKeyboard(this, window.decorView)
        finish()
      }
    }
  }

  /** Runs the middle leg of the passkey ceremony, which requires an activity context. */
  private fun assertPasskey(parameters: PasskeyAssertionParameters) {
    lifecycleScope.launch {
      val result = try {
        SignalPasskeyManager.getPasskeyAssertion(
          activityContext = this@TwoFactorVerifyActivity,
          rpId = parameters.relyingPartyId,
          challenge = parameters.challenge,
          timeoutSeconds = parameters.timeoutSeconds,
          allowedCredentialIds = parameters.allowedCredentialIds
        )
      } catch (e: CancellationException) {
        Log.w(TAG, "The passkey ceremony was canceled before it could finish.", e)
        viewModel.onEvent(TwoFactorVerifyEvent.PasskeyCeremonyCompleted(PasskeyAssertionResult.UserCanceled))
        throw e
      }

      viewModel.onEvent(TwoFactorVerifyEvent.PasskeyCeremonyCompleted(result))
    }
  }
}

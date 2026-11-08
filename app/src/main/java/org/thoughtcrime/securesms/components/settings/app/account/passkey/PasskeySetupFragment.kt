/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.passkey

import android.os.Bundle
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.signal.appsettings.passkeysetup.PasskeyCreationParameters
import org.signal.appsettings.passkeysetup.PasskeySetupAction
import org.signal.appsettings.passkeysetup.PasskeySetupEvent
import org.signal.appsettings.passkeysetup.PasskeySetupScreen
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.viewModel
import org.signal.core.util.logging.Log
import org.signal.passwordmanager.PasskeyCreationResult
import org.signal.passwordmanager.SignalPasskeyManager
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.account.twofactor.TwoFactorNavArgs
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.signal.appsettings.R as AppSettingsR

/**
 * Explains passkeys before the user sets one up. Carries out the [PasskeySetupAction]s that need an Activity or the
 * nav graph.
 */
class PasskeySetupFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(PasskeySetupFragment::class)
  }

  private val viewModel: PasskeySetupViewModel by viewModel {
    PasskeySetupViewModel(
      repository = PasskeySetupRepository(
        defaultPasskeyName = getString(AppSettingsR.string.AccountSettingsRepository__passkey)
      )
    )
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { action -> handleAction(action) }

    PasskeySetupScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: PasskeySetupAction) {
    when (action) {
      PasskeySetupAction.NavigateBack -> {
        requireActivity().onBackPressedDispatcher.onBackPressed()
      }
      is PasskeySetupAction.OpenSupportArticle -> {
        CommunicationActions.openBrowserLink(requireContext(), action.url)
      }
      is PasskeySetupAction.CreatePasskey -> {
        createPasskey(action.parameters)
      }
      is PasskeySetupAction.NavigateToNameNewPasskey -> {
        findNavController().safeNavigate(
          R.id.action_passkeySetupFragment_to_twoFactorNameFragment,
          Bundle().apply { TwoFactorNavArgs.putNewMethod(this, action.method) }
        )
      }
      PasskeySetupAction.ShowNoPasskeyProvider -> {
        toast(AppSettingsR.string.AccountSettingsFragment__no_passkey_provider)
      }
      PasskeySetupAction.ShowPasskeyCreationFailed -> {
        toast(AppSettingsR.string.AccountSettingsFragment__couldnt_create_passkey)
      }
    }
  }

  /** Runs the middle leg of the passkey ceremony, which requires an activity context. */
  private fun createPasskey(parameters: PasskeyCreationParameters) {
    lifecycleScope.launch {
      val result = try {
        SignalPasskeyManager.createPasskey(
          activityContext = requireActivity(),
          rpId = parameters.relyingPartyId,
          rpName = parameters.relyingPartyName,
          userHandle = parameters.userHandle,
          userName = parameters.userName,
          allowedAlgorithms = parameters.allowedAlgorithms,
          excludeCredentialIds = parameters.excludeCredentialIds
        )
      } catch (e: CancellationException) {
        Log.w(TAG, "The passkey ceremony was canceled before it could finish.", e)
        viewModel.onEvent(PasskeySetupEvent.PasskeyCeremonyCompleted(PasskeyCreationResult.UserCanceled))
        throw e
      }

      viewModel.onEvent(PasskeySetupEvent.PasskeyCeremonyCompleted(result))
    }
  }

  private fun toast(@StringRes message: Int) {
    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
  }
}

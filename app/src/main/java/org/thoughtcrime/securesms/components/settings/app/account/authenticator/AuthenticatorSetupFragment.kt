/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.authenticatorsetup.AuthenticatorSetupAction
import org.signal.appsettings.authenticatorsetup.AuthenticatorSetupEvent
import org.signal.appsettings.authenticatorsetup.AuthenticatorSetupScreen
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.Util
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.signal.appsettings.R as AppSettingsR

/**
 * Walks the user through setting up an authenticator app. Carries out the [AuthenticatorSetupAction]s that need an
 * Activity or the nav graph.
 */
class AuthenticatorSetupFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(AuthenticatorSetupFragment::class)
  }

  private val viewModel: AuthenticatorSetupViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { action -> handleAction(action) }

    AuthenticatorSetupScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: AuthenticatorSetupAction) {
    when (action) {
      AuthenticatorSetupAction.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
      is AuthenticatorSetupAction.LaunchAuthenticatorApp -> launchAuthenticatorApp(action.uri)
      is AuthenticatorSetupAction.CopyKeyToClipboard -> Util.copyToClipboard(requireContext(), action.key)
      AuthenticatorSetupAction.ShowKeyCopied -> toast(AppSettingsR.string.AuthenticatorSetupScreen__copied_to_clipboard)
      AuthenticatorSetupAction.ShowNoAuthenticatorAppFound -> toast(AppSettingsR.string.AuthenticatorSetupScreen__no_authenticator_app_found)
      AuthenticatorSetupAction.NavigateToCodeEntry -> findNavController().safeNavigate(R.id.action_authenticatorSetupFragment_to_authenticatorCodeEntryFragment)
    }
  }

  private fun launchAuthenticatorApp(uri: String) {
    try {
      startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "No app is willing to handle the authenticator setup link.", e)
      viewModel.onEvent(AuthenticatorSetupEvent.NoAuthenticatorAppFound)
    }
  }

  private fun toast(@StringRes message: Int) {
    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
  }
}

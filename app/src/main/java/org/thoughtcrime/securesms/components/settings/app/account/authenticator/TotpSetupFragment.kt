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
import org.signal.appsettings.totpsetup.TotpSetupAction
import org.signal.appsettings.totpsetup.TotpSetupEvent
import org.signal.appsettings.totpsetup.TotpSetupScreen
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.Util
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.signal.appsettings.R as AppSettingsR

/**
 * Walks the user through setting up an authenticator app. Carries out the [TotpSetupAction]s that need an
 * Activity or the nav graph.
 */
class TotpSetupFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(TotpSetupFragment::class)
  }

  private val viewModel: TotpSetupViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { action -> handleAction(action) }

    TotpSetupScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: TotpSetupAction) {
    when (action) {
      TotpSetupAction.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
      is TotpSetupAction.LaunchTotpApp -> launchTotpApp(action.uri)
      is TotpSetupAction.CopyKeyToClipboard -> Util.copyToClipboardSensitive(requireContext(), action.key)
      TotpSetupAction.ShowKeyCopied -> toast(AppSettingsR.string.TotpSetupScreen__copied_to_clipboard)
      TotpSetupAction.ShowNoTotpAppFound -> toast(AppSettingsR.string.TotpSetupScreen__no_authenticator_app_found)
      TotpSetupAction.NavigateToCodeEntry -> findNavController().safeNavigate(R.id.action_authenticatorSetupFragment_to_authenticatorCodeEntryFragment)
    }
  }

  private fun launchTotpApp(uri: String) {
    try {
      startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "No app is willing to handle the authenticator setup link.", e)
      viewModel.onEvent(TotpSetupEvent.NoTotpAppFound)
    }
  }

  private fun toast(@StringRes message: Int) {
    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
  }
}

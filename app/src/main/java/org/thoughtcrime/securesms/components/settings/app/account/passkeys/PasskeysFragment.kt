/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.passkeys

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.appsettings.passkeys.PasskeysAction
import org.signal.appsettings.passkeys.PasskeysScreen
import org.signal.appsettings.passkeys.PasskeysViewModel
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.util.viewModel

/**
 * Explains passkeys and lets the user start creating one. Carries out the [PasskeysAction]s that need an Activity or
 * the nav graph.
 */
class PasskeysFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(PasskeysFragment::class)
  }

  private val viewModel: PasskeysViewModel by viewModel { PasskeysViewModel(AppPasskeysRepository()) }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { action -> handleAction(action) }

    PasskeysScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: PasskeysAction) {
    when (action) {
      PasskeysAction.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
      PasskeysAction.LaunchPasskeyCreation -> Log.w(TAG, "Passkey creation isn't implemented yet.")
      PasskeysAction.OpenLearnMore -> Log.w(TAG, "There's no support article to open yet.")
    }
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.account.TwoFactorMethod
import org.signal.appsettings.totpcodeentry.TotpCodeEntryAction
import org.signal.appsettings.totpcodeentry.TotpCodeEntryScreen
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.account.twofactor.TwoFactorNavArgs
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Wrapper around [org.signal.appsettings.totpcodeentry.TotpCodeEntryScreen]
 */
class TotpCodeEntryFragment : ComposeFragment() {

  /** A one-time code is not a credential, and a password manager offering to save one every time is pure noise. */
  override val autofillEnabled: Boolean = false

  private val viewModel: TotpCodeEntryViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { action -> handleAction(action) }

    TotpCodeEntryScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: TotpCodeEntryAction) {
    when (action) {
      TotpCodeEntryAction.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
      is TotpCodeEntryAction.NavigateToNaming -> {
        val args = Bundle().apply {
          TwoFactorNavArgs.putNewMethod(
            this,
            TwoFactorMethod(id = action.appId, kind = TwoFactorMethod.Kind.AUTHENTICATOR_APP, name = null, createdAt = action.createdAt)
          )
        }
        findNavController().safeNavigate(R.id.action_authenticatorCodeEntryFragment_to_twoFactorNameFragment, args)
      }
      TotpCodeEntryAction.NavigateToSetup -> findNavController().popBackStack(R.id.authenticatorSetupFragment, false)
    }
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.twofactor

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.account.TwoFactorMethod
import org.signal.appsettings.twofactornameentry.TwoFactorNameEntryAction
import org.signal.appsettings.twofactornameentry.TwoFactorNameEntryScreen
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.viewModel
import org.thoughtcrime.securesms.R
import org.signal.appsettings.R as AppSettingsR

/**
 * Wrapper around [TwoFactorNameEntryScreen].
 */
class TwoFactorNameEntryFragment : ComposeFragment() {

  /** The name the user gives a second factor is not a credential, so there's nothing here worth offering to save. */
  override val autofillEnabled: Boolean = false

  private val viewModel: TwoFactorNameEntryViewModel by viewModel {
    TwoFactorNameEntryViewModel(
      methodId = TwoFactorNavArgs.methodId(arguments),
      kind = TwoFactorNavArgs.methodKind(arguments),
      createdAt = TwoFactorNavArgs.createdAt(arguments) ?: System.currentTimeMillis(),
      renamedMethod = TwoFactorNavArgs.renamedMethod(arguments)
    )
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { action -> handleAction(action) }

    TwoFactorNameEntryScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: TwoFactorNameEntryAction) {
    when (action) {
      TwoFactorNameEntryAction.NavigateBack -> {
        requireActivity().onBackPressedDispatcher.onBackPressed()
      }

      TwoFactorNameEntryAction.NavigateToAccountSettings -> {
        findNavController().popBackStack(R.id.accountSettingsFragment, false)
      }

      is TwoFactorNameEntryAction.ShowMethodSetUp -> {
        val stringId = when (action.kind) {
          TwoFactorMethod.Kind.AUTHENTICATOR_APP -> AppSettingsR.string.TwoFactorNameEntryScreen__authenticator_app_set_up
          TwoFactorMethod.Kind.PASSKEY -> AppSettingsR.string.TwoFactorNameEntryScreen__passkey_set_up
          TwoFactorMethod.Kind.OTHER -> AppSettingsR.string.TwoFactorNameEntryScreen__two_factor_method_set_up
        }
        toast(stringId)
      }

      is TwoFactorNameEntryAction.ShowMethodRenamed -> {
        val stringId = when (action.kind) {
          TwoFactorMethod.Kind.AUTHENTICATOR_APP -> AppSettingsR.string.TwoFactorNameEntryScreen__authenticator_app_renamed
          TwoFactorMethod.Kind.PASSKEY -> AppSettingsR.string.TwoFactorNameEntryScreen__passkey_renamed
          TwoFactorMethod.Kind.OTHER -> AppSettingsR.string.TwoFactorNameEntryScreen__two_factor_method_renamed
        }
        toast(stringId)
      }

      TwoFactorNameEntryAction.ShowNameNotSaved -> {
        toast(AppSettingsR.string.TwoFactorNameEntryScreen__couldnt_save_name)
      }
    }
  }

  private fun toast(@StringRes message: Int) {
    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
  }
}

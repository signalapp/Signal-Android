/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.twofactor

import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter

/**
 * Names or renames a second factor under the legacy nav graph. The result goes back to account settings as a fragment
 * result under [RESULT_KEY].
 */
class TwoFactorNameEntryFragment : ComposeFragment() {

  companion object {
    const val RESULT_KEY = "two_factor_method_named"
    const val RESULT_KIND = "kind"
    const val RESULT_RENAMED = "renamed"
  }

  /** The name the user gives a second factor is not a credential, so there's nothing here worth offering to save. */
  override val autofillEnabled: Boolean = false

  private val appSettingsRouter by appSettingsRouter(
    onNavigateBack = { popUpTo ->
      if (popUpTo?.route == AppSettingsRoute.AccountRoute.Account::class) {
        findNavController().popBackStack(R.id.accountSettingsFragment, false)
      } else {
        requireActivity().onBackPressedDispatcher.onBackPressed()
      }
    }
  )

  @Composable
  override fun FragmentContent() {
    TwoFactorNameEntryEntry(
      parentEventEmitter = appSettingsRouter::onEvent,
      methodId = TwoFactorNavArgs.methodId(arguments),
      kind = TwoFactorNavArgs.methodKind(arguments),
      createdAt = TwoFactorNavArgs.createdAt(arguments),
      renamedMethod = TwoFactorNavArgs.renamedMethod(arguments),
      onMethodNamed = { result ->
        setFragmentResult(RESULT_KEY, bundleOf(RESULT_KIND to result.kind.name, RESULT_RENAMED to result.renamed))
      }
    )
  }
}

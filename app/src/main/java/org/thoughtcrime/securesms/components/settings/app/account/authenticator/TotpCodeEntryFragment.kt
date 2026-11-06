/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.account.twofactor.TwoFactorNavArgs
import org.thoughtcrime.securesms.components.settings.app.account.twofactor.toTwoFactorMethod
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Collects the code from the authenticator app being added, under the legacy nav graph.
 */
class TotpCodeEntryFragment : ComposeFragment() {

  /** A one-time code is not a credential, and a password manager offering to save one every time is pure noise. */
  override val autofillEnabled: Boolean = false

  private val appSettingsRouter by appSettingsRouter(
    onNavigateBack = { popUpTo ->
      if (popUpTo?.route == AppSettingsRoute.AccountRoute.TotpSetup::class) {
        findNavController().popBackStack(R.id.authenticatorSetupFragment, false)
      } else {
        requireActivity().onBackPressedDispatcher.onBackPressed()
      }
    },
    onNavigateToScreen = ::navigateToScreen
  )

  @Composable
  override fun FragmentContent() {
    TotpCodeEntryEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      is AppSettingsRoute.AccountRoute.NameNewTwoFactorMethod -> findNavController().safeNavigate(
        R.id.action_authenticatorCodeEntryFragment_to_twoFactorNameFragment,
        Bundle().apply { TwoFactorNavArgs.putNewMethod(this, route.toTwoFactorMethod()) }
      )
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.passkey

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
 * Explains passkeys and adds one to the account, under the legacy nav graph.
 */
class PasskeySetupFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  @Composable
  override fun FragmentContent() {
    PasskeySetupEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      is AppSettingsRoute.AccountRoute.NameNewTwoFactorMethod -> findNavController().safeNavigate(
        R.id.action_passkeySetupFragment_to_twoFactorNameFragment,
        Bundle().apply { TwoFactorNavArgs.putNewMethod(this, route.toTwoFactorMethod()) }
      )
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }
}

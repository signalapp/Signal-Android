/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.account.TwoFactorMethod
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.account.twofactor.TwoFactorNavArgs
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Account settings shown on a primary device, under the legacy nav graph.
 */
class AccountSettingsFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  @Composable
  override fun FragmentContent() {
    AccountSettingsEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    val navController = findNavController()
    when (route) {
      AppSettingsRoute.AccountRoute.SignalLoginDetails -> navController.safeNavigate(R.id.action_accountSettingsFragment_to_settingsSignalLoginDetailsFragment)
      AppSettingsRoute.AccountRoute.TotpSetup -> navController.safeNavigate(R.id.action_accountSettingsFragment_to_authenticatorSetupFragment)
      AppSettingsRoute.AccountRoute.PasskeySetup -> navController.safeNavigate(R.id.action_accountSettingsFragment_to_passkeySetupFragment)
      is AppSettingsRoute.AccountRoute.RenameTwoFactorMethod -> navController.safeNavigate(
        R.id.action_accountSettingsFragment_to_twoFactorNameFragment,
        Bundle().apply { TwoFactorNavArgs.putRenamedMethod(this, route.toTwoFactorMethod()) }
      )
      AppSettingsRoute.AccountRoute.AdvancedPinSettings -> navController.safeNavigate(R.id.action_accountSettingsFragment_to_advancedPinSettingsActivity)
      AppSettingsRoute.ChangeNumberRoute.Start -> navController.safeNavigate(R.id.action_accountSettingsFragment_to_changePhoneNumberFragment)
      AppSettingsRoute.AccountRoute.OldDeviceTransfer -> navController.safeNavigate(R.id.action_accountSettingsFragment_to_oldDeviceTransferActivity)
      AppSettingsRoute.AccountRoute.ExportAccountData -> navController.safeNavigate(R.id.action_accountSettingsFragment_to_exportAccountFragment)
      AppSettingsRoute.AccountRoute.DeleteAccount -> navController.safeNavigate(R.id.action_accountSettingsFragment_to_deleteAccountFragment)
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }
}

/** The method a [AppSettingsRoute.AccountRoute.RenameTwoFactorMethod] identifies, rebuilt from the fields it carries. */
private fun AppSettingsRoute.AccountRoute.RenameTwoFactorMethod.toTwoFactorMethod(): TwoFactorMethod {
  return TwoFactorMethod(
    id = methodId,
    kind = TwoFactorMethod.Kind.entries.firstOrNull { it.name == methodKind } ?: TwoFactorMethod.Kind.OTHER,
    name = methodName,
    createdAt = methodCreatedAt
  )
}

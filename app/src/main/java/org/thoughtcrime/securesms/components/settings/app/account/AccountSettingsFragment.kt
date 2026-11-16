/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.account.TwoFactorMethod
import org.signal.appsettings.account.TwoFactorMethodNamed
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.navigation.LocalResultEventBus
import org.signal.core.ui.navigation.ResultEventBus
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.account.twofactor.TwoFactorNameEntryFragment
import org.thoughtcrime.securesms.components.settings.app.account.twofactor.TwoFactorNavArgs
import org.thoughtcrime.securesms.components.settings.app.account.twofactor.toTwoFactorMethod
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Account settings shown on a primary device, under the legacy nav graph.
 */
class AccountSettingsFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  /** Stands in for the Nav3 host's bus, so the name screen's fragment result reaches the Entry the same way. */
  private val resultEventBus = ResultEventBus()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    parentFragmentManager.setFragmentResultListener(TwoFactorNameEntryFragment.RESULT_KEY, this) { _, bundle ->
      val kind = TwoFactorMethod.Kind.entries.firstOrNull { it.name == bundle.getString(TwoFactorNameEntryFragment.RESULT_KIND) } ?: TwoFactorMethod.Kind.OTHER
      resultEventBus.sendResult(result = TwoFactorMethodNamed(kind = kind, renamed = bundle.getBoolean(TwoFactorNameEntryFragment.RESULT_RENAMED)))
    }
  }

  @Composable
  override fun FragmentContent() {
    CompositionLocalProvider(LocalResultEventBus provides resultEventBus) {
      AccountSettingsEntry(parentEventEmitter = appSettingsRouter::onEvent)
    }
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

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.delete

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.navigation.LocalResultEventBus
import org.signal.core.ui.navigation.ResultEventBus
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.registration.ui.countrycode.Country
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Lets a user delete their account, under the legacy nav graph.
 */
class DeleteAccountFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter(onNavigateToScreen = ::navigateToScreen)

  /** Stands in for the Nav3 host's bus, so the country picker's fragment result reaches the Entry the same way. */
  private val resultEventBus = ResultEventBus()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    parentFragmentManager.setFragmentResultListener(DeleteAccountCountryCodeFragment.RESULT_KEY, this) { _, bundle ->
      val country: Country? = bundle.getParcelable(DeleteAccountCountryCodeFragment.RESULT_COUNTRY)
      if (country != null) {
        resultEventBus.sendResult(result = DeleteAccountCountrySelected(country.regionCode))
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    CompositionLocalProvider(LocalResultEventBus provides resultEventBus) {
      DeleteAccountEntry(parentEventEmitter = appSettingsRouter::onEvent)
    }
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      AppSettingsRoute.AccountRoute.DeleteAccountCountryCode -> findNavController().safeNavigate(R.id.action_deleteAccountFragment_to_deleteAccountCountryFragment)
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }
}

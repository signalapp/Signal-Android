/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.pnp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.phonenumberprivacy.PhoneNumberPrivacySettingsAction
import org.signal.appsettings.phonenumberprivacy.PhoneNumberPrivacySettingsScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack
import org.signal.core.ui.compose.CollectActions

/**
 * Hosts [PhoneNumberPrivacySettingsScreen], navigating back through [parentEventEmitter].
 */
@Composable
fun PhoneNumberPrivacySettingsEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val viewModel: PhoneNumberPrivacySettingsViewModel = viewModel { PhoneNumberPrivacySettingsViewModel() }
  val state by viewModel.state.collectAsStateWithLifecycle()

  CollectActions(viewModel.actions) { action ->
    when (action) {
      PhoneNumberPrivacySettingsAction.NavigateBack -> parentEventEmitter.navigateBack()
    }
  }

  PhoneNumberPrivacySettingsScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}

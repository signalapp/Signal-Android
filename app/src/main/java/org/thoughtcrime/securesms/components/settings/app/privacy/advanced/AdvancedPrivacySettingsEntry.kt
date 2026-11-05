/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.advanced

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.advancedprivacy.AdvancedPrivacySettingsAction
import org.signal.appsettings.advancedprivacy.AdvancedPrivacySettingsScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.CollectActions
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.CommunicationActions

/**
 * The advanced privacy settings, wired to their view model and to the links they open.
 */
@Composable
fun AdvancedPrivacySettingsEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val context = LocalContext.current
  val viewModel: AdvancedPrivacySettingsViewModel = viewModel { AdvancedPrivacySettingsViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  val sealedSenderUrl = stringResource(R.string.AdvancedPrivacySettingsFragment__sealed_sender_link)
  val keyVerificationUrl = stringResource(R.string.verify_display_fragment__link)

  CollectActions(viewModel.actions) { action ->
    when (action) {
      AdvancedPrivacySettingsAction.OpenSealedSenderLearnMore -> CommunicationActions.openBrowserLink(context, sealedSenderUrl)
      AdvancedPrivacySettingsAction.OpenAutomaticKeyVerificationLearnMore -> CommunicationActions.openBrowserLink(context, keyVerificationUrl)
    }
  }

  AdvancedPrivacySettingsScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}

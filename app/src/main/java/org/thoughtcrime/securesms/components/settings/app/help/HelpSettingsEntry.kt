/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.help

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.help.HelpSettingsAction
import org.signal.appsettings.help.HelpSettingsScreen
import org.signal.appsettings.help.HelpSettingsViewModel
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.CollectActions
import org.signal.core.util.Util
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.CommunicationActions

/**
 * The help section, wired to its view model and to the links it opens.
 */
@Composable
fun HelpSettingsEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val context = LocalContext.current
  val supportCenterUrl = stringResource(R.string.support_center_url)
  val termsAndPrivacyPolicyUrl = stringResource(R.string.terms_and_privacy_policy_url)
  val viewModel: HelpSettingsViewModel = viewModel { HelpSettingsViewModel(versionName = BuildConfig.VERSION_NAME, parentEventEmitter = parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  CollectActions(viewModel.actions) { action ->
    when (action) {
      HelpSettingsAction.OpenSupportCenter -> CommunicationActions.openBrowserLink(context, supportCenterUrl)
      is HelpSettingsAction.CopyVersionToClipboard -> Util.copyToClipboard(context, action.versionName)
      HelpSettingsAction.OpenTermsAndPrivacyPolicy -> CommunicationActions.openBrowserLink(context, termsAndPrivacyPolicyUrl)
    }
  }

  HelpSettingsScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}

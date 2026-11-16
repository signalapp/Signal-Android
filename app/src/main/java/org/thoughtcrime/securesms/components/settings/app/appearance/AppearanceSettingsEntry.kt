/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.appearance

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.appearance.AppearanceSettingsAction
import org.signal.appsettings.appearance.AppearanceSettingsEvent
import org.signal.appsettings.appearance.AppearanceSettingsScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.CollectActions
import org.signal.core.util.PlayServicesUtil
import org.signal.core.util.PlayStoreUtil
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SplashScreenUtil

/**
 * Hosts [AppearanceSettingsScreen] and carries out its [AppearanceSettingsAction]s. The view model navigates by sending
 * events to [parentEventEmitter], so this works from whichever host owns the app settings back stack.
 */
@Composable
fun AppearanceSettingsEntry(
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) {
  val viewModel: AppearanceSettingsViewModel = viewModel { AppearanceSettingsViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val activity = LocalActivity.current
  val context = LocalContext.current
  val confirmLanguageDownload = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
    if (result.resultCode != Activity.RESULT_OK) {
      viewModel.onEvent(AppearanceSettingsEvent.LanguageDownloadConfirmationDeclined)
    }
  }

  CollectActions(viewModel.actions) { action ->
    when (action) {
      is AppearanceSettingsAction.ApplySplashScreenTheme -> SplashScreenUtil.setSplashScreenThemeIfNecessary(activity, action.theme.toSettingsTheme())
      is AppearanceSettingsAction.OpenBrowserLink -> CommunicationActions.openBrowserLink(context, action.url)
      AppearanceSettingsAction.OpenPlayStoreSettings -> PlayStoreUtil.openPlayStoreSettings(context)
      AppearanceSettingsAction.OpenPlayServicesSettings -> PlayServicesUtil.openPlayServicesSettings(context)
      is AppearanceSettingsAction.ConfirmLanguageDownload -> AppearanceSettingsRepository.confirmLanguageDownload(action.sessionId, confirmLanguageDownload)
    }
  }

  AppearanceSettingsScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}

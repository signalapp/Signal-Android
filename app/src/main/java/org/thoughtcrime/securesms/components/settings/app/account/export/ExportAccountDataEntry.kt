/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ShareCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.exportaccountdata.ExportAccountDataAction
import org.signal.appsettings.exportaccountdata.ExportAccountDataScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.CollectActions
import org.thoughtcrime.securesms.util.CommunicationActions

/**
 * Exporting a report of the user's account data, handing the generated file to the system share sheet.
 */
@Composable
fun ExportAccountDataEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val context = LocalContext.current
  val viewModel: ExportAccountDataViewModel = viewModel { ExportAccountDataViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  CollectActions(viewModel.actions) { action ->
    when (action) {
      is ExportAccountDataAction.OpenBrowserLink -> CommunicationActions.openBrowserLink(context, action.url)
      is ExportAccountDataAction.ShareGeneratedReport -> {
        ShareCompat.IntentBuilder(context)
          .setStream(action.uri)
          .setType(action.mimeType)
          .startChooser()
      }
    }
  }

  ExportAccountDataScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.totpcodeentry.TotpCodeEntryScreen

/**
 * The code from the authenticator app being added, wired to its view model.
 */
@Composable
fun TotpCodeEntryEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val viewModel: TotpCodeEntryViewModel = viewModel { TotpCodeEntryViewModel(parentEventEmitter = parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  TotpCodeEntryScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}

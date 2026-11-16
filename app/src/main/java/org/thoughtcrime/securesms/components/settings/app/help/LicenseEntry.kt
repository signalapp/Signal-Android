/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.help

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.help.LicenseScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent

/**
 * The open source licenses, wired to their view model.
 */
@Composable
fun LicenseEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val viewModel: LicenseViewModel = viewModel { LicenseViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  LicenseScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}

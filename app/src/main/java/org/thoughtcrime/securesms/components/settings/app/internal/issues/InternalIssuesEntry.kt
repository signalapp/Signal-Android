/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.issues

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack
import org.thoughtcrime.securesms.dependencies.AppDependencies

/**
 * The recorded app issues screen, wired to its view model, leaving through [parentEventEmitter].
 */
@Composable
fun InternalIssuesEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val viewModel: InternalIssuesViewModel = viewModel { InternalIssuesViewModel(AppDependencies.application) }
  val state by viewModel.state.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    viewModel.onEvent(InternalIssuesScreenEvent.Load)
  }

  InternalIssuesScreen(
    state = state,
    onEvent = viewModel::onEvent,
    onBack = { parentEventEmitter.navigateBack() }
  )
}

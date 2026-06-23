/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.issues

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.ComposeFragment

class InternalIssuesFragment : ComposeFragment() {

  private val viewModel: InternalIssuesViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
      viewModel.onEvent(InternalIssuesScreenEvent.Load)
    }

    InternalIssuesScreen(
      state = state,
      onEvent = viewModel::onEvent,
      onBack = { findNavController().popBackStack() }
    )
  }
}

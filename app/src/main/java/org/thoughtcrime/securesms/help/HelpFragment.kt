/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.help

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.viewModel

class HelpFragment : ComposeFragment() {

  private val viewModel: HelpViewModel by viewModel {
    val categoryCount = resources.getStringArray(R.array.HelpFragment__categories_6).size
    HelpViewModel(
      startCategoryIndex = (arguments?.getInt(START_CATEGORY_INDEX, 0) ?: 0).coerceIn(0, categoryCount - 1),
      application = AppDependencies.application
    )
  }

  override fun onResume() {
    super.onResume()
    viewModel.onScreenResumed()
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    HelpScreenContent(
      state = state,
      onEvent = { event ->
        when (event) {
          HelpScreenEvents.NavigationClick -> { requireActivity().onBackPressedDispatcher.onBackPressed() }
          HelpScreenEvents.FAQClick -> {
            CommunicationActions.openBrowserLink(
              requireContext(),
              getString(R.string.HelpFragment__link__faq)
            )
          }
          HelpScreenEvents.WhatIsDebugLogClick -> {
            CommunicationActions.openBrowserLink(
              requireContext(),
              getString(R.string.HelpFragment__link__debug_info)
            )
          }
          else -> viewModel.onEvent(event)
        }
      },
      sideEffect = viewModel.sideEffect
    )
  }

  companion object {
    const val START_CATEGORY_INDEX = "start_category_index"
    const val PAYMENT_INDEX = 6
    const val DONATION_INDEX = 7
    const val REMOTE_BACKUPS_INDEX = 8
  }
}

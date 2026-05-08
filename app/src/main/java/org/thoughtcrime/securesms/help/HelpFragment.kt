/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.help

import androidx.compose.runtime.Composable
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.viewModel

class HelpFragment : ComposeFragment() {

  private val viewModel: HelpViewModel by viewModel {
    HelpViewModel(
      startCategoryIndex = arguments?.getInt(START_CATEGORY_INDEX, 0) ?: PAYMENT_INDEX,
      application = AppDependencies.application
    )
  }

  @Composable
  override fun FragmentContent() {
    HelpScreen(
      viewModel = viewModel,
      onNavigationClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
      onWhatIsDebugLogClick = {
        CommunicationActions.openBrowserLink(
          requireContext(),
          getString(R.string.HelpFragment__link__debug_info)
        )
      },
      onFaqClick = {
        CommunicationActions.openBrowserLink(
          requireContext(),
          getString(R.string.HelpFragment__link__faq)
        )
      }
    )
  }

  companion object {
    const val START_CATEGORY_INDEX = "start_category_index"
    const val PAYMENT_INDEX = 6
    const val DONATION_INDEX = 7
    const val REMOTE_BACKUPS_INDEX = 8
  }
}

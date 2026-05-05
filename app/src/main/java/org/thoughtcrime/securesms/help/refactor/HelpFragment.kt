/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.help.refactor

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R

class HelpFragment : ComposeFragment() {

  private val viewModel: HelpViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val startCategoryIndex = arguments?.getInt(START_CATEGORY_INDEX, 0) ?: 6

    HelpScreen(
      viewModel = viewModel,
      startCategoryIndex = startCategoryIndex,
      onNavigationClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
      onWhatIsDebugLogClick = {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setData(Uri.parse(getString(R.string.HelpFragment__link__debug_info)))
        startActivity(intent)
      },
      onFaqClick = {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setData(Uri.parse(getString(R.string.HelpFragment__link__faq)))
        startActivity(intent)
      },
    )
  }

  companion object {
    const val START_CATEGORY_INDEX = "start_category_index"
    const val PAYMENT_INDEX        = 6
    const val DONATION_INDEX       = 7
    const val REMOTE_BACKUPS_INDEX = 8
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.delete

import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.ComposeFragment

/**
 * Country code picker specific to deleting an account.
 */
class DeleteAccountCountryCodeFragment : ComposeFragment() {

  companion object {
    const val RESULT_KEY = "result_key"
    const val RESULT_COUNTRY = "result_country"
  }

  @Composable
  override fun FragmentContent() {
    DeleteAccountCountryCodeEntry(
      onCountrySelected = { country ->
        setFragmentResult(RESULT_KEY, bundleOf(RESULT_COUNTRY to country))
        findNavController().popBackStack()
      },
      onDismissed = { findNavController().popBackStack() }
    )
  }
}

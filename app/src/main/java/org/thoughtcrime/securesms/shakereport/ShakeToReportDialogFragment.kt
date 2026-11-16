/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.shakereport

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeDialogFragment
import org.signal.core.util.Util
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.sharing.MultiShareArgs

/**
 * Hosts [ShakeToReportDialog]. The upload lives in the view model, so it survives configuration changes.
 */
class ShakeToReportDialogFragment : ComposeDialogFragment() {

  companion object {
    private const val FRAGMENT_TAG = "ShakeToReportDialogFragment"

    /**
     * @return False if the dialog could not be shown, in which case the caller is responsible for re-enabling shake detection.
     */
    @JvmStatic
    fun show(fragmentManager: FragmentManager): Boolean {
      if (fragmentManager.findFragmentByTag(FRAGMENT_TAG) != null) {
        return true
      }

      if (fragmentManager.isStateSaved) {
        return false
      }

      ShakeToReportDialogFragment().show(fragmentManager, FRAGMENT_TAG)
      return true
    }
  }

  private val viewModel: ShakeToReportViewModel by viewModels()

  @Composable
  override fun DialogContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { handleAction(it) }

    ShakeToReportDialog(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  override fun onDestroy() {
    super.onDestroy()
    if (activity?.isChangingConfigurations != true) {
      AppDependencies.shakeToReport.enableIfVisible()
    }
  }

  private fun handleAction(action: ShakeToReportAction) {
    when (action) {
      ShakeToReportAction.Dismiss -> {
        dismissAllowingStateLoss()
      }
      is ShakeToReportAction.CopyUrl -> {
        Util.copyToClipboard(requireContext(), action.url)
        Toast.makeText(requireContext(), R.string.ShakeToReport_copied, Toast.LENGTH_SHORT).show()
        dismissAllowingStateLoss()
      }
      is ShakeToReportAction.ShareUrl -> {
        MultiselectForwardFragment.showFullScreen(
          parentFragmentManager,
          MultiselectForwardFragmentArgs(
            multiShareArgs = listOf(MultiShareArgs.Builder().withDraftText(action.url).build()),
            title = R.string.MultiselectForwardFragment__share_with
          )
        )
        dismissAllowingStateLoss()
      }
    }
  }
}

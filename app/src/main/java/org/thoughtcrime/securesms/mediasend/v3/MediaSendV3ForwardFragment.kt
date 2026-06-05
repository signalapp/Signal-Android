/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v3

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.signal.core.util.getParcelableArrayListCompat
import org.signal.core.util.logging.Log
import org.signal.mediasend.MediaRecipientId
import org.signal.mediasend.MediaSendActivityContract
import org.signal.mediasend.MediaSendState
import org.signal.mediasend.MediaSendViewModel
import org.signal.mediasend.SendResult
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.safety.SafetyNumberBottomSheet
import org.thoughtcrime.securesms.stories.Stories
import org.signal.core.ui.R as CoreUiR

/**
 * View-backed wrapper around [MultiselectForwardFragment] that provides the [ViewGroup] container
 * required by [MultiselectForwardFragment.Callback.getContainer] for bottom bar inflation.
 *
 * Implements the callback interface and uses the shared [MediaSendViewModel] to drive
 * the send flow forward.
 */
class MediaSendV3ForwardFragment : Fragment(R.layout.multiselect_forward_activity), MultiselectForwardFragment.Callback {

  companion object {
    private val TAG = Log.tag(MediaSendV3ForwardFragment::class.java)
  }

  private val viewModel: MediaSendViewModel by activityViewModels {
    MediaSendViewModel.Factory(args = MediaSendActivityContract.Args.fromIntent(requireActivity().intent))
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    if (savedInstanceState == null) {
      val state = viewModel.state.value
      val forwardFragment = MultiselectForwardFragment.create(
        MultiselectForwardFragmentArgs(
          title = R.string.MediaReviewFragment__send_to,
          storySendRequirements = state.storySendRequirements.toAppSendRequirements(),
          isSearchEnabled = !state.isStory,
          isViewOnce = state.viewOnceToggleState == MediaSendState.ViewOnceToggleState.ONCE
        )
      )

      childFragmentManager.beginTransaction()
        .replace(R.id.fragment_container, forwardFragment)
        .commitNow()
    }
  }

  override fun onFinishForwardAction() = Unit

  override fun exitFlow() {
    requireActivity().finish()
  }

  override fun onSearchInputFocused() = Unit

  override fun setResult(bundle: Bundle) {
    val selectedRecipients: List<ContactSearchKey.RecipientSearchKey> = bundle.getParcelableArrayListCompat(MultiselectForwardFragment.RESULT_SELECTION, ContactSearchKey.RecipientSearchKey::class.java)
      ?: emptyList()

    val recipientIds = selectedRecipients.map { MediaRecipientId(it.recipientId.toLong()) }
    viewModel.setAdditionalRecipients(recipientIds)

    viewLifecycleOwner.lifecycleScope.launch {
      when (val result = viewModel.send()) {
        is SendResult.Success -> {
          Log.d(TAG, "Send completed successfully.")
          requireActivity().finish()
        }
        is SendResult.Error -> {
          Log.w(TAG, "Send failed: ${result.message}")
          requireActivity().finish()
        }
        is SendResult.UntrustedIdentity -> {
          Log.w(TAG, "Send failed due to untrusted identities.")
          SafetyNumberBottomSheet
            .forRecipientIdsAndDestinations(result.recipientIds.map { RecipientId.from(it) }, selectedRecipients)
            .show(childFragmentManager)
        }
      }
    }
  }

  override fun getContainer(): ViewGroup {
    return requireView().findViewById(R.id.fragment_container_wrapper)
  }

  override fun getDialogBackgroundColor(): Int {
    return ContextCompat.getColor(requireContext(), CoreUiR.color.signal_colorBackground)
  }

  override fun getStorySendRequirements(): Stories.MediaTransform.SendRequirements? {
    return viewModel.getStorySendRequirements().toAppSendRequirements()
  }
}

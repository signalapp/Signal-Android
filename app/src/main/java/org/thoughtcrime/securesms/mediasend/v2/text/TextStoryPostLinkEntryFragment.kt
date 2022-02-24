package org.thoughtcrime.securesms.mediasend.v2.text

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.constraintlayout.widget.Group
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.KeyboardEntryDialogFragment
import org.thoughtcrime.securesms.linkpreview.LinkPreviewRepository
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel
import org.thoughtcrime.securesms.stories.StoryLinkPreviewView
import org.thoughtcrime.securesms.util.visible

class TextStoryPostLinkEntryFragment : KeyboardEntryDialogFragment(
  contentLayoutId = R.layout.stories_text_post_link_entry_fragment
) {

  private val linkPreviewViewModel: LinkPreviewViewModel by viewModels(
    factoryProducer = { LinkPreviewViewModel.Factory(LinkPreviewRepository()) }
  )

  private val viewModel: TextStoryPostCreationViewModel by viewModels(
    ownerProducer = {
      requireActivity()
    }
  )

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val input: EditText = view.findViewById(R.id.input)
    val linkPreview: StoryLinkPreviewView = view.findViewById(R.id.link_preview)
    val confirmButton: View = view.findViewById(R.id.confirm_button)

    val shareALinkGroup: Group = view.findViewById(R.id.share_a_link_group)

    input.addTextChangedListener(
      afterTextChanged = {
        linkPreviewViewModel.onTextChanged(requireContext(), it!!.toString(), input.selectionStart, input.selectionEnd)
      }
    )

    confirmButton.setOnClickListener {
      if (linkPreviewViewModel.hasLinkPreview()) {
        viewModel.setLinkPreview(linkPreviewViewModel.linkPreviewState.value!!.linkPreview.get().url)
      }

      dismissAllowingStateLoss()
    }

    linkPreviewViewModel.linkPreviewState.observe(viewLifecycleOwner) { state ->
      linkPreview.bind(state)
      shareALinkGroup.visible = !state.isLoading && !state.linkPreview.isPresent && state.error == null
      confirmButton.isEnabled = state.linkPreview.isPresent
    }
  }

  override fun onDismiss(dialog: DialogInterface) {
    linkPreviewViewModel.onSend()
    super.onDismiss(dialog)
  }
}

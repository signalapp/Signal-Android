package org.thoughtcrime.securesms.mediasend.v2.text

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.drawToBitmap
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.linkpreview.LinkPreviewRepository
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel
import org.thoughtcrime.securesms.mediasend.v2.HudCommand
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionViewModel
import org.thoughtcrime.securesms.stories.StoryTextPostView
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class TextStoryPostCreationFragment : Fragment(R.layout.stories_text_post_creation_fragment), TextStoryPostTextEntryFragment.Callback {

  private lateinit var scene: ConstraintLayout
  private lateinit var linkButton: View
  private lateinit var backgroundButton: AppCompatImageView
  private lateinit var send: View
  private lateinit var storyTextPostView: StoryTextPostView

  private val sharedViewModel: MediaSelectionViewModel by viewModels(
    ownerProducer = {
      requireActivity()
    }
  )

  private val viewModel: TextStoryPostCreationViewModel by viewModels(
    ownerProducer = {
      requireActivity()
    }
  )

  private val linkPreviewViewModel: LinkPreviewViewModel by viewModels(
    ownerProducer = {
      requireActivity()
    },
    factoryProducer = {
      LinkPreviewViewModel.Factory(LinkPreviewRepository())
    }
  )

  private val lifecycleDisposable = LifecycleDisposable()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    scene = view.findViewById(R.id.scene)
    linkButton = view.findViewById(R.id.add_link)
    backgroundButton = view.findViewById(R.id.background_selector)
    send = view.findViewById(R.id.send)
    storyTextPostView = view.findViewById(R.id.story_text_post)

    storyTextPostView.showCloseButton()

    lifecycleDisposable.bindTo(viewLifecycleOwner)
    lifecycleDisposable += sharedViewModel.hudCommands.subscribe {
      if (it == HudCommand.GoToCapture) {
        findNavController().popBackStack()
      }
    }

    viewModel.typeface.observe(viewLifecycleOwner) { typeface ->
      storyTextPostView.setTypeface(typeface)
    }

    viewModel.state.observe(viewLifecycleOwner) { state ->
      backgroundButton.background = state.backgroundColor.chatBubbleMask
      storyTextPostView.bindFromCreationState(state)

      if (state.linkPreviewUri != null) {
        linkPreviewViewModel.onTextChanged(requireContext(), state.linkPreviewUri, 0, state.linkPreviewUri.lastIndex)
      } else {
        linkPreviewViewModel.onSend()
      }

      val canSend = state.body.isNotEmpty() || !state.linkPreviewUri.isNullOrEmpty()
      send.alpha = if (canSend) 1f else 0.5f
      send.isEnabled = canSend
    }

    linkPreviewViewModel.linkPreviewState.observe(viewLifecycleOwner) { state ->
      storyTextPostView.bindLinkPreviewState(state, View.GONE)
      storyTextPostView.postAdjustLinkPreviewTranslationY()
    }

    storyTextPostView.setTextViewClickListener {
      storyTextPostView.hidePostContent()
      TextStoryPostTextEntryFragment().show(childFragmentManager, null)
    }

    backgroundButton.setOnClickListener {
      viewModel.cycleBackgroundColor()
    }

    linkButton.setOnClickListener {
      TextStoryPostLinkEntryFragment().show(childFragmentManager, null)
    }

    storyTextPostView.setLinkPreviewCloseListener {
      viewModel.setLinkPreview("")
    }

    send.setOnClickListener {
      storyTextPostView.hideCloseButton()
      viewModel.setBitmap(storyTextPostView.drawToBitmap())
      findNavController().safeNavigate(R.id.action_textStoryPostCreationFragment_to_textStoryPostSendFragment)
    }
  }

  override fun onResume() {
    super.onResume()
    storyTextPostView.showCloseButton()
  }

  override fun onTextStoryPostTextEntryDismissed() {
    storyTextPostView.showPostContent()
  }
}

package org.thoughtcrime.securesms.mediasend.v2.text

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.drawToBitmap
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.linkpreview.LinkPreviewRepository
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel
import org.thoughtcrime.securesms.mediasend.v2.HudCommand
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionViewModel
import org.thoughtcrime.securesms.mediasend.v2.stories.StoriesMultiselectForwardActivity
import org.thoughtcrime.securesms.mediasend.v2.text.send.TextStoryPostSendRepository
import org.thoughtcrime.securesms.mediasend.v2.text.send.TextStoryPostSendResult
import org.thoughtcrime.securesms.safety.SafetyNumberBottomSheet
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.stories.StoryTextPostView
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil
import org.thoughtcrime.securesms.util.visible

class TextStoryPostCreationFragment : Fragment(R.layout.stories_text_post_creation_fragment), TextStoryPostTextEntryFragment.Callback, SafetyNumberBottomSheet.Callbacks {

  private lateinit var scene: ConstraintLayout
  private lateinit var backgroundButton: AppCompatImageView
  private lateinit var send: View
  private lateinit var storyTextPostView: StoryTextPostView
  private lateinit var sendInProgressCard: View

  private val sharedViewModel: MediaSelectionViewModel by viewModels(
    ownerProducer = {
      requireActivity()
    }
  )

  private val viewModel: TextStoryPostCreationViewModel by viewModels(
    ownerProducer = {
      requireActivity()
    },
    factoryProducer = {
      TextStoryPostCreationViewModel.Factory(TextStoryPostSendRepository())
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
    backgroundButton = view.findViewById(R.id.background_selector)
    send = view.findViewById(R.id.send)
    storyTextPostView = view.findViewById(R.id.story_text_post)
    sendInProgressCard = view.findViewById(R.id.send_in_progress_indicator)

    val backgroundProtection: View = view.findViewById(R.id.background_protection)
    val addLinkProtection: View = view.findViewById(R.id.add_link_protection)

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

    LiveDataUtil.combineLatest(viewModel.state, linkPreviewViewModel.linkPreviewState) { viewState, linkState ->
      Pair(viewState.body.isBlank(), linkState)
    }.observe(viewLifecycleOwner) { (useLargeThumb, linkState) ->
      storyTextPostView.bindLinkPreviewState(linkState, View.GONE, useLargeThumb)
      storyTextPostView.postAdjustLinkPreviewTranslationY()
    }

    storyTextPostView.setTextViewClickListener {
      storyTextPostView.hidePostContent()
      storyTextPostView.isEnabled = false
      TextStoryPostTextEntryFragment().show(childFragmentManager, null)
    }

    backgroundProtection.setOnClickListener {
      viewModel.cycleBackgroundColor()
    }

    addLinkProtection.setOnClickListener {
      TextStoryPostLinkEntryFragment().show(childFragmentManager, null)
    }

    storyTextPostView.setLinkPreviewCloseListener {
      viewModel.setLinkPreview("")
    }

    val launcher = registerForActivityResult(StoriesMultiselectForwardActivity.SelectionContract()) {
      if (it.isNotEmpty()) {
        performSend(it.toSet())
      } else {
        send.isClickable = true
        sendInProgressCard.visible = false
      }
    }

    send.setOnClickListener {
      send.isClickable = false
      sendInProgressCard.visible = true

      storyTextPostView.hideCloseButton()

      val contacts = (sharedViewModel.destination.getRecipientSearchKeyList() + sharedViewModel.destination.getRecipientSearchKey())
        .filterIsInstance(ContactSearchKey::class.java)
        .toSet()

      if (contacts.isEmpty()) {
        val bitmap = storyTextPostView.drawToBitmap()
        viewModel.compressToBlob(bitmap).observeOn(AndroidSchedulers.mainThread()).subscribe { uri ->
          launcher.launch(
            StoriesMultiselectForwardActivity.Args(
              MultiselectForwardFragmentArgs(
                title = R.string.MediaReviewFragment__send_to,
                canSendToNonPush = false,
                storySendRequirements = Stories.MediaTransform.SendRequirements.VALID_DURATION,
                isSearchEnabled = false
              ),
              listOf(uri)
            )
          )
        }
      } else {
        performSend(contacts)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    storyTextPostView.showCloseButton()
    requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
  }

  override fun onTextStoryPostTextEntryDismissed() {
    storyTextPostView.postDelayed(resources.getInteger(R.integer.text_entry_exit_duration).toLong()) {
      storyTextPostView.showPostContent()
      storyTextPostView.isEnabled = true
    }
  }

  private fun performSend(contacts: Set<ContactSearchKey>) {
    lifecycleDisposable += viewModel.send(
      contacts = contacts,
      linkPreviewViewModel.linkPreviewState.value?.linkPreview?.orElse(null)
    ).observeOn(AndroidSchedulers.mainThread()).subscribe { result ->
      when (result) {
        TextStoryPostSendResult.Success -> {
          Toast.makeText(requireContext(), R.string.TextStoryPostCreationFragment__sent_story, Toast.LENGTH_SHORT).show()
          requireActivity().finish()
        }
        TextStoryPostSendResult.Failure -> {
          Toast.makeText(requireContext(), R.string.TextStoryPostCreationFragment__failed_to_send_story, Toast.LENGTH_SHORT).show()
          requireActivity().finish()
        }
        is TextStoryPostSendResult.UntrustedRecordsError -> {
          send.isClickable = true
          sendInProgressCard.visible = false

          SafetyNumberBottomSheet
            .forIdentityRecordsAndDestinations(result.untrustedRecords, contacts.toList())
            .show(childFragmentManager)
        }
      }
    }
  }

  override fun sendAnywayAfterSafetyNumberChangedInBottomSheet(destinations: List<ContactSearchKey.RecipientSearchKey>) {
    performSend(destinations.toSet())
  }

  override fun onMessageResentAfterSafetyNumberChangeInBottomSheet() {
    error("Unsupported, we do not hand in a message id.")
  }

  override fun onCanceled() = Unit
}

package org.thoughtcrime.securesms.mediasend.v2.text

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.drawToBitmap
import androidx.core.view.postDelayed
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.camera.CameraDisplay
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.getWindowBreakpoint
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.dp
import org.signal.mediasend.screens.capture.TextStoryBarEvents
import org.signal.mediasend.screens.capture.TextStoryHorizontalBar
import org.signal.mediasend.screens.capture.TextStoryVerticalBar
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.databinding.StoriesTextPostCreationFragmentBinding
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.linkpreview.LinkPreviewState
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModelV2
import org.thoughtcrime.securesms.mediasend.v2.stories.StoriesMultiselectForwardActivity
import org.thoughtcrime.securesms.mediasend.v2.text.send.TextStoryPostSendResult
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.safety.SafetyNumberBottomSheet
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.activityViewModel
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.visible
import java.util.Optional

class TextStoryPostCreationFragment : Fragment(R.layout.stories_text_post_creation_fragment), TextStoryPostTextEntryFragment.Callback, SafetyNumberBottomSheet.Callbacks {

  private var _binding: StoriesTextPostCreationFragmentBinding? = null
  private val binding: StoriesTextPostCreationFragmentBinding get() = _binding!!

  private val callback: Callback
    get() = requireListener()

  private val viewModel: TextStoryPostCreationViewModel by activityViewModel { extras ->
    TextStoryPostCreationViewModel.create(extras, callback.textStoryDraftText)
  }

  private val linkPreviewViewModel: LinkPreviewViewModelV2 by activityViewModel { extras ->
    LinkPreviewViewModelV2(extras.createSavedStateHandle(), enablePlaceholder = true)
  }

  private val lifecycleDisposable = LifecycleDisposable()

  private var textStoryBackground: ChatColors? by mutableStateOf(null)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    _binding = StoriesTextPostCreationFragmentBinding.bind(view)

    ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
      val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      v.updatePadding(left = systemBars.left, top = systemBars.top, right = systemBars.right, bottom = systemBars.bottom)
      insets
    }

    binding.storyTextPost.enableCreationMode()

    lifecycleDisposable.bindTo(viewLifecycleOwner)

    lifecycleDisposable += viewModel.typeface.subscribeBy { typeface ->
      binding.storyTextPost.setTypeface(typeface)
    }

    lifecycleDisposable += viewModel.state.subscribeBy { state ->
      textStoryBackground = state.backgroundColor
      binding.storyTextPost.bindFromCreationState(state)

      if (state.linkPreviewUri != null) {
        linkPreviewViewModel.onTextChanged(state.linkPreviewUri, 0, state.linkPreviewUri.lastIndex)
      } else {
        linkPreviewViewModel.onSend()
      }

      val canSend = state.body.isNotBlank() || !state.linkPreviewUri.isNullOrEmpty()
      binding.send.alpha = if (canSend) 1f else 0.5f
      binding.send.isEnabled = canSend
    }

    lifecycleDisposable += Flowable.combineLatest(viewModel.state, linkPreviewViewModel.linkPreviewState) { viewState, linkState ->
      Pair(viewState.body.isBlank(), linkState)
    }.subscribeBy { (useLargeThumb, linkState) ->
      binding.storyTextPost.bindLinkPreviewState(linkState, View.GONE, useLargeThumb)
      binding.storyTextPost.postAdjustLinkPreviewTranslationY()
    }

    binding.storyTextPost.setTextViewClickListener {
      binding.storyTextPost.hidePostContent()
      binding.storyTextPost.isEnabled = false
      TextStoryPostTextEntryFragment().show(childFragmentManager, null)
    }

    binding.storyTextPost.setLinkPreviewCloseListener {
      viewModel.setLinkPreview("")
    }

    binding.storyTextPost.setLinkPreviewClickListener {
      TextStoryPostLinkEntryFragment(true).show(childFragmentManager, null)
    }

    val launcher = registerForActivityResult(StoriesMultiselectForwardActivity.SelectionContract()) {
      if (it.isNotEmpty()) {
        performSend(it.toSet())
      } else {
        onSendAborted()
      }
    }

    binding.send.setOnClickListener {
      binding.send.isClickable = false
      binding.sendInProgressIndicator.visible = true

      binding.storyTextPost.disableCreationMode()

      val contacts: Set<ContactSearchKey.RecipientSearchKey> = callback.textStoryDestinations

      if (contacts.isEmpty()) {
        val bitmap = binding.storyTextPost.drawToBitmap()
        lifecycleDisposable += viewModel.compressToBlob(bitmap).observeOn(AndroidSchedulers.mainThread()).subscribe { uri ->
          launcher.launch(
            StoriesMultiselectForwardActivity.Args(
              MultiselectForwardFragmentArgs(
                title = R.string.MediaReviewFragment__send_to,
                storySendRequirements = Stories.MediaTransform.SendRequirements.VALID_DURATION,
                isSearchEnabled = false
              ),
              listOf(uri)
            )
          )
        }
      } else if (callback.isAddToGroupStoryFlow) {
        confirmAddToGroupStory(contacts)
      } else {
        performSend(contacts)
      }
    }

    initializeScenePositioning()
    initializeTextStoryBar()
  }

  override fun onResume() {
    super.onResume()
    binding.storyTextPost.enableCreationMode()
  }

  override fun onDestroy() {
    super.onDestroy()
    _binding = null
  }

  override fun onTextStoryPostTextEntryDismissed() {
    binding.storyTextPost.postDelayed(resources.getInteger(R.integer.text_entry_exit_duration).toLong()) {
      binding.storyTextPost.showPostContent()
      binding.storyTextPost.isEnabled = true
    }
  }

  private fun initializeScenePositioning() {
    val cameraDisplay = CameraDisplay.getDisplay(requireActivity())

    if (!cameraDisplay.roundViewFinderCorners) {
      binding.storyTextPostCard.radius = 0f
    }

    binding.send.updateLayoutParams<ConstraintLayout.LayoutParams> {
      bottomMargin = cameraDisplay.getNextPaddingBottom().dp
      rightMargin = cameraDisplay.getNextPaddingEnd().dp
      leftMargin = cameraDisplay.getNextPaddingEnd().dp
    }

    if (cameraDisplay.getCameraViewportGravity() == CameraDisplay.CameraViewportGravity.CENTER) {
      val constraintSet = ConstraintSet()
      constraintSet.clone(binding.scene)
      constraintSet.connect(R.id.story_text_post_card, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
      constraintSet.applyTo(binding.scene)
    } else {
      binding.storyTextPostCard.updateLayoutParams<ConstraintLayout.LayoutParams> {
        bottomMargin = cameraDisplay.getCameraViewportMarginBottom()
      }
    }
  }

  private fun initializeTextStoryBar() {
    val cameraDisplay = CameraDisplay.getDisplay(requireActivity())
    val isSmallScreen = resources.getWindowBreakpoint() is WindowBreakpoint.Small

    val composeView = ComposeView(requireContext()).apply {
      layoutParams = ConstraintLayout.LayoutParams(
        ConstraintLayout.LayoutParams.WRAP_CONTENT,
        ConstraintLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        if (isSmallScreen) {
          startToStart = ConstraintLayout.LayoutParams.PARENT_ID
          endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
          bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
          bottomMargin = cameraDisplay.getCameraCaptureMarginBottom(resources) + 6.dp
        } else {
          topToTop = ConstraintLayout.LayoutParams.PARENT_ID
          bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
          endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        }
      }

      setContent {
        SignalTheme {
          val background = textStoryBackground ?: return@SignalTheme
          if (isSmallScreen) {
            TextStoryHorizontalBar(
              background = background.chatBubbleBrush,
              onEvent = ::onTextStoryBarEvent
            )
          } else {
            TextStoryVerticalBar(
              background = background.chatBubbleBrush,
              onEvent = ::onTextStoryBarEvent,
              modifier = Modifier.horizontalGutters()
            )
          }
        }
      }
    }

    binding.scene.addView(composeView)
  }

  private fun onTextStoryBarEvent(event: TextStoryBarEvents) {
    when (event) {
      TextStoryBarEvents.CycleBackgroundColor -> viewModel.cycleBackgroundColor()
      TextStoryBarEvents.AddLink -> TextStoryPostLinkEntryFragment().show(childFragmentManager, null)
    }
  }

  private fun confirmAddToGroupStory(contacts: Set<ContactSearchKey.RecipientSearchKey>) {
    val context = requireContext()

    viewLifecycleOwner.lifecycleScope.launch {
      val displayName = withContext(Dispatchers.Default) {
        Recipient.resolved(contacts.first().recipientId).getDisplayName(context)
      }

      MaterialAlertDialogBuilder(context)
        .setMessage(getString(R.string.MediaReviewFragment__add_to_the_group_story, displayName))
        .setPositiveButton(R.string.MediaReviewFragment__add_to_story) { _, _ -> performSend(contacts) }
        .setNegativeButton(android.R.string.cancel) { _, _ -> onSendAborted() }
        .setOnCancelListener { onSendAborted() }
        .show()
    }
  }

  /**
   * Re-enables the screen after the user backs out of sending, since nothing else will restore it.
   */
  private fun onSendAborted() {
    binding.send.isClickable = true
    binding.sendInProgressIndicator.visible = false
    binding.storyTextPost.enableCreationMode()
  }

  private fun performSend(contacts: Set<ContactSearchKey>) {
    lifecycleDisposable += viewModel.send(
      contacts = contacts,
      getLinkPreview()
    ).observeOn(AndroidSchedulers.mainThread()).subscribe { result ->
      when (result) {
        TextStoryPostSendResult.Success -> {
          Toast.makeText(requireContext(), R.string.TextStoryPostCreationFragment__sent_story, Toast.LENGTH_SHORT).show()
          callback.onSentWithoutResult()
        }
        TextStoryPostSendResult.Failure -> {
          Toast.makeText(requireContext(), R.string.TextStoryPostCreationFragment__failed_to_send_story, Toast.LENGTH_SHORT).show()
          requireActivity().finish()
        }
        is TextStoryPostSendResult.UntrustedRecordsError -> {
          binding.send.isClickable = true
          binding.sendInProgressIndicator.visible = false

          SafetyNumberBottomSheet
            .forIdentityRecordsAndDestinations(result.untrustedRecords, contacts.toList())
            .show(childFragmentManager)
        }
      }
    }
  }

  private fun getLinkPreview(): LinkPreview? {
    val linkPreviewState: LinkPreviewState = linkPreviewViewModel.linkPreviewStateSnapshot

    return if (linkPreviewState.linkPreview.isPresent) {
      linkPreviewState.linkPreview.get()
    } else if (!linkPreviewState.activeUrlForError.isNullOrEmpty()) {
      LinkPreview(linkPreviewState.activeUrlForError, linkPreviewState.activeUrlForError, "", 0L, Optional.empty())
    } else {
      null
    }
  }

  override fun sendAnywayAfterSafetyNumberChangedInBottomSheet(destinations: List<ContactSearchKey.RecipientSearchKey>) {
    performSend(destinations.toSet())
  }

  override fun onMessageResentAfterSafetyNumberChangeInBottomSheet() {
    error("Unsupported, we do not hand in a message id.")
  }

  override fun onCanceled() = Unit

  /**
   * Flow-level information the host is responsible for, so that this fragment can be dropped into
   * any media send flow without knowing which one it is running in.
   */
  interface Callback {
    /**
     * The recipients to send to, or empty if the user still needs to select them.
     */
    val textStoryDestinations: Set<ContactSearchKey.RecipientSearchKey>

    val isAddToGroupStoryFlow: Boolean

    /**
     * Text the flow was launched with, used to seed the post body and link preview.
     */
    val textStoryDraftText: CharSequence?

    /**
     * The story was sent by this flow, so there is no payload to hand back to whoever launched it.
     *
     * Shares the name and semantics of [MediaReviewFragment.Callback.onSentWithoutResult] so a host
     * supporting both flows can satisfy them with one implementation.
     */
    fun onSentWithoutResult()
  }
}

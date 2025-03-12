package org.thoughtcrime.securesms.mediasend.v2.review

import android.animation.Animator
import android.animation.AnimatorSet
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewSwitcher
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.signal.core.util.bytes
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.concurrent.SimpleTask
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversation.MessageSendType
import org.thoughtcrime.securesms.conversation.ScheduleMessageContextMenu
import org.thoughtcrime.securesms.conversation.ScheduleMessageTimePickerBottomSheet
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardActivity
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.MediaSendActivityResult
import org.thoughtcrime.securesms.mediasend.v2.HudCommand
import org.thoughtcrime.securesms.mediasend.v2.MediaAnimations
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionNavigator
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionState
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionViewModel
import org.thoughtcrime.securesms.mediasend.v2.MediaValidator
import org.thoughtcrime.securesms.mediasend.v2.stories.StoriesMultiselectForwardActivity
import org.thoughtcrime.securesms.mms.MediaConstraints
import org.thoughtcrime.securesms.mms.SentMediaQuality
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.scribbles.ImageEditorFragment
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.SystemWindowInsetsSetter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.views.TouchInterceptingFrameLayout
import org.thoughtcrime.securesms.util.visible
import org.thoughtcrime.securesms.video.TranscodingQuality
import org.thoughtcrime.securesms.video.videoconverter.VideoThumbnailsRangeSelectorView
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Allows the user to view and edit selected media.
 */
class MediaReviewFragment : Fragment(R.layout.v2_media_review_fragment), ScheduleMessageTimePickerBottomSheet.ScheduleCallback, VideoThumbnailsRangeSelectorView.RangeDragListener {

  private val sharedViewModel: MediaSelectionViewModel by viewModels(
    ownerProducer = { requireActivity() }
  )

  private lateinit var callback: Callback

  private lateinit var drawToolButton: View
  private lateinit var cropAndRotateButton: View
  private lateinit var qualityButton: ImageView
  private lateinit var saveButton: View
  private lateinit var sendButton: ImageView
  private lateinit var addMediaButton: View
  private lateinit var viewOnceButton: ViewSwitcher
  private lateinit var emojiButton: ShapeableImageView
  private lateinit var addMessageButton: TextView
  private lateinit var recipientDisplay: TextView
  private lateinit var pager: ViewPager2
  private lateinit var controls: ConstraintLayout
  private lateinit var selectionRecycler: RecyclerView
  private lateinit var controlsShade: View
  private lateinit var videoTimeLine: VideoThumbnailsRangeSelectorView
  private lateinit var videoSizeHint: TextView
  private lateinit var videoTimelinePlaceholder: View
  private lateinit var progress: ProgressBar
  private lateinit var progressWrapper: TouchInterceptingFrameLayout

  private val exclusionZone = listOf(Rect())
  private val navigator = MediaSelectionNavigator(
    toGallery = R.id.action_mediaReviewFragment_to_mediaGalleryFragment
  )

  private var animatorSet: AnimatorSet? = null
  private var disposables: LifecycleDisposable = LifecycleDisposable()
  private var sentMediaQuality: SentMediaQuality = SignalStore.settings.sentMediaQuality
  private var viewOnceToggleState: MediaSelectionState.ViewOnceToggleState = MediaSelectionState.ViewOnceToggleState.default
  private var scheduledSendTime: Long? = null
  private var readyToSend = true

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    postponeEnterTransition()

    SystemWindowInsetsSetter.attach(view, viewLifecycleOwner)

    disposables.bindTo(viewLifecycleOwner)

    callback = requireListener()

    drawToolButton = view.findViewById(R.id.draw_tool)
    cropAndRotateButton = view.findViewById(R.id.crop_and_rotate_tool)
    qualityButton = view.findViewById(R.id.quality_selector)
    saveButton = view.findViewById(R.id.save_to_media)
    sendButton = view.findViewById(R.id.send)
    addMediaButton = view.findViewById(R.id.add_media)
    viewOnceButton = view.findViewById(R.id.view_once_toggle)
    emojiButton = view.findViewById(R.id.emoji_button)
    addMessageButton = view.findViewById(R.id.add_a_message)
    recipientDisplay = view.findViewById(R.id.recipient)
    pager = view.findViewById(R.id.media_pager)
    controls = view.findViewById(R.id.controls)
    selectionRecycler = view.findViewById(R.id.selection_recycler)
    controlsShade = view.findViewById(R.id.controls_shade)
    progress = view.findViewById(R.id.progress)
    progressWrapper = view.findViewById(R.id.progress_wrapper)
    videoTimeLine = view.findViewById(R.id.video_timeline)
    videoSizeHint = view.findViewById(R.id.video_size_hint)
    videoTimelinePlaceholder = view.findViewById(R.id.timeline_placeholder)

    DrawableCompat.setTint(progress.indeterminateDrawable, Color.WHITE)
    progressWrapper.setOnInterceptTouchEventListener { true }

    val pagerAdapter = MediaReviewFragmentPagerAdapter(this)

    disposables += sharedViewModel.hudCommands.subscribe {
      when (it) {
        HudCommand.ResumeEntryTransition -> startPostponedEnterTransition()
        else -> Unit
      }
    }

    pager.adapter = pagerAdapter

    controls.addOnLayoutChangeListener { v, left, _, right, _, _, _, _, _ ->
      val outRect: Rect = exclusionZone[0]
      videoTimeLine.getHitRect(outRect)
      outRect.left = left
      outRect.right = right
      ViewCompat.setSystemGestureExclusionRects(v, exclusionZone)
    }

    drawToolButton.setOnClickListener {
      sharedViewModel.sendCommand(HudCommand.StartDraw)
    }

    cropAndRotateButton.setOnClickListener {
      sharedViewModel.sendCommand(HudCommand.StartCropAndRotate)
    }

    qualityButton.setOnClickListener {
      QualitySelectorBottomSheet().show(parentFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }

    saveButton.setOnClickListener {
      sharedViewModel.sendCommand(HudCommand.SaveMedia)
    }

    val multiselectContract = MultiselectForwardActivity.SelectionContract()
    val storiesContract = StoriesMultiselectForwardActivity.SelectionContract()

    val multiselectLauncher = registerForActivityResult(multiselectContract) { keys ->
      if (keys.isNotEmpty()) {
        Log.d(TAG, "Performing send from multi-select activity result.")
        performSend(keys)
      } else {
        readyToSend = true
      }
    }

    val storiesLauncher = registerForActivityResult(storiesContract) { keys ->
      if (keys.isNotEmpty()) {
        Log.d(TAG, "Performing send from stories activity result.")
        performSend(keys)
      } else {
        readyToSend = true
      }
    }

    sendButton.setOnClickListener {
      if (!readyToSend) {
        Log.d(TAG, "Attachment send button not currently enabled. Ignoring click event.")
        return@setOnClickListener
      } else {
        Log.d(TAG, "Attachment send button enabled. Processing click event.")
        readyToSend = false
      }

      val viewOnce: Boolean = sharedViewModel.state.value?.viewOnceToggleState == MediaSelectionState.ViewOnceToggleState.ONCE

      if (sharedViewModel.isContactSelectionRequired) {
        val args = MultiselectForwardFragmentArgs(
          title = R.string.MediaReviewFragment__send_to,
          storySendRequirements = sharedViewModel.getStorySendRequirements(),
          isSearchEnabled = !sharedViewModel.isStory(),
          isViewOnce = viewOnce
        )

        if (sharedViewModel.isStory()) {
          val snapshot = sharedViewModel.state.value

          if (snapshot != null) {
            readyToSend = false
            SimpleTask.run(viewLifecycleOwner.lifecycle, {
              snapshot.selectedMedia.take(2).map { media ->
                val editorData = snapshot.editorStateMap[media.uri]
                if (MediaUtil.isImageType(media.contentType) && editorData != null && editorData is ImageEditorFragment.Data) {
                  val model = editorData.readModel()
                  if (model != null) {
                    ImageEditorFragment.renderToSingleUseBlob(requireContext(), model)
                  } else {
                    media.uri
                  }
                } else {
                  media.uri
                }
              }
            }, {
              storiesLauncher.launch(StoriesMultiselectForwardActivity.Args(args, it))
            })
          } else {
            storiesLauncher.launch(StoriesMultiselectForwardActivity.Args(args, emptyList()))
          }
          scheduledSendTime = null
        } else {
          multiselectLauncher.launch(args)
        }
      } else if (sharedViewModel.isAddToGroupStoryFlow) {
        MaterialAlertDialogBuilder(requireContext())
          .setMessage(getString(R.string.MediaReviewFragment__add_to_the_group_story, sharedViewModel.state.value!!.recipient!!.getDisplayName(requireContext())))
          .setPositiveButton(R.string.MediaReviewFragment__add_to_story) { _, _ ->
            Log.d(TAG, "Performing send add to group story dialog.")
            performSend()
          }
          .setNegativeButton(android.R.string.cancel) { _, _ ->
            readyToSend = true
          }
          .setOnCancelListener {
            readyToSend = true
          }
          .setOnDismissListener {
            readyToSend = true
          }
          .show()
        scheduledSendTime = null
      } else {
        Log.d(TAG, "Performing send from send button.")
        performSend()
      }
    }
    if (!sharedViewModel.isStory()) {
      sendButton.setOnLongClickListener {
        ScheduleMessageContextMenu.show(it, (requireView() as ViewGroup)) { time: Long ->
          if (time == -1L) {
            scheduledSendTime = null
            ScheduleMessageTimePickerBottomSheet.showSchedule(childFragmentManager)
          } else {
            scheduledSendTime = time
            sendButton.performClick()
          }
        }
        true
      }
    }

    addMediaButton.setOnClickListener {
      launchGallery()
    }

    viewOnceButton.setOnClickListener {
      sharedViewModel.incrementViewOnceState()
    }

    emojiButton.setOnClickListener {
      AddMessageDialogFragment.show(parentFragmentManager, sharedViewModel.state.value?.message, true)
    }

    addMessageButton.setOnClickListener {
      AddMessageDialogFragment.show(parentFragmentManager, sharedViewModel.state.value?.message, false)
    }

    if (sharedViewModel.isReply) {
      addMessageButton.setText(R.string.MediaReviewFragment__add_a_reply)
    }

    pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
      override fun onPageSelected(position: Int) {
        qualityButton.alpha = 0f
        saveButton.alpha = 0f
        sharedViewModel.onPageChanged(position)
      }
    })

    if (MediaConstraints.isVideoTranscodeAvailable()) {
      videoTimeLine.registerEditorOnRangeChangeListener(this)
    }

    val selectionAdapter = MappingAdapter(false)
    MediaReviewAddItem.register(selectionAdapter) {
      launchGallery()
    }
    MediaReviewSelectedItem.register(selectionAdapter) { media, isSelected ->
      if (isSelected) {
        sharedViewModel.removeMedia(media)
      } else {
        sharedViewModel.onPageChanged(media)
      }
    }
    selectionRecycler.adapter = selectionAdapter
    ItemTouchHelper(MediaSelectionItemTouchHelper(sharedViewModel)).attachToRecyclerView(selectionRecycler)

    sharedViewModel.state.observe(viewLifecycleOwner) { state ->
      pagerAdapter.submitMedia(state.selectedMedia)

      selectionAdapter.submitList(
        state.selectedMedia.map { MediaReviewSelectedItem.Model(it, state.focusedMedia == it) } + MediaReviewAddItem.Model
      )

      presentSendButton(readyToSend, state.sendType, state.recipient)
      presentPager(state)
      presentAddMessageEntry(state.viewOnceToggleState, state.message)
      presentImageQualityToggle(state)
      if (state.quality != sentMediaQuality) {
        presentQualityToggleToast(state)
      }
      sentMediaQuality = state.quality

      viewOnceButton.displayedChild = if (state.viewOnceToggleState == MediaSelectionState.ViewOnceToggleState.ONCE) 1 else 0
      if (state.viewOnceToggleState != viewOnceToggleState &&
        state.viewOnceToggleState == MediaSelectionState.ViewOnceToggleState.ONCE &&
        state.selectedMedia.size == 1
      ) {
        presentViewOnceToggleToast(MediaUtil.isNonGifVideo(state.selectedMedia[0]))
      }
      viewOnceToggleState = state.viewOnceToggleState

      presentVideoTimeline(state)
      presentVideoSizeHint(state)

      computeViewStateAndAnimate(state)
    }

    disposables.bindTo(viewLifecycleOwner)
    disposables += sharedViewModel.mediaErrors
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(this::handleMediaValidatorFilterError)

    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          callback.onPopFromReview()
        }
      }
    )
  }

  private fun presentViewOnceToggleToast(isVideo: Boolean) {
    val description = if (isVideo) {
      getString(R.string.MediaReviewFragment__video_set_to_view_once)
    } else {
      getString(R.string.MediaReviewFragment__photo_set_to_view_once)
    }

    MediaReviewToastPopupWindow.show(controls, R.drawable.symbol_view_once_24, description)
  }

  private fun presentQualityToggleToast(state: MediaSelectionState) {
    val mediaList = state.selectedMedia
    if (mediaList.isEmpty()) {
      return
    }

    val description = if (mediaList.size == 1) {
      val media: Media = mediaList[0]
      if (MediaUtil.isNonGifVideo(media)) {
        if (state.quality == SentMediaQuality.HIGH) {
          getString(R.string.MediaReviewFragment__video_set_to_high_quality)
        } else {
          getString(R.string.MediaReviewFragment__video_set_to_standard_quality)
        }
      } else if (MediaUtil.isImageType(media.contentType)) {
        if (state.quality == SentMediaQuality.HIGH) {
          getString(R.string.MediaReviewFragment__photo_set_to_high_quality)
        } else {
          getString(R.string.MediaReviewFragment__photo_set_to_standard_quality)
        }
      } else {
        Log.i(TAG, "Could not display quality toggle toast for attachment of type: ${media.contentType}")
        return
      }
    } else {
      if (state.quality == SentMediaQuality.HIGH) {
        resources.getQuantityString(R.plurals.MediaReviewFragment__items_set_to_high_quality, mediaList.size, mediaList.size)
      } else {
        resources.getQuantityString(R.plurals.MediaReviewFragment__items_set_to_standard_quality, mediaList.size, mediaList.size)
      }
    }

    val icon = when (state.quality) {
      SentMediaQuality.HIGH -> R.drawable.symbol_quality_high_24
      else -> R.drawable.symbol_quality_high_slash_24
    }

    MediaReviewToastPopupWindow.show(controls, icon, description)
  }

  override fun onResume() {
    super.onResume()
    sharedViewModel.kick()
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }

  private fun handleMediaValidatorFilterError(error: MediaValidator.FilterError) {
    when (error) {
      MediaValidator.FilterError.None -> return
      MediaValidator.FilterError.ItemTooLarge -> Toast.makeText(requireContext(), R.string.MediaReviewFragment__one_or_more_items_were_too_large, Toast.LENGTH_SHORT).show()
      MediaValidator.FilterError.ItemInvalidType -> Toast.makeText(requireContext(), R.string.MediaReviewFragment__one_or_more_items_were_invalid, Toast.LENGTH_SHORT).show()
      MediaValidator.FilterError.TooManyItems -> Toast.makeText(requireContext(), R.string.MediaReviewFragment__too_many_items_selected, Toast.LENGTH_SHORT).show()
      is MediaValidator.FilterError.NoItems -> {
        if (error.cause != null) {
          handleMediaValidatorFilterError(error.cause)
        } else {
          Toast.makeText(requireContext(), R.string.MediaReviewFragment__one_or_more_items_were_invalid, Toast.LENGTH_SHORT).show()
        }
        callback.onNoMediaSelected()
      }
    }

    sharedViewModel.clearMediaErrors()
  }

  private fun launchGallery() {
    val controller = findNavController()
    navigator.goToGallery(controller)
  }

  private fun performSend(selection: List<ContactSearchKey> = listOf()) {
    Log.d(TAG, "Performing attachment send.")
    readyToSend = false
    progressWrapper.visible = true
    progressWrapper.animate()
      .setStartDelay(300)
      .setInterpolator(MediaAnimations.interpolator)
      .alpha(1f)

    disposables += sharedViewModel
      .send(selection.filterIsInstance<ContactSearchKey.RecipientSearchKey>(), scheduledSendTime)
      .subscribe(
        { result ->
          callback.onSentWithResult(result)
          readyToSend = true
        },
        { error ->
          callback.onSendError(error)
          readyToSend = true
        },
        {
          callback.onSentWithoutResult()
          readyToSend = true
        }
      )
  }

  private fun presentAddMessageEntry(viewOnceState: MediaSelectionState.ViewOnceToggleState, message: CharSequence?) {
    when (viewOnceState) {
      MediaSelectionState.ViewOnceToggleState.INFINITE -> {
        addMessageButton.gravity = Gravity.CENTER_VERTICAL
        addMessageButton.setText(
          message.takeIf { it.isNotNullOrBlank() } ?: getString(R.string.MediaReviewFragment__add_a_message),
          TextView.BufferType.SPANNABLE
        )
        addMessageButton.isClickable = true
      }
      MediaSelectionState.ViewOnceToggleState.ONCE -> {
        addMessageButton.gravity = Gravity.CENTER
        addMessageButton.setText(R.string.MediaReviewFragment__view_once_message)
        addMessageButton.isClickable = false
      }
    }
  }

  private fun presentImageQualityToggle(state: MediaSelectionState) {
    qualityButton.updateLayoutParams<ConstraintLayout.LayoutParams> {
      if (MediaUtil.isImageAndNotGif(state.focusedMedia?.contentType ?: "")) {
        startToStart = ConstraintLayout.LayoutParams.UNSET
        startToEnd = cropAndRotateButton.id
      } else {
        startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        startToEnd = ConstraintLayout.LayoutParams.UNSET
      }
    }
    qualityButton.setImageResource(
      when (state.quality) {
        SentMediaQuality.STANDARD -> R.drawable.symbol_quality_high_slash_24
        SentMediaQuality.HIGH -> R.drawable.symbol_quality_high_24
      }
    )
  }

  private fun presentSendButton(enabled: Boolean, sendType: MessageSendType, recipient: Recipient?) {
    val sendButtonBackgroundTint = when {
      !enabled -> ContextCompat.getColor(requireContext(), R.color.core_grey_50)
      recipient != null -> recipient.chatColors.asSingleColor()
      sendType.usesSignalTransport -> ContextCompat.getColor(requireContext(), R.color.signal_colorOnSecondaryContainer)
      else -> ContextCompat.getColor(requireContext(), R.color.core_grey_50)
    }

    val sendButtonForegroundDrawable = when {
      recipient != null -> ContextCompat.getDrawable(requireContext(), R.drawable.symbol_send_fill_24)
      else -> ContextCompat.getDrawable(requireContext(), R.drawable.symbol_arrow_end_24)
    }

    val sendButtonForegroundTint = when {
      !enabled -> ContextCompat.getColor(requireContext(), R.color.signal_colorSecondaryContainer)
      recipient != null -> ContextCompat.getColor(requireContext(), R.color.signal_colorOnCustom)
      else -> ContextCompat.getColor(requireContext(), R.color.signal_colorSecondaryContainer)
    }

    sendButton.setImageDrawable(sendButtonForegroundDrawable)
    sendButton.setColorFilter(sendButtonForegroundTint)
    ViewCompat.setBackgroundTintList(sendButton, ColorStateList.valueOf(sendButtonBackgroundTint))
  }

  private fun presentPager(state: MediaSelectionState) {
    pager.isUserInputEnabled = state.isTouchEnabled

    val indexOfSelectedItem = state.selectedMedia.indexOf(state.focusedMedia)

    if (pager.currentItem == indexOfSelectedItem) {
      return
    }

    if (indexOfSelectedItem != -1) {
      pager.setCurrentItem(indexOfSelectedItem, false)
    } else {
      pager.setCurrentItem(0, false)
    }
  }

  private fun presentVideoTimeline(state: MediaSelectionState) {
    val mediaItem = state.focusedMedia ?: return
    if (!MediaUtil.isVideoType(mediaItem.contentType) || !MediaConstraints.isVideoTranscodeAvailable()) {
      return
    }
    val uri = mediaItem.uri
    val updatedInputInTimeline = videoTimeLine.setInput(uri)
    if (updatedInputInTimeline) {
      videoTimeLine.unregisterDragListener()
    }
    val size: Long = tryGetUriSize(requireContext(), uri, Long.MAX_VALUE)
    val maxSend = sharedViewModel.getMediaConstraints().getVideoMaxSize()
    if (size > maxSend) {
      videoTimeLine.setTimeLimit(state.transcodingPreset.calculateMaxVideoUploadDurationInSeconds(maxSend), TimeUnit.SECONDS)
    }

    if (state.isTouchEnabled) {
      val data = state.getOrCreateVideoTrimData(uri)

      if (data.totalInputDurationUs > 0) {
        videoTimeLine.setRange(data.startTimeUs, data.endTimeUs)
      }
    }
  }

  private fun presentVideoSizeHint(state: MediaSelectionState) {
    val focusedMedia = state.focusedMedia ?: return
    val trimData = state.getOrCreateVideoTrimData(focusedMedia.uri)

    videoSizeHint.text = if (state.isVideoTrimmingVisible) {
      val seconds = trimData.getDuration().inWholeSeconds
      val bytes = TranscodingQuality.createFromPreset(state.transcodingPreset, trimData.getDuration().inWholeMilliseconds).byteCountEstimate
      String.format(Locale.getDefault(), "%d:%02d • %s", seconds / 60, seconds % 60, bytes.bytes.toUnitString())
    } else {
      null
    }
  }

  private fun computeViewStateAndAnimate(state: MediaSelectionState) {
    this.animatorSet?.cancel()

    val animators = mutableListOf<Animator>()

    animators.addAll(computeAddMessageAnimators(state))
    animators.addAll(computeEmojiButtonAnimators(state))
    animators.addAll(computeViewOnceButtonAnimators(state))
    animators.addAll(computeAddMediaButtonsAnimators(state))
    animators.addAll(computeSendButtonAnimators(state))
    animators.addAll(computeSaveButtonAnimators(state))
    animators.addAll(computeQualityButtonAnimators(state))
    animators.addAll(computeCropAndRotateButtonAnimators(state))
    animators.addAll(computeDrawToolButtonAnimators(state))
    animators.addAll(computeRecipientDisplayAnimators(state))
    animators.addAll(computeControlsShadeAnimators(state))
    animators.addAll(computeVideoTimelineAnimator(state))

    val animatorSet = AnimatorSet()
    animatorSet.playTogether(animators)
    animatorSet.start()

    this.animatorSet = animatorSet
  }

  private fun computeControlsShadeAnimators(state: MediaSelectionState): List<Animator> {
    val animators = mutableListOf<Animator>()
    animators += if (state.isTouchEnabled) {
      MediaReviewAnimatorController.getFadeInAnimator(controlsShade)
    } else {
      MediaReviewAnimatorController.getFadeOutAnimator(controlsShade)
    }

    animators += if (state.isVideoTrimmingVisible) {
      MediaReviewAnimatorController.getHeightAnimator(videoTimelinePlaceholder, videoTimelinePlaceholder.height, resources.getDimension(R.dimen.video_timeline_height_expanded).roundToInt())
    } else {
      MediaReviewAnimatorController.getHeightAnimator(videoTimelinePlaceholder, videoTimelinePlaceholder.height, resources.getDimension(R.dimen.video_timeline_height_collapsed).roundToInt())
    }

    return animators
  }

  private fun computeVideoTimelineAnimator(state: MediaSelectionState): List<Animator> {
    val animators = mutableListOf<Animator>()

    if (state.isVideoTrimmingVisible) {
      animators += MediaReviewAnimatorController.getFadeInAnimator(videoTimeLine).apply {
        startDelay = 100
        duration = 500
      }
    } else {
      animators += MediaReviewAnimatorController.getFadeOutAnimator(videoTimeLine).apply {
        duration = 400
      }
    }

    animators += if (state.isVideoTrimmingVisible && state.isTouchEnabled) {
      MediaReviewAnimatorController.getFadeInAnimator(videoSizeHint).apply {
        startDelay = 100
        duration = 500
      }
    } else {
      MediaReviewAnimatorController.getFadeOutAnimator(videoSizeHint).apply {
        duration = 400
      }
    }

    return animators
  }

  private fun computeAddMessageAnimators(state: MediaSelectionState): List<Animator> {
    return if (!state.isTouchEnabled) {
      listOf(
        MediaReviewAnimatorController.getFadeOutAnimator(addMessageButton)
      )
    } else {
      listOf(
        MediaReviewAnimatorController.getFadeInAnimator(addMessageButton)
      )
    }
  }

  private fun computeViewOnceButtonAnimators(state: MediaSelectionState): List<Animator> {
    return if (state.isTouchEnabled && state.selectedMedia.size == 1 && !state.isStory && !MediaUtil.isDocumentType(state.focusedMedia?.contentType)) {
      listOf(MediaReviewAnimatorController.getFadeInAnimator(viewOnceButton))
    } else {
      listOf(MediaReviewAnimatorController.getFadeOutAnimator(viewOnceButton))
    }
  }

  private fun computeEmojiButtonAnimators(state: MediaSelectionState): List<Animator> {
    return if (state.isTouchEnabled && state.viewOnceToggleState != MediaSelectionState.ViewOnceToggleState.ONCE) {
      listOf(MediaReviewAnimatorController.getFadeInAnimator(emojiButton))
    } else {
      listOf(MediaReviewAnimatorController.getFadeOutAnimator(emojiButton))
    }
  }

  private fun computeAddMediaButtonsAnimators(state: MediaSelectionState): List<Animator> {
    return when {
      !state.isTouchEnabled || state.viewOnceToggleState == MediaSelectionState.ViewOnceToggleState.ONCE || MediaUtil.isDocumentType(state.focusedMedia?.contentType) -> {
        listOf(
          MediaReviewAnimatorController.getFadeOutAnimator(addMediaButton),
          MediaReviewAnimatorController.getFadeOutAnimator(selectionRecycler)
        )
      }
      state.selectedMedia.size > 1 -> {
        listOf(
          MediaReviewAnimatorController.getFadeOutAnimator(addMediaButton),
          MediaReviewAnimatorController.getFadeInAnimator(selectionRecycler)
        )
      }
      else -> {
        listOf(
          MediaReviewAnimatorController.getFadeInAnimator(addMediaButton),
          MediaReviewAnimatorController.getFadeOutAnimator(selectionRecycler)
        )
      }
    }
  }

  private fun computeSendButtonAnimators(state: MediaSelectionState): List<Animator> {
    return if (state.isTouchEnabled) {
      listOf(
        MediaReviewAnimatorController.getFadeInAnimator(sendButton, isEnabled = state.canSend)
      )
    } else {
      listOf(
        MediaReviewAnimatorController.getFadeOutAnimator(sendButton, isEnabled = state.canSend)
      )
    }
  }

  private fun computeSaveButtonAnimators(state: MediaSelectionState): List<Animator> {
    return if (state.isTouchEnabled && !MediaUtil.isVideo(state.focusedMedia?.contentType) && !MediaUtil.isDocumentType(state.focusedMedia?.contentType)) {
      listOf(
        MediaReviewAnimatorController.getFadeInAnimator(saveButton)
      )
    } else {
      listOf(
        MediaReviewAnimatorController.getFadeOutAnimator(saveButton)
      )
    }
  }

  private fun computeQualityButtonAnimators(state: MediaSelectionState): List<Animator> {
    return if (state.isTouchEnabled && !state.isStory && !MediaUtil.isDocumentType(state.focusedMedia?.contentType)) {
      listOf(MediaReviewAnimatorController.getFadeInAnimator(qualityButton))
    } else {
      listOf(MediaReviewAnimatorController.getFadeOutAnimator(qualityButton))
    }
  }

  private fun computeCropAndRotateButtonAnimators(state: MediaSelectionState): List<Animator> {
    return if (state.isTouchEnabled && MediaUtil.isImageAndNotGif(state.focusedMedia?.contentType ?: "")) {
      listOf(MediaReviewAnimatorController.getFadeInAnimator(cropAndRotateButton))
    } else {
      listOf(MediaReviewAnimatorController.getFadeOutAnimator(cropAndRotateButton))
    }
  }

  private fun computeDrawToolButtonAnimators(state: MediaSelectionState): List<Animator> {
    return if (state.isTouchEnabled && MediaUtil.isImageAndNotGif(state.focusedMedia?.contentType ?: "")) {
      listOf(MediaReviewAnimatorController.getFadeInAnimator(drawToolButton))
    } else {
      listOf(MediaReviewAnimatorController.getFadeOutAnimator(drawToolButton))
    }
  }

  private fun computeRecipientDisplayAnimators(state: MediaSelectionState): List<Animator> {
    return if (state.isTouchEnabled && state.recipient != null) {
      recipientDisplay.text = if (state.recipient.isSelf) requireContext().getString(R.string.note_to_self) else state.recipient.getDisplayName(requireContext())
      listOf(MediaReviewAnimatorController.getFadeInAnimator(recipientDisplay))
    } else {
      listOf(MediaReviewAnimatorController.getFadeOutAnimator(recipientDisplay))
    }
  }

  companion object {
    private val TAG = Log.tag(MediaReviewFragment::class.java)

    @JvmStatic
    private fun tryGetUriSize(context: Context, uri: Uri, defaultValue: Long): Long {
      return try {
        var size: Long = 0
        context.contentResolver.query(uri, null, null, null, null).use { cursor ->
          if (cursor != null && cursor.moveToFirst() && cursor.getColumnIndex(OpenableColumns.SIZE) >= 0) {
            size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
          }
        }
        if (size <= 0) {
          size = MediaUtil.getMediaSize(context, uri)
        }
        size
      } catch (e: IOException) {
        Log.w(TAG, e)
        defaultValue
      }
    }
  }

  interface Callback {
    fun onSentWithResult(mediaSendActivityResult: MediaSendActivityResult)
    fun onSentWithoutResult()
    fun onSendError(error: Throwable)
    fun onNoMediaSelected()
    fun onPopFromReview()
  }

  override fun onScheduleSend(scheduledTime: Long) {
    scheduledSendTime = scheduledTime
    sendButton.performClick()
  }

  override fun onRangeDrag(minValue: Long, maxValue: Long, duration: Long, end: Boolean) {
    sharedViewModel.onEditVideoDuration(totalDurationUs = duration, startTimeUs = minValue, endTimeUs = maxValue, touchEnabled = end)
  }
}

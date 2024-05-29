/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.components.transfercontrols

import android.content.Context
import android.os.Build
import android.text.StaticLayout
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.components.RecyclerViewParentTransitionController
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.databinding.TransferControlsViewBinding
import org.thoughtcrime.securesms.events.PartProgressEvent
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.ThrottledDebouncer
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.visible
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt

class TransferControlView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr) {
  private val uuid = UUID.randomUUID().toString()
  private val binding: TransferControlsViewBinding

  private var state = TransferControlViewState()
  private val progressUpdateDebouncer: ThrottledDebouncer = ThrottledDebouncer(100)

  private var mode: Mode = Mode.GONE

  init {
    tag = uuid
    binding = TransferControlsViewBinding.inflate(LayoutInflater.from(context), this)
    visibility = GONE
    isLongClickable = false

    addOnAttachStateChangeListener(RecyclerViewParentTransitionController(child = this))
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    EventBus.getDefault().unregister(this)
  }

  private fun updateState(stateFactory: (TransferControlViewState) -> TransferControlViewState) {
    val newState = stateFactory.invoke(state)
    if (newState != state && !(deriveMode(state) == Mode.GONE && deriveMode(newState) == Mode.GONE)) {
      progressUpdateDebouncer.publish {
        applyState(newState)
      }
    }
    state = newState
  }

  fun isGone(): Boolean {
    return mode == Mode.GONE
  }

  private fun applyState(currentState: TransferControlViewState) {
    val mode = deriveMode(currentState)
    verboseLog("New state applying, mode = $mode")

    children.forEach {
      it.clearAnimation()
    }

    when (mode) {
      Mode.PENDING_GALLERY -> displayPendingGallery(currentState)
      Mode.PENDING_GALLERY_CONTAINS_PLAYABLE -> displayPendingGalleryWithPlayable(currentState)
      Mode.PENDING_SINGLE_ITEM -> displayPendingSingleItem(currentState)
      Mode.PENDING_VIDEO_PLAYABLE -> displayPendingPlayableVideo(currentState)
      Mode.DOWNLOADING_GALLERY -> displayDownloadingGallery(currentState)
      Mode.DOWNLOADING_SINGLE_ITEM -> displayDownloadingSingleItem(currentState)
      Mode.DOWNLOADING_VIDEO_PLAYABLE -> displayDownloadingPlayableVideo(currentState)
      Mode.UPLOADING_GALLERY -> displayUploadingGallery(currentState)
      Mode.UPLOADING_SINGLE_ITEM -> displayUploadingSingleItem(currentState)
      Mode.RETRY_DOWNLOADING -> displayRetry(currentState, false)
      Mode.RETRY_UPLOADING -> displayRetry(currentState, true)
      Mode.GONE -> displayChildrenAsGone()
    }
    this.mode = mode
  }

  private fun deriveMode(currentState: TransferControlViewState): Mode {
    if (currentState.slides.isEmpty()) {
      verboseLog("Setting empty slide deck to GONE")
      return Mode.GONE
    }

    if (currentState.slides.all { it.transferState == AttachmentTable.TRANSFER_PROGRESS_DONE }) {
      verboseLog("Setting slide deck that's finished to GONE\n\t${slidesAsListOfTimestamps(currentState.slides)}")
      return Mode.GONE
    }

    if (currentState.isVisible) {
      if (currentState.slides.size == 1) {
        val slide = currentState.slides.first()
        if (slide.hasVideo()) {
          if (currentState.isUpload) {
            return when (slide.transferState) {
              AttachmentTable.TRANSFER_PROGRESS_STARTED -> {
                Mode.UPLOADING_SINGLE_ITEM
              }

              AttachmentTable.TRANSFER_PROGRESS_PENDING -> {
                Mode.PENDING_SINGLE_ITEM
              }

              else -> {
                Mode.RETRY_UPLOADING
              }
            }
          } else {
            return when (slide.transferState) {
              AttachmentTable.TRANSFER_PROGRESS_STARTED -> {
                if (currentState.playableWhileDownloading) {
                  Mode.DOWNLOADING_VIDEO_PLAYABLE
                } else {
                  Mode.DOWNLOADING_SINGLE_ITEM
                }
              }

              AttachmentTable.TRANSFER_PROGRESS_FAILED -> {
                Mode.RETRY_DOWNLOADING
              }

              else -> {
                if (currentState.playableWhileDownloading) {
                  Mode.PENDING_VIDEO_PLAYABLE
                } else {
                  Mode.PENDING_SINGLE_ITEM
                }
              }
            }
          }
        } else {
          return if (currentState.isUpload) {
            when (slide.transferState) {
              AttachmentTable.TRANSFER_PROGRESS_FAILED -> {
                Mode.RETRY_UPLOADING
              }

              AttachmentTable.TRANSFER_PROGRESS_PENDING -> {
                Mode.PENDING_SINGLE_ITEM
              }

              else -> {
                Mode.UPLOADING_SINGLE_ITEM
              }
            }
          } else {
            return when (slide.transferState) {
              AttachmentTable.TRANSFER_PROGRESS_STARTED -> {
                Mode.DOWNLOADING_SINGLE_ITEM
              }

              AttachmentTable.TRANSFER_PROGRESS_FAILED -> {
                Mode.RETRY_DOWNLOADING
              }

              else -> {
                Mode.PENDING_SINGLE_ITEM
              }
            }
          }
        }
      } else {
        when (getTransferState(currentState.slides)) {
          AttachmentTable.TRANSFER_PROGRESS_STARTED -> {
            return if (currentState.isUpload) {
              Mode.UPLOADING_GALLERY
            } else {
              Mode.DOWNLOADING_GALLERY
            }
          }

          AttachmentTable.TRANSFER_PROGRESS_PENDING -> {
            return if (containsPlayableSlides(currentState.slides)) {
              Mode.PENDING_GALLERY_CONTAINS_PLAYABLE
            } else {
              Mode.PENDING_GALLERY
            }
          }

          AttachmentTable.TRANSFER_PROGRESS_FAILED -> {
            return if (currentState.isUpload) {
              Mode.RETRY_UPLOADING
            } else {
              Mode.RETRY_DOWNLOADING
            }
          }

          AttachmentTable.TRANSFER_PROGRESS_DONE -> {
            verboseLog("[Case 2] Setting slide deck that's finished to GONE\t${slidesAsListOfTimestamps(currentState.slides)}")
            return Mode.GONE
          }
        }
      }
    } else {
      verboseLog("Setting slide deck to GONE because isVisible is false:\t${slidesAsListOfTimestamps(currentState.slides)}")
      return Mode.GONE
    }

    Log.i(TAG, "[$uuid] Hit default mode case, this should not happen.")
    return Mode.GONE
  }

  private fun displayPendingGallery(currentState: TransferControlViewState) {
    binding.primaryProgressView.startClickListener = currentState.startTransferClickListener
    applyFocusableAndClickable(
      currentState,
      listOf(binding.primaryProgressView, binding.primaryDetailsText, binding.primaryBackground),
      listOf(binding.secondaryProgressView, binding.playVideoButton)
    )
    binding.primaryProgressView.setStopped(false)
    showAllViews(
      playVideoButton = false,
      secondaryProgressView = false,
      secondaryDetailsText = currentState.showSecondaryText
    )

    binding.primaryDetailsText.setOnClickListener(currentState.startTransferClickListener)
    binding.primaryBackground.setOnClickListener(currentState.startTransferClickListener)

    binding.primaryDetailsText.translationX = if (ViewUtil.isLtr(this)) {
      ViewUtil.dpToPx(-PRIMARY_TEXT_OFFSET_DP).toFloat()
    } else {
      ViewUtil.dpToPx(PRIMARY_TEXT_OFFSET_DP).toFloat()
    }
    setSecondaryDetailsText(currentState)
  }

  private fun displayPendingGalleryWithPlayable(currentState: TransferControlViewState) {
    binding.secondaryProgressView.startClickListener = currentState.startTransferClickListener
    binding.secondaryDetailsText.setOnClickListener(currentState.startTransferClickListener)
    binding.secondaryBackground.setOnClickListener(currentState.startTransferClickListener)
    super.setClickable(false)
    binding.secondaryProgressView.isClickable = currentState.showSecondaryText
    binding.secondaryProgressView.isFocusable = currentState.showSecondaryText
    binding.secondaryDetailsText.isClickable = currentState.showSecondaryText
    binding.secondaryDetailsText.isFocusable = currentState.showSecondaryText
    binding.secondaryBackground.isClickable = currentState.showSecondaryText
    binding.secondaryBackground.isFocusable = currentState.showSecondaryText
    binding.primaryProgressView.isClickable = false
    binding.primaryProgressView.isFocusable = false
    showAllViews(
      playVideoButton = false,
      primaryProgressView = false,
      primaryDetailsText = false,
      secondaryProgressView = currentState.showSecondaryText,
      secondaryDetailsText = currentState.showSecondaryText
    )

    binding.secondaryProgressView.setStopped(false)
    setSecondaryDetailsText(currentState)
    binding.secondaryDetailsText.translationX = if (ViewUtil.isLtr(this)) {
      ViewUtil.dpToPx(-SECONDARY_TEXT_OFFSET_DP).toFloat()
    } else {
      ViewUtil.dpToPx(SECONDARY_TEXT_OFFSET_DP).toFloat()
    }
  }

  private fun displayPendingSingleItem(currentState: TransferControlViewState) {
    binding.primaryProgressView.startClickListener = currentState.startTransferClickListener
    applyFocusableAndClickable(currentState, listOf(binding.primaryProgressView), listOf(binding.secondaryProgressView, binding.playVideoButton))
    binding.primaryProgressView.setStopped(false)
    showAllViews(
      playVideoButton = false,
      primaryDetailsText = false,
      secondaryProgressView = false,
      secondaryDetailsText = currentState.showSecondaryText
    )
    binding.secondaryDetailsText.translationX = 0f
    setSecondaryDetailsText(currentState)
  }

  private fun displayPendingPlayableVideo(currentState: TransferControlViewState) {
    binding.secondaryProgressView.startClickListener = currentState.startTransferClickListener
    binding.secondaryDetailsText.setOnClickListener(currentState.startTransferClickListener)
    binding.secondaryBackground.setOnClickListener(currentState.startTransferClickListener)
    binding.playVideoButton.setOnClickListener(currentState.instantPlaybackClickListener)
    applyFocusableAndClickable(
      currentState,
      listOf(binding.secondaryProgressView, binding.secondaryDetailsText, binding.secondaryBackground, binding.playVideoButton),
      listOf(binding.primaryProgressView)
    )
    binding.secondaryProgressView.setStopped(false)
    showAllViews(
      primaryProgressView = false,
      primaryDetailsText = false,
      secondaryDetailsText = currentState.showSecondaryText,
      secondaryProgressView = currentState.showSecondaryText
    )
    setSecondaryDetailsText(currentState)
    binding.secondaryDetailsText.translationX = if (ViewUtil.isLtr(this)) {
      ViewUtil.dpToPx(-SECONDARY_TEXT_OFFSET_DP).toFloat()
    } else {
      ViewUtil.dpToPx(SECONDARY_TEXT_OFFSET_DP).toFloat()
    }
  }

  private fun displayDownloadingGallery(currentState: TransferControlViewState) {
    applyFocusableAndClickable(currentState, listOf(binding.secondaryProgressView), listOf(binding.primaryProgressView, binding.playVideoButton))
    showAllViews(
      playVideoButton = false,
      primaryProgressView = false,
      primaryDetailsText = false,
      secondaryDetailsText = currentState.showSecondaryText
    )

    val progress = calculateProgress(currentState)
    if (progress == 0f) {
      binding.secondaryProgressView.setProgress(progress)
    } else {
      binding.secondaryProgressView.cancelClickListener = currentState.cancelTransferClickedListener
      binding.secondaryProgressView.setProgress(progress)
    }
    binding.secondaryDetailsText.translationX = 0f
    setSecondaryDetailsText(currentState)
  }

  private fun displayDownloadingSingleItem(currentState: TransferControlViewState) {
    binding.primaryProgressView.cancelClickListener = currentState.cancelTransferClickedListener
    applyFocusableAndClickable(currentState, listOf(binding.primaryProgressView), listOf(binding.secondaryProgressView, binding.playVideoButton))
    showAllViews(
      playVideoButton = false,
      primaryDetailsText = false,
      secondaryProgressView = false,
      secondaryDetailsText = currentState.showSecondaryText
    )

    val progress = calculateProgress(currentState)
    if (progress == 0f) {
      binding.primaryProgressView.setProgress(progress)
    } else {
      binding.primaryProgressView.setProgress(progress)
    }
    binding.secondaryDetailsText.translationX = 0f
    setSecondaryDetailsText(currentState)
  }

  private fun displayDownloadingPlayableVideo(currentState: TransferControlViewState) {
    binding.secondaryProgressView.cancelClickListener = currentState.cancelTransferClickedListener
    applyFocusableAndClickable(currentState, listOf(binding.secondaryProgressView, binding.playVideoButton), listOf(binding.primaryProgressView))
    showAllViews(
      primaryDetailsText = false,
      secondaryProgressView = currentState.showSecondaryText,
      secondaryDetailsText = currentState.showSecondaryText
    )

    binding.playVideoButton.setOnClickListener(currentState.instantPlaybackClickListener)

    val progress = calculateProgress(currentState)
    if (progress == 0f) {
      binding.secondaryProgressView.setProgress(progress)
    } else {
      binding.secondaryProgressView.setProgress(progress)
    }
    binding.secondaryDetailsText.translationX = 0f
    setSecondaryDetailsText(currentState)
  }

  private fun displayUploadingSingleItem(currentState: TransferControlViewState) {
    binding.secondaryProgressView.cancelClickListener = currentState.cancelTransferClickedListener
    applyFocusableAndClickable(currentState, listOf(binding.secondaryProgressView), listOf(binding.primaryProgressView, binding.playVideoButton))
    showAllViews(
      playVideoButton = false,
      primaryProgressView = false,
      primaryDetailsText = false,
      secondaryDetailsText = currentState.showSecondaryText
    )

    val progress = calculateProgress(currentState)
    binding.secondaryProgressView.setProgress(progress)

    binding.secondaryDetailsText.translationX = 0f
    setSecondaryDetailsText(currentState)
  }

  private fun displayUploadingGallery(currentState: TransferControlViewState) {
    binding.secondaryProgressView.cancelClickListener = currentState.cancelTransferClickedListener
    applyFocusableAndClickable(currentState, listOf(binding.secondaryProgressView), listOf(binding.primaryProgressView, binding.playVideoButton))
    showAllViews(
      playVideoButton = false,
      primaryProgressView = false,
      primaryDetailsText = false
    )

    val progress = calculateProgress(currentState)
    binding.secondaryProgressView.setProgress(progress)

    binding.secondaryDetailsText.translationX = 0f
    setSecondaryDetailsText(currentState)
  }

  private fun displayRetry(currentState: TransferControlViewState, isUploading: Boolean) {
    if (currentState.startTransferClickListener == null) {
      Log.w(TAG, "No click listener set for retry!")
    }

    binding.secondaryProgressView.startClickListener = currentState.startTransferClickListener
    applyFocusableAndClickable(
      currentState,
      listOf(binding.secondaryProgressView, binding.secondaryDetailsText, binding.secondaryBackground),
      listOf(binding.primaryProgressView, binding.playVideoButton)
    )
    showAllViews(
      playVideoButton = false,
      primaryProgressView = false,
      primaryDetailsText = false,
      secondaryDetailsText = currentState.showSecondaryText
    )
    binding.secondaryBackground.setOnClickListener(currentState.startTransferClickListener)
    binding.secondaryDetailsText.setOnClickListener(currentState.startTransferClickListener)
    binding.secondaryProgressView.setStopped(isUploading)
    setSecondaryDetailsText(currentState)
    binding.secondaryDetailsText.translationX = if (ViewUtil.isLtr(this)) {
      ViewUtil.dpToPx(-RETRY_SECONDARY_TEXT_OFFSET_DP).toFloat()
    } else {
      ViewUtil.dpToPx(RETRY_SECONDARY_TEXT_OFFSET_DP).toFloat()
    }
  }

  private fun displayChildrenAsGone() {
    children.forEach {
      if (it.visible && it.animation == null) {
        ViewUtil.fadeOut(it, 250)
      }
    }
  }

  /**
   * Shows all views by defaults, but allows individual views to be overridden to not be shown.
   *
   * @param root
   * @param playVideoButton
   * @param primaryProgressView
   * @param primaryDetailsText
   * @param secondaryProgressView
   * @param secondaryDetailsText
   */
  private fun showAllViews(
    root: Boolean = true,
    playVideoButton: Boolean = true,
    primaryProgressView: Boolean = true,
    primaryDetailsText: Boolean = true,
    secondaryProgressView: Boolean = true,
    secondaryDetailsText: Boolean = true
  ) {
    this.visible = root
    binding.playVideoButton.visible = playVideoButton
    binding.primaryProgressView.visibility = if (primaryProgressView) View.VISIBLE else View.INVISIBLE
    binding.primaryDetailsText.visible = primaryDetailsText
    binding.primaryBackground.visible = primaryProgressView || primaryDetailsText || playVideoButton
    binding.secondaryProgressView.visible = secondaryProgressView
    binding.secondaryDetailsText.visible = secondaryDetailsText
    binding.secondaryBackground.visible = secondaryProgressView || secondaryDetailsText
    val textPadding = if (secondaryProgressView) {
      context.resources.getDimensionPixelSize(R.dimen.transfer_control_view_progressbar_to_textview_margin)
    } else {
      context.resources.getDimensionPixelSize(R.dimen.transfer_control_view_parent_to_textview_margin)
    }
    ViewUtil.setPaddingStart(binding.secondaryDetailsText, textPadding)
    if (ViewUtil.isLtr(binding.secondaryDetailsText)) {
      (binding.secondaryDetailsText.layoutParams as MarginLayoutParams).leftMargin = textPadding
    } else {
      (binding.secondaryDetailsText.layoutParams as MarginLayoutParams).rightMargin = textPadding
    }
  }

  private fun applyFocusableAndClickable(currentState: TransferControlViewState, activeViews: List<View>, inactiveViews: List<View>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val focusIntDef = if (currentState.isFocusable) View.FOCUSABLE else View.NOT_FOCUSABLE
      activeViews.forEach { it.focusable = focusIntDef }
      inactiveViews.forEach { it.focusable = View.NOT_FOCUSABLE }
    }
    activeViews.forEach { it.isClickable = currentState.isClickable }
    inactiveViews.forEach {
      it.setOnClickListener(null)
      it.isClickable = false
    }
  }

  override fun setFocusable(focusable: Boolean) {
    super.setFocusable(false)
    verboseLog("setFocusable update: $focusable")
    updateState { it.copy(isFocusable = focusable) }
  }

  override fun setClickable(clickable: Boolean) {
    super.setClickable(false)
    verboseLog("setClickable update: $clickable")
    updateState { it.copy(isClickable = clickable) }
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  fun onEventAsync(event: PartProgressEvent) {
    val attachment = event.attachment
    updateState {
      verboseLog("onEventAsync update")
      if (!it.networkProgress.containsKey(attachment)) {
        verboseLog("onEventAsync update ignored")
        return@updateState it
      }

      if (event.type == PartProgressEvent.Type.COMPRESSION) {
        val mutableMap = it.compressionProgress.toMutableMap()
        val updateEvent = Progress.fromEvent(event)
        val existingEvent = mutableMap[attachment]
        if (existingEvent == null || updateEvent.completed > existingEvent.completed) {
          mutableMap[attachment] = updateEvent
        }
        verboseLog("onEventAsync compression update")
        return@updateState it.copy(compressionProgress = mutableMap.toMap())
      } else {
        val mutableMap = it.networkProgress.toMutableMap()
        val updateEvent = Progress.fromEvent(event)
        val existingEvent = mutableMap[attachment]
        if (existingEvent == null || updateEvent.completed > existingEvent.completed) {
          mutableMap[attachment] = updateEvent
        }
        verboseLog("onEventAsync network update")
        return@updateState it.copy(networkProgress = mutableMap.toMap())
      }
    }
  }

  fun setSlides(slides: List<Slide>) {
    require(slides.isNotEmpty()) { "[$uuid] Must provide at least one slide." }
    updateState { state ->
      verboseLog("State update for new slides: ${slidesAsListOfTimestamps(slides)}")
      val isNewSlideSet = !isUpdateToExistingSet(state, slides)
      val networkProgress: MutableMap<Attachment, Progress> = if (isNewSlideSet) HashMap() else state.networkProgress.toMutableMap()
      if (isNewSlideSet) {
        slides.forEach { networkProgress[it.asAttachment()] = Progress(0L, it.fileSize) }
      }
      val compressionProgress: MutableMap<Attachment, Progress> = if (isNewSlideSet) HashMap() else state.compressionProgress.toMutableMap()
      var allStreamableOrDone = true
      for (slide in slides) {
        val attachment = slide.asAttachment()
        if (attachment.transferState == AttachmentTable.TRANSFER_PROGRESS_DONE) {
          networkProgress[attachment] = Progress(attachment.size, attachment.size)
        } else if (!MediaUtil.isInstantVideoSupported(slide)) {
          allStreamableOrDone = false
        }
      }
      val playableWhileDownloading = allStreamableOrDone
      val isUpload = slides.any { it.asAttachment().uploadTimestamp == 0L } && slides.all { (it.asAttachment() as? DatabaseAttachment)?.hasData == true }

      val result = state.copy(
        slides = slides,
        networkProgress = networkProgress,
        compressionProgress = compressionProgress,
        playableWhileDownloading = playableWhileDownloading,
        isUpload = isUpload
      )
      verboseLog("New state calculated and being returned for new slides: ${slidesAsListOfTimestamps(slides)}\n$result")
      return@updateState result
    }
    verboseLog("End of setSlides() for ${slidesAsListOfTimestamps(slides)}")
  }

  private fun slidesAsListOfTimestamps(slides: List<Slide>): String {
    if (!VERBOSE_DEVELOPMENT_LOGGING) {
      return ""
    }

    return slides.map { it.asAttachment().uploadTimestamp }.joinToString()
  }

  private fun isUpdateToExistingSet(currentState: TransferControlViewState, slides: List<Slide>): Boolean {
    if (slides.size != currentState.networkProgress.size) {
      return false
    }
    for (slide in slides) {
      if (!currentState.networkProgress.containsKey(slide.asAttachment())) {
        return false
      }
    }
    return true
  }

  fun setTransferClickListener(listener: OnClickListener) {
    verboseLog("transferClickListener update")
    updateState {
      it.copy(
        startTransferClickListener = listener
      )
    }
  }

  fun setCancelClickListener(listener: OnClickListener) {
    verboseLog("cancelClickListener update")
    updateState {
      it.copy(
        cancelTransferClickedListener = listener
      )
    }
  }

  fun setInstantPlaybackClickListener(listener: OnClickListener) {
    verboseLog("instantPlaybackClickListener update")
    updateState {
      it.copy(
        instantPlaybackClickListener = listener
      )
    }
  }

  fun clear() {
    clearAnimation()
    visibility = GONE
    updateState { TransferControlViewState() }
  }

  fun setShowSecondaryText(showSecondaryText: Boolean) {
    verboseLog("showSecondaryText update: $showSecondaryText")
    updateState {
      it.copy(
        showSecondaryText = showSecondaryText
      )
    }
  }

  fun setVisible(isVisible: Boolean) {
    verboseLog("showSecondaryText update: $isVisible")
    updateState {
      it.copy(
        isVisible = isVisible
      )
    }
  }

  private fun isCompressing(state: TransferControlViewState): Boolean {
    val total = state.compressionProgress.sumTotal()
    return total > 0 && state.compressionProgress.sumCompleted() / total < 0.99f
  }

  private fun calculateProgress(state: TransferControlViewState): Float {
    val totalCompressionProgress: Float = state.compressionProgress.values.map { it.completed.toFloat() / it.total }.sum()
    val totalDownloadProgress: Float = state.networkProgress.values.map { it.completed.toFloat() / it.total }.sum()
    val weightedProgress = UPLOAD_TASK_WEIGHT * totalDownloadProgress + COMPRESSION_TASK_WEIGHT * totalCompressionProgress
    val weightedTotal = (UPLOAD_TASK_WEIGHT * state.networkProgress.size + COMPRESSION_TASK_WEIGHT * state.compressionProgress.size).toFloat()
    return weightedProgress / weightedTotal
  }

  private fun setSecondaryDetailsText(currentState: TransferControlViewState) {
    when (deriveMode(currentState)) {
      Mode.PENDING_GALLERY -> {
        binding.secondaryDetailsText.updateLayoutParams {
          width = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        val remainingSlides = currentState.slides.filterNot { it.transferState == AttachmentTable.TRANSFER_PROGRESS_DONE }
        val downloadCount = remainingSlides.size
        binding.primaryDetailsText.text = context.resources.getQuantityString(R.plurals.TransferControlView_n_items, downloadCount, downloadCount)
        val mebibyteCount = (currentState.networkProgress.sumTotal() - currentState.networkProgress.sumCompleted()) / MEBIBYTE
        binding.secondaryDetailsText.text = context.getString(R.string.TransferControlView__filesize, mebibyteCount)
      }

      Mode.PENDING_GALLERY_CONTAINS_PLAYABLE -> {
        binding.secondaryDetailsText.updateLayoutParams {
          width = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        val mebibyteCount = (currentState.networkProgress.sumTotal() - currentState.networkProgress.sumCompleted()) / MEBIBYTE
        binding.secondaryDetailsText.text = context.getString(R.string.TransferControlView__filesize, mebibyteCount)
      }

      Mode.PENDING_SINGLE_ITEM, Mode.PENDING_VIDEO_PLAYABLE -> {
        binding.secondaryDetailsText.updateLayoutParams {
          width = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        val mebibyteCount = (currentState.slides.sumOf { it.asAttachment().size }) / MEBIBYTE
        binding.secondaryDetailsText.text = context.getString(R.string.TransferControlView__filesize, mebibyteCount)
      }

      Mode.DOWNLOADING_GALLERY, Mode.DOWNLOADING_SINGLE_ITEM, Mode.DOWNLOADING_VIDEO_PLAYABLE, Mode.UPLOADING_GALLERY, Mode.UPLOADING_SINGLE_ITEM -> {
        if (currentState.isUpload && (currentState.networkProgress.sumCompleted() == 0L || isCompressing(currentState))) {
          binding.secondaryDetailsText.updateLayoutParams {
            width = ViewGroup.LayoutParams.WRAP_CONTENT
          }
          binding.secondaryDetailsText.text = context.getString(R.string.TransferControlView__processing)
        } else {
          val progressMiB = currentState.networkProgress.sumCompleted() / MEBIBYTE
          val totalMiB = currentState.networkProgress.sumTotal() / MEBIBYTE
          val completedLabel = context.resources.getString(R.string.TransferControlView__download_progress, totalMiB, totalMiB)
          val desiredWidth = StaticLayout.getDesiredWidth(completedLabel, binding.secondaryDetailsText.paint)
          binding.secondaryDetailsText.text = context.resources.getString(R.string.TransferControlView__download_progress, progressMiB, totalMiB)
          val roundedWidth = ceil(desiredWidth.toDouble()).roundToInt() + binding.secondaryDetailsText.compoundPaddingLeft + binding.secondaryDetailsText.compoundPaddingRight
          binding.secondaryDetailsText.updateLayoutParams {
            width = roundedWidth
          }
        }
      }

      Mode.RETRY_DOWNLOADING, Mode.RETRY_UPLOADING -> {
        binding.secondaryDetailsText.text = resources.getString(R.string.NetworkFailure__retry)
        binding.secondaryDetailsText.updateLayoutParams {
          width = ViewGroup.LayoutParams.WRAP_CONTENT
        }
      }

      Mode.GONE -> Unit
    }
  }

  /**
   * This is an extremely chatty logging mode for local development. Each view is assigned a UUID so that you can filter by view inside a conversation.
   */
  private fun verboseLog(message: String) {
    if (VERBOSE_DEVELOPMENT_LOGGING) {
      Log.d(TAG, "[$uuid] $message")
    }
  }

  companion object {
    private const val TAG = "TransferControlView"
    private const val VERBOSE_DEVELOPMENT_LOGGING = false
    private const val UPLOAD_TASK_WEIGHT = 1
    private const val SECONDARY_TEXT_OFFSET_DP = 6
    private const val RETRY_SECONDARY_TEXT_OFFSET_DP = 6
    private const val PRIMARY_TEXT_OFFSET_DP = 4
    private const val MEBIBYTE = 1048576f

    /**
     * A weighting compared to [UPLOAD_TASK_WEIGHT]
     */
    private const val COMPRESSION_TASK_WEIGHT = 3

    @JvmStatic
    fun getTransferState(slides: List<Slide>): Int {
      var transferState = AttachmentTable.TRANSFER_PROGRESS_DONE
      var allFailed = true
      for (slide in slides) {
        if (slide.transferState != AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE) {
          allFailed = false
          transferState = if (slide.transferState == AttachmentTable.TRANSFER_PROGRESS_PENDING && transferState == AttachmentTable.TRANSFER_PROGRESS_DONE) {
            slide.transferState
          } else {
            transferState.coerceAtLeast(slide.transferState)
          }
        }
      }
      return if (allFailed) AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE else transferState
    }

    @JvmStatic
    fun containsPlayableSlides(slides: List<Slide>): Boolean {
      return slides.any { MediaUtil.isInstantVideoSupported(it) }
    }
  }

  data class Progress(val completed: Long, val total: Long) {
    companion object {
      fun fromEvent(event: PartProgressEvent): Progress {
        return Progress(event.progress, event.total)
      }
    }
  }

  private fun Map<Attachment, Progress>.sumCompleted(): Long {
    return this.values.sumOf { it.completed }
  }

  private fun Map<Attachment, Progress>.sumTotal(): Long {
    return this.values.sumOf { it.total }
  }

  enum class Mode {
    PENDING_GALLERY,
    PENDING_GALLERY_CONTAINS_PLAYABLE,
    PENDING_SINGLE_ITEM,
    PENDING_VIDEO_PLAYABLE,
    DOWNLOADING_GALLERY,
    DOWNLOADING_SINGLE_ITEM,
    DOWNLOADING_VIDEO_PLAYABLE,
    UPLOADING_GALLERY,
    UPLOADING_SINGLE_ITEM,
    RETRY_DOWNLOADING,
    RETRY_UPLOADING,
    GONE
  }
}

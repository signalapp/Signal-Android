/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.components.transfercontrols

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.text.format.Formatter
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.components.RecyclerViewParentTransitionController
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.databinding.TransferControlsViewBinding
import org.thoughtcrime.securesms.events.PartProgressEvent
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.visible
import java.util.UUID

class TransferControlView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr) {
  private val uuid = UUID.randomUUID().toString()
  private val binding: TransferControlsViewBinding

  private var state = TransferControlViewState()

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
    if (newState != state) {
      applyState(newState)
    }
    state = newState
  }

  private fun applyState(currentState: TransferControlViewState) {
    val mode = deriveMode(currentState)
    verboseLog("New state applying, mode = $mode")
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
          if (currentState.isOutgoing) {
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
          return if (currentState.isOutgoing) {
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
            return if (currentState.isOutgoing) {
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
            return if (currentState.isOutgoing) {
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
    binding.primaryProgressView.startClickListener = currentState.downloadClickedListener
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

    binding.primaryDetailsText.setOnClickListener(currentState.downloadClickedListener)
    binding.primaryBackground.setOnClickListener(currentState.downloadClickedListener)

    binding.primaryDetailsText.translationX = if (ViewUtil.isLtr(this)) {
      ViewUtil.dpToPx(-8).toFloat()
    } else {
      ViewUtil.dpToPx(8).toFloat()
    }
    val remainingSlides = currentState.slides.filterNot { it.transferState == AttachmentTable.TRANSFER_PROGRESS_DONE }
    val downloadCount = remainingSlides.size
    binding.primaryDetailsText.text = context.resources.getQuantityString(R.plurals.TransferControlView_n_items, downloadCount, downloadCount)
    val byteCount = remainingSlides.sumOf { it.asAttachment().size }
    binding.secondaryDetailsText.text = Formatter.formatShortFileSize(context, byteCount)
  }

  private fun displayPendingGalleryWithPlayable(currentState: TransferControlViewState) {
    binding.secondaryProgressView.startClickListener = currentState.downloadClickedListener
    super.setClickable(false)
    binding.secondaryProgressView.isClickable = currentState.showSecondaryText
    binding.secondaryProgressView.isFocusable = currentState.showSecondaryText
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

    val byteCount = currentState.slides.sumOf { it.asAttachment().size }
    binding.secondaryDetailsText.text = Formatter.formatShortFileSize(context, byteCount)
  }

  private fun displayPendingSingleItem(currentState: TransferControlViewState) {
    binding.primaryProgressView.startClickListener = currentState.downloadClickedListener
    applyFocusableAndClickable(currentState, listOf(binding.primaryProgressView), listOf(binding.secondaryProgressView, binding.playVideoButton))
    binding.primaryProgressView.setStopped(false)
    showAllViews(
      playVideoButton = false,
      primaryDetailsText = false,
      secondaryProgressView = false,
      secondaryDetailsText = currentState.showSecondaryText
    )
    val byteCount = currentState.slides.sumOf { it.asAttachment().size }
    binding.secondaryDetailsText.text = Formatter.formatShortFileSize(context, byteCount)
  }

  private fun displayPendingPlayableVideo(currentState: TransferControlViewState) {
    binding.secondaryProgressView.startClickListener = currentState.downloadClickedListener
    binding.playVideoButton.setOnClickListener(currentState.instantPlaybackClickListener)
    applyFocusableAndClickable(currentState, listOf(binding.secondaryProgressView, binding.playVideoButton), listOf(binding.primaryProgressView))
    binding.secondaryProgressView.setStopped(false)
    showAllViews(
      primaryProgressView = false,
      primaryDetailsText = false,
      secondaryDetailsText = currentState.showSecondaryText,
      secondaryProgressView = currentState.showSecondaryText
    )
    val byteCount = currentState.slides.sumOf { it.asAttachment().size }
    binding.secondaryDetailsText.text = Formatter.formatShortFileSize(context, byteCount)
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
      binding.secondaryProgressView.setUploading(progress)
    } else {
      binding.secondaryProgressView.cancelClickListener = currentState.cancelDownloadClickedListener
      binding.secondaryProgressView.setDownloading(progress)
    }

    binding.secondaryDetailsText.text = deriveSecondaryDetailsText(currentState)
  }

  private fun displayDownloadingSingleItem(currentState: TransferControlViewState) {
    binding.primaryProgressView.cancelClickListener = currentState.cancelDownloadClickedListener
    applyFocusableAndClickable(currentState, listOf(binding.primaryProgressView), listOf(binding.secondaryProgressView, binding.playVideoButton))
    showAllViews(
      playVideoButton = false,
      primaryDetailsText = false,
      secondaryProgressView = false,
      secondaryDetailsText = currentState.showSecondaryText
    )

    val progress = calculateProgress(currentState)
    if (progress == 0f) {
      binding.primaryProgressView.setUploading(progress)
    } else {
      binding.primaryProgressView.setDownloading(progress)
    }

    binding.secondaryDetailsText.text = deriveSecondaryDetailsText(currentState)
  }

  private fun displayDownloadingPlayableVideo(currentState: TransferControlViewState) {
    binding.secondaryProgressView.cancelClickListener = currentState.cancelDownloadClickedListener
    applyFocusableAndClickable(currentState, listOf(binding.secondaryProgressView, binding.playVideoButton), listOf(binding.primaryProgressView))
    showAllViews(
      primaryDetailsText = false,
      secondaryProgressView = currentState.showSecondaryText,
      secondaryDetailsText = currentState.showSecondaryText
    )

    binding.playVideoButton.setOnClickListener(currentState.instantPlaybackClickListener)

    val progress = calculateProgress(currentState)
    if (progress == 0f) {
      binding.secondaryProgressView.setUploading(progress)
    } else {
      binding.secondaryProgressView.setDownloading(progress)
    }
    binding.secondaryDetailsText.text = deriveSecondaryDetailsText(currentState)
  }

  private fun displayUploadingSingleItem(currentState: TransferControlViewState) {
    binding.secondaryProgressView.startClickListener = currentState.downloadClickedListener
    applyFocusableAndClickable(currentState, listOf(binding.secondaryProgressView), listOf(binding.primaryProgressView, binding.playVideoButton))
    showAllViews(
      playVideoButton = false,
      primaryProgressView = false,
      primaryDetailsText = false,
      secondaryDetailsText = currentState.showSecondaryText
    )

    val progress = calculateProgress(currentState)
    binding.secondaryProgressView.setUploading(progress)

    binding.secondaryDetailsText.text = deriveSecondaryDetailsText(currentState)
  }

  private fun displayUploadingGallery(currentState: TransferControlViewState) {
    binding.secondaryProgressView.startClickListener = currentState.downloadClickedListener
    applyFocusableAndClickable(currentState, listOf(binding.secondaryProgressView), listOf(binding.primaryProgressView, binding.playVideoButton))
    showAllViews(
      playVideoButton = false,
      primaryProgressView = false,
      primaryDetailsText = false
    )

    val progress = calculateProgress(currentState)
    binding.secondaryProgressView.setUploading(progress)

    binding.secondaryDetailsText.text = deriveSecondaryDetailsText(currentState)
  }

  private fun displayRetry(currentState: TransferControlViewState, isUploading: Boolean) {
    binding.secondaryProgressView.startClickListener = currentState.downloadClickedListener
    applyFocusableAndClickable(currentState, listOf(binding.secondaryProgressView), listOf(binding.primaryProgressView, binding.playVideoButton))
    showAllViews(
      playVideoButton = false,
      primaryProgressView = false,
      primaryDetailsText = false,
      secondaryDetailsText = currentState.showSecondaryText
    )

    binding.secondaryProgressView.setStopped(isUploading)
    binding.secondaryDetailsText.text = resources.getString(R.string.NetworkFailure__retry)
  }

  private fun displayChildrenAsGone() {
    children.forEach {
      it.visible = false
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
    val textPadding = if (secondaryProgressView) 0 else context.resources.getDimensionPixelSize(R.dimen.transfer_control_view_progressbar_to_textview_margin)
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
        mutableMap[attachment] = Progress.fromEvent(event)
        verboseLog("onEventAsync compression update")
        return@updateState it.copy(compressionProgress = mutableMap.toMap())
      } else {
        val mutableMap = it.networkProgress.toMutableMap()
        mutableMap[attachment] = Progress.fromEvent(event)
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
          networkProgress[attachment] = Progress(1L, attachment.size)
        } else if (!MediaUtil.isInstantVideoSupported(slide)) {
          allStreamableOrDone = false
        }
      }
      val playableWhileDownloading = allStreamableOrDone
      val isOutgoing = slides.any { it.asAttachment().uploadTimestamp == 0L }

      val result = state.copy(
        slides = slides,
        networkProgress = networkProgress,
        compressionProgress = compressionProgress,
        playableWhileDownloading = playableWhileDownloading,
        isOutgoing = isOutgoing
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

  fun setDownloadClickListener(listener: OnClickListener) {
    verboseLog("downloadClickListener update")
    updateState {
      it.copy(
        downloadClickedListener = listener
      )
    }
  }

  fun setCancelClickListener(listener: OnClickListener) {
    verboseLog("cancelClickListener update")
    updateState {
      it.copy(
        cancelDownloadClickedListener = listener
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
    // We never get a completion event so it never actually reaches 100%
    return state.compressionProgress.sumTotal() > 0 && state.compressionProgress.values.map { it.completed.toFloat() / it.total }.sum() < 0.99f
  }

  private fun calculateProgress(state: TransferControlViewState): Float {
    val totalCompressionProgress: Float = state.compressionProgress.values.map { it.completed.toFloat() / it.total }.sum()
    val totalDownloadProgress: Float = state.networkProgress.values.map { it.completed.toFloat() / it.total }.sum()
    val weightedProgress = UPLOAD_TASK_WEIGHT * totalDownloadProgress + COMPRESSION_TASK_WEIGHT * totalCompressionProgress
    val weightedTotal = (UPLOAD_TASK_WEIGHT * state.networkProgress.size + COMPRESSION_TASK_WEIGHT * state.compressionProgress.size).toFloat()
    return weightedProgress / weightedTotal
  }

  @SuppressLint("SetTextI18n")
  private fun deriveSecondaryDetailsText(currentState: TransferControlViewState): String {
    return if (isCompressing(currentState)) {
      return context.getString(R.string.TransferControlView__processing)
    } else {
      val progressText = Formatter.formatShortFileSize(context, currentState.networkProgress.sumCompleted())
      val totalText = Formatter.formatShortFileSize(context, currentState.networkProgress.sumTotal())
      "$progressText/$totalText"
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

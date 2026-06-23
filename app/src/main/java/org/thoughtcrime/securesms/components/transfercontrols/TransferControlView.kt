/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.components.transfercontrols

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.ByteSize
import org.signal.core.util.ThrottledDebouncer
import org.signal.core.util.bytes
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.components.RecyclerViewParentTransitionController
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.events.PartProgressEvent
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.util.MediaUtil
import java.util.UUID

/**
 * Displays the start/cancel/progress controls that overlay an attachment thumbnail.
 */
class TransferControlView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AbstractComposeView(context, attrs, defStyleAttr) {

  companion object {
    private val TAG = Log.tag(TransferControlView::class.java)

    /** Flip to true locally to trace a single view's render transitions and ignored progress events. */
    private const val VERBOSE_DEVELOPMENT_LOGGING = false
  }

  private var state = TransferControlViewState()

  /** Throttled observable flow of [state] */
  private var renderState by mutableStateOf<TransferControlsRenderState>(TransferControlsRenderState.Gone)

  private val progressUpdateDebouncer = ThrottledDebouncer(100)

  /** Per-instance id so a single recycled view can be isolated in logcat when [VERBOSE_DEVELOPMENT_LOGGING] is on. */
  private val viewId by lazy { UUID.randomUUID().toString().take(8) }

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    isLongClickable = false
    addOnAttachStateChangeListener(RecyclerViewParentTransitionController(child = this))
  }

  @Composable
  override fun Content() {
    SignalTheme {
      TransferControls(
        state = renderState,
        onStartClick = { state.startTransferClickListener?.onClick(this) },
        onCancelClick = { state.cancelTransferClickedListener?.onClick(this) },
        onPlayClick = { state.instantPlaybackClickListener?.onClick(this) }
      )
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    EventBus.getDefault().unregister(this)
  }

  fun isGone(): Boolean {
    return TransferControls.deriveRenderState(state) is TransferControlsRenderState.Gone
  }

  private fun updateState(stateFactory: (TransferControlViewState) -> TransferControlViewState) {
    val newState = stateFactory(state)

    val oldRender = TransferControls.deriveRenderState(state)
    val newRender = TransferControls.deriveRenderState(newState)
    state = newState

    if (oldRender == newRender) {
      return
    }

    verboseLog { "render $oldRender -> $newRender slides=[${slidesAsLogString(newState.slides)}]" }

    if (oldRender is TransferControlsRenderState.InProgress && oldRender.isProgressOnlyDifference(newRender)) {
      progressUpdateDebouncer.publish {
        renderState = newRender
        visibility = VISIBLE
      }
    } else {
      progressUpdateDebouncer.clear()
      renderState = newRender
      if (newRender !is TransferControlsRenderState.Gone) {
        visibility = VISIBLE
      }
    }
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  fun onEventAsync(event: PartProgressEvent) {
    val attachment = event.attachment
    updateState {
      if (!it.networkProgress.containsKey(attachment)) {
        verboseLog { "Ignoring progress event for an attachment not in this view's slide set (likely a recycled view). ts=${attachment.uploadTimestamp}" }
        return@updateState it
      }

      if (event.type == PartProgressEvent.Type.COMPRESSION) {
        val progress = it.compressionProgress.toMutableMap()
        progress.applyProgress(attachment, Progress.fromEvent(event))
        return@updateState it.copy(compressionProgress = progress.toMap())
      } else {
        val progress = it.networkProgress.toMutableMap()
        progress.applyProgress(attachment, Progress.fromEvent(event))
        return@updateState it.copy(networkProgress = progress.toMap())
      }
    }
  }

  fun setSlides(slides: List<Slide>) {
    require(slides.isNotEmpty()) { "Must provide at least one slide." }
    updateState { state ->
      val isNewSlideSet = !isUpdateToExistingSet(state, slides)
      val networkProgress: MutableMap<Attachment, Progress> = if (isNewSlideSet) HashMap() else state.networkProgress.toMutableMap()
      if (isNewSlideSet) {
        slides.forEach { networkProgress[it.asAttachment()] = Progress(0.bytes, it.fileSize.bytes) }
      }
      val compressionProgress: MutableMap<Attachment, Progress> = if (isNewSlideSet) HashMap() else state.compressionProgress.toMutableMap()
      var allStreamableOrDone = true
      for (slide in slides) {
        val attachment = slide.asAttachment()
        if (attachment.transferState == AttachmentTable.TRANSFER_PROGRESS_DONE) {
          networkProgress[attachment] = Progress(attachment.size.bytes, attachment.size.bytes)
        } else if (!MediaUtil.isInstantVideoSupported(slide)) {
          allStreamableOrDone = false
        }
      }
      val playableWhileDownloading = allStreamableOrDone
      val isUpload = slides.all {
        (it.asAttachment() as? DatabaseAttachment)?.hasData == true
      }

      state.copy(
        slides = slides,
        networkProgress = networkProgress,
        compressionProgress = compressionProgress,
        playableWhileDownloading = playableWhileDownloading,
        isUpload = isUpload
      )
    }
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
    updateState { it.copy(startTransferClickListener = listener) }
  }

  fun setCancelClickListener(listener: OnClickListener) {
    updateState { it.copy(cancelTransferClickedListener = listener) }
  }

  fun setInstantPlaybackClickListener(listener: OnClickListener) {
    updateState { it.copy(instantPlaybackClickListener = listener) }
  }

  fun clear() {
    visibility = GONE
    updateState { TransferControlViewState() }
  }

  fun setShowSecondaryText(showSecondaryText: Boolean) {
    updateState { it.copy(showSecondaryText = showSecondaryText) }
  }

  fun setVisible(isVisible: Boolean) {
    updateState { it.copy(isVisible = isVisible) }
  }

  fun setAwaitingPrimaryResponse(awaiting: Boolean) {
    updateState { it.copy(awaitingPrimaryResponse = awaiting) }
  }

  override fun setFocusable(focusable: Boolean) {
    super.setFocusable(false)
    updateState { it.copy(isFocusable = focusable) }
  }

  override fun setClickable(clickable: Boolean) {
    super.setClickable(false)
    updateState { it.copy(isClickable = clickable) }
  }

  private fun MutableMap<Attachment, Progress>.applyProgress(attachment: Attachment, update: Progress) {
    if (update.completed < 0.bytes) {
      remove(attachment)
    } else {
      put(attachment, update)
    }
  }

  private inline fun verboseLog(message: () -> String) {
    if (VERBOSE_DEVELOPMENT_LOGGING) {
      Log.d(TAG, "[$viewId] ${message()}")
    }
  }

  private fun slidesAsLogString(slides: List<Slide>): String {
    return slides.joinToString { "ts=${it.asAttachment().uploadTimestamp},xfer=${it.transferState}" }
  }

  data class Progress(val completed: ByteSize, val total: ByteSize) {
    companion object {
      fun fromEvent(event: PartProgressEvent): Progress {
        return Progress(event.progress.bytes, event.total.bytes)
      }
    }
  }
}

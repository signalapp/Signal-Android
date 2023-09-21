/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.components.transfercontrols

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.content.Context
import android.text.format.Formatter
import android.util.AttributeSet
import android.view.View
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.annimon.stream.Stream
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.events.PartProgressEvent
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.ThrottledDebouncer
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.visible

class TransferControlView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr) {
  private var slides: List<Slide> = emptyList()
  private var current: MutableSet<View> = HashSet()
  private var playableWhileDownloading = false
  private var showDownloadText = true
  private val downloadDetails: View
  private val downloadDetailsText: TextView
  private val primaryDetailsText: TextView
  private val secondaryViewSpace: Space
  private val playVideoButton: AppCompatImageView
  private val primaryProgressView: TransferProgressView
  private val secondaryProgressView: TransferProgressView
  private val networkProgress: MutableMap<Attachment, Progress>
  private val compressionProgress: MutableMap<Attachment, Progress>
  private val debouncer: ThrottledDebouncer = ThrottledDebouncer(8) // frame time for 120 Hz

  init {
    inflate(context, R.layout.transfer_controls_view, this)
    isLongClickable = false
    visibility = GONE
    layoutTransition = LayoutTransition()
    networkProgress = HashMap()
    compressionProgress = HashMap()
    primaryProgressView = findViewById(R.id.primary_progress_view)
    secondaryProgressView = findViewById(R.id.secondary_progress_view)
    playVideoButton = findViewById(R.id.play_video_button)
    downloadDetails = findViewById(R.id.secondary_background)
    downloadDetailsText = findViewById(R.id.download_details_text)
    secondaryViewSpace = findViewById(R.id.secondary_view_space)
    primaryDetailsText = findViewById(R.id.primary_details_text)
  }

  override fun setFocusable(focusable: Boolean) {
    super.setFocusable(focusable)
    progressView.isFocusable = focusable
    if (playVideoButton.visibility == VISIBLE) {
      playVideoButton.isFocusable = focusable
    }
  }

  override fun setClickable(clickable: Boolean) {
    super.setClickable(clickable)
    secondaryProgressView.isClickable = secondaryProgressView.visible && clickable
    primaryProgressView.isClickable = primaryProgressView.visible && clickable
    playVideoButton.isClickable = playVideoButton.visible && clickable
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    EventBus.getDefault().unregister(this)
  }

  fun setSlide(slides: Slide) {
    setSlides(listOf(slides))
  }

  fun setSlides(slides: List<Slide>) {
    require(slides.isNotEmpty()) { "Must provide at least one slide." }
    this.slides = slides
    if (!isUpdateToExistingSet(slides)) {
      networkProgress.clear()
      compressionProgress.clear()
      slides.forEach { networkProgress[it.asAttachment()] = Progress(0L, it.fileSize) }
    }
    var allStreamableOrDone = true
    for (slide in slides) {
      val attachment = slide.asAttachment()
      if (attachment.transferState == AttachmentTable.TRANSFER_PROGRESS_DONE) {
        networkProgress[attachment] = Progress(1L, attachment.size)
      } else if (!MediaUtil.isInstantVideoSupported(slide)) {
        allStreamableOrDone = false
      }
    }
    playableWhileDownloading = allStreamableOrDone
    setPlayableWhileDownloading(playableWhileDownloading)
    val uploading = slides.any { it.asAttachment().uploadTimestamp == 0L }
    when (getTransferState(slides)) {
      AttachmentTable.TRANSFER_PROGRESS_STARTED -> showProgressSpinner(calculateProgress(), uploading)
      AttachmentTable.TRANSFER_PROGRESS_PENDING -> {
        updateDownloadText()
        progressView.setStopped(false)
        this.visible = true
      }

      AttachmentTable.TRANSFER_PROGRESS_FAILED -> {
        downloadDetailsText.setText(R.string.NetworkFailure__retry)
        progressView.setStopped(false)
        this.visible = true
      }

      else -> this.visible = false
    }
  }

  private val progressView: TransferProgressView
    get() = if (playableWhileDownloading) {
      secondaryProgressView
    } else {
      primaryProgressView
    }

  @JvmOverloads
  fun showProgressSpinner(progress: Float = calculateProgress(), uploading: Boolean = false) {
    if (uploading || progress == 0f) {
      progressView.setUploading(progress)
    } else {
      progressView.setDownloading(progress)
    }
  }

  fun setDownloadClickListener(listener: OnClickListener?) {
    primaryProgressView.startClickListener = listener
    secondaryProgressView.startClickListener = listener
  }

  fun setCancelClickListener(listener: OnClickListener?) {
    primaryProgressView.cancelClickListener = listener
    secondaryProgressView.cancelClickListener = listener
  }

  fun setInstantPlaybackClickListener(onPlayClickedListener: OnClickListener?) {
    playVideoButton.setOnClickListener(onPlayClickedListener)
  }

  fun clear() {
    clearAnimation()
    visibility = GONE
    if (current.isNotEmpty()) {
      for (v in current) {
        v.clearAnimation()
        v.visibility = GONE
      }
    }
    current.clear()
    slides = emptyList()
  }

  fun setShowDownloadText(showDownloadText: Boolean) {
    this.showDownloadText = showDownloadText
    updateDownloadText()
  }

  private fun isUpdateToExistingSet(slides: List<Slide>): Boolean {
    if (slides.size != networkProgress.size) {
      return false
    }
    for (slide in slides) {
      if (!networkProgress.containsKey(slide.asAttachment())) {
        return false
      }
    }
    return true
  }

  private fun updateDownloadText() {
    val byteCount = slides.sumOf { it.asAttachment().size }
    downloadDetailsText.text = Formatter.formatShortFileSize(context, byteCount)
    downloadDetailsText.invalidate()

    if (slides.size > 1) {
      val downloadCount = Stream.of(slides).reduce(0) { count: Int, slide: Slide -> if (slide.transferState != AttachmentTable.TRANSFER_PROGRESS_DONE) count + 1 else count }
      primaryDetailsText.text = context.resources.getQuantityString(R.plurals.TransferControlView_n_items, downloadCount, downloadCount)
      primaryDetailsText.visible = showDownloadText
    } else {
      primaryDetailsText.text = ""
      primaryDetailsText.visible = false
    }
  }

  @SuppressLint("SetTextI18n")
  private fun updateDownloadProgressText(isCompression: Boolean) {
    val context = context
    val progress = if (isCompression) compressionProgress.values.sumOf { it.completed } else networkProgress.values.sumOf { it.completed }
    val total = if (isCompression) compressionProgress.values.sumOf { it.total } else networkProgress.values.sumOf { it.total }
    val progressText = Formatter.formatShortFileSize(context, progress)
    val totalText = Formatter.formatShortFileSize(context, total)
    downloadDetailsText.text = "$progressText/$totalText"
    downloadDetailsText.invalidate()
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  fun onEventAsync(event: PartProgressEvent) {
    val attachment = event.attachment
    if (networkProgress.containsKey(attachment)) {
      val proportionCompleted = event.progress.toFloat() / event.total
      if (event.type == PartProgressEvent.Type.COMPRESSION) {
        compressionProgress[attachment] = Progress.fromEvent(event)
      } else {
        networkProgress[attachment] = Progress.fromEvent(event)
      }
      debouncer.publish {
        val progress = calculateProgress()
        if (attachment.uploadTimestamp == 0L) {
          progressView.setUploading(progress)
        } else {
          progressView.setDownloading(progress)
        }
        updateDownloadProgressText(event.type == PartProgressEvent.Type.COMPRESSION)
      }
    }
  }

  private fun setPlayableWhileDownloading(playableWhileDownloading: Boolean) {
    playVideoButton.visible = playableWhileDownloading
    secondaryProgressView.visible = playableWhileDownloading
    secondaryViewSpace.visible = !playableWhileDownloading // exists because constraint layout was being very weird about margins and this was the only way
    primaryProgressView.visibility = if (playableWhileDownloading) INVISIBLE else VISIBLE
    val textPadding = if (playableWhileDownloading) 0 else context.resources.getDimensionPixelSize(R.dimen.transfer_control_view_progressbar_to_textview_margin)
    ViewUtil.setPaddingStart(downloadDetailsText, textPadding)
  }

  private fun calculateProgress(): Float {
    val totalDownloadProgress: Float = networkProgress.values.map { it.completed.toFloat() / it.total }.sum()
    val totalCompressionProgress: Float = compressionProgress.values.map { it.completed.toFloat() / it.total }.sum()
    val weightedProgress = UPLOAD_TASK_WEIGHT * totalDownloadProgress + COMPRESSION_TASK_WEIGHT * totalCompressionProgress
    val weightedTotal = (UPLOAD_TASK_WEIGHT * networkProgress.size + COMPRESSION_TASK_WEIGHT * compressionProgress.size).toFloat()
    return weightedProgress / weightedTotal
  }

  companion object {
    private const val TAG = "TransferControlView"
    private const val UPLOAD_TASK_WEIGHT = 1

    /**
     * A weighting compared to [.UPLOAD_TASK_WEIGHT]
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
  }

  data class Progress(val completed: Long, val total: Long) {
    companion object {
      fun fromEvent(event: PartProgressEvent): Progress {
        return Progress(event.progress, event.total)
      }
    }
  }
}

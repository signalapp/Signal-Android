/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.transfercontrols

import org.signal.core.models.database.AttachmentId
import org.signal.core.util.ByteSize
import org.signal.core.util.bytes
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream

/**
 * Pure, Android-View-free logic for the transfer controls UI.
 *
 * [deriveRenderState] maps a [TransferControlViewState] to a [TransferControlsRenderState], which is a small, fully-resolved
 * description of what should be drawn. It carries semantic data (counts, byte sizes) rather than formatted strings so that it
 * can be unit tested on the JVM; string formatting happens in the composable.
 */
object TransferControls {

  /**
   * Where the active transfer control (start button / progress indicator) is positioned.
   *
   * [CENTER] is the large, centered control used for single-item downloads.
   * [CORNER] is the small control tucked in the corner, used for galleries, playable video, all uploads, and retries.
   */
  enum class Placement {
    CENTER,
    CORNER
  }

  sealed interface ProgressLabel {
    /** Attachment processing taking place, like transcoding */
    data object Processing : ProgressLabel

    /** Uploading/downloading progress */
    data class Bytes(val completed: ByteSize, val total: ByteSize) : ProgressLabel
  }

  fun deriveRenderState(
    state: TransferControlViewState,
    awaitingAttachmentIds: Set<AttachmentId> = emptySet()
  ): TransferControlsRenderState {
    if (state.slides.isEmpty()) {
      return TransferControlsRenderState.Gone
    }

    if (state.slides.all { it.transferState == AttachmentTable.TRANSFER_PROGRESS_DONE }) {
      return TransferControlsRenderState.Gone
    }

    if (!state.isVisible) {
      return TransferControlsRenderState.Gone
    }

    // If any attachments are being backfilled, overwrite with in progress state to maintain spinner
    val awaitingBackfill = state.slides.any { (it.asAttachment() as? DatabaseAttachment)?.attachmentId in awaitingAttachmentIds }
    if (awaitingBackfill) {
      val downloading = state.slides.any { it.transferState == AttachmentTable.TRANSFER_PROGRESS_STARTED }
      return TransferControlsRenderState.InProgress(
        isUpload = false,
        placement = if (state.slides.size == 1) Placement.CENTER else Placement.CORNER,
        progress = if (downloading) calculateProgress(state) else null,
        showPlayButton = false,
        cancelable = downloading,
        label = if (downloading) progressLabel(state) else null
      )
    }

    return when (deriveMode(state)) {
      Mode.PENDING_GALLERY -> TransferControlsRenderState.Pending(
        isUpload = state.isUpload,
        placement = Placement.CENTER,
        showPlayButton = false,
        itemCount = state.slides.count { it.transferState != AttachmentTable.TRANSFER_PROGRESS_DONE },
        sizeBytes = if (state.showSecondaryText) state.networkProgress.sumTotal() - state.networkProgress.sumCompleted() else null
      )

      Mode.PENDING_GALLERY_CONTAINS_PLAYABLE -> TransferControlsRenderState.Pending(
        isUpload = state.isUpload,
        placement = Placement.CORNER,
        showPlayButton = false,
        sizeBytes = if (state.showSecondaryText) state.networkProgress.sumTotal() - state.networkProgress.sumCompleted() else null
      )

      Mode.PENDING_SINGLE_ITEM -> TransferControlsRenderState.Pending(
        isUpload = state.isUpload,
        placement = Placement.CENTER,
        showPlayButton = false,
        sizeBytes = if (state.showSecondaryText) state.slides.sumOf { it.asAttachment().size }.bytes else null
      )

      Mode.PENDING_VIDEO_PLAYABLE -> TransferControlsRenderState.Pending(
        isUpload = state.isUpload,
        placement = Placement.CORNER,
        showPlayButton = true,
        sizeBytes = if (state.showSecondaryText) state.slides.sumOf { it.asAttachment().size }.bytes else null
      )

      Mode.DOWNLOADING_GALLERY -> TransferControlsRenderState.InProgress(
        isUpload = false,
        placement = Placement.CORNER,
        progress = calculateProgress(state),
        showPlayButton = false,
        cancelable = calculateProgress(state) != 0f,
        label = progressLabel(state)
      )

      Mode.DOWNLOADING_SINGLE_ITEM -> TransferControlsRenderState.InProgress(
        isUpload = false,
        placement = Placement.CENTER,
        progress = calculateProgress(state),
        showPlayButton = false,
        cancelable = true,
        label = progressLabel(state)
      )

      Mode.DOWNLOADING_VIDEO_PLAYABLE -> TransferControlsRenderState.InProgress(
        isUpload = false,
        placement = Placement.CORNER,
        progress = calculateProgress(state),
        showPlayButton = true,
        cancelable = true,
        label = progressLabel(state)
      )

      Mode.UPLOADING_SINGLE_ITEM -> TransferControlsRenderState.InProgress(
        isUpload = true,
        placement = Placement.CORNER,
        progress = calculateProgress(state),
        showPlayButton = false,
        cancelable = true,
        label = progressLabel(state)
      )

      Mode.UPLOADING_GALLERY -> TransferControlsRenderState.InProgress(
        isUpload = true,
        placement = Placement.CORNER,
        progress = calculateProgress(state),
        showPlayButton = false,
        cancelable = true,
        // Note: the legacy view always showed this label for uploading galleries, regardless of showSecondaryText.
        label = progressLabel(state)
      )

      Mode.RETRY_DOWNLOADING -> TransferControlsRenderState.Retry(isUpload = false)
      Mode.RETRY_UPLOADING -> TransferControlsRenderState.Retry(isUpload = true)
      Mode.GONE -> TransferControlsRenderState.Gone
    }
  }

  private fun progressLabel(state: TransferControlViewState): ProgressLabel {
    return if (state.isUpload && (state.networkProgress.sumCompleted() == 0L.bytes || isCompressing(state))) {
      ProgressLabel.Processing
    } else if (state.isUpload) {
      ProgressLabel.Bytes(state.networkProgress.sumCompleted(), state.networkProgress.sumTotal())
    } else {
      val total = state.slides.sumOf { AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(it.fileSize)) }.bytes
      val completed = state.networkProgress.sumCompleted().let { if (it > total) total else it }
      ProgressLabel.Bytes(completed, total)
    }
  }

  private fun isCompressing(state: TransferControlViewState): Boolean {
    val total = state.compressionProgress.sumTotal()
    return total > 0L.bytes && state.compressionProgress.sumCompleted().percentageOf(total) < 0.99f
  }

  private fun calculateProgress(state: TransferControlViewState): Float {
    val totalCompressionProgress: Float = state.compressionProgress.values.map { it.completed.percentageOf(it.total) }.sum()
    val totalDownloadProgress: Float = state.networkProgress.values.map { it.completed.percentageOf(it.total) }.sum()
    val weightedProgress = UPLOAD_TASK_WEIGHT * totalDownloadProgress + COMPRESSION_TASK_WEIGHT * totalCompressionProgress
    val weightedTotal = (UPLOAD_TASK_WEIGHT * state.networkProgress.size + COMPRESSION_TASK_WEIGHT * state.compressionProgress.size).toFloat()
    return weightedProgress / weightedTotal
  }

  private fun deriveMode(state: TransferControlViewState): Mode {
    if (state.slides.isEmpty()) {
      return Mode.GONE
    }

    if (state.slides.all { it.transferState == AttachmentTable.TRANSFER_PROGRESS_DONE }) {
      return Mode.GONE
    }

    if (state.isVisible) {
      if (state.slides.size == 1) {
        val slide = state.slides.first()
        if (slide.hasVideo()) {
          if (state.isUpload) {
            return when (slide.transferState) {
              AttachmentTable.TRANSFER_PROGRESS_STARTED -> Mode.UPLOADING_SINGLE_ITEM
              AttachmentTable.TRANSFER_PROGRESS_PENDING -> Mode.PENDING_SINGLE_ITEM
              else -> Mode.RETRY_UPLOADING
            }
          } else {
            return when (slide.transferState) {
              AttachmentTable.TRANSFER_PROGRESS_STARTED -> {
                if (state.playableWhileDownloading) Mode.DOWNLOADING_VIDEO_PLAYABLE else Mode.DOWNLOADING_SINGLE_ITEM
              }

              AttachmentTable.TRANSFER_PROGRESS_FAILED -> Mode.RETRY_DOWNLOADING
              else -> {
                if (state.playableWhileDownloading) Mode.PENDING_VIDEO_PLAYABLE else Mode.PENDING_SINGLE_ITEM
              }
            }
          }
        } else {
          return if (state.isUpload) {
            when (slide.transferState) {
              AttachmentTable.TRANSFER_PROGRESS_FAILED -> Mode.RETRY_UPLOADING
              AttachmentTable.TRANSFER_PROGRESS_PENDING -> Mode.PENDING_SINGLE_ITEM
              else -> Mode.UPLOADING_SINGLE_ITEM
            }
          } else {
            when (slide.transferState) {
              AttachmentTable.TRANSFER_PROGRESS_STARTED -> Mode.DOWNLOADING_SINGLE_ITEM
              AttachmentTable.TRANSFER_PROGRESS_FAILED -> Mode.RETRY_DOWNLOADING
              else -> Mode.PENDING_SINGLE_ITEM
            }
          }
        }
      } else {
        when (getTransferState(state.slides)) {
          AttachmentTable.TRANSFER_PROGRESS_STARTED -> {
            return if (state.isUpload) Mode.UPLOADING_GALLERY else Mode.DOWNLOADING_GALLERY
          }

          AttachmentTable.TRANSFER_PROGRESS_PENDING -> {
            return if (containsPlayableSlides(state.slides)) Mode.PENDING_GALLERY_CONTAINS_PLAYABLE else Mode.PENDING_GALLERY
          }

          AttachmentTable.TRANSFER_PROGRESS_FAILED -> {
            return if (state.isUpload) Mode.RETRY_UPLOADING else Mode.RETRY_DOWNLOADING
          }

          AttachmentTable.TRANSFER_PROGRESS_DONE -> return Mode.GONE
        }
      }
    } else {
      return Mode.GONE
    }

    return Mode.GONE
  }

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

  private fun Map<Attachment, TransferControlView.Progress>.sumCompleted(): ByteSize {
    return this.values.sumOf { it.completed.inWholeBytes }.bytes
  }

  private fun Map<Attachment, TransferControlView.Progress>.sumTotal(): ByteSize {
    return this.values.sumOf { it.total.inWholeBytes }.bytes
  }

  private const val UPLOAD_TASK_WEIGHT = 1

  private const val COMPRESSION_TASK_WEIGHT = 3

  private enum class Mode {
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

/**
 * A fully-resolved description of what the transfer controls should display. Produced by [TransferControls.deriveRenderState].
 */
sealed interface TransferControlsRenderState {
  data object Gone : TransferControlsRenderState

  data class Pending(
    val isUpload: Boolean,
    val placement: TransferControls.Placement,
    val showPlayButton: Boolean,
    val itemCount: Int? = null,
    val sizeBytes: ByteSize? = null
  ) : TransferControlsRenderState

  data class InProgress(
    val isUpload: Boolean,
    val placement: TransferControls.Placement,
    val progress: Float?,
    val showPlayButton: Boolean,
    val cancelable: Boolean,
    val label: TransferControls.ProgressLabel?
  ) : TransferControlsRenderState {
    fun isProgressOnlyDifference(other: TransferControlsRenderState): Boolean {
      return other is InProgress && copy(progress = other.progress, label = other.label) == other
    }
  }

  data class Retry(
    val isUpload: Boolean
  ) : TransferControlsRenderState
}

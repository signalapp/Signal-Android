/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.video.trim

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.EventDrivenPresenter
import org.signal.core.util.logging.Log
import org.signal.mediasend.screens.edit.video.VideoTrimData
import kotlin.math.ceil

/** Drives [VideoTrimBar]: loads its thumbnails and duration, and applies the rules of what range may be selected. */
class VideoTrimBarPresenter(
  private val coroutineScope: CoroutineScope
) : EventDrivenPresenter<VideoTrimBarEvents>(TAG, coroutineScope) {

  companion object {
    private val TAG = Log.tag(VideoTrimBarPresenter::class)

    /** The shortest range that may be selected. */
    const val MINIMUM_RANGE_US = 500_000L
  }

  private val _state = MutableStateFlow(VideoTrimBarState())
  private val _actions = Channel<VideoTrimBarAction>(Channel.BUFFERED)

  val state: StateFlow<VideoTrimBarState> = _state.asStateFlow()
  val actions: Flow<VideoTrimBarAction> = _actions.receiveAsFlow()

  private var source: Uri? = null
  private var stripWidthPx: Int = 0
  private var stripHeightPx: Int = 0
  private var thumbnailJob: Job? = null

  /** Set when the host has no trim for the source yet, so the full range has to be reported once the duration is read. */
  private var needsInitialRange = false
  private var hasReportedInitialRange = false

  override suspend fun processEvent(event: VideoTrimBarEvents) {
    when (event) {
      is VideoTrimBarEvents.SourceChanged -> onSourceChanged(event.uri)
      is VideoTrimBarEvents.TrimDataChanged -> onTrimDataChanged(event)
      is VideoTrimBarEvents.PlaybackPositionChanged -> onPlaybackPositionChanged(event)
      is VideoTrimBarEvents.StripMeasured -> onStripMeasured(event.widthPx, event.heightPx)
      is VideoTrimBarEvents.DragStarted -> onDragStarted(event.target)
      is VideoTrimBarEvents.HandleDragged -> onHandleDragged(event.target, event.positionUs)
      is VideoTrimBarEvents.PlayheadDragged -> onPlayheadDragged(event.positionUs)
      is VideoTrimBarEvents.PlayheadTapped -> onPlayheadTapped(event.positionUs)
      VideoTrimBarEvents.DragEnded -> finishDrag()
    }
  }

  private suspend fun onSourceChanged(uri: Uri) {
    if (uri == source) {
      return
    }

    // A drag the switch cut short still has to be reported as finished, or the host stays locked in its interaction.
    finishDrag()

    source = uri
    needsInitialRange = false
    hasReportedInitialRange = false
    _state.value = VideoTrimBarState()
    loadThumbnails()
  }

  private suspend fun onTrimDataChanged(event: VideoTrimBarEvents.TrimDataChanged) {
    if (event.uri != source) {
      return
    }

    _state.update { it.copy(maxRangeUs = event.maxDurationUs) }

    if (_state.value.isDragging) {
      return
    }

    val trimData = event.videoTrimData
    if (trimData.totalInputDurationUs > 0) {
      _state.update {
        it.copy(
          durationUs = trimData.totalInputDurationUs,
          startUs = trimData.startTimeUs,
          endUs = trimData.endTimeUs
        )
      }
    } else if (!hasReportedInitialRange) {
      needsInitialRange = true
      reportInitialRangeIfReady()
    }
  }

  private fun onPlaybackPositionChanged(event: VideoTrimBarEvents.PlaybackPositionChanged) {
    if (event.uri != source || _state.value.isDragging) {
      return
    }

    _state.update { it.copy(playheadUs = event.positionUs) }
  }

  private fun onStripMeasured(widthPx: Int, heightPx: Int) {
    if (widthPx == stripWidthPx && heightPx == stripHeightPx) {
      return
    }

    stripWidthPx = widthPx
    stripHeightPx = heightPx
    loadThumbnails()
  }

  private fun onDragStarted(target: TrimDragTarget) {
    if (source == null || _state.value.durationUs == null) {
      return
    }

    _state.update { it.copy(activeDrag = target) }
  }

  private suspend fun onHandleDragged(target: TrimDragTarget, positionUs: Long) {
    val uri = source ?: return
    val state = _state.value
    val durationUs = state.durationUs ?: return
    if (state.activeDrag != target) {
      return
    }

    val (startUs, endUs) = clampRange(
      target = target,
      positionUs = positionUs,
      startUs = state.startUs,
      endUs = state.endUs,
      durationUs = durationUs,
      maxRangeUs = state.maxRangeUs
    )

    if (startUs == state.startUs && endUs == state.endUs) {
      return
    }

    val updated = state.copy(startUs = startUs, endUs = endUs)
    _state.value = updated
    _actions.send(VideoTrimBarAction.TrimChanged(uri, updated.toVideoTrimData(), editingComplete = false))
  }

  private suspend fun onPlayheadDragged(positionUs: Long) {
    if (_state.value.activeDrag == TrimDragTarget.PLAYHEAD) {
      seekTo(positionUs, editingComplete = false)
    }
  }

  private suspend fun onPlayheadTapped(positionUs: Long) {
    val state = _state.value
    if (state.durationUs != null && !state.isDragging) {
      seekTo(positionUs, editingComplete = true)
    }
  }

  /** Keeps the playhead inside the selection; a drag move that goes nowhere isn't reported. */
  private suspend fun seekTo(positionUs: Long, editingComplete: Boolean) {
    val uri = source ?: return
    val state = _state.value

    val playheadUs = positionUs.coerceIn(state.startUs, state.endUs)
    if (!editingComplete && playheadUs == state.playheadUs) {
      return
    }

    _state.value = state.copy(playheadUs = playheadUs)
    _actions.send(VideoTrimBarAction.Seek(uri, playheadUs, editingComplete))
  }

  private suspend fun finishDrag() {
    val uri = source ?: return
    val state = _state.value

    when (state.activeDrag) {
      TrimDragTarget.START, TrimDragTarget.END -> _actions.send(VideoTrimBarAction.TrimChanged(uri, state.toVideoTrimData(), editingComplete = true))
      TrimDragTarget.PLAYHEAD -> _actions.send(VideoTrimBarAction.Seek(uri, state.playheadUs, editingComplete = true))
      null -> return
    }

    _state.update { it.copy(activeDrag = null) }
  }

  /**
   * Restarts decoding for the current source at the strip's current size. The strip is tiled with squares as tall as it
   * is, the last one cut off by its end.
   */
  private fun loadThumbnails() {
    thumbnailJob?.cancel()
    thumbnailJob = null

    val uri = source ?: return
    if (stripWidthPx <= 0 || stripHeightPx <= 0) {
      return
    }

    val count = ceil(stripWidthPx / stripHeightPx.toFloat()).toInt()
    _state.update { it.copy(thumbnails = List(count) { null }) }

    thumbnailJob = coroutineScope.launch {
      VideoThumbnailRepository.thumbnails(uri, count, stripHeightPx).collect { result ->
        // Cancellation stops this collector, but a result already in hand must still never land on another video.
        if (uri != source) {
          return@collect
        }

        when (result) {
          is VideoThumbnailResult.DurationKnown -> onDurationKnown(result.durationUs)
          is VideoThumbnailResult.Thumbnail -> {
            _state.update { state ->
              if (result.index in state.thumbnails.indices) {
                state.copy(thumbnails = state.thumbnails.toMutableList().apply { set(result.index, result.bitmap) })
              } else {
                state
              }
            }
          }
        }
      }
    }
  }

  private suspend fun onDurationKnown(durationUs: Long) {
    if (durationUs <= 0 || _state.value.durationUs != null) {
      return
    }

    _state.update { it.copy(durationUs = durationUs, startUs = 0, endUs = durationUs) }
    reportInitialRangeIfReady()
  }

  /**
   * The host learns a video's duration from here, so until it has a trim for it, the full range read from the video is
   * reported once -- which is also what lets the host clamp an over-long video down to its limit.
   */
  private suspend fun reportInitialRangeIfReady() {
    val uri = source ?: return
    val durationUs = _state.value.durationUs ?: return
    if (!needsInitialRange || hasReportedInitialRange) {
      return
    }

    hasReportedInitialRange = true
    needsInitialRange = false

    val updated = _state.value.copy(startUs = 0, endUs = durationUs)
    _state.value = updated
    _actions.send(VideoTrimBarAction.TrimChanged(uri, updated.toVideoTrimData(), editingComplete = true))
  }

  private fun VideoTrimBarState.toVideoTrimData(): VideoTrimData {
    return VideoTrimData(
      isDurationEdited = isTrimmed,
      totalInputDurationUs = durationUs ?: 0,
      startTimeUs = startUs,
      endTimeUs = endUs
    )
  }
}

/**
 * Moves the [target] handle to [positionUs] and returns the resulting (start, end). When [maxRangeUs] is set, dragging
 * one handle past it pulls the other along rather than stopping.
 */
internal fun clampRange(
  target: TrimDragTarget,
  positionUs: Long,
  startUs: Long,
  endUs: Long,
  durationUs: Long,
  maxRangeUs: Long?
): Pair<Long, Long> {
  val minRangeUs = minOf(VideoTrimBarPresenter.MINIMUM_RANGE_US, durationUs)
  val limitUs = maxRangeUs?.takeIf { it > VideoTrimBarPresenter.MINIMUM_RANGE_US }

  return when (target) {
    TrimDragTarget.START -> {
      val newStart = positionUs.clampTo(0, endUs - minRangeUs)
      val newEnd = if (limitUs != null) endUs.clampTo(newStart + minRangeUs, minOf(newStart + limitUs, durationUs)) else endUs
      newStart to newEnd
    }

    TrimDragTarget.END -> {
      val newEnd = positionUs.clampTo(startUs + minRangeUs, durationUs)
      val newStart = if (limitUs != null) startUs.clampTo(maxOf(0, newEnd - limitUs), newEnd - minRangeUs) else startUs
      newStart to newEnd
    }

    TrimDragTarget.PLAYHEAD -> startUs to endUs
  }
}

/** Unlike [coerceIn], tolerates an empty range, favouring [max]. */
private fun Long.clampTo(min: Long, max: Long): Long = minOf(maxOf(this, min), max)

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.video.trim

import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private const val MAX_CACHED_STRIPS = 10
  }

  private val _state = MutableStateFlow(VideoTrimBarState())
  private val _actions = Channel<VideoTrimBarAction>(Channel.BUFFERED)

  private val _previews = MutableStateFlow<Map<Uri, VideoTrimBarPreview>>(emptyMap())

  val state: StateFlow<VideoTrimBarState> = _state.asStateFlow()
  val actions: Flow<VideoTrimBarAction> = _actions.receiveAsFlow()

  /** The source and each preloaded video, so the bar can be shown for one before it becomes the source. */
  val previews: StateFlow<Map<Uri, VideoTrimBarPreview>> = _previews.asStateFlow()

  private var source: Uri? = null
  private var preloadUris: Set<Uri> = emptySet()
  private var stripWidthPx: Int = 0
  private var stripHeightPx: Int = 0
  private val decodeJobs = mutableMapOf<Uri, Job>()
  private val playheads = mutableMapOf<Uri, Long>()

  /** Fully decoded strips, kept after their video stops being wanted so that coming back to it needs no decode. */
  private val decodedStrips = LruCache<Uri, VideoTrimBarPreview>(MAX_CACHED_STRIPS)

  /** One decode at a time, so preloads queue up rather than competing with the source's. */
  private val decodeLock = Mutex()

  /** Set when the host has no trim for the source yet, so the full range has to be reported once the duration is read. */
  private var needsInitialRange = false
  private var hasReportedInitialRange = false

  override suspend fun processEvent(event: VideoTrimBarEvents) {
    when (event) {
      is VideoTrimBarEvents.SourceChanged -> onSourceChanged(event.uri)
      is VideoTrimBarEvents.TrimDataChanged -> onTrimDataChanged(event)
      is VideoTrimBarEvents.PreloadChanged -> onPreloadChanged(event.uris)
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

    val previousSource = source
    source = uri
    needsInitialRange = false
    hasReportedInitialRange = false

    if (previousSource != null) {
      playheads[previousSource]?.let { playheadUs -> updatePreview(previousSource) { it.copy(playheadUs = playheadUs) } }
    }

    loadThumbnails()
    val preview = _previews.value[uri]
    _state.value = VideoTrimBarState(source = uri, playheadUs = playheads[uri] ?: 0, thumbnails = preview?.thumbnails.orEmpty())

    preview?.durationUs?.let { onDurationKnown(it) }
  }

  private fun onPreloadChanged(uris: Set<Uri>) {
    if (uris == preloadUris) {
      return
    }

    preloadUris = uris
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

  /** Recorded for every video, since a preloaded video's player is already up and showing a frame before it is the source. */
  private fun onPlaybackPositionChanged(event: VideoTrimBarEvents.PlaybackPositionChanged) {
    if (event.uri == source) {
      if (_state.value.isDragging) {
        return
      }

      playheads[event.uri] = event.positionUs
      _state.update { it.copy(playheadUs = event.positionUs) }
    } else {
      playheads[event.uri] = event.positionUs
      updatePreview(event.uri) { it.copy(playheadUs = event.positionUs) }
    }
  }

  private fun onStripMeasured(widthPx: Int, heightPx: Int) {
    if (widthPx == stripWidthPx && heightPx == stripHeightPx) {
      return
    }

    stripWidthPx = widthPx
    stripHeightPx = heightPx

    decodeJobs.values.forEach { it.cancel() }
    decodeJobs.clear()
    decodedStrips.evictAll()
    _previews.value = emptyMap()
    _state.update { it.copy(thumbnails = emptyList()) }
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
   * Drops the previews of videos no longer wanted, and gives each wanted one without a preview its cached strip, or
   * starts decoding it, source first.
   */
  private fun loadThumbnails() {
    val wanted = (listOfNotNull(source) + preloadUris).distinct()

    (decodeJobs.keys - wanted.toSet()).forEach { decodeJobs.remove(it)?.cancel() }
    _previews.update { previews -> previews.filterKeys { it in wanted } }

    if (stripWidthPx <= 0 || stripHeightPx <= 0) {
      return
    }

    wanted.filterNot { it in _previews.value }.forEach { uri ->
      val cached = decodedStrips[uri]
      if (cached != null) {
        setPreview(uri, cached.copy(playheadUs = playheads[uri]))
      } else {
        decode(uri)
      }
    }
  }

  /** The strip is tiled with squares as tall as it is, the last one cut off by its end. */
  private fun decode(uri: Uri) {
    val count = ceil(stripWidthPx / stripHeightPx.toFloat()).toInt()
    setPreview(uri, VideoTrimBarPreview(thumbnails = List(count) { null }, playheadUs = playheads[uri]))

    // Started only once registered, since a decode that finishes without suspending would otherwise find no job to match.
    val decodeJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
      val thisDecode = coroutineContext.job

      decodeLock.withLock {
        VideoThumbnailRepository.thumbnails(uri, count, stripHeightPx).collect { result ->
          // Cancellation stops this collector, but a result already in hand must still never land on a newer decode.
          if (decodeJobs[uri] !== thisDecode) {
            return@collect
          }

          when (result) {
            is VideoThumbnailResult.DurationKnown -> {
              updatePreview(uri) { it.copy(durationUs = result.durationUs) }
              if (uri == source) {
                onDurationKnown(result.durationUs)
              }
            }

            is VideoThumbnailResult.Thumbnail -> {
              updatePreview(uri) { preview ->
                if (result.index in preview.thumbnails.indices) {
                  preview.copy(thumbnails = preview.thumbnails.toMutableList().apply { set(result.index, result.bitmap) })
                } else {
                  preview
                }
              }
            }
          }
        }
      }

      val decoded = _previews.value[uri]
      if (decodeJobs[uri] === thisDecode && decoded != null && decoded.thumbnails.all { it != null }) {
        decodedStrips.put(uri, decoded.copy(playheadUs = null))
      }
    }
    decodeJobs[uri] = decodeJob
    decodeJob.start()
  }

  /** Leaves a video without a preview alone, since having one is what marks its thumbnails as loaded or on their way. */
  private fun updatePreview(uri: Uri, transform: (VideoTrimBarPreview) -> VideoTrimBarPreview) {
    val preview = _previews.value[uri] ?: return
    setPreview(uri, transform(preview))
  }

  private fun setPreview(uri: Uri, preview: VideoTrimBarPreview) {
    _previews.update { it + (uri to preview) }

    if (uri == source) {
      _state.update { it.copy(thumbnails = preview.thumbnails) }
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

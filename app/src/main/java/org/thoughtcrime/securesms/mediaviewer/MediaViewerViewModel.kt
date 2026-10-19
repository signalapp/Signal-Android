/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediaviewer

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.models.database.AttachmentId
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.incrementalmac.InvalidMacException
import org.signal.mediaviewer.MediaViewerAction
import org.signal.mediaviewer.MediaViewerEvent
import org.signal.mediaviewer.MediaViewerItem
import org.signal.mediaviewer.MediaViewerScreen
import org.signal.mediaviewer.MediaViewerState
import org.signal.mediaviewer.ScrollRequest
import org.signal.paging.PagedData
import org.signal.paging.PagingConfig
import org.signal.paging.ProxyPagingController
import org.signal.paging.StateFlowPagedData
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.toStyledString
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory
import org.thoughtcrime.securesms.mms.PartAuthority

/**
 * Drives [MediaViewerScreen]: pages the thread's gallery around the opened attachment, refreshes
 * when the underlying attachments change (finishing if the current one is removed), and turns user
 * events into host actions.
 */
class MediaViewerViewModel(
  private val args: MediaIntentFactory.MediaPreviewArgs,
  private val context: Context = AppDependencies.application,
  private val repository: MediaViewerRepository = MediaViewerRepository,
  private val dataSourceFactory: () -> MediaViewerPagedDataSource = {
    MediaViewerPagedDataSource(AppDependencies.application, args.threadId, args.sorting, args.leftIsRecent)
  },
  private val databaseObserver: DatabaseObserver = AppDependencies.databaseObserver,
  private val pagedDataFactory: (MediaViewerPagedDataSource, PagingConfig) -> StateFlowPagedData<AttachmentId, MediaViewerItem> = { source, config ->
    PagedData.createForStateFlow(source, config)
  }
) : EventDrivenViewModel<MediaViewerEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(MediaViewerViewModel::class)

    private const val PAGE_SIZE = 25
    private const val INVALIDATION_DEBOUNCE_MS = 250L

    @VisibleForTesting
    internal const val CHROME_AUTO_HIDE_DELAY_MS = 2_000L
  }

  private val _state = MutableStateFlow(
    MediaViewerState(
      showThread = args.showThread,
      allMediaInRail = args.allMediaInRail,
      messageBodies = args.initialMessageBody
        ?.let { mapOf(args.messageId to it.toStyledString()) }
        ?: emptyMap()
    )
  )
  val state: StateFlow<MediaViewerState> = _state.asStateFlow()

  private val _actions = Channel<MediaViewerAction>(Channel.BUFFERED)
  val actions: Flow<MediaViewerAction> = _actions.receiveAsFlow()

  private val proxyController = ProxyPagingController<AttachmentId>()
  private val invalidations = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
  private val invalidationObserver = DatabaseObserver.Observer { invalidations.tryEmit(Unit) }

  private var dataSource: MediaViewerPagedDataSource? = null
  private val startingAttachmentId: AttachmentId? = args.initialMediaUri.takeIf { PartAuthority.isAttachmentUri(it) }?.let { PartAuthority.requireAttachmentId(it) }
  private var deletedAttachmentId: AttachmentId? = null
  private var initialDisplayIndex = 0
  private var hasAppliedPagedData = false
  private var pagerScrollInProgress = false
  private var pendingFirstPagedItems: List<MediaViewerItem?>? = null
  private var chromeHideJob: Job? = null
  private var nextScrollRequestId = 1L
  private val requestedBodyMessageIds = mutableSetOf<Long>()

  init {
    databaseObserver.registerAttachmentUpdatedObserver(invalidationObserver)
    databaseObserver.registerAttachmentDeletedObserver(invalidationObserver)

    @OptIn(FlowPreview::class)
    viewModelScope.launch {
      // One attachment's lifecycle fires a burst of notifications, each of which would re-run the gallery query.
      invalidations.debounce(INVALIDATION_DEBOUNCE_MS).collect { onEvent(MediaViewerEvent.DataInvalidated) }
    }

    loadGallery()
  }

  /** Whether the shared element exit transition should run: the user is still on the media they entered on. */
  fun shouldFinishAfterTransition(): Boolean {
    val startingId = startingAttachmentId ?: return false
    return _state.value.currentItem?.attachmentId == startingId
  }

  override suspend fun processEvent(event: MediaViewerEvent) {
    when (event) {
      is MediaViewerEvent.PageScrolled -> applyPageScrolled(event)
      is MediaViewerEvent.PageSettled -> applyPageSettled(event)
      is MediaViewerEvent.PagerScrollStateChanged -> applyPagerScrollStateChanged(event)
      is MediaViewerEvent.RailVisibleRange -> applyRailVisibleRange(event)
      is MediaViewerEvent.MediaReady -> applyMediaReady()
      is MediaViewerEvent.MediaTapped -> applyMediaTapped()
      is MediaViewerEvent.SharedAnimationChanged -> applySharedAnimationChanged(event)
      is MediaViewerEvent.HdrDetected -> applyHdrDetected(event)
      is MediaViewerEvent.VideoPlaybackStarted -> applyVideoPlaybackStarted()
      is MediaViewerEvent.VideoPlaybackStopped -> applyVideoPlaybackStopped()
      is MediaViewerEvent.VideoPlaybackError -> applyVideoPlaybackError(event)
      is MediaViewerEvent.ChromeAutoHideElapsed -> applyChromeAutoHideElapsed()
      is MediaViewerEvent.BackClicked -> applyBackClicked()
      is MediaViewerEvent.ToolbarTitleClicked -> applyToolbarTitleClicked()
      is MediaViewerEvent.ShareClicked -> applyShareClicked()
      is MediaViewerEvent.ForwardClicked -> applyForwardClicked()
      is MediaViewerEvent.SaveClicked -> applySaveClicked()
      is MediaViewerEvent.EditClicked -> applyEditClicked()
      is MediaViewerEvent.DeleteClicked -> applyDeleteClicked()
      is MediaViewerEvent.DeleteForEveryoneValidityResolved -> applyDeleteForEveryoneValidityResolved(event)
      is MediaViewerEvent.DeleteConfirmed -> applyDeleteConfirmed(event)
      is MediaViewerEvent.ViewStorageSettingsClicked -> applyViewStorageSettingsClicked()
      is MediaViewerEvent.DialogDismissed -> applyDialogDismissed()
      is MediaViewerEvent.DataInvalidated -> applyDataInvalidated()
      is MediaViewerEvent.PagedDataUpdated -> applyPagedData(event.items)
      is MediaViewerEvent.InitialItemLoaded -> applyInitialItemLoaded(event)
      is MediaViewerEvent.GalleryAnchorMissing -> applyGalleryAnchorMissing()
      is MediaViewerEvent.MessageBodiesResolved -> applyMessageBodiesResolved(event)
      is MediaViewerEvent.ScrollRequestStarted -> applyScrollRequestStarted(event)
      is MediaViewerEvent.RailItemClicked -> applyRailItemClicked(event)
    }
  }

  private fun applyPageScrolled(event: MediaViewerEvent.PageScrolled) {
    if (hasAppliedPagedData) {
      proxyController.onDataNeededAroundIndex(event.position)
    }
    _state.update { state ->
      if (state.scrollRequest == null && state.position != event.position) {
        state.copy(position = event.position)
      } else {
        state
      }
    }
  }

  private fun applyPageSettled(event: MediaViewerEvent.PageSettled) {
    cancelChromeAutoHide()
    _state.update { state ->
      val request = state.scrollRequest
      if (request != null && !request.started && request.position != event.position) {
        state
      } else {
        state.copy(position = event.position, scrollRequest = null)
      }
    }
    val current = _state.value.currentItem
    if (current != null && current.transferNeeded) {
      viewModelScope.launch { repository.downloadAttachmentIfNeeded(current.attachmentId) }
    }
  }

  private fun applyRailVisibleRange(event: MediaViewerEvent.RailVisibleRange) {
    if (_state.value.allMediaInRail && hasAppliedPagedData) {
      proxyController.onDataNeededAroundIndex((event.first + event.last) / 2)
    }
  }

  private fun applyMediaReady() {
    _state.update { it.copy(loadState = MediaViewerState.LoadState.MEDIA_READY) }
  }

  private fun applyMediaTapped() {
    cancelChromeAutoHide()
    _state.update { it.copy(chromeVisible = !it.chromeVisible) }
  }

  private fun applySharedAnimationChanged(event: MediaViewerEvent.SharedAnimationChanged) {
    _state.update { it.copy(isInSharedAnimation = event.running) }
  }

  private fun applyHdrDetected(event: MediaViewerEvent.HdrDetected) {
    _state.update { state ->
      if (event.uri in state.hdrCapableUris) {
        state
      } else {
        state.copy(hdrCapableUris = state.hdrCapableUris + event.uri)
      }
    }
  }

  private suspend fun applyVideoPlaybackStarted() {
    if (_state.value.currentItem?.isVideoGif == false) {
      _actions.send(MediaViewerAction.PauseVoiceNotes)
    }
    scheduleChromeAutoHide()
  }

  private fun applyVideoPlaybackStopped() {
    cancelChromeAutoHide()
  }

  private suspend fun applyVideoPlaybackError(event: MediaViewerEvent.VideoPlaybackError) {
    if (event.error is InvalidMacException) {
      viewModelScope.launch { repository.handleIncrementalMacError(event.uri) }
    }
    _actions.send(MediaViewerAction.ShowToast(R.string.MediaPreviewActivity_unable_to_play_media))
    _actions.send(MediaViewerAction.Finish)
  }

  private fun applyChromeAutoHideElapsed() {
    _state.update { it.copy(chromeVisible = false) }
  }

  private suspend fun applyBackClicked() {
    _actions.send(MediaViewerAction.FinishAfterTransition)
  }

  private fun applyToolbarTitleClicked() {
    val messageId = _state.value.currentItem?.messageId ?: return
    if (messageId <= 0) {
      return
    }
    viewModelScope.launch {
      val intent = repository.getMessagePositionIntent(context, messageId)
      if (intent != null) {
        _actions.send(MediaViewerAction.JumpToMessage(intent))
      } else {
        Log.w(TAG, "Could not find message position for message ID: $messageId")
        _actions.send(MediaViewerAction.ShowToast(R.string.MediaPreviewActivity_error_finding_message))
      }
    }
  }

  private suspend fun applyShareClicked() {
    val item = _state.value.currentItem ?: return
    val uri = item.playbackUri ?: return
    _actions.send(MediaViewerAction.ShareMedia(uri, item.contentType))
  }

  private suspend fun applyForwardClicked() {
    val item = _state.value.currentItem ?: return
    val uri = item.playbackUri ?: return
    _actions.send(MediaViewerAction.ShowForwardSheet(item.threadId, uri, item.contentType))
  }

  private suspend fun applySaveClicked() {
    val item = _state.value.currentItem ?: return
    if (!item.hasData && repository.isOptimizedStorageEnabled()) {
      _state.update { it.copy(dialog = MediaViewerState.Dialog.SaveOffloadedMedia) }
      return
    }

    val uri = item.playbackUri
    val contentType = item.contentType
    if (uri == null || contentType == null) {
      Log.w(TAG, "Unable to save attachment with null URI or contentType.")
      return
    }
    val date = if (item.date > 0) {
      item.date
    } else {
      System.currentTimeMillis()
    }
    _actions.send(MediaViewerAction.SaveMedia(uri, contentType, date))
  }

  private fun applyEditClicked() {
    val item = _state.value.currentItem ?: return
    viewModelScope.launch {
      val media = repository.getMediaForEdit(item.attachmentId, item.date)
      if (media != null) {
        _actions.send(MediaViewerAction.OpenEditor(media))
      } else {
        _actions.send(MediaViewerAction.ShowToast(R.string.MediaPreviewFragment_edit_media_error))
      }
    }
  }

  private fun applyDeleteClicked() {
    val item = _state.value.currentItem ?: return
    viewModelScope.launch {
      val validity = repository.getDeleteValidity(item.attachmentId)
      onEvent(MediaViewerEvent.DeleteForEveryoneValidityResolved(validity.canDeleteForEveryone, validity.isNoteToSelf))
    }
  }

  private fun applyDeleteForEveryoneValidityResolved(event: MediaViewerEvent.DeleteForEveryoneValidityResolved) {
    _state.update {
      it.copy(dialog = MediaViewerState.Dialog.DeleteConfirmation(event.canDeleteForEveryone, event.isNoteToSelf))
    }
  }

  private fun applyDeleteConfirmed(event: MediaViewerEvent.DeleteConfirmed) {
    val item = _state.value.currentItem ?: return
    _state.update { it.copy(dialog = MediaViewerState.Dialog.None) }
    viewModelScope.launch {
      val result = if (event.deleteForEveryone) {
        repository.remoteDelete(item.attachmentId)
      } else {
        repository.localDelete(item.attachmentId)
      }

      when (result) {
        is Either.Right -> {
          deletedAttachmentId = item.attachmentId
          if (dataSource == null) {
            _actions.send(MediaViewerAction.Finish)
            return@launch
          }
        }
        is Either.Left -> {
          Log.e(TAG, "Delete failed!", result.value)
          _actions.send(MediaViewerAction.ShowToast(R.string.MediaPreviewFragment_media_delete_error))
        }
      }
      onEvent(MediaViewerEvent.DataInvalidated)
    }
  }

  private suspend fun applyViewStorageSettingsClicked() {
    _state.update { it.copy(dialog = MediaViewerState.Dialog.None) }
    _actions.send(MediaViewerAction.OpenStorageSettings)
  }

  private fun applyDialogDismissed() {
    _state.update { it.copy(dialog = MediaViewerState.Dialog.None) }
  }

  private fun applyDataInvalidated() {
    val source = dataSource ?: return
    val currentId = _state.value.currentItem?.attachmentId
    val position = _state.value.position
    viewModelScope.launch {
      if (currentId == null || withContext(SignalDispatchers.Default) { source.isStillViewable(currentId) }) {
        proxyController.onDataInvalidated()
        if (hasAppliedPagedData) {
          proxyController.onDataNeededAroundIndex(position)
        }
        return@launch
      }

      when {
        currentId == deletedAttachmentId -> {
          _actions.send(MediaViewerAction.Finish)
        }
        repository.attachmentExists(currentId) -> {
          Log.w(TAG, "Current attachment is no longer viewable but still exists. Keeping it on screen and skipping the refresh.")
        }
        else -> {
          _actions.send(MediaViewerAction.ShowToast(R.string.MediaPreviewActivity_media_no_longer_available))
          _actions.send(MediaViewerAction.Finish)
        }
      }
    }
  }

  private fun applyInitialItemLoaded(event: MediaViewerEvent.InitialItemLoaded) {
    _state.update { state ->
      if (state.loadState == MediaViewerState.LoadState.INIT) {
        state.copy(
          position = 0,
          items = listOf(event.item),
          loadState = MediaViewerState.LoadState.DATA_LOADED
        )
      } else {
        state
      }
    }
  }

  private suspend fun applyGalleryAnchorMissing() {
    if (_state.value.items.isEmpty()) {
      Log.w(TAG, "The opened attachment is not in the gallery and could not be loaded, finishing.")
      _actions.send(MediaViewerAction.ShowToast(R.string.MediaPreviewActivity_media_no_longer_available))
      _actions.send(MediaViewerAction.Finish)
    } else {
      Log.w(TAG, "The opened attachment is not in the gallery. Showing it alone.")
    }
  }

  private fun applyMessageBodiesResolved(event: MediaViewerEvent.MessageBodiesResolved) {
    _state.update { it.copy(messageBodies = it.messageBodies + event.bodies) }
  }

  private fun applyScrollRequestStarted(event: MediaViewerEvent.ScrollRequestStarted) {
    _state.update { state ->
      val request = state.scrollRequest
      if (request?.id == event.id) {
        state.copy(scrollRequest = request.copy(started = true))
      } else {
        state
      }
    }
  }

  private fun applyRailItemClicked(event: MediaViewerEvent.RailItemClicked) {
    val index = _state.value.items.indexOfFirst { it?.attachmentId == event.attachmentId }
    if (index >= 0 && index != _state.value.position) {
      // Moved optimistically so the rail responds before the pager finishes animating.
      _state.update { it.copy(position = index, scrollRequest = ScrollRequest(nextScrollRequestId++, index, animated = true)) }
    }
  }

  private fun applyPagerScrollStateChanged(event: MediaViewerEvent.PagerScrollStateChanged) {
    pagerScrollInProgress = event.inProgress
    if (!event.inProgress) {
      pendingFirstPagedItems?.let { pending ->
        pendingFirstPagedItems = null
        applyPagedData(pending)
      }
    }
  }

  private fun applyPagedData(items: List<MediaViewerItem?>) {
    if (items.isEmpty()) {
      return
    }

    // The first application re-indexes the pager, so hold it until any in-flight swipe settles.
    if (!hasAppliedPagedData && pagerScrollInProgress) {
      pendingFirstPagedItems = items
      return
    }

    val firstApplication = !hasAppliedPagedData
    hasAppliedPagedData = true

    _state.update { state ->
      val loadState = if (state.loadState == MediaViewerState.LoadState.MEDIA_READY) {
        MediaViewerState.LoadState.MEDIA_READY
      } else {
        MediaViewerState.LoadState.DATA_LOADED
      }

      if (firstApplication) {
        val anchoredPosition = (initialDisplayIndex + state.position).coerceIn(0, items.lastIndex)
        state.copy(
          items = items,
          position = anchoredPosition,
          scrollRequest = ScrollRequest(nextScrollRequestId++, anchoredPosition, animated = false),
          loadState = loadState
        )
      } else {
        state.copy(
          items = items,
          position = state.position.coerceIn(0, items.lastIndex),
          loadState = loadState
        )
      }
    }

    resolveMissingMessageBodies(items)
  }

  private fun resolveMissingMessageBodies(items: List<MediaViewerItem?>) {
    val missing = items.asSequence()
      .filterNotNull()
      .map { it.messageId }
      .filter { it > 0 }
      .filter { it !in requestedBodyMessageIds }
      .toSet()

    if (missing.isEmpty()) {
      return
    }

    requestedBodyMessageIds += missing
    viewModelScope.launch {
      val bodies = repository.resolveMessageBodies(context, missing)
      onEvent(MediaViewerEvent.MessageBodiesResolved(bodies))
    }
  }

  private fun loadGallery() {
    val startingId = startingAttachmentId
    if (startingId == null) {
      Log.w(TAG, "Could not parse an attachment id from the initial media uri, finishing.")
      _actions.trySend(MediaViewerAction.Finish)
      return
    }

    val initialItemJob = viewModelScope.launch {
      repository.getInitialItem(context, startingId, args)?.let { onEvent(MediaViewerEvent.InitialItemLoaded(it)) }
    }

    viewModelScope.launch {
      val source = dataSourceFactory()
      val displayIndex = withContext(SignalDispatchers.Default) { source.findDisplayIndex(startingId) }
      if (displayIndex < 0) {
        initialItemJob.join()
        onEvent(MediaViewerEvent.GalleryAnchorMissing)
        return@launch
      }

      dataSource = source
      initialDisplayIndex = displayIndex

      val pagedData = pagedDataFactory(
        source,
        PagingConfig.Builder()
          .setPageSize(PAGE_SIZE)
          .setBufferPages(1)
          .setStartIndex(initialDisplayIndex)
          .build()
      )
      proxyController.set(pagedData.controller)

      // The paging layer fills unfetched positions with nulls despite the non-null element type.
      pagedData.data.collect { items ->
        onEvent(MediaViewerEvent.PagedDataUpdated(items))
      }
    }
  }

  private fun scheduleChromeAutoHide() {
    chromeHideJob?.cancel()
    chromeHideJob = viewModelScope.launch {
      delay(CHROME_AUTO_HIDE_DELAY_MS)
      onEvent(MediaViewerEvent.ChromeAutoHideElapsed)
    }
  }

  private fun cancelChromeAutoHide() {
    chromeHideJob?.cancel()
    chromeHideJob = null
  }

  override fun onCleared() {
    databaseObserver.unregisterObserver(invalidationObserver)
    super.onCleared()
  }

  class Factory(private val args: MediaIntentFactory.MediaPreviewArgs) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      @Suppress("UNCHECKED_CAST")
      return MediaViewerViewModel(args) as T
    }
  }
}

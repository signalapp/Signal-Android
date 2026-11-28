/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediaoverview.v2

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.mediaoverview.MediaOverviewAction
import org.signal.mediaoverview.MediaOverviewEvent
import org.signal.mediaoverview.MediaOverviewItem
import org.signal.mediaoverview.MediaOverviewRow
import org.signal.mediaoverview.MediaOverviewState
import org.signal.mediaoverview.MediaOverviewState.Dialog
import org.signal.mediaoverview.MediaOverviewState.Tab
import org.thoughtcrime.securesms.database.MediaTable
import java.time.LocalDate

/**
 * Drives [org.signal.mediaoverview.MediaOverviewScreen] for one conversation, or for every
 * conversation when [threadId] is [MediaTable.ALL_THREADS].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaOverviewViewModel(
  private val threadId: Long,
  private val repository: MediaOverviewRepository = MediaOverviewRepository()
) : EventDrivenViewModel<MediaOverviewEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(MediaOverviewViewModel::class)

    private const val PAGE_SIZE = 60
    private const val INITIAL_LOAD_SIZE = PAGE_SIZE
    private const val MAX_LOADED_ITEMS = 600
  }

  private val isAllThreads = threadId == MediaTable.ALL_THREADS.toLong()

  private val _state = MutableStateFlow(
    MediaOverviewState(
      isAllThreads = isAllThreads,
      selectedTab = if (isAllThreads) Tab.ALL else Tab.MEDIA,
      sorting = if (isAllThreads) MediaOverviewState.Sorting.LARGEST else MediaOverviewState.Sorting.NEWEST,
      detailLayout = isAllThreads
    )
  )
  private val _actions = Channel<MediaOverviewAction>(Channel.BUFFERED)

  val state: StateFlow<MediaOverviewState> = _state.asStateFlow()
  val actions: Flow<MediaOverviewAction> = _actions.receiveAsFlow()

  /** Each tab's paged media. Nothing is queried until a tab's flow is first collected. */
  val media: Map<Tab, Flow<PagingData<MediaOverviewRow>>> = _state.value.tabs.associateWith { tab -> pagedMedia(tab) }

  init {
    if (!isAllThreads) {
      viewModelScope.launch {
        repository.observeTitle(threadId).collect { title ->
          _state.update { it.copy(title = title) }
        }
      }
    }

    viewModelScope.launch {
      repository.mediaChanges().collect { dropMissingItems() }
    }
  }

  override suspend fun processEvent(event: MediaOverviewEvent) {
    when (event) {
      MediaOverviewEvent.BackClicked -> {
        _actions.send(MediaOverviewAction.NavigateBack)
      }
      is MediaOverviewEvent.TabSelected -> {
        _state.update { it.copy(selectedTab = event.tab, contextMenuItem = null) }
      }
      MediaOverviewEvent.SortClicked -> {
        _state.update { it.copy(dialog = Dialog.SortOrder) }
      }
      is MediaOverviewEvent.SortSelected -> {
        _state.update { it.copy(sorting = event.sorting, dialog = Dialog.None) }
      }
      MediaOverviewEvent.LayoutToggled -> {
        _state.update { it.copy(detailLayout = !it.detailLayout) }
      }
      is MediaOverviewEvent.ItemClicked -> {
        if (_state.value.isMultiSelect) {
          toggleSelection(event.item)
        } else {
          openItem(event.item)
        }
      }
      is MediaOverviewEvent.ItemLongClicked -> {
        if (_state.value.isMultiSelect) {
          toggleSelection(event.item)
        } else {
          _state.update { it.copy(contextMenuItem = event.item) }
        }
      }
      is MediaOverviewEvent.AudioButtonClicked -> {
        onAudioButtonClicked(event.item)
      }
      MediaOverviewEvent.ContextMenuDismissed -> {
        _state.update { it.copy(contextMenuItem = null) }
      }
      MediaOverviewEvent.ContextMenuForwardClicked -> {
        val item = consumeContextMenuItem() ?: return
        val uri = item.uri ?: return
        _actions.send(MediaOverviewAction.ShowForwardSheet(item.threadId, uri, item.contentType))
      }
      MediaOverviewEvent.ContextMenuShareClicked -> {
        val item = consumeContextMenuItem() ?: return
        if (item.isLink) {
          item.linkUrl?.takeIf { it.isNotEmpty() }?.let { _actions.send(MediaOverviewAction.ShareLink(it)) }
        } else {
          item.uri?.let { _actions.send(MediaOverviewAction.ShareMedia(it, item.contentType)) }
        }
      }
      MediaOverviewEvent.ContextMenuSaveClicked -> {
        val item = consumeContextMenuItem() ?: return
        save(setOf(item.key))
      }
      MediaOverviewEvent.ContextMenuDeleteClicked -> {
        val item = consumeContextMenuItem() ?: return
        _state.update { it.copy(dialog = Dialog.ConfirmDelete(setOf(item.key))) }
      }
      MediaOverviewEvent.ContextMenuSelectClicked -> {
        val item = consumeContextMenuItem() ?: return
        _state.update { it.copy(selection = mapOf(item.key to item.size)) }
      }
      MediaOverviewEvent.ContextMenuJumpToMessageClicked -> {
        val item = consumeContextMenuItem() ?: return
        repository.createConversationIntent(item.key)?.let { _actions.send(MediaOverviewAction.OpenConversation(it)) }
      }
      MediaOverviewEvent.SaveSelectedClicked -> {
        save(_state.value.selectedKeys)
      }
      MediaOverviewEvent.SelectAllClicked -> {
        selectAll()
      }
      MediaOverviewEvent.DeleteSelectedClicked -> {
        _state.update { it.copy(dialog = Dialog.ConfirmDelete(it.selectedKeys)) }
      }
      MediaOverviewEvent.SelectionCleared -> {
        _state.update { it.copy(selection = emptyMap()) }
      }
      MediaOverviewEvent.DeleteConfirmed -> {
        val dialog = _state.value.dialog as? Dialog.ConfirmDelete ?: return
        delete(dialog.keys)
      }
      MediaOverviewEvent.SaveAvailableConfirmed -> {
        val dialog = _state.value.dialog as? Dialog.PartiallyOffloaded ?: return
        _state.update { it.copy(dialog = Dialog.None) }
        sendSave(dialog.saveableKeys)
      }
      MediaOverviewEvent.ViewStorageSettingsClicked -> {
        _state.update { it.copy(dialog = Dialog.None) }
        _actions.send(MediaOverviewAction.OpenStorageSettings)
      }
      MediaOverviewEvent.DialogDismissed -> {
        _state.update { it.copy(dialog = Dialog.None) }
      }
      MediaOverviewEvent.SaveFinished -> {
        _state.update { it.copy(selection = emptyMap()) }
      }
      is MediaOverviewEvent.AudioPlaybackChanged -> {
        val playback = event.uri?.let { MediaOverviewState.AudioPlayback(it, event.isPlaying, event.progress) }
        _state.update { it.copy(audioPlayback = playback) }
      }
    }
  }

  private fun pagedMedia(tab: Tab): Flow<PagingData<MediaOverviewRow>> {
    return _state
      .map { it.sorting }
      .distinctUntilChanged()
      .flatMapLatest { sorting ->
        val sections = MediaOverviewSections(sorting, LocalDate.now())
        Pager(
          config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = INITIAL_LOAD_SIZE,
            maxSize = MAX_LOADED_ITEMS,
            enablePlaceholders = true,
            jumpThreshold = PAGE_SIZE * 3
          ),
          pagingSourceFactory = { MediaOverviewPagingSource(repository, threadId, tab, sorting) }
        ).flow.map { it.withSectionHeaders(sections) }
      }
      .cachedIn(viewModelScope)
  }

  private fun toggleSelection(item: MediaOverviewItem) {
    _state.update {
      val selection = if (item.key in it.selection) it.selection - item.key else it.selection + (item.key to item.size)
      it.copy(selection = selection)
    }
  }

  private suspend fun selectAll() {
    val state = _state.value
    _state.update { it.copy(dialog = Dialog.SelectAllLoading) }

    val selection = repository.getAllKeysWithSizes(threadId, state.selectedTab, state.sorting)

    _state.update { it.copy(selection = selection, dialog = Dialog.None) }
  }

  /** Drops selected or long-pressed items that have since been deleted. */
  private suspend fun dropMissingItems() {
    val state = _state.value
    val keys = state.selectedKeys + listOfNotNull(state.contextMenuKey)
    if (keys.isEmpty()) {
      return
    }

    val existing = repository.getExistingKeys(keys)
    val missing = keys - existing
    if (missing.isEmpty()) {
      return
    }

    _state.update {
      it.copy(
        selection = it.selection - missing,
        contextMenuItem = it.contextMenuItem?.takeIf { item -> item.key !in missing }
      )
    }
  }

  private suspend fun openItem(item: MediaOverviewItem) {
    if (item.isLink) {
      item.linkUrl?.takeIf { it.isNotEmpty() }?.let { _actions.send(MediaOverviewAction.OpenLink(it)) }
      return
    }

    if (!item.hasAttachment) {
      return
    }

    if (item.displayUri == null) {
      if (item.transferState == MediaOverviewItem.TransferState.OFFLOADED) {
        repository.download(item.key)
      }
      return
    }

    when (item.kind) {
      MediaOverviewItem.Kind.IMAGE,
      MediaOverviewItem.Kind.VIDEO -> {
        if (item.transferState != MediaOverviewItem.TransferState.DONE && item.transferState != MediaOverviewItem.TransferState.OFFLOADED) {
          _actions.send(MediaOverviewAction.ShowMediaNotSentYet)
        } else {
          repository.createPreviewIntent(item.key, _state.value.sorting, isAllThreads)?.let { intent ->
            _actions.send(MediaOverviewAction.OpenMediaPreview(item.key, intent))
          }
        }
      }
      MediaOverviewItem.Kind.AUDIO -> {
        // Audio plays from the row's own play button, so tapping the row does nothing.
      }
      MediaOverviewItem.Kind.DOCUMENT,
      MediaOverviewItem.Kind.LINK -> {
        item.uri?.let { _actions.send(MediaOverviewAction.OpenFileExternally(it, item.contentType)) }
      }
    }
  }

  private suspend fun onAudioButtonClicked(item: MediaOverviewItem) {
    val uri = item.uri

    if (uri == null) {
      repository.download(item.key)
      return
    }

    val playback = _state.value.audioPlayback?.takeIf { it.uri == uri }
    if (playback?.isPlaying == true) {
      _actions.send(MediaOverviewAction.PauseAudio(uri))
    } else {
      _actions.send(MediaOverviewAction.PlayAudio(uri, item.messageId, playback?.progress?.toDouble() ?: 0.0))
    }
  }

  private suspend fun save(keys: Set<MediaOverviewItem.Key>) {
    if (keys.isEmpty()) {
      return
    }

    if (repository.isOptimizeStorageEnabled()) {
      val items = repository.getItems(keys)
      if (items.isEmpty()) {
        return
      }

      val offloaded = items.filter { it.isOffloaded }
      if (offloaded.size == items.size) {
        _state.update { it.copy(dialog = Dialog.AllOffloaded) }
        return
      } else if (offloaded.isNotEmpty()) {
        val saveable = items.filterNot { it.isOffloaded }.map { it.key }.toSet()
        _state.update { it.copy(dialog = Dialog.PartiallyOffloaded(saveable)) }
        return
      }
    }

    sendSave(keys)
  }

  private suspend fun sendSave(keys: Set<MediaOverviewItem.Key>) {
    _actions.send(MediaOverviewAction.SaveMedia(repository.getSaveableMedia(keys)))
  }

  private suspend fun delete(keys: Set<MediaOverviewItem.Key>) {
    _state.update {
      it.copy(
        selection = it.selection - keys,
        dialog = Dialog.DeleteProgress(processed = 0, total = keys.size)
      )
    }

    repository.delete(keys) { processed ->
      _state.update { it.copy(dialog = Dialog.DeleteProgress(processed = processed, total = keys.size)) }
    }

    _state.update { it.copy(dialog = Dialog.None) }
  }

  /** Clears the open long-press menu, returning the item it was open for. */
  private fun consumeContextMenuItem(): MediaOverviewItem? {
    val item = _state.value.contextMenuItem ?: return null
    _state.update { it.copy(contextMenuItem = null) }
    return item
  }
}

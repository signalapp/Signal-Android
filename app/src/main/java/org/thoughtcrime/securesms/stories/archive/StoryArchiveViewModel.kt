package org.thoughtcrime.securesms.stories.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.signal.paging.PagedData
import org.signal.paging.PagingConfig
import org.signal.paging.PagingController
import org.signal.paging.ProxyPagingController
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.dependencies.AppDependencies

class StoryArchiveViewModel : ViewModel() {

  private val repository = StoryArchiveRepository()

  private val _state = MutableStateFlow(StoryArchiveState())
  val state: StateFlow<StoryArchiveState> = _state

  private var pagedDataJob: Job? = null
  private val proxyPagingController = ProxyPagingController<Long>()
  val pagingController: PagingController<Long> get() = proxyPagingController

  private val databaseObserver = DatabaseObserver.Observer {
    proxyPagingController.onDataInvalidated()
  }

  init {
    AppDependencies.databaseObserver.registerConversationListObserver(databaseObserver)
    loadPagedData()
  }

  fun refresh() {
    loadPagedData()
  }

  fun setSortOrder(sortOrder: SortOrder) {
    _state.value = _state.value.copy(sortOrder = sortOrder, isLoading = true)
    loadPagedData()
  }

  fun toggleSelection(messageId: Long) {
    val current = _state.value
    val newSelection = if (current.selectedIds.contains(messageId)) {
      current.selectedIds - messageId
    } else {
      current.selectedIds + messageId
    }
    _state.value = current.copy(
      selectedIds = newSelection,
      multiSelectEnabled = newSelection.isNotEmpty()
    )
  }

  fun clearSelection() {
    _state.value = _state.value.copy(
      multiSelectEnabled = false,
      selectedIds = emptySet(),
      showDeleteConfirmation = false
    )
  }

  fun requestDeleteSelected() {
    _state.value = _state.value.copy(showDeleteConfirmation = true)
  }

  fun cancelDelete() {
    _state.value = _state.value.copy(showDeleteConfirmation = false)
  }

  fun confirmDeleteSelected() {
    val idsToDelete = _state.value.selectedIds
    _state.value = _state.value.copy(
      multiSelectEnabled = false,
      selectedIds = emptySet(),
      showDeleteConfirmation = false
    )
    viewModelScope.launch(Dispatchers.IO) {
      repository.deleteStories(idsToDelete)
    }
  }

  private fun loadPagedData() {
    val sortNewest = _state.value.sortOrder == SortOrder.NEWEST
    val dataSource = StoryArchivePagedDataSource(sortNewest)
    val config = PagingConfig.Builder()
      .setPageSize(30)
      .setBufferPages(1)
      .setStartIndex(0)
      .build()

    val newPagedData = PagedData.createForStateFlow(dataSource, config)
    proxyPagingController.set(newPagedData.controller)

    pagedDataJob?.cancel()
    pagedDataJob = viewModelScope.launch {
      newPagedData.data.collectLatest { stories ->
        _state.value = _state.value.copy(stories = stories, isLoading = false)
      }
    }
  }

  override fun onCleared() {
    AppDependencies.databaseObserver.unregisterObserver(databaseObserver)
  }
}

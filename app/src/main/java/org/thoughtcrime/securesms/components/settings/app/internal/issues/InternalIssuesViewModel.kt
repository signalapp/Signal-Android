/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.issues

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.database.LogDatabase
import org.thoughtcrime.securesms.database.LogDatabase.IssueTable.IssueRecord
import org.thoughtcrime.securesms.database.model.IssuePriority
import org.thoughtcrime.securesms.keyvalue.SignalStore

class InternalIssuesViewModel(application: Application) : AndroidViewModel(application) {

  private val _state = MutableStateFlow(InternalIssuesState())
  val state: StateFlow<InternalIssuesState> = _state.asStateFlow()

  private val issues = LogDatabase.getInstance(application).issues

  private var allIssues: List<IssueRecord> = emptyList()

  fun onEvent(event: InternalIssuesScreenEvent) {
    when (event) {
      InternalIssuesScreenEvent.Load -> load()
      InternalIssuesScreenEvent.ClearAll -> clearAll()
      is InternalIssuesScreenEvent.ToggleExpanded -> toggleExpanded(event.id)
      is InternalIssuesScreenEvent.SetNotificationPriority -> setNotificationPriority(event.priority)
      is InternalIssuesScreenEvent.SetNameFilter -> _state.update { it.copy(nameFilter = event.name).withVisibleIssues() }
      is InternalIssuesScreenEvent.SetSortOrder -> _state.update { it.copy(sortOrder = event.order).withVisibleIssues() }
    }
  }

  private fun load() {
    viewModelScope.launch {
      allIssues = withContext(Dispatchers.Default) { issues.getRecent() }
      _state.update { it.copy(loading = false, notificationPriority = SignalStore.internal.issueNotificationPriority).withVisibleIssues() }
    }
  }

  private fun setNotificationPriority(priority: IssuePriority) {
    SignalStore.internal.issueNotificationPriority = priority
    _state.update { it.copy(notificationPriority = priority) }
  }

  private fun clearAll() {
    viewModelScope.launch {
      withContext(Dispatchers.Default) { issues.clear() }
      allIssues = emptyList()
      _state.update { it.copy(nameFilter = null, expandedIds = emptySet()).withVisibleIssues() }
    }
  }

  private fun toggleExpanded(id: Long) {
    _state.update {
      val expanded = if (it.expandedIds.contains(id)) it.expandedIds - id else it.expandedIds + id
      it.copy(expandedIds = expanded)
    }
  }

  private fun InternalIssuesState.withVisibleIssues(): InternalIssuesState {
    val visible = allIssues
      .filter { nameFilter == null || it.name == nameFilter }
      .sortedWith(sortOrder.comparator())

    return copy(issues = visible, names = allIssues.map { it.name }.distinct().sorted())
  }

  private fun IssueSortOrder.comparator(): Comparator<IssueRecord> {
    return when (this) {
      IssueSortOrder.CREATED_DESC -> compareByDescending { it.createdAt }
      IssueSortOrder.CREATED_ASC -> compareBy { it.createdAt }
      IssueSortOrder.DURATION_DESC -> compareByDescending { it.duration ?: Long.MIN_VALUE }
      IssueSortOrder.DURATION_ASC -> compareBy { it.duration ?: Long.MAX_VALUE }
      IssueSortOrder.PRIORITY_DESC -> compareByDescending { it.priority.value }
      IssueSortOrder.PRIORITY_ASC -> compareBy { it.priority.value }
    }
  }
}

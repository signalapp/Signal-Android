/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.selectcontact

import android.Manifest
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.ui.compose.PermissionController
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.contacts.index.ContactIndexBuildResult
import org.thoughtcrime.securesms.contacts.index.ContactIndexRecord
import org.thoughtcrime.securesms.contactshare.SharedContactSource
import org.thoughtcrime.securesms.contactshare.screens.selectcontact.SelectContactState.ContactsPermissionState

@OptIn(ExperimentalCoroutinesApi::class)
class SelectContactViewModel(
  private val source: ContactIndexSource,
  private val savedState: SavedStateHandle = SavedStateHandle()
) : EventDrivenViewModel<SelectContactEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(SelectContactViewModel::class)

    /** The system picker can take the activity down with it, and the rebuild on the way back would otherwise re-raise the prompt. */
    private const val KEY_PERMISSION_DECISION = "permission_decision"

    const val PAGE_SIZE = 100

    /** A full page, so a fling that reaches the loaded edge does not stop there waiting. */
    private const val PREFETCH_DISTANCE = PAGE_SIZE

    /** Six pages leaves three beyond the initial three-page load, so a long scroll does not re-read. */
    private const val MAX_SIZE = PAGE_SIZE * 6
  }

  private val _state = MutableStateFlow(SelectContactState())
  val state: StateFlow<SelectContactState> = _state.asStateFlow()

  private val _actions = Channel<SelectContactAction>(Channel.BUFFERED)
  val actions: Flow<SelectContactAction> = _actions.receiveAsFlow()

  /** No `permanentDenialMessage`: this screen answers a permanent denial with the picker button and footer instead of a dialog. */
  val contactsPermission = PermissionController(permission = Manifest.permission.READ_CONTACTS)

  private val query = MutableStateFlow("")

  /** Bumped after each rebuild so the pager throws away pages read from the previous index. */
  private val indexGeneration = MutableStateFlow(0)

  val rows: Flow<PagingData<SelectContactRow>> = combine(query, indexGeneration) { query, _ -> query }
    .flatMapLatest { query ->
      Pager(
        config = PagingConfig(
          pageSize = PAGE_SIZE,
          prefetchDistance = PREFETCH_DISTANCE,
          maxSize = MAX_SIZE,
          // The unfiltered index is counted, so the list can be its full length from the first page.
          enablePlaceholders = true
        ),
        pagingSourceFactory = { ContactIndexPagingSource(source, query) }
      ).flow.map { page ->
        page.withSectionHeaders(showHeaders = query.isBlank())
      }
    }
    .cachedIn(viewModelScope)

  init {
    onEvent(SelectContactEvent.Initialize)
  }

  override suspend fun processEvent(event: SelectContactEvent) {
    when (event) {
      SelectContactEvent.Initialize -> rebuild()

      is SelectContactEvent.QueryChanged -> {
        if (event.query == _state.value.query) {
          return
        }

        _state.update { it.copy(query = event.query) }
        query.value = event.query
      }

      is SelectContactEvent.ContactClicked -> {
        // The editor reads the provider again after this, so the row would otherwise sit there
        // looking untapped for the whole handoff.
        _state.update { it.copy(isLoading = true) }

        val resolved = source.resolve(event.contact)

        if (resolved != null) {
          _actions.send(SelectContactAction.ContactResolved(resolved))
        } else {
          Log.w(TAG, "Could not resolve the selected contact.")
          _state.update { it.copy(isLoading = false) }
          _actions.send(SelectContactAction.CouldNotOpenContact)
        }
      }

      SelectContactEvent.BackClicked -> _actions.send(SelectContactAction.Exit)

      SelectContactEvent.AllowContactsAccessClicked -> {
        if (contactsPermission.request()) {
          rebuild()
        } else if (contactsPermission.isPermanentlyDenied) {
          recordPermissionDecision(ContactsPermissionState.PERMANENTLY_DENIED)
          _state.update { it.copy(showPermissionDeniedSheet = contactsPermission.wasRefusedWithoutPrompting) }
        }
      }

      SelectContactEvent.DismissContactsAccessClicked -> recordPermissionDecision(ContactsPermissionState.DISMISSED)

      SelectContactEvent.OpenSystemContactPickerClicked -> _actions.send(SelectContactAction.LaunchSystemContactPicker)

      is SelectContactEvent.SystemContactPicked -> {
        if (event.phoneUri != null) {
          _actions.send(SelectContactAction.ContactResolved(SharedContactSource.SystemPhone(event.phoneUri)))
        }
      }

      SelectContactEvent.LearnMoreClicked -> {
        _state.update { it.copy(showPermissionDeniedSheet = true) }
      }

      SelectContactEvent.PermissionDeniedSheetDismissed -> {
        _state.update { it.copy(showPermissionDeniedSheet = false) }
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    source.close()
  }

  private fun recordPermissionDecision(decision: ContactsPermissionState) {
    savedState[KEY_PERMISSION_DECISION] = decision.name
    _state.update { it.copy(contactsPermission = decision) }
  }

  /** Also the path taken after access is granted, since the address book half was not read before. */
  private suspend fun rebuild() {
    _state.update { it.copy(isLoading = true) }

    val result = source.build()

    val built = when (result) {
      is ContactIndexBuildResult.Success -> ContactsPermissionState.GRANTED
      is ContactIndexBuildResult.SignalOnly -> ContactsPermissionState.DENIED

      ContactIndexBuildResult.OutOfSpace -> {
        Log.w(TAG, "Not enough space to index contacts. Showing an empty list.")
        ContactsPermissionState.GRANTED
      }

      is ContactIndexBuildResult.Failure -> {
        Log.w(TAG, "Could not index contacts. Showing an empty list.")
        ContactsPermissionState.GRANTED
      }
    }

    val permission = if (built == ContactsPermissionState.GRANTED) {
      savedState.remove<String>(KEY_PERMISSION_DECISION)
      ContactsPermissionState.GRANTED
    } else {
      savedState.get<String>(KEY_PERMISSION_DECISION)?.let { ContactsPermissionState.valueOf(it) } ?: built
    }

    val count = when (result) {
      is ContactIndexBuildResult.Success -> result.count
      is ContactIndexBuildResult.SignalOnly -> result.count
      else -> 0
    }

    _state.update { it.copy(contactsPermission = permission, indexCount = count, isLoading = false) }
    indexGeneration.update { it + 1 }
  }
}

/**
 * Interleaves section headers into a page of contacts.
 *
 * Relies on the index being ordered section major, so each section is one unbroken run. Filtered
 * results are a subset and do not share those boundaries, which is why search renders flat.
 */
private fun PagingData<ContactIndexRecord>.withSectionHeaders(showHeaders: Boolean): PagingData<SelectContactRow> {
  val rows: PagingData<SelectContactRow> = map { SelectContactRow.Contact(it) }

  if (!showHeaders) {
    return rows
  }

  return rows.insertSeparators { before, after ->
    val nextSection = (after as? SelectContactRow.Contact)?.contact?.section ?: return@insertSeparators null
    val previousSection = (before as? SelectContactRow.Contact)?.contact?.section

    if (previousSection != nextSection) SelectContactRow.Header(nextSection) else null
  }
}

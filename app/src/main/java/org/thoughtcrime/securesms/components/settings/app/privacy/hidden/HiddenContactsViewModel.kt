/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.hidden

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.hiddencontacts.HiddenContactsEvent
import org.signal.appsettings.hiddencontacts.HiddenContactsState
import org.signal.appsettings.hiddencontacts.HiddenContactsState.Dialog
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [org.signal.appsettings.hiddencontacts.HiddenContactsScreen]. Navigation goes straight to [parentEventEmitter].
 */
class HiddenContactsViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit,
  private val repository: HiddenContactsRepository = HiddenContactsRepository()
) : EventDrivenViewModel<HiddenContactsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(HiddenContactsViewModel::class)
  }

  private val _state = MutableStateFlow(HiddenContactsState())

  val state: StateFlow<HiddenContactsState> = _state.asStateFlow()

  override suspend fun processEvent(event: HiddenContactsEvent) {
    when (event) {
      HiddenContactsEvent.ScreenResumed -> {
        refresh()
      }
      HiddenContactsEvent.NavigateBackClicked -> {
        parentEventEmitter.navigateBack()
      }
      is HiddenContactsEvent.ContactClicked -> {
        val contact = _state.value.contacts.firstOrNull { it.id == event.id } ?: return
        _state.update { it.copy(dialog = Dialog.ConfirmUnhide(contact)) }
      }
      HiddenContactsEvent.UnhideConfirmed -> {
        val dialog = _state.value.dialog as? Dialog.ConfirmUnhide ?: return

        _state.update { it.copy(dialog = Dialog.None) }
        repository.unhide(dialog.contact.id)
        refresh()
      }
      HiddenContactsEvent.DialogDismissed -> {
        _state.update { it.copy(dialog = Dialog.None) }
      }
    }
  }

  private suspend fun refresh() {
    val contacts = repository.getHiddenContacts()
    _state.update { it.copy(contacts = contacts, isLoading = false) }
  }
}

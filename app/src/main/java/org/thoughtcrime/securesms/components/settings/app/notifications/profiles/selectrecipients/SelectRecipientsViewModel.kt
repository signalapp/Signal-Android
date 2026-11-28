/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.selectrecipients

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.selectrecipients.SelectRecipientsEvent
import org.signal.appsettings.selectrecipients.SelectRecipientsScreen
import org.signal.appsettings.selectrecipients.SelectRecipientsState
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [SelectRecipientsScreen], adding the picks to the people and groups profile [profileId] already allows
 * through, [currentMembers].
 */
class SelectRecipientsViewModel(
  private val profileId: Long,
  currentMembers: Set<RecipientId>,
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) : EventDrivenViewModel<SelectRecipientsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(SelectRecipientsViewModel::class)
  }

  private val _state = MutableStateFlow(SelectRecipientsState(currentMembers = currentMembers))

  val state: StateFlow<SelectRecipientsState> = _state.asStateFlow()

  override suspend fun processEvent(event: SelectRecipientsEvent) {
    when (event) {
      SelectRecipientsEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
      is SelectRecipientsEvent.SelectionChanged -> _state.update { it.copy(selection = event.selection) }
      SelectRecipientsEvent.AddClicked -> save()
    }
  }

  private suspend fun save() {
    val state = _state.value
    if (!state.canSave) {
      return
    }

    _state.update { it.copy(isSaving = true) }
    SelectRecipientsRepository.setAllowedMembers(profileId, state.allowedMembers)
    _state.update { it.copy(isSaving = false) }

    parentEventEmitter.navigateBack()
  }
}

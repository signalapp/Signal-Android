/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.self.featured

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlow
import org.signal.appsettings.badges.SelectFeaturedBadgeEvent
import org.signal.appsettings.badges.SelectFeaturedBadgeState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.network.NetworkResult
import org.thoughtcrime.securesms.badges.BadgeRepository
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Drives choosing which of the user's badges is featured on their profile, and saving that choice.
 */
class SelectFeaturedBadgeViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) : EventDrivenViewModel<SelectFeaturedBadgeEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(SelectFeaturedBadgeViewModel::class)
  }

  private val _state = MutableStateFlow(SelectFeaturedBadgeState())
  val state: StateFlow<SelectFeaturedBadgeState> = _state.asStateFlow()

  init {
    viewModelScope.launch {
      Recipient.observable(Recipient.self().id).asFlow().filterNot { it.isResolving }.collect { recipient ->
        _state.update {
          val unexpiredBadges = recipient.badges.filterNot { badge -> badge.isExpired() }
          it.copy(
            stage = if (it.stage == SelectFeaturedBadgeState.Stage.INIT) SelectFeaturedBadgeState.Stage.READY else it.stage,
            selectedBadge = unexpiredBadges.firstOrNull(),
            badges = unexpiredBadges
          )
        }
      }
    }
  }

  override suspend fun processEvent(event: SelectFeaturedBadgeEvent) {
    when (event) {
      SelectFeaturedBadgeEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
      is SelectFeaturedBadgeEvent.BadgeClicked -> _state.update { it.copy(selectedBadge = event.badge) }
      SelectFeaturedBadgeEvent.SaveClicked -> save()
      SelectFeaturedBadgeEvent.SnackbarDismissed -> _state.update { it.copy(snackbar = SelectFeaturedBadgeState.Snackbar.None) }
    }
  }

  private suspend fun save() {
    val badge = _state.value.selectedBadge
    if (!_state.value.canSave || badge == null) {
      return
    }

    _state.update { it.copy(stage = SelectFeaturedBadgeState.Stage.SAVING) }

    val result = BadgeRepository.setFeaturedBadge(badge)
    if (result is NetworkResult.Success) {
      parentEventEmitter.navigateBack()
    } else {
      Log.e(TAG, "Failed to update profile.", result.getCause())
      _state.update { it.copy(stage = SelectFeaturedBadgeState.Stage.READY, snackbar = SelectFeaturedBadgeState.Snackbar.FailedToUpdateProfile) }
    }
  }
}

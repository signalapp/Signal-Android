/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.self.overview

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.appsettings.badges.BadgesOverviewAction
import org.signal.appsettings.badges.BadgesOverviewEvent
import org.signal.appsettings.badges.BadgesOverviewState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.navigateTo
import org.signal.core.models.badges.Badge
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives the list of the user's badges and the settings for showing them on their profile.
 */
class BadgesOverviewViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) : EventDrivenViewModel<BadgesOverviewEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(BadgesOverviewViewModel::class)
  }

  private val _state = MutableStateFlow(BadgesOverviewState())
  private val _actions = Channel<BadgesOverviewAction>(Channel.BUFFERED)

  val state: StateFlow<BadgesOverviewState> = _state.asStateFlow()
  val actions: Flow<BadgesOverviewAction> = _actions.receiveAsFlow()

  init {
    viewModelScope.launch {
      BadgesOverviewRepository.observeSelf().collect { self ->
        _state.update {
          it.copy(
            stage = if (it.stage == BadgesOverviewState.Stage.INIT) BadgesOverviewState.Stage.READY else it.stage,
            badges = self.badges,
            featuredBadge = self.featuredBadge,
            displayBadgesOnProfile = BadgesOverviewRepository.getDisplayBadgesOnProfile(),
            hasUnexpiredBadges = self.badges.any { badge -> badge.expirationTimestamp > System.currentTimeMillis() }
          )
        }
      }
    }

    viewModelScope.launch {
      BadgesOverviewRepository.observeInternetConnection().collect { isConnected ->
        _state.update { it.copy(hasInternet = isConnected) }
      }
    }

    viewModelScope.launch {
      val fadedBadgeId = BadgesOverviewRepository.getFadedBadgeId()
      _state.update { it.copy(fadedBadgeId = fadedBadgeId) }
    }
  }

  override suspend fun processEvent(event: BadgesOverviewEvent) {
    when (event) {
      BadgesOverviewEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
      is BadgesOverviewEvent.BadgeClicked -> onBadgeClicked(event.badge)
      is BadgesOverviewEvent.DisplayBadgesOnProfileToggled -> setDisplayBadgesOnProfile(event.enabled)
      BadgesOverviewEvent.FeaturedBadgeClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.DonationsRoute.Featured)
      BadgesOverviewEvent.SnackbarDismissed -> _state.update { it.copy(snackbar = BadgesOverviewState.Snackbar.None) }
    }
  }

  private suspend fun onBadgeClicked(badge: Badge) {
    if (badge.isExpired() || badge.id == _state.value.fadedBadgeId) {
      _actions.send(BadgesOverviewAction.ShowExpiredBadge(badge))
    } else {
      _actions.send(BadgesOverviewAction.ShowBadge(badge))
    }
  }

  /** Saved off the event queue, so the user can still leave the screen while the service is slow to answer. */
  private fun setDisplayBadgesOnProfile(enabled: Boolean) {
    _state.update { it.copy(stage = BadgesOverviewState.Stage.UPDATING_BADGE_DISPLAY_STATE) }

    viewModelScope.launch {
      val saved = BadgesOverviewRepository.setDisplayBadgesOnProfile(enabled)
      _state.update {
        it.copy(
          stage = BadgesOverviewState.Stage.READY,
          snackbar = if (saved) it.snackbar else BadgesOverviewState.Snackbar.FailedToUpdateProfile
        )
      }
    }
  }
}

/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.rx3.asObservable
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.NotificationProfilesRepository
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.megaphone.Megaphone
import org.thoughtcrime.securesms.megaphone.Megaphones
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.stories.Stories

class MainNavigationViewModel(initialListLocation: MainNavigationListLocation = MainNavigationListLocation.CHATS) : ViewModel() {
  private val megaphoneRepository = AppDependencies.megaphoneRepository

  /**
   * A shared flow of detail location requests that the MainActivity will service.
   * This is immediately set back to empty after requesting a detail location to prevent duplicate launches.
   */
  private val detailLocationRequestFlow = MutableStateFlow<MainNavigationDetailLocation>(MainNavigationDetailLocation.Empty)
  val detailLocationRequests: StateFlow<MainNavigationDetailLocation> = detailLocationRequestFlow

  /**
   * The latest detail location that has been requested, for consumption by other components.
   */
  private val detailLocationFlow = MutableStateFlow<MainNavigationDetailLocation>(MainNavigationDetailLocation.Empty)
  val detailLocation: StateFlow<MainNavigationDetailLocation> = detailLocationFlow
  val detailLocationObservable: Observable<MainNavigationDetailLocation> = detailLocationFlow.asObservable()

  private val internalMegaphone = MutableStateFlow(Megaphone.NONE)
  val megaphone: StateFlow<Megaphone> = internalMegaphone

  private val internalSnackbar = MutableStateFlow<SnackbarState?>(null)
  val snackbar: StateFlow<SnackbarState?> = internalSnackbar

  private val internalNavigationEvents = MutableSharedFlow<NavigationEvent>()
  val navigationEvents: Flow<NavigationEvent> = internalNavigationEvents

  private val notificationProfilesRepository: NotificationProfilesRepository = NotificationProfilesRepository()

  private val internalMainNavigationState = MutableStateFlow(MainNavigationState(selectedDestination = initialListLocation))
  val mainNavigationState: StateFlow<MainNavigationState> = internalMainNavigationState

  /**
   * This is Rx because these are still accessed from Java.
   */
  private val internalTabClickEvents: MutableSharedFlow<MainNavigationListLocation> = MutableSharedFlow()
  val tabClickEvents: Observable<MainNavigationListLocation> = internalTabClickEvents.filter { Stories.isFeatureEnabled() }.asObservable()

  init {
    performStoreUpdate(MainNavigationRepository.getNumberOfUnreadMessages()) { unreadChats, state ->
      state.copy(chatsCount = unreadChats.toInt())
    }

    performStoreUpdate(MainNavigationRepository.getNumberOfUnseenCalls()) { unseenCalls, state ->
      state.copy(callsCount = unseenCalls.toInt())
    }

    performStoreUpdate(MainNavigationRepository.getNumberOfUnseenStories()) { unseenStories, state ->
      state.copy(storiesCount = unseenStories.toInt())
    }

    performStoreUpdate(MainNavigationRepository.getHasFailedOutgoingStories()) { hasFailedStories, state ->
      state.copy(storyFailure = hasFailedStories)
    }
  }

  fun goTo(location: MainNavigationDetailLocation) {
    viewModelScope.launch {
      detailLocationRequestFlow.emit(location)
      detailLocationFlow.emit(location)
    }
  }

  fun goToCameraFirstStoryCapture() {
    viewModelScope.launch {
      internalNavigationEvents.emit(NavigationEvent.STORY_CAMERA_FIRST)
    }
  }

  fun getNextMegaphone() {
    megaphoneRepository.getNextMegaphone { next ->
      internalMegaphone.update { next ?: Megaphone.NONE }
    }
  }

  fun setSnackbar(snackbarState: SnackbarState?) {
    internalSnackbar.update { snackbarState }
  }

  fun onMegaphoneSnoozed(event: Megaphones.Event) {
    megaphoneRepository.markSeen(event)
    internalMegaphone.update { Megaphone.NONE }
  }

  fun onMegaphoneCompleted(event: Megaphones.Event) {
    internalMegaphone.update { Megaphone.NONE }
    megaphoneRepository.markFinished(event)
  }

  fun onMegaphoneVisible(visible: Megaphone) {
    megaphoneRepository.markVisible(visible.event)
  }

  fun refreshNavigationBarState() {
    internalMainNavigationState.update { it.copy(compact = SignalStore.settings.useCompactNavigationBar, isStoriesFeatureEnabled = Stories.isFeatureEnabled()) }
  }

  fun getNotificationProfiles(): Flow<List<NotificationProfile>> {
    return notificationProfilesRepository.getProfiles().asFlow()
  }

  fun onChatsSelected() {
    internalTabClickEvents.tryEmit(MainNavigationListLocation.CHATS)
    internalMainNavigationState.update {
      it.copy(selectedDestination = MainNavigationListLocation.CHATS)
    }
  }

  fun onCallsSelected() {
    internalTabClickEvents.tryEmit(MainNavigationListLocation.CALLS)
    internalMainNavigationState.update {
      it.copy(selectedDestination = MainNavigationListLocation.CALLS)
    }
  }

  fun onStoriesSelected() {
    internalTabClickEvents.tryEmit(MainNavigationListLocation.STORIES)
    internalMainNavigationState.update {
      it.copy(selectedDestination = MainNavigationListLocation.STORIES)
    }
  }

  private fun <T : Any> performStoreUpdate(flow: Flow<T>, fn: (T, MainNavigationState) -> MainNavigationState) {
    viewModelScope.launch {
      flow.collectLatest { item ->
        internalMainNavigationState.update { state -> fn(item, state) }
      }
    }
  }

  enum class NavigationEvent {
    STORY_CAMERA_FIRST
  }
}

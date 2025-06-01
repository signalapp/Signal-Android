/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class MainNavigationViewModel(
  initialListLocation: MainNavigationListLocation = MainNavigationListLocation.CHATS,
  initialDetailLocation: MainNavigationDetailLocation = MainNavigationDetailLocation.Empty
) : ViewModel() {
  private val megaphoneRepository = AppDependencies.megaphoneRepository

  private var navigator: LegacyNavigator? = null

  /**
   * The latest detail location that has been requested, for consumption by other components.
   */
  private val internalDetailLocation = MutableStateFlow(initialDetailLocation)
  val detailLocation: StateFlow<MainNavigationDetailLocation> = internalDetailLocation
  val detailLocationObservable: Observable<MainNavigationDetailLocation> = internalDetailLocation.asObservable()

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
  val tabClickEvents: Observable<MainNavigationListLocation> = internalTabClickEvents.asObservable()

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

  /**
   * Sets the navigator on the view-model. This wraps the given navigator in our own delegating implementation
   * such that we can react to navigateTo/Back signals and maintain proper state for internalDetailLocation.
   */
  fun wrapNavigator(composeScope: CoroutineScope, threePaneScaffoldNavigator: ThreePaneScaffoldNavigator<Any>, goToLegacyDetailLocation: (MainNavigationDetailLocation) -> Unit): ThreePaneScaffoldNavigator<Any> {
    val previous = this.navigator
    val wrapped = LegacyNavigator(composeScope, threePaneScaffoldNavigator, goToLegacyDetailLocation)
    this.navigator = wrapped

    if (previous != null) {
      val destination = previous.currentDestination?.contentKey ?: return wrapped
      if (destination is MainNavigationListLocation) {
        goTo(destination)
      }
    } else {
      goTo(mainNavigationState.value.selectedDestination)
    }

    if (previous != null) {
      val destination = previous.currentDestination?.contentKey ?: return wrapped
      if (destination is MainNavigationDetailLocation) {
        goTo(destination)
      }
    } else {
      goTo(internalDetailLocation.value)
    }

    return wrapped
  }

  /**
   * Navigates to the requested location. If the navigator is not present, this functionally sets our
   * "default" location to that specified, and we will route the user there when the navigator is set.
   */
  fun goTo(location: MainNavigationDetailLocation) {
    if (navigator == null) {
      internalDetailLocation.update {
        location
      }
    }

    navigator?.composeScope?.launch {
      navigator?.navigateTo(ThreePaneScaffoldRole.Primary, location)
    }
  }

  fun goTo(location: MainNavigationListLocation) {
    internalMainNavigationState.update {
      it.copy(selectedDestination = location)
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
    onTabSelected(MainNavigationListLocation.CHATS)
  }

  fun onArchiveSelected() {
    onTabSelected(MainNavigationListLocation.ARCHIVE)
  }

  fun onCallsSelected() {
    onTabSelected(MainNavigationListLocation.CALLS)
  }

  fun onStoriesSelected() {
    onTabSelected(MainNavigationListLocation.STORIES)
  }

  private fun onTabSelected(destination: MainNavigationListLocation) {
    viewModelScope.launch {
      val currentTab = internalMainNavigationState.value.selectedDestination
      if (currentTab == destination) {
        internalTabClickEvents.emit(destination)
      } else {
        internalMainNavigationState.update {
          it.copy(selectedDestination = destination)
        }
      }
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

  /**
   * ScaffoldNavigator wrapper that delegates to a default implementation
   * Ensures we properly update `internalDetailLocation` as the user moves between
   * screens.
   *
   * Delegates to a legacy method if the user is not a large-screen-ui enabled user.
   */
  @Stable
  private inner class LegacyNavigator(
    val composeScope: CoroutineScope,
    private val delegate: ThreePaneScaffoldNavigator<Any>,
    private val goToLegacyDetailLocation: (MainNavigationDetailLocation) -> Unit
  ) : ThreePaneScaffoldNavigator<Any> by delegate {

    /**
     * Due to some weirdness with `navigateBack`, we don't seem to be able to execute
     * code after running the delegate method. So instead, we mark that we saw the call
     * and then handle updates in `seekBack`.
     */
    private var didNavigateBack: Boolean = false

    /**
     * If we're not a large screen user, this delegates to the legacy method.
     * Otherwise, we will delegate to the delegate, and update our detail location.
     */
    override suspend fun navigateTo(pane: ThreePaneScaffoldRole, contentKey: Any?) {
      if (!SignalStore.internal.largeScreenUi && contentKey is MainNavigationDetailLocation) {
        goToLegacyDetailLocation(contentKey)
      } else if (contentKey is MainNavigationDetailLocation.Conversation) {
        delegate.navigateTo(pane, contentKey)
      }

      if (SignalStore.internal.largeScreenUi && contentKey is MainNavigationDetailLocation) {
        internalDetailLocation.emit(contentKey)
      }
    }

    /**
     * Marks the back, and delegates to the delegate.
     */
    override suspend fun navigateBack(backNavigationBehavior: BackNavigationBehavior): Boolean {
      didNavigateBack = true
      return delegate.navigateBack(backNavigationBehavior)
    }

    /**
     * Delegates to the delegate, and then consumes the back. If back is consumed, we will update
     * the internal detail location.
     */
    override suspend fun seekBack(backNavigationBehavior: BackNavigationBehavior, fraction: Float) {
      delegate.seekBack(backNavigationBehavior, fraction)

      if (didNavigateBack) {
        didNavigateBack = false

        val destination = currentDestination?.contentKey as? MainNavigationDetailLocation ?: MainNavigationDetailLocation.Empty
        internalDetailLocation.emit(destination)
      }
    }
  }
}

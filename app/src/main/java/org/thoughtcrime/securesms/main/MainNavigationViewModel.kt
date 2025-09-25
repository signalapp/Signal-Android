/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
import org.thoughtcrime.securesms.window.WindowSizeClass

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class MainNavigationViewModel(
  initialListLocation: MainNavigationListLocation = MainNavigationListLocation.CHATS
) : ViewModel(), MainNavigationRouter {
  private val megaphoneRepository = AppDependencies.megaphoneRepository

  private var navigator: ThreePaneScaffoldNavigator<Any>? = null
  private var navigatorScope: CoroutineScope? = null
  private var goToLegacyDetailLocation: ((MainNavigationDetailLocation) -> Unit)? = null

  /**
   * The latest detail location that has been requested, for consumption by other components.
   */
  private val internalDetailLocation = MutableSharedFlow<MainNavigationDetailLocation>()
  val detailLocation: SharedFlow<MainNavigationDetailLocation> = internalDetailLocation
  val detailLocationObservable: Observable<MainNavigationDetailLocation> = internalDetailLocation.asObservable()

  private val internalMegaphone = MutableStateFlow(Megaphone.NONE)
  val megaphone: StateFlow<Megaphone> = internalMegaphone

  private val internalSnackbar = MutableStateFlow<SnackbarState?>(null)
  val snackbar: StateFlow<SnackbarState?> = internalSnackbar

  private val internalNavigationEvents = MutableSharedFlow<NavigationEvent>()
  val navigationEvents: Flow<NavigationEvent> = internalNavigationEvents

  private val notificationProfilesRepository: NotificationProfilesRepository = NotificationProfilesRepository()

  private val internalMainNavigationState = MutableStateFlow(MainNavigationState(currentListLocation = initialListLocation))
  val mainNavigationState: StateFlow<MainNavigationState> = internalMainNavigationState

  /**
   * This is Rx because these are still accessed from Java.
   */
  private val internalTabClickEvents: MutableSharedFlow<MainNavigationListLocation> = MutableSharedFlow()
  val tabClickEvents: Observable<MainNavigationListLocation> = internalTabClickEvents.asObservable()

  private var earlyNavigationListLocationRequested: MainNavigationListLocation? = null
  var earlyNavigationDetailLocationRequested: MainNavigationDetailLocation? = null
    private set

  private var earlyFocusedPaneRequested: ThreePaneScaffoldRole? = null

  /**
   * Which pane we display to the user at a given time should be driven solely by user intention. There are cases
   * where the user can change configurations (such as opening a foldable) and we will restore state and errantly
   * take them back into a PRIMARY pane. This boolean helps avoid these cases.
   */
  private var lockPaneToSecondary = false

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
    this.goToLegacyDetailLocation = goToLegacyDetailLocation
    this.navigatorScope = composeScope
    this.navigator = Nav(threePaneScaffoldNavigator)

    earlyNavigationListLocationRequested?.let {
      goTo(it)
    }

    earlyNavigationListLocationRequested = null

    earlyFocusedPaneRequested?.let {
      setFocusedPane(it)
    }

    earlyFocusedPaneRequested = null

    earlyNavigationDetailLocationRequested?.let {
      goTo(it)
    }

    return this.navigator!!
  }

  fun clearEarlyDetailLocation() {
    earlyNavigationDetailLocationRequested = null
  }

  fun setFocusedPane(role: ThreePaneScaffoldRole) {
    val roleToGoTo = if (lockPaneToSecondary) {
      ThreePaneScaffoldRole.Secondary
    } else {
      role
    }

    if (navigator == null) {
      earlyFocusedPaneRequested = roleToGoTo
      return
    }

    navigatorScope?.launch {
      navigator?.navigateTo(roleToGoTo)
    }
  }

  /**
   * Navigates to the requested location. If the navigator is not present, this functionally sets our
   * "default" location to that specified, and we will route the user there when the navigator is set.
   *
   * This does not update what panel is currently focused, so that we can perform actions (such as first
   * render) *before* swapping panes. This helps to prevent flashing / duplicate loads.
   */
  override fun goTo(location: MainNavigationDetailLocation) {
    lockPaneToSecondary = false

    if (!WindowSizeClass.isLargeScreenSupportEnabled()) {
      goToLegacyDetailLocation?.invoke(location)
      return
    }

    if (navigator == null) {
      earlyNavigationDetailLocationRequested = location
      return
    }

    viewModelScope.launch {
      internalDetailLocation.emit(location)
    }
  }

  override fun goTo(location: MainNavigationListLocation) {
    lockPaneToSecondary = true

    if (navigator == null) {
      earlyNavigationListLocationRequested = location
      return
    }

    internalMainNavigationState.update {
      it.copy(currentListLocation = location)
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
      val currentTab = internalMainNavigationState.value.currentListLocation
      if (currentTab == destination) {
        internalTabClickEvents.emit(destination)
      } else {
        setFocusedPane(ThreePaneScaffoldRole.Secondary)
        goTo(destination)
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
   * Ensures that when the user navigates back from the PRIMARY to SECONDARY pane, we lock our pane until they choose another primary
   * piece of content via [goTo].
   */
  private inner class Nav<T>(private val delegate: ThreePaneScaffoldNavigator<T>) : ThreePaneScaffoldNavigator<T> by delegate {
    override suspend fun seekBack(backNavigationBehavior: BackNavigationBehavior, fraction: Float) {
      delegate.seekBack(backNavigationBehavior, fraction)

      if (fraction == 0f) {
        lockPaneToSecondary = true
      }
    }
  }
}

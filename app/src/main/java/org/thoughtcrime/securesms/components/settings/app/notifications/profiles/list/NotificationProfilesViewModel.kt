package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.list

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [NotificationProfilesScreen].
 */
class NotificationProfilesViewModel : EventDrivenViewModel<NotificationProfilesEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(NotificationProfilesViewModel::class)
  }

  private val _state = MutableStateFlow(NotificationProfilesState())
  private val _actions = Channel<NotificationProfilesAction>(Channel.BUFFERED)

  val state: StateFlow<NotificationProfilesState> = _state.asStateFlow()
  val actions: Flow<NotificationProfilesAction> = _actions.receiveAsFlow()

  init {
    viewModelScope.launch {
      NotificationProfilesListRepository.observeProfiles().collect { profileList ->
        _state.update {
          it.copy(profiles = profileList.profiles, activeProfileId = profileList.activeProfileId, activeStatus = profileList.activeStatus)
        }
      }
    }
  }

  override suspend fun processEvent(event: NotificationProfilesEvent) {
    when (event) {
      NotificationProfilesEvent.NavigateBackClicked -> _actions.send(NotificationProfilesAction.NavigateBack)
      NotificationProfilesEvent.NewProfileClicked -> _actions.send(NotificationProfilesAction.NavigateToNewProfile)
      is NotificationProfilesEvent.ProfileClicked -> _actions.send(NotificationProfilesAction.NavigateToProfileDetails(event.profileId))
    }
  }
}

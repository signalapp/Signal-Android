package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.list

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.navigateTo
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [NotificationProfilesScreen].
 */
class NotificationProfilesViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) : EventDrivenViewModel<NotificationProfilesEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(NotificationProfilesViewModel::class)
  }

  private val _state = MutableStateFlow(NotificationProfilesState())

  val state: StateFlow<NotificationProfilesState> = _state.asStateFlow()

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
      NotificationProfilesEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
      NotificationProfilesEvent.NewProfileClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.NotificationsRoute.EditProfile())
      is NotificationProfilesEvent.ProfileClicked -> parentEventEmitter.navigateTo(AppSettingsRoute.NotificationsRoute.ProfileDetails(event.profileId))
    }
  }
}

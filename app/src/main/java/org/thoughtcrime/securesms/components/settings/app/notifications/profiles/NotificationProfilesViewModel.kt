package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.rx3.asFlow

class NotificationProfilesViewModel(private val repository: NotificationProfilesRepository) : ViewModel() {

  val state: Flow<NotificationProfilesState> = repository.getProfiles()
    .asFlow()
    .map { profiles -> NotificationProfilesState(profiles = profiles) }

  class Factory() : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(NotificationProfilesViewModel(NotificationProfilesRepository()))!!
    }
  }
}

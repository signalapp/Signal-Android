package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import org.thoughtcrime.securesms.database.NotificationProfileTables
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile

class EditNotificationProfileViewModel(private val profileId: Long, private val repository: NotificationProfilesRepository) : ViewModel() {

  private val createMode: Boolean = profileId == -1L
  private var selectedEmoji: String = ""

  fun getInitialState(): Single<InitialState> {
    val initialState = if (createMode) {
      Single.just(InitialState(createMode))
    } else {
      repository.getProfile(profileId)
        .take(1)
        .map { InitialState(createMode, it.name, it.emoji) }
        .singleOrError()
    }

    return initialState.observeOn(AndroidSchedulers.mainThread())
  }

  fun onEmojiSelected(emoji: String) {
    selectedEmoji = emoji
  }

  fun save(name: String): Single<SaveNotificationProfileResult> {
    val save = if (createMode) repository.createProfile(name, selectedEmoji) else repository.updateProfile(profileId, name, selectedEmoji)

    return save.map { r ->
      when (r) {
        is NotificationProfileTables.NotificationProfileChangeResult.Success -> SaveNotificationProfileResult.Success(r.notificationProfile, createMode)
        NotificationProfileTables.NotificationProfileChangeResult.DuplicateName -> SaveNotificationProfileResult.DuplicateNameFailure
      }
    }.observeOn(AndroidSchedulers.mainThread())
  }

  class Factory(private val profileId: Long) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(EditNotificationProfileViewModel(profileId, NotificationProfilesRepository()))!!
    }
  }

  data class InitialState(
    val createMode: Boolean,
    val name: String = "",
    val emoji: String = ""
  )

  sealed class SaveNotificationProfileResult {
    data class Success(val profile: NotificationProfile, val createMode: Boolean) : SaveNotificationProfileResult()
    object DuplicateNameFailure : SaveNotificationProfileResult()
  }
}

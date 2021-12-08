package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import org.thoughtcrime.securesms.database.NotificationProfileDatabase
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

class NotificationProfileDetailsViewModel(private val profileId: Long, private val repository: NotificationProfilesRepository) : ViewModel() {

  fun getProfile(): Observable<State> {
    return repository.getProfiles()
      .map { profiles ->
        val profile = profiles.firstOrNull { it.id == profileId }
        if (profile == null) {
          State.Invalid
        } else {
          State.Valid(
            profile = profile,
            recipients = profile.allowedMembers.map { Recipient.resolved(it) },
            isOn = NotificationProfiles.getActiveProfile(profiles) == profile
          )
        }
      }
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun addMember(id: RecipientId): Single<NotificationProfile> {
    return repository.addMember(profileId, id)
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun removeMember(id: RecipientId): Single<Recipient> {
    return repository.removeMember(profileId, id)
      .map { Recipient.resolved(id) }
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun deleteProfile(): Completable {
    return repository.deleteProfile(profileId)
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun toggleEnabled(profile: NotificationProfile): Completable {
    return repository.manuallyToggleProfile(profile)
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun toggleAllowAllMentions(): Single<NotificationProfile> {
    return repository.getProfile(profileId)
      .take(1)
      .singleOrError()
      .flatMap { repository.updateProfile(it.copy(allowAllMentions = !it.allowAllMentions)) }
      .map { (it as NotificationProfileDatabase.NotificationProfileChangeResult.Success).notificationProfile }
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun toggleAllowAllCalls(): Single<NotificationProfile> {
    return repository.getProfile(profileId)
      .take(1)
      .singleOrError()
      .flatMap { repository.updateProfile(it.copy(allowAllCalls = !it.allowAllCalls)) }
      .map { (it as NotificationProfileDatabase.NotificationProfileChangeResult.Success).notificationProfile }
      .observeOn(AndroidSchedulers.mainThread())
  }

  sealed class State {
    data class Valid(
      val profile: NotificationProfile,
      val recipients: List<Recipient>,
      val isOn: Boolean
    ) : State()
    object Invalid : State()
  }

  class Factory(private val profileId: Long) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(NotificationProfileDetailsViewModel(profileId, NotificationProfilesRepository()))!!
    }
  }
}

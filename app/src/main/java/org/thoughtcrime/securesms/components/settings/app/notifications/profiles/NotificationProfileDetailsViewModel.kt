package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.thoughtcrime.securesms.database.NotificationProfileDatabase
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.livedata.Store

class NotificationProfileDetailsViewModel(private val profileId: Long, private val repository: NotificationProfilesRepository) : ViewModel() {

  private val store = Store<State>(State.NotLoaded)
  private val disposables = CompositeDisposable()

  val state: LiveData<State> = store.stateLiveData

  init {
    disposables += repository.getProfiles()
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
      .subscribeBy(
        onNext = { newState ->
          when (newState) {
            State.NotLoaded -> Unit
            State.Invalid -> store.update { newState }
            is State.Valid -> updateWithValidState(newState)
          }
        }
      )
  }

  private fun updateWithValidState(newState: State.Valid) {
    store.update { oldState: State ->
      if (oldState is State.Valid) {
        oldState.copy(profile = newState.profile, recipients = newState.recipients, isOn = newState.isOn)
      } else {
        newState
      }
    }
  }

  override fun onCleared() {
    disposables.clear()
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

  fun showAllMembers() {
    store.update { s ->
      if (s is State.Valid) {
        s.copy(expanded = true)
      } else {
        s
      }
    }
  }

  sealed class State {
    data class Valid(
      val profile: NotificationProfile,
      val recipients: List<Recipient>,
      val isOn: Boolean,
      val expanded: Boolean = false
    ) : State()
    object Invalid : State()
    object NotLoaded : State()
  }

  class Factory(private val profileId: Long) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(NotificationProfileDetailsViewModel(profileId, NotificationProfilesRepository()))!!
    }
  }
}

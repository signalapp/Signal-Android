package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import org.thoughtcrime.securesms.recipients.Recipient

class NotificationProfileDetailsViewModel(private val profileId: Long, private val repository: NotificationProfilesRepository) : ViewModel() {

  private val internalState = MutableStateFlow<State>(State.NotLoaded)
  private val disposables = CompositeDisposable()

  val state: StateFlow<State> = internalState

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
            State.Invalid -> internalState.update { newState }
            is State.Valid -> updateWithValidState(newState)
          }
        }
      )
  }

  private fun updateWithValidState(newState: State.Valid) {
    internalState.update { oldState: State ->
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
    return repository.toggleAllowAllMentions(profileId)
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun toggleAllowAllCalls(): Single<NotificationProfile> {
    return repository.toggleAllowAllCalls(profileId)
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun showAllMembers() {
    internalState.update { s ->
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

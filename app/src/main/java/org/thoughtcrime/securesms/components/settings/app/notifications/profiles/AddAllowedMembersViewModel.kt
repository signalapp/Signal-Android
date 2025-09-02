package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

class AddAllowedMembersViewModel(private val profileId: Long, private val repository: NotificationProfilesRepository) : ViewModel() {

  private val internalSnackbarRequests = MutableSharedFlow<Unit>()

  val snackbarRequests: Flow<Unit> = internalSnackbarRequests

  fun requestSnackbar() {
    viewModelScope.launch {
      internalSnackbarRequests.emit(Unit)
    }
  }

  fun getProfile(): Observable<NotificationProfileAndRecipients> {
    return repository.getProfile(profileId)
      .map { profile ->
        NotificationProfileAndRecipients(profile, profile.allowedMembers.map { Recipient.resolved(it) })
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

  fun toggleAllowAllMentions(): Single<NotificationProfile> {
    return repository.toggleAllowAllMentions(profileId)
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun toggleAllowAllCalls(): Single<NotificationProfile> {
    return repository.toggleAllowAllCalls(profileId)
      .observeOn(AndroidSchedulers.mainThread())
  }

  @Immutable
  data class NotificationProfileAndRecipients(val profile: NotificationProfile, val recipients: List<Recipient>)

  class Factory(private val profileId: Long) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(AddAllowedMembersViewModel(profileId, NotificationProfilesRepository()))!!
    }
  }
}

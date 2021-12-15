package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.recipients.RecipientId

class SelectRecipientsViewModel(
  private val profileId: Long,
  currentSelection: Set<RecipientId>,
  private val repository: NotificationProfilesRepository
) : ViewModel() {

  val recipients: MutableSet<RecipientId> = currentSelection.toMutableSet()

  fun select(recipientId: RecipientId) {
    recipients += recipientId
  }

  fun deselect(recipientId: RecipientId) {
    recipients.remove(recipientId)
  }

  fun updateAllowedMembers(): Single<NotificationProfile> {
    return repository.updateAllowedMembers(profileId, recipients)
      .observeOn(AndroidSchedulers.mainThread())
  }

  class Factory(private val profileId: Long, val currentSelection: Set<RecipientId>) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(SelectRecipientsViewModel(profileId, currentSelection, NotificationProfilesRepository()))!!
    }
  }
}

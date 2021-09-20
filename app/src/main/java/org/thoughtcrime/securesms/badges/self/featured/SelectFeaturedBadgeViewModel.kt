package org.thoughtcrime.securesms.badges.self.featured

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.badges.BadgeRepository
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.livedata.Store

class SelectFeaturedBadgeViewModel(repository: BadgeRepository) : ViewModel() {

  private val store = Store(SelectFeaturedBadgeState())

  val state: LiveData<SelectFeaturedBadgeState> = store.stateLiveData

  private val disposables = CompositeDisposable()

  init {
    store.update(Recipient.live(Recipient.self().id).liveDataResolved) { recipient, state ->
      state.copy(selectedBadge = recipient.badges.firstOrNull(), allUnlockedBadges = recipient.badges)
    }
  }

  fun setSelectedBadge(badge: Badge) {
    store.update { it.copy(selectedBadge = badge) }
  }

  fun save() {
    // TODO "Persist selection to database"
  }

  override fun onCleared() {
    disposables.clear()
  }

  class Factory(private val badgeRepository: BadgeRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(SelectFeaturedBadgeViewModel(badgeRepository)))
    }
  }
}

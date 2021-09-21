package org.thoughtcrime.securesms.badges.self.overview

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.subjects.PublishSubject
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.badges.BadgeRepository
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.livedata.Store

private val TAG = Log.tag(BadgesOverviewViewModel::class.java)

class BadgesOverviewViewModel(private val badgeRepository: BadgeRepository) : ViewModel() {
  private val store = Store(BadgesOverviewState())
  private val eventSubject = PublishSubject.create<BadgesOverviewEvent>()

  val state: LiveData<BadgesOverviewState> = store.stateLiveData
  val events: Observable<BadgesOverviewEvent> = eventSubject.observeOn(AndroidSchedulers.mainThread())

  val disposables = CompositeDisposable()

  init {
    store.update(Recipient.live(Recipient.self().id).liveDataResolved) { recipient, state ->
      state.copy(
        stage = if (state.stage == BadgesOverviewState.Stage.INIT) BadgesOverviewState.Stage.READY else state.stage,
        allUnlockedBadges = recipient.badges,
        displayBadgesOnProfile = recipient.badges.firstOrNull()?.visible == true
      )
    }
  }

  fun setDisplayBadgesOnProfile(displayBadgesOnProfile: Boolean) {
    disposables += badgeRepository.setVisibilityForAllBadges(displayBadgesOnProfile)
      .subscribe(
        {
          store.update { it.copy(stage = BadgesOverviewState.Stage.READY) }
        },
        { error ->
          Log.e(TAG, "Failed to update visibility.", error)
          store.update { it.copy(stage = BadgesOverviewState.Stage.READY) }
          eventSubject.onNext(BadgesOverviewEvent.FAILED_TO_UPDATE_PROFILE)
        }
      )
  }

  override fun onCleared() {
    disposables.clear()
  }

  class Factory(private val badgeRepository: BadgeRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(BadgesOverviewViewModel(badgeRepository)))
    }
  }
}

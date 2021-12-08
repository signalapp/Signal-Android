package org.thoughtcrime.securesms.components.settings.app.notifications.manual

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.NotificationProfilesRepository
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.util.isBetween
import org.thoughtcrime.securesms.util.livedata.Store
import org.thoughtcrime.securesms.util.toMillis
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class NotificationProfileSelectionViewModel(private val repository: NotificationProfilesRepository) : ViewModel() {

  private val store = Store(NotificationProfileSelectionState(timeSlotB = getTimeSlotB()))

  val state: LiveData<NotificationProfileSelectionState> = store.stateLiveData

  val disposables = CompositeDisposable()

  init {
    disposables += repository.getProfiles().subscribeBy(onNext = { profiles -> store.update { it.copy(notificationProfiles = profiles) } })

    disposables += Observable
      .interval(0, 1, TimeUnit.MINUTES)
      .map { getTimeSlotB() }
      .distinctUntilChanged()
      .subscribe { calendar ->
        store.update { it.copy(timeSlotB = calendar) }
      }
  }

  override fun onCleared() {
    disposables.clear()
  }

  fun setExpanded(notificationProfile: NotificationProfile) {
    store.update {
      it.copy(expandedId = if (it.expandedId == notificationProfile.id) -1L else notificationProfile.id)
    }
  }

  fun toggleEnabled(profile: NotificationProfile) {
    disposables += repository.manuallyToggleProfile(profile)
      .subscribe()
  }

  fun enableForOneHour(profile: NotificationProfile) {
    disposables += repository.manuallyEnableProfileForDuration(profile.id, System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1))
      .subscribe()
  }

  fun enableUntil(profile: NotificationProfile, enableUntil: LocalDateTime) {
    disposables += repository.manuallyEnableProfileForDuration(profile.id, enableUntil.toMillis())
      .subscribe()
  }

  companion object {
    @Suppress("CascadeIf")
    private fun getTimeSlotB(): LocalDateTime {
      val now = LocalDateTime.now()
      val sixPm = now.withHour(18).withMinute(0).withSecond(0)
      val eightAm = now.withHour(8).withMinute(0).withSecond(0)

      return if (now.isBetween(eightAm, sixPm)) {
        sixPm
      } else if (now.isBefore(eightAm)) {
        eightAm
      } else {
        eightAm.plusDays(1)
      }
    }
  }

  class Factory(private val notificationProfilesRepository: NotificationProfilesRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(NotificationProfileSelectionViewModel(notificationProfilesRepository))!!
    }
  }
}

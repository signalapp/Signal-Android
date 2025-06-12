package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import java.time.DayOfWeek

/**
 * ViewModel for driving edit schedule UI. UI starts in a disabled state until the first schedule is loaded
 * from the database and into the [scheduleSubject] allowing the safe use of !! with [schedule].
 */
class EditNotificationProfileScheduleViewModel(
  private val profileId: Long,
  private val createMode: Boolean,
  private val repository: NotificationProfilesRepository
) : ViewModel() {

  private val disposables = CompositeDisposable()
  private val scheduleSubject: BehaviorSubject<NotificationProfileSchedule> = BehaviorSubject.create()
  private val schedule: NotificationProfileSchedule
    get() = scheduleSubject.value!!

  init {
    disposables += repository.getProfile(profileId)
      .take(1)
      .map { it.schedule }
      .singleOrError()
      .subscribeBy(onSuccess = { scheduleSubject.onNext(it) })
  }

  override fun onCleared() {
    super.onCleared()
    disposables.dispose()
  }

  fun schedule(): Observable<NotificationProfileSchedule> {
    return scheduleSubject.subscribeOn(AndroidSchedulers.mainThread())
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun toggleDay(day: DayOfWeek) {
    val newDaysEnabled = schedule.daysEnabled.toMutableSet()
    if (newDaysEnabled.contains(day)) {
      newDaysEnabled.remove(day)
    } else {
      newDaysEnabled += day
    }
    scheduleSubject.onNext(schedule.copy(daysEnabled = newDaysEnabled))
  }

  fun setStartTime(hour: Int, minute: Int) {
    scheduleSubject.onNext(schedule.copy(start = hour * 100 + minute))
  }

  fun setEndTime(hour: Int, minute: Int) {
    scheduleSubject.onNext(schedule.copy(end = hour * 100 + minute))
  }

  fun setEnabled(enabled: Boolean) {
    scheduleSubject.onNext(schedule.copy(enabled = enabled))
  }

  fun save(createMode: Boolean): Single<SaveScheduleResult> {
    val result: Single<SaveScheduleResult> = if (schedule.enabled && schedule.daysEnabled.isEmpty()) {
      Single.just(SaveScheduleResult.NoDaysSelected)
    } else if (createMode && !schedule.enabled) {
      Single.just(SaveScheduleResult.Success)
    } else {
      repository.updateSchedule(schedule)
        .toSingleDefault(SaveScheduleResult.Success)
        .flatMap { r ->
          repository.scheduleNotificationProfileSync(profileId)
          if (schedule.enabled && schedule.coversTime(System.currentTimeMillis())) {
            repository.manuallyEnableProfileForSchedule(profileId, schedule)
              .toSingleDefault(r)
          } else {
            Single.just(r)
          }
        }
    }
    return result.observeOn(AndroidSchedulers.mainThread())
  }

  class Factory(private val profileId: Long, private val createMode: Boolean) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(EditNotificationProfileScheduleViewModel(profileId, createMode, NotificationProfilesRepository()))!!
    }
  }

  enum class SaveScheduleResult {
    NoDaysSelected,
    Success
  }
}

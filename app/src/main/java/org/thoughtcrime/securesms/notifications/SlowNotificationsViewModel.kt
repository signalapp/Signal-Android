/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.notifications

import android.os.Build
import androidx.annotation.WorkerThread
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.thoughtcrime.securesms.notifications.SlowNotificationHeuristics.isHavingDelayedNotifications
import org.thoughtcrime.securesms.notifications.SlowNotificationHeuristics.isPotentiallyCausedByBatteryOptimizations
import org.thoughtcrime.securesms.notifications.SlowNotificationHeuristics.shouldPromptBatterySaver
import org.thoughtcrime.securesms.notifications.SlowNotificationHeuristics.shouldPromptUserForLogs
import java.util.concurrent.TimeUnit

/**
 * View model for checking for slow notifications and if we should prompt the user with help or for information.
 */
class SlowNotificationsViewModel : ViewModel() {

  private val checkSubject = BehaviorSubject.create<Unit>()

  val slowNotificationState: Observable<State>

  init {
    slowNotificationState = checkSubject
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.io())
      .throttleFirst(1, TimeUnit.MINUTES)
      .switchMapSingle {
        checkHeuristics()
      }
      .distinctUntilChanged()
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun checkSlowNotificationHeuristics() {
    checkSubject.onNext(Unit)
  }

  @WorkerThread
  private fun checkHeuristics(): Single<State> {
    return Single.fromCallable {
      var state = State.NONE
      if (isHavingDelayedNotifications()) {
        if (isPotentiallyCausedByBatteryOptimizations() && Build.VERSION.SDK_INT >= 23) {
          if (shouldPromptBatterySaver()) {
            state = State.PROMPT_BATTERY_SAVER_DIALOG
          }
        } else if (shouldPromptUserForLogs()) {
          state = State.PROMPT_DEBUGLOGS
        }
      }

      return@fromCallable state
    }.subscribeOn(Schedulers.io())
  }

  enum class State {
    NONE,
    PROMPT_BATTERY_SAVER_DIALOG,
    PROMPT_DEBUGLOGS
  }
}

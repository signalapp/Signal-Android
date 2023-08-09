/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.SingleSubject
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogRepository

class PromptLogsViewModel : ViewModel() {

  private val submitDebugLogRepository = SubmitDebugLogRepository()

  fun submitLogs(): Single<String> {
    val singleSubject = SingleSubject.create<String?>()
    submitDebugLogRepository.buildAndSubmitLog { result ->
      if (result.isPresent) {
        singleSubject.onSuccess(result.get())
      } else {
        singleSubject.onError(Throwable())
      }
    }

    return singleSubject.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
  }
}

/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.SingleSubject
import org.thoughtcrime.securesms.crash.CrashConfig
import org.thoughtcrime.securesms.database.LogDatabase
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogRepository

class PromptLogsViewModel(private val context: Application, private val purpose: DebugLogsPromptDialogFragment.Purpose) : AndroidViewModel(context) {

  private val submitDebugLogRepository = SubmitDebugLogRepository()

  private val disposables = CompositeDisposable()

  fun onVisible() {
    if (purpose == DebugLogsPromptDialogFragment.Purpose.CRASH) {
      disposables += Single
        .fromCallable {
          LogDatabase.getInstance(context).crashes.markAsPrompted(CrashConfig.patterns, System.currentTimeMillis())
        }
        .subscribeOn(Schedulers.io())
        .subscribe()
    }
  }

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

  override fun onCleared() {
    disposables.clear()
  }

  class Factory(private val context: Application, private val purpose: DebugLogsPromptDialogFragment.Purpose) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(PromptLogsViewModel(context, purpose))!!
    }
  }
}

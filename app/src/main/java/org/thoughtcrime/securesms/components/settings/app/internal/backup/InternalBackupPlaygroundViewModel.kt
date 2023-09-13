/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.backup

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.backup.v2.BackupRepository

class InternalBackupPlaygroundViewModel : ViewModel() {

  var backupData: ByteArray? = null

  val disposables = CompositeDisposable()

  private val _state: MutableState<ScreenState> = mutableStateOf(ScreenState(backupState = BackupState.NONE))
  val state: State<ScreenState> = _state

  fun export() {
    _state.value = _state.value.copy(backupState = BackupState.EXPORT_IN_PROGRESS)

    disposables += Single.fromCallable { BackupRepository.export() }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { data ->
        backupData = data
        _state.value = _state.value.copy(backupState = BackupState.EXPORT_DONE)
      }
  }

  fun import() {
    backupData?.let {
      _state.value = _state.value.copy(backupState = BackupState.IMPORT_IN_PROGRESS)

      disposables += Single.fromCallable { BackupRepository.import(it) }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { nothing ->
          backupData = null
          _state.value = _state.value.copy(backupState = BackupState.NONE)
        }
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  data class ScreenState(
    val backupState: BackupState
  )

  enum class BackupState(val inProgress: Boolean = false) {
    NONE, EXPORT_IN_PROGRESS(true), EXPORT_DONE, IMPORT_IN_PROGRESS(true)
  }
}

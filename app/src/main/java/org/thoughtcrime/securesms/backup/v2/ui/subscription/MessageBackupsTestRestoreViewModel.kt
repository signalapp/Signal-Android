/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.BackupRestoreJob
import org.thoughtcrime.securesms.jobs.BackupRestoreMediaJob
import org.thoughtcrime.securesms.jobs.SyncArchivedMediaJob
import org.thoughtcrime.securesms.recipients.Recipient
import java.io.InputStream
import kotlin.time.Duration.Companion.seconds

class MessageBackupsTestRestoreViewModel : ViewModel() {
  val disposables = CompositeDisposable()

  private val _state: MutableState<ScreenState> = mutableStateOf(ScreenState(importState = ImportState.NONE, plaintext = false))
  val state: State<ScreenState> = _state

  fun import(length: Long, inputStreamFactory: () -> InputStream) {
    _state.value = _state.value.copy(importState = ImportState.IN_PROGRESS)

    val self = Recipient.self()
    val selfData = BackupRepository.SelfData(self.aci.get(), self.pni.get(), self.e164.get(), ProfileKey(self.profileKey))

    disposables += Single.fromCallable { BackupRepository.import(length, inputStreamFactory, selfData, plaintext = _state.value.plaintext) }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy {
        _state.value = _state.value.copy(importState = ImportState.NONE)
      }
  }

  fun restore() {
    _state.value = _state.value.copy(importState = ImportState.IN_PROGRESS)
    disposables += Single.fromCallable {
      AppDependencies
        .jobManager
        .startChain(BackupRestoreJob())
        .then(SyncArchivedMediaJob())
        .then(BackupRestoreMediaJob())
        .enqueueAndBlockUntilCompletion(120.seconds.inWholeMilliseconds)
    }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy {
        _state.value = _state.value.copy(importState = ImportState.RESTORED)
      }
  }

  fun onPlaintextToggled() {
    _state.value = _state.value.copy(plaintext = !_state.value.plaintext)
  }

  override fun onCleared() {
    disposables.clear()
  }

  data class ScreenState(
    val importState: ImportState,
    val plaintext: Boolean
  )

  enum class ImportState(val inProgress: Boolean = false) {
    NONE,
    IN_PROGRESS(true),
    RESTORED
  }
}

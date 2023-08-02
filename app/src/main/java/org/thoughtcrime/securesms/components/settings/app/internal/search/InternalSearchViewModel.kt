/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.search

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.RecipientRecord
import java.util.concurrent.TimeUnit

class InternalSearchViewModel : ViewModel() {

  private val _results: MutableState<ImmutableList<InternalSearchResult>> = mutableStateOf(persistentListOf())
  val results: State<ImmutableList<InternalSearchResult>> = _results

  private val _query: MutableState<String> = mutableStateOf("")
  val query: State<String> = _query

  private val disposable: CompositeDisposable = CompositeDisposable()

  private val querySubject: BehaviorSubject<String> = BehaviorSubject.create()

  init {
    disposable += querySubject
      .distinctUntilChanged()
      .debounce(250, TimeUnit.MILLISECONDS, Schedulers.io())
      .observeOn(Schedulers.io())
      .map { query ->
        SignalDatabase.recipients.queryByInternalFields(query)
          .map { record ->
            InternalSearchResult(
              id = record.id,
              name = record.displayName(),
              aci = record.aci?.toString(),
              pni = record.pni.toString(),
              groupId = record.groupId
            )
          }
          .toImmutableList()
      }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { results ->
        _results.value = results
      }
  }

  fun onQueryChanged(value: String) {
    _query.value = value
    querySubject.onNext(value)
  }

  override fun onCleared() {
    disposable.clear()
  }

  private fun RecipientRecord.displayName(): String {
    return when {
      this.recipientType == RecipientTable.RecipientType.GV1 -> "GV1::${this.groupId}"
      this.recipientType == RecipientTable.RecipientType.GV2 -> "GV2::${this.groupId}"
      this.recipientType == RecipientTable.RecipientType.MMS -> "MMS_GROUP::${this.groupId}"
      this.recipientType == RecipientTable.RecipientType.DISTRIBUTION_LIST -> "DLIST::${this.distributionListId}"
      this.systemDisplayName?.isNotBlank() == true -> this.systemDisplayName
      this.signalProfileName.toString().isNotBlank() -> this.signalProfileName.serialize()
      this.e164 != null -> this.e164
      else -> "Unknown"
    }
  }
}

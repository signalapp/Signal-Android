/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui.about

import androidx.compose.runtime.IntState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import java.util.Optional

class AboutSheetViewModel(
  recipientId: RecipientId,
  repository: AboutSheetRepository = AboutSheetRepository()
) : ViewModel() {

  private val _recipient: MutableState<Optional<Recipient>> = mutableStateOf(Optional.empty())
  val recipient: State<Optional<Recipient>> = _recipient

  private val _groupsInCommonCount: MutableIntState = mutableIntStateOf(0)
  val groupsInCommonCount: IntState = _groupsInCommonCount

  private val _verified: MutableState<Boolean> = mutableStateOf(false)
  val verified: State<Boolean> = _verified

  private val recipientDisposable: Disposable = Recipient
    .observable(recipientId)
    .observeOn(AndroidSchedulers.mainThread())
    .subscribeBy {
      _recipient.value = Optional.of(it)
    }

  private val groupsInCommonDisposable: Disposable = repository
    .getGroupsInCommonCount(recipientId)
    .observeOn(AndroidSchedulers.mainThread())
    .subscribeBy {
      _groupsInCommonCount.intValue = it
    }

  private val verifiedDisposable: Disposable = repository
    .getVerified(recipientId)
    .observeOn(AndroidSchedulers.mainThread())
    .subscribeBy {
      _verified.value = it
    }

  override fun onCleared() {
    recipientDisposable.dispose()
    groupsInCommonDisposable.dispose()
  }
}

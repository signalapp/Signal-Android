/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.requests

import android.content.Context
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.rx.RxStore

class CallLinkIncomingRequestViewModel(
  private val recipientId: RecipientId
) : ViewModel() {

  private val repository = CallLinkIncomingRequestRepository()
  private val store = RxStore(CallLinkIncomingRequestState())
  private val disposables = CompositeDisposable().apply {
    add(store)
  }

  override fun onCleared() {
    disposables.dispose()
  }

  fun observeState(context: Context): Flowable<CallLinkIncomingRequestState> {
    disposables += store.update(Recipient.observable(recipientId).toFlowable(BackpressureStrategy.LATEST)) { r, s ->
      s.copy(
        recipient = r,
        name = r.getShortDisplayName(context),
        subtitle = r.e164.orElse(""),
        isSystemContact = r.isSystemContact
      )
    }

    disposables += store.update(repository.getGroupsInCommon(recipientId).toFlowable(BackpressureStrategy.LATEST)) { g, s ->
      s.copy(groupsInCommon = g.toDisplayText(context))
    }

    return store.stateFlowable
  }
}

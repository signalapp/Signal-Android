/**
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.links.details

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.ringrtc.CallLinkState
import org.thoughtcrime.securesms.calls.links.CallLinks
import org.thoughtcrime.securesms.calls.links.UpdateCallLinkRepository
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.service.webrtc.links.UpdateCallLinkResult

class CallLinkDetailsViewModel(
  private val callLinkRoomId: CallLinkRoomId,
  private val repository: CallLinkDetailsRepository = CallLinkDetailsRepository(),
  private val mutationRepository: UpdateCallLinkRepository = UpdateCallLinkRepository()
) : ViewModel() {
  private val disposables = CompositeDisposable()

  private val _state: MutableState<CallLinkDetailsState> = mutableStateOf(CallLinkDetailsState())
  val state: State<CallLinkDetailsState> = _state
  val nameSnapshot: String
    get() = state.value.callLink?.state?.name ?: error("Call link not loaded yet.")

  val rootKeySnapshot: ByteArray
    get() = state.value.callLink?.credentials?.linkKeyBytes ?: error("Call link not loaded yet.")

  init {
    disposables += repository.refreshCallLinkState(callLinkRoomId)
    disposables += CallLinks.watchCallLink(callLinkRoomId).subscribeBy {
      _state.value = CallLinkDetailsState(
        callLink = it
      )
    }
  }

  override fun onCleared() {
    super.onCleared()
    disposables.dispose()
  }

  fun setApproveAllMembers(approveAllMembers: Boolean): Single<UpdateCallLinkResult> {
    val credentials = _state.value.callLink?.credentials ?: error("User cannot change the name of this call.")
    return mutationRepository.setCallRestrictions(credentials, if (approveAllMembers) CallLinkState.Restrictions.ADMIN_APPROVAL else CallLinkState.Restrictions.NONE)
  }

  fun setName(name: String): Single<UpdateCallLinkResult> {
    val credentials = _state.value.callLink?.credentials ?: error("User cannot change the name of this call.")
    return mutationRepository.setCallName(credentials, name)
  }

  fun revoke(): Single<UpdateCallLinkResult> {
    val credentials = _state.value.callLink?.credentials ?: error("User cannot change the name of this call.")
    return mutationRepository.revokeCallLink(credentials)
  }
}

/**
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.links.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.util.logging.Log
import org.signal.ringrtc.CallLinkState
import org.thoughtcrime.securesms.calls.links.CallLinks
import org.thoughtcrime.securesms.calls.links.UpdateCallLinkRepository
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.service.webrtc.links.UpdateCallLinkResult
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class CallLinkDetailsViewModel(
  callLinkRoomId: CallLinkRoomId,
  repository: CallLinkDetailsRepository = CallLinkDetailsRepository(),
  private val mutationRepository: UpdateCallLinkRepository = UpdateCallLinkRepository()
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(CallLinkDetailsViewModel::class)
  }

  private val disposables = CompositeDisposable()

  private val _state: MutableStateFlow<CallLinkDetailsState> = MutableStateFlow(CallLinkDetailsState())
  val state: StateFlow<CallLinkDetailsState> = _state
  val nameSnapshot: String
    get() = state.value.callLink?.state?.name ?: error("Call link not loaded yet.")

  val rootKeySnapshot: ByteArray
    get() = state.value.callLink?.credentials?.linkKeyBytes ?: error("Call link not loaded yet.")

  val epochSnapshot: ByteArray?
    get() = state.value.callLink?.credentials?.epochBytes

  private val recipientSubject = BehaviorSubject.create<Recipient>()
  val recipientSnapshot: Recipient?
    get() = recipientSubject.value

  private val internalShowAlreadyInACall = MutableStateFlow(false)
  val showAlreadyInACall: StateFlow<Boolean> = internalShowAlreadyInACall

  init {
    disposables += repository.refreshCallLinkState(callLinkRoomId)
    disposables += CallLinks.watchCallLink(callLinkRoomId)
      .subscribeOn(Schedulers.io())
      .subscribeBy { callLink ->
        _state.update { it.copy(callLink = callLink) }
      }

    disposables += repository
      .watchCallLinkRecipient(callLinkRoomId)
      .subscribeBy(onNext = recipientSubject::onNext)

    disposables += recipientSubject
      .map { it.id }
      .distinctUntilChanged()
      .flatMap { recipientId ->
        AppDependencies.signalCallManager.peekInfoCache
          .distinctUntilChanged()
          .filter { it.containsKey(recipientId) }
          .map { it[recipientId]!! }
          .distinctUntilChanged()
          .toObservable()
      }
      .subscribeOn(Schedulers.io())
      .subscribeBy { callLinkPeekInfo ->
        _state.update { it.copy(peekInfo = callLinkPeekInfo) }
      }
  }

  override fun onCleared() {
    super.onCleared()
    disposables.dispose()
  }

  fun showAlreadyInACall(showAlreadyInACall: Boolean) {
    internalShowAlreadyInACall.update { showAlreadyInACall }
  }

  fun setDisplayRevocationDialog(displayRevocationDialog: Boolean) {
    _state.update { it.copy(displayRevocationDialog = displayRevocationDialog) }
  }

  suspend fun setApproveAllMembers(approveAllMembers: Boolean) {
    val result = suspendCoroutine { continuation ->
      val credentials = _state.value.callLink?.credentials ?: error("User cannot change the name of this call.")
      disposables += mutationRepository
        .setCallRestrictions(credentials, if (approveAllMembers) CallLinkState.Restrictions.ADMIN_APPROVAL else CallLinkState.Restrictions.NONE)
        .doOnSubscribe {
          _state.update { it.copy(isLoadingAdminApprovalChange = true) }
        }
        .doFinally {
          _state.update { it.copy(isLoadingAdminApprovalChange = false) }
        }
        .subscribeBy(
          onSuccess = { continuation.resume(Result.success(it)) },
          onError = { continuation.resume(Result.failure(it)) }
        )
    }.getOrNull()

    if (result == null) {
      handleError("setApproveAllMembers")
      return
    }

    if (result is UpdateCallLinkResult.Failure) {
      Log.w(TAG, "Failed to change restrictions. $result")

      if (result.status == 409.toShort()) {
        toastCallLinkInUse()
      } else {
        toastFailure()
      }
    }
  }

  suspend fun setName(name: String) {
    val result = suspendCoroutine { continuation ->
      val credentials = _state.value.callLink?.credentials ?: error("User cannot change the name of this call.")
      disposables += mutationRepository.setCallName(credentials, name)
        .subscribeBy(
          onSuccess = { continuation.resume(Result.success(it)) },
          onError = { continuation.resume(Result.failure(it)) }
        )
    }.getOrNull()

    if (result == null) {
      handleError("setName")
    } else {
      if (result !is UpdateCallLinkResult.Update) {
        Log.w(TAG, "Failed to set name. $name")
        toastFailure()
      }
    }
  }

  suspend fun delete(): Boolean {
    val result = suspendCoroutine { continuation ->
      val credentials = _state.value.callLink?.credentials ?: error("User cannot change the name of this call.")
      disposables += mutationRepository.deleteCallLink(credentials)
        .subscribeBy(
          onSuccess = { continuation.resume(Result.success(it)) },
          onError = { continuation.resume(Result.failure(it)) }
        )
    }.getOrNull()

    when (result) {
      null -> handleError("delete")
      is UpdateCallLinkResult.Delete -> return true
      is UpdateCallLinkResult.CallLinkIsInUse -> {
        Log.w(TAG, "Failed to delete in-use call link.")
        toastCouldNotDeleteCallLink()
      }
      else -> {
        Log.w(TAG, "Failed to delete call link. $result")
        toastFailure()
      }
    }

    return false
  }

  private fun handleError(method: String): (throwable: Throwable) -> Unit {
    return {
      Log.w(TAG, "Failure during $method", it)
      toastFailure()
    }
  }

  private fun toastCallLinkInUse() {
    _state.update { it.copy(failureSnackbar = CallLinkDetailsState.FailureSnackbar.COULD_NOT_UPDATE_ADMIN_APPROVAL) }
  }

  private fun toastFailure() {
    _state.update { it.copy(failureSnackbar = CallLinkDetailsState.FailureSnackbar.COULD_NOT_SAVE_CHANGES) }
  }

  private fun toastCouldNotDeleteCallLink() {
    _state.update { it.copy(failureSnackbar = CallLinkDetailsState.FailureSnackbar.COULD_NOT_DELETE_CALL_LINK) }
  }

  class Factory(private val callLinkRoomId: CallLinkRoomId) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(CallLinkDetailsViewModel(callLinkRoomId)) as T
    }
  }
}

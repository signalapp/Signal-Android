/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui.about

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.memberlabel.MemberLabel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.RemoteConfig

class AboutSheetViewModel(
  recipientId: RecipientId,
  groupId: GroupId.V2? = null,
  private val repository: AboutSheetRepository = AboutSheetRepository()
) : ViewModel() {

  private val internalState: MutableStateFlow<AboutSheetUiState> = MutableStateFlow(AboutSheetUiState())
  val state: StateFlow<AboutSheetUiState> = internalState.asStateFlow()

  private val disposables = CompositeDisposable()

  private val recipientDisposable: Disposable = Recipient
    .observable(recipientId)
    .observeOn(AndroidSchedulers.mainThread())
    .subscribeBy { recipient ->
      internalState.update { it.copy(recipient = recipient) }
    }

  private val groupsInCommonDisposable: Disposable = repository
    .getGroupsInCommonCount(recipientId)
    .observeOn(AndroidSchedulers.mainThread())
    .subscribeBy { groupsInCommon ->
      internalState.update { it.copy(groupsInCommonCount = groupsInCommon) }
    }

  private val verifiedDisposable: Disposable = repository
    .getVerified(recipientId)
    .observeOn(AndroidSchedulers.mainThread())
    .subscribeBy { verified ->
      internalState.update { it.copy(verified = verified) }
    }

  init {
    disposables.addAll(recipientDisposable, groupsInCommonDisposable, verifiedDisposable)

    if (groupId != null && RemoteConfig.sendMemberLabels) {
      observeMemberLabel(groupId)
    }
  }

  private fun observeMemberLabel(groupId: GroupId.V2) {
    disposables.add(
      repository.getMemberLabel(groupId)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeBy { memberLabel ->
          internalState.update { it.copy(memberLabel = memberLabel.orNull()) }
        }
    )

    disposables.add(
      repository.canEditMemberLabel(groupId)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeBy { canEditMemberLabel ->
          internalState.update { it.copy(canEditMemberLabel = canEditMemberLabel) }
        }
    )
  }

  override fun onCleared() {
    disposables.dispose()
  }
}

data class AboutSheetUiState(
  val recipient: Recipient? = null,
  val groupsInCommonCount: Int = 0,
  val verified: Boolean = false,
  val memberLabel: MemberLabel? = null,
  val canEditMemberLabel: Boolean = false
)

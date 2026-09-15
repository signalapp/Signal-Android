/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.details

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsState.ContactAction
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsState.DetailAction
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsState.DetailKind
import org.thoughtcrime.securesms.recipients.RecipientId

class SharedContactDetailsViewModel(
  private val contact: Contact,
  private val repository: SharedContactDetailsRepository
) : EventDrivenViewModel<SharedContactDetailsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(SharedContactDetailsViewModel::class)

    /** Which rows sit between the header and the details. */
    fun contactActionsFor(
      isOnSignal: Boolean,
      hasInviteTarget: Boolean,
      hasAnythingToSave: Boolean
    ): List<ContactAction> {
      return buildList {
        if (!isOnSignal && hasInviteTarget) {
          add(ContactAction.INVITE_TO_SIGNAL)
        }
        if (hasAnythingToSave) {
          add(ContactAction.ADD_TO_PHONE_CONTACTS)
        }
        if (isOnSignal) {
          add(ContactAction.ADD_TO_GROUP)
        }
      }
    }

    fun actionsFor(kind: DetailKind, isOnSignal: Boolean): List<DetailAction> {
      return when {
        kind == DetailKind.PHONE && isOnSignal -> {
          listOf(DetailAction.MESSAGE, DetailAction.VIDEO_CALL, DetailAction.AUDIO_CALL, DetailAction.COPY)
        }
        kind == DetailKind.ADDRESS -> listOf(DetailAction.OPEN_IN_MAPS, DetailAction.COPY)
        else -> listOf(DetailAction.COPY)
      }
    }
  }

  private val _state = MutableStateFlow(SharedContactDetailsState(isLoading = true))
  val state: StateFlow<SharedContactDetailsState> = _state.asStateFlow()

  private val _actions = Channel<SharedContactDetailsAction>(Channel.BUFFERED)
  val actions: Flow<SharedContactDetailsAction> = _actions.receiveAsFlow()

  init {
    onEvent(SharedContactDetailsEvent.Initialize)
  }

  override suspend fun processEvent(event: SharedContactDetailsEvent) {
    when (event) {
      SharedContactDetailsEvent.Initialize -> _state.value = repository.loadState(contact)

      SharedContactDetailsEvent.BackClicked -> _actions.send(SharedContactDetailsAction.Exit)

      SharedContactDetailsEvent.MessageClicked -> sendForRecipient { SharedContactDetailsAction.StartChat(it) }
      SharedContactDetailsEvent.VideoCallClicked -> sendForRecipient { SharedContactDetailsAction.StartVideoCall(it) }
      SharedContactDetailsEvent.AudioCallClicked -> sendForRecipient { SharedContactDetailsAction.StartAudioCall(it) }

      is SharedContactDetailsEvent.ActionClicked -> onActionClicked(event.action)

      is SharedContactDetailsEvent.DetailPressed -> onDetailPressed(event.id)

      is SharedContactDetailsEvent.DetailActionClicked -> onDetailActionClicked(event.action)

      SharedContactDetailsEvent.ContextMenuDismissed -> _state.update { it.copy(contextMenu = null) }
    }
  }

  private suspend fun onActionClicked(contactAction: ContactAction) {
    when (contactAction) {
      ContactAction.ADD_TO_GROUP -> sendForRecipient { SharedContactDetailsAction.AddToGroup(it) }

      ContactAction.ADD_TO_PHONE_CONTACTS -> _actions.send(SharedContactDetailsAction.AddToPhoneContacts)

      ContactAction.INVITE_TO_SIGNAL -> {
        val action = inviteAction()

        if (action == null) {
          Log.w(TAG, "Nothing on the card to send an invite to.")
          return
        }

        _actions.send(action)
      }
    }
  }

  private fun onDetailPressed(id: String) {
    val current = _state.value
    val detail = current.details.firstOrNull { it.id == id }

    if (detail == null) {
      Log.w(TAG, "Press on an unknown detail. Ignoring.")
      return
    }

    _state.update {
      it.copy(
        contextMenu = SharedContactDetailsState.ContextMenu(
          detailId = detail.id,
          actions = actionsFor(detail.kind, current.isOnSignal)
        )
      )
    }
  }

  private suspend fun onDetailActionClicked(detailAction: DetailAction) {
    val current = _state.value
    val detail = current.contextMenu?.let { open -> current.details.firstOrNull { it.id == open.detailId } }

    _state.update { it.copy(contextMenu = null) }

    if (detail == null) {
      Log.w(TAG, "Detail action with no open menu. Ignoring.")
      return
    }

    when (detailAction) {
      DetailAction.MESSAGE -> return sendForRecipient { SharedContactDetailsAction.StartChat(it) }
      DetailAction.VIDEO_CALL -> return sendForRecipient { SharedContactDetailsAction.StartVideoCall(it) }
      DetailAction.AUDIO_CALL -> return sendForRecipient { SharedContactDetailsAction.StartAudioCall(it) }
      else -> Unit
    }

    val action = when (detailAction) {
      DetailAction.OPEN_IN_MAPS -> SharedContactDetailsAction.OpenInMaps(detail.copyText)
      DetailAction.COPY -> SharedContactDetailsAction.CopyToClipboard(detail.copyText)
      else -> null
    }

    if (action == null) {
      Log.w(TAG, "No matched recipient to act on.")
      return
    }

    _actions.send(action)
  }

  private suspend fun sendForRecipient(action: (RecipientId) -> SharedContactDetailsAction) {
    val recipientId = repository.resolveOrCreateRecipient(contact)

    if (recipientId == null) {
      Log.w(TAG, "No matched recipient to act on.")
      return
    }

    _state.update { it.copy(signalRecipientId = recipientId) }
    _actions.send(action(recipientId))
  }

  /** Null when the card carries neither a number nor an email. */
  private fun inviteAction(): SharedContactDetailsAction? {
    val number = contact.phoneNumbers.firstOrNull()?.number
    val email = contact.emails.firstOrNull()?.email

    return when {
      number != null -> SharedContactDetailsAction.InviteBySms(number)
      email != null -> SharedContactDetailsAction.InviteByEmail(email)
      else -> null
    }
  }
}

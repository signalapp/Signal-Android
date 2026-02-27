/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.memberlabel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.StringUtil
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.conversation.colors.NameColor
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupInsufficientRightsException
import org.thoughtcrime.securesms.groups.memberlabel.MemberLabelUiState.SaveState
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.NetworkResult

class MemberLabelViewModel(
  private val memberLabelRepo: MemberLabelRepository = MemberLabelRepository.instance,
  private val groupId: GroupId.V2,
  private val recipientId: RecipientId
) : ViewModel() {

  private var originalLabelEmoji: String = ""
  private var originalLabelText: String = ""

  private val internalUiState = MutableStateFlow(MemberLabelUiState())
  val uiState: StateFlow<MemberLabelUiState> = internalUiState.asStateFlow()

  init {
    loadInitialState()
  }

  private fun loadInitialState() {
    viewModelScope.launch(SignalDispatchers.IO) {
      val memberLabel = memberLabelRepo.getLabel(groupId, recipientId)
      originalLabelEmoji = memberLabel?.emoji.orEmpty()
      originalLabelText = memberLabel?.text.orEmpty()

      internalUiState.update {
        it.copy(
          recipient = memberLabelRepo.getRecipient(recipientId),
          labelEmoji = originalLabelEmoji,
          labelText = originalLabelText,
          senderNameColor = memberLabelRepo.getSenderNameColor(groupId, recipientId)
        )
      }
    }
  }

  fun onLabelEmojiChanged(emoji: String) {
    internalUiState.update {
      it.copy(
        labelEmoji = emoji,
        hasChanges = hasChanges(emoji, it.labelText)
      )
    }
  }

  fun onLabelTextChanged(text: String) {
    val truncatedText = MemberLabel.truncateLabelText(text)
    internalUiState.update {
      it.copy(
        labelText = truncatedText,
        hasChanges = hasChanges(labelEmoji = it.labelEmoji, labelText = truncatedText)
      )
    }
  }

  fun clearLabel() {
    internalUiState.update {
      it.copy(
        labelEmoji = "",
        labelText = "",
        hasChanges = hasChanges(labelEmoji = "", labelText = "")
      )
    }
  }

  private fun hasChanges(labelEmoji: String, labelText: String): Boolean {
    return labelEmoji != originalLabelEmoji || MemberLabel.sanitizeLabelText(labelText) != originalLabelText
  }

  fun save() {
    if (!internalUiState.value.isSaveEnabled) {
      return
    }

    viewModelScope.launch(SignalDispatchers.IO) {
      internalUiState.update {
        it.copy(saveState = SaveState.InProgress)
      }

      val currentState = internalUiState.value
      val result = memberLabelRepo.setLabel(
        groupId = groupId,
        label = MemberLabel(
          emoji = currentState.labelEmoji.ifEmpty { null },
          text = currentState.labelText
        )
      )

      val newSaveState: SaveState = when (result) {
        is NetworkResult.Success -> SaveState.Success

        is NetworkResult.NetworkError<*> -> SaveState.NetworkError

        is NetworkResult.ApplicationError<*> -> {
          if (result.throwable is GroupInsufficientRightsException) {
            SaveState.InsufficientRights
          } else {
            throw result.throwable
          }
        }

        is NetworkResult.StatusCodeError<*> -> throw result.exception
      }

      internalUiState.update {
        it.copy(saveState = newSaveState)
      }
    }
  }

  fun onSaveStateConsumed() {
    internalUiState.update {
      it.copy(saveState = null)
    }
  }
}

data class MemberLabelUiState(
  val labelEmoji: String = "",
  val labelText: String = "",
  val recipient: Recipient? = null,
  val senderNameColor: NameColor? = null,
  val hasChanges: Boolean = false,
  val saveState: SaveState? = null
) {
  val sanitizedLabelText: String get() = MemberLabel.sanitizeLabelText(labelText)

  val remainingCharacters: Int
    get() = MemberLabel.MAX_LABEL_GRAPHEMES - StringUtil.getGraphemeCount(sanitizedLabelText)

  val isSaveEnabled: Boolean
    get() {
      val isCleared = sanitizedLabelText.isEmpty() && labelEmoji.isEmpty()
      val graphemeCount = StringUtil.getGraphemeCount(sanitizedLabelText)
      val hasValidLabel = graphemeCount in MemberLabel.MIN_LABEL_GRAPHEMES..MemberLabel.MAX_LABEL_GRAPHEMES
      return hasChanges && (hasValidLabel || isCleared) && saveState != SaveState.InProgress
    }

  sealed interface SaveState {
    data object InProgress : SaveState
    data object Success : SaveState
    data object NetworkError : SaveState
    data object InsufficientRights : SaveState
  }
}

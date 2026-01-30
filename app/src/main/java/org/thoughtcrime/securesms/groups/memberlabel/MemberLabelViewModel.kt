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
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.memberlabel.MemberLabelUiState.SaveState
import org.thoughtcrime.securesms.recipients.RecipientId

private const val MIN_LABEL_TEXT_LENGTH = 1
private const val MAX_LABEL_TEXT_LENGTH = 24

class MemberLabelViewModel(
  private val memberLabelRepo: MemberLabelRepository,
  private val recipientId: RecipientId
) : ViewModel() {

  constructor(
    groupId: GroupId.V2,
    recipientId: RecipientId
  ) : this(
    memberLabelRepo = MemberLabelRepository(groupId = groupId),
    recipientId = recipientId
  )

  private var originalLabelEmoji: String = ""
  private var originalLabelText: String = ""

  private val internalUiState = MutableStateFlow(MemberLabelUiState())
  val uiState: StateFlow<MemberLabelUiState> = internalUiState.asStateFlow()

  init {
    loadExistingLabel()
  }

  private fun loadExistingLabel() {
    viewModelScope.launch(SignalDispatchers.IO) {
      val memberLabel = memberLabelRepo.getLabel(recipientId)
      originalLabelEmoji = memberLabel?.emoji.orEmpty()
      originalLabelText = memberLabel?.text.orEmpty()

      internalUiState.update {
        it.copy(
          labelEmoji = originalLabelEmoji,
          labelText = originalLabelText
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
    val sanitizedText = text.take(MAX_LABEL_TEXT_LENGTH)
    internalUiState.update {
      it.copy(
        labelText = sanitizedText,
        hasChanges = hasChanges(labelEmoji = it.labelEmoji, labelText = sanitizedText)
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
    return labelEmoji != originalLabelEmoji || labelText != originalLabelText
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
      memberLabelRepo.setLabel(
        label = MemberLabel(
          emoji = currentState.labelEmoji.ifEmpty { null },
          text = currentState.labelText
        )
      )

      internalUiState.update {
        it.copy(saveState = SaveState.Success)
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
  val hasChanges: Boolean = false,
  val saveState: SaveState? = null
) {
  val remainingCharacters: Int
    get() = MAX_LABEL_TEXT_LENGTH - labelText.length

  val isSaveEnabled: Boolean
    get() {
      val isCleared = labelText.isEmpty() && labelEmoji.isEmpty()
      val hasValidLabel = labelText.length >= MIN_LABEL_TEXT_LENGTH
      return hasChanges && (hasValidLabel || isCleared) && saveState != SaveState.InProgress
    }

  sealed interface SaveState {
    data object InProgress : SaveState
    data object Success : SaveState
  }
}

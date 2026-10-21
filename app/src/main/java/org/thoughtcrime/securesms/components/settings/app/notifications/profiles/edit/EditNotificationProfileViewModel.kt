/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.edit

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.BreakIteratorCompat
import org.signal.core.util.logging.Log

/**
 * Drives [EditNotificationProfileScreen], for creating a profile when [profileId] is [NEW_PROFILE] and editing it otherwise.
 */
class EditNotificationProfileViewModel(
  private val profileId: Long
) : EventDrivenViewModel<EditNotificationProfileEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(EditNotificationProfileViewModel::class)

    const val NEW_PROFILE = -1L
  }

  private val createMode = profileId == NEW_PROFILE

  private val _state = MutableStateFlow(EditNotificationProfileState(createMode = createMode, isLoaded = createMode))
  private val _actions = Channel<EditNotificationProfileAction>(Channel.BUFFERED)

  val state: StateFlow<EditNotificationProfileState> = _state.asStateFlow()
  val actions: Flow<EditNotificationProfileAction> = _actions.receiveAsFlow()

  init {
    if (!createMode) {
      viewModelScope.launch {
        val profile = EditNotificationProfileRepository.getProfile(profileId)
        if (profile == null) {
          Log.w(TAG, "Profile no longer exists.")
          _actions.send(EditNotificationProfileAction.NavigateBack)
        } else {
          _state.update { it.copy(isLoaded = true, name = profile.name, emoji = profile.emoji) }
        }
      }
    }
  }

  override suspend fun processEvent(event: EditNotificationProfileEvent) {
    when (event) {
      EditNotificationProfileEvent.NavigateBackClicked -> _actions.send(EditNotificationProfileAction.NavigateBack)
      EditNotificationProfileEvent.EmojiButtonClicked -> _state.update { it.copy(showEmojiPicker = true) }
      is EditNotificationProfileEvent.EmojiSelected -> _state.update { it.copy(emoji = event.emoji, showEmojiPicker = false) }
      EditNotificationProfileEvent.EmojiPickerDismissed -> _state.update { it.copy(showEmojiPicker = false) }
      is EditNotificationProfileEvent.NameChanged -> {
        _state.update { it.copy(name = event.name.truncateToMaxGraphemes(), nameError = EditNotificationProfileState.NameError.None) }
      }
      EditNotificationProfileEvent.ClearClicked -> _state.update { it.copy(name = "", emoji = "", nameError = EditNotificationProfileState.NameError.None) }
      is EditNotificationProfileEvent.PresetClicked -> {
        _state.update { it.copy(name = event.name, emoji = event.emoji, nameError = EditNotificationProfileState.NameError.None) }
      }
      EditNotificationProfileEvent.SaveClicked -> save()
    }
  }

  private suspend fun save() {
    val state = _state.value
    if (!state.isLoaded || state.isSaving) {
      return
    }

    val name = state.name
    if (name.isEmpty()) {
      _state.update { it.copy(nameError = EditNotificationProfileState.NameError.Empty) }
      return
    }

    _state.update { it.copy(isSaving = true) }

    val result = if (createMode) {
      EditNotificationProfileRepository.createProfile(name, state.emoji)
    } else {
      EditNotificationProfileRepository.updateProfile(profileId, name, state.emoji)
    }

    _state.update { it.copy(isSaving = false) }

    when (result) {
      is EditNotificationProfileRepository.SaveResult.Success -> {
        _actions.send(if (createMode) EditNotificationProfileAction.NavigateToAllowedMembers(result.profileId) else EditNotificationProfileAction.NavigateBack)
      }
      EditNotificationProfileRepository.SaveResult.DuplicateName -> {
        _state.update { it.copy(nameError = EditNotificationProfileState.NameError.Duplicate) }
      }
    }
  }

  private fun String.truncateToMaxGraphemes(): String {
    val breakIterator = BreakIteratorCompat.getInstance().apply { setText(this@truncateToMaxGraphemes) }
    return if (breakIterator.countBreaks() > EditNotificationProfileState.NAME_MAX_GRAPHEMES) {
      breakIterator.take(EditNotificationProfileState.NAME_MAX_GRAPHEMES).toString()
    } else {
      this
    }
  }
}

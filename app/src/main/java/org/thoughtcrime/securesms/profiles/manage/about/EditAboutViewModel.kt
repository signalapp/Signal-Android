/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.about

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.about.EditAboutEvent
import org.signal.appsettings.about.EditAboutState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.StringUtil
import org.signal.core.util.logging.Log
import org.whispersystems.signalservice.api.crypto.ProfileCipher

/**
 * Edits the about text and emoji on the user's own profile, leaving once they're saved. Unsaved edits are kept in
 * [savedStateHandle] so they survive process death.
 */
class EditAboutViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit,
  private val savedStateHandle: SavedStateHandle
) : EventDrivenViewModel<EditAboutEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(EditAboutViewModel::class)

    private const val KEY_ABOUT = "about"
    private const val KEY_EMOJI = "emoji"
  }

  private val _state = MutableStateFlow(
    EditAboutState(
      about = savedStateHandle[KEY_ABOUT] ?: StringUtil.trimToFit(EditAboutRepository.getAbout(), EditAboutState.ABOUT_MAX_GRAPHEMES, ProfileCipher.MAX_POSSIBLE_ABOUT_LENGTH),
      emoji = savedStateHandle[KEY_EMOJI] ?: EditAboutRepository.getAboutEmoji()
    )
  )

  val state: StateFlow<EditAboutState> = _state.asStateFlow()

  override suspend fun processEvent(event: EditAboutEvent) {
    when (event) {
      EditAboutEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
      is EditAboutEvent.AboutChanged -> setAbout(about = StringUtil.trimToFit(event.about, EditAboutState.ABOUT_MAX_GRAPHEMES, ProfileCipher.MAX_POSSIBLE_ABOUT_LENGTH), emoji = _state.value.emoji)
      EditAboutEvent.EmojiButtonClicked -> _state.update { it.copy(showEmojiPicker = true) }
      is EditAboutEvent.EmojiSelected -> setAbout(about = _state.value.about, emoji = event.emoji)
      EditAboutEvent.EmojiPickerDismissed -> _state.update { it.copy(showEmojiPicker = false) }
      is EditAboutEvent.PresetClicked -> setAbout(about = StringUtil.trimToFit(event.about, EditAboutState.ABOUT_MAX_GRAPHEMES, ProfileCipher.MAX_POSSIBLE_ABOUT_LENGTH), emoji = event.emoji)
      EditAboutEvent.ClearClicked -> setAbout(about = "", emoji = "")
      EditAboutEvent.SaveClicked -> save()
      EditAboutEvent.SnackbarDismissed -> _state.update { it.copy(snackbar = EditAboutState.Snackbar.None) }
    }
  }

  /** Also closes the emoji picker, since a pick from it is one of the ways the emoji changes. */
  private fun setAbout(about: String, emoji: String) {
    savedStateHandle[KEY_ABOUT] = about
    savedStateHandle[KEY_EMOJI] = emoji
    _state.update { it.copy(about = about, emoji = emoji, showEmojiPicker = false) }
  }

  private suspend fun save() {
    val state = _state.value
    if (state.saving) {
      return
    }

    _state.update { it.copy(saving = true) }

    when (EditAboutRepository.setAbout(about = state.about, emoji = state.emoji)) {
      EditAboutRepository.SaveResult.SUCCESS -> parentEventEmitter.navigateBack()
      EditAboutRepository.SaveResult.NETWORK_FAILURE -> _state.update { it.copy(saving = false, snackbar = EditAboutState.Snackbar.NetworkFailure) }
    }
  }
}

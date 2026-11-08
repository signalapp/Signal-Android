/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.name

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.profilename.EditProfileNameEvent
import org.signal.appsettings.profilename.EditProfileNameState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.StringUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.profiles.ProfileName

/**
 * Edits the name on the user's own profile, leaving once the new name is saved. Unsaved edits are kept in
 * [savedStateHandle] so they survive process death.
 */
class EditProfileNameViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit,
  private val savedStateHandle: SavedStateHandle
) : EventDrivenViewModel<EditProfileNameEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(EditProfileNameViewModel::class)

    /** Each part is capped in grapheme clusters as well as in bytes, so a name stays something a row can show. */
    const val NAME_MAX_GRAPHEMES = 26

    private const val KEY_GIVEN_NAME = "given_name"
    private const val KEY_FAMILY_NAME = "family_name"
  }

  private val _state = MutableStateFlow(
    EditProfileNameRepository.getProfileName().let {
      EditProfileNameState(
        givenName = savedStateHandle[KEY_GIVEN_NAME] ?: StringUtil.trimToFit(it.givenName, NAME_MAX_GRAPHEMES, ProfileName.MAX_PART_LENGTH),
        familyName = savedStateHandle[KEY_FAMILY_NAME] ?: StringUtil.trimToFit(it.familyName, NAME_MAX_GRAPHEMES, ProfileName.MAX_PART_LENGTH)
      )
    }
  )

  val state: StateFlow<EditProfileNameState> = _state.asStateFlow()

  override suspend fun processEvent(event: EditProfileNameEvent) {
    when (event) {
      EditProfileNameEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
      is EditProfileNameEvent.GivenNameChanged -> {
        val givenName = StringUtil.trimToFit(event.name, NAME_MAX_GRAPHEMES, ProfileName.MAX_PART_LENGTH)
        savedStateHandle[KEY_GIVEN_NAME] = givenName
        _state.update { it.copy(givenName = givenName) }
      }
      is EditProfileNameEvent.FamilyNameChanged -> {
        val familyName = StringUtil.trimToFit(event.name, NAME_MAX_GRAPHEMES, ProfileName.MAX_PART_LENGTH)
        savedStateHandle[KEY_FAMILY_NAME] = familyName
        _state.update { it.copy(familyName = familyName) }
      }
      EditProfileNameEvent.SaveClicked -> save()
      EditProfileNameEvent.SnackbarDismissed -> _state.update { it.copy(snackbar = EditProfileNameState.Snackbar.None) }
    }
  }

  private suspend fun save() {
    val state = _state.value
    if (!state.canSave) {
      return
    }

    _state.update { it.copy(saving = true) }

    when (EditProfileNameRepository.setProfileName(ProfileName.fromParts(state.givenName, state.familyName))) {
      EditProfileNameRepository.SaveResult.SUCCESS -> parentEventEmitter.navigateBack()
      EditProfileNameRepository.SaveResult.NETWORK_FAILURE -> _state.update { it.copy(saving = false, snackbar = EditProfileNameState.Snackbar.NetworkFailure) }
    }
  }
}

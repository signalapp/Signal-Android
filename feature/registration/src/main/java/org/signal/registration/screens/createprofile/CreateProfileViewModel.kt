/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.createprofile

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.EventDrivenViewModel
import org.signal.registration.screens.util.navigateTo

/**
 * ViewModel for the registration profile-creation screen. Holds the user's typed name and selected
 * avatar bytes and submits them to [RegistrationRepository.setProfile] when the user advances.
 */
class CreateProfileViewModel(
  private val repository: RegistrationRepository,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<CreateProfileScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(CreateProfileViewModel::class)
  }

  private val _state = MutableStateFlow(CreateProfileState())
  val state: StateFlow<CreateProfileState> = _state

  init {
    viewModelScope.launch {
      val stored = repository.getStoredProfileData()
      Log.i(TAG, "[init] Loaded stored profile data. givenName=${stored.givenName.isNotEmpty()}, familyName=${stored.familyName.isNotEmpty()}, avatar=${stored.avatar != null}")

      val seeded = _state.value.copy(
        givenName = stored.givenName,
        familyName = stored.familyName,
        avatar = stored.avatar,
        discoverableByPhoneNumber = stored.discoverableByPhoneNumber ?: true,
        isLoading = false
      )

      if (stored.givenName.isNotEmpty() && stored.avatar != null) {
        Log.i(TAG, "[init] Profile name + avatar already present. Auto-submitting and skipping screen.")
        _state.value = seeded.copy(isSubmitting = true)
        submitProfile(seeded)
      } else {
        _state.value = seeded
      }
    }
  }

  override suspend fun processEvent(event: CreateProfileScreenEvents) {
    applyEvent(state.value, event, parentEventEmitter, repository) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: CreateProfileState,
    event: CreateProfileScreenEvents,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    repository: RegistrationRepository,
    stateEmitter: (CreateProfileState) -> Unit
  ) {
    when (event) {
      is CreateProfileScreenEvents.GivenNameChanged -> {
        stateEmitter(state.copy(givenName = event.value))
      }
      is CreateProfileScreenEvents.FamilyNameChanged -> {
        stateEmitter(state.copy(familyName = event.value))
      }
      is CreateProfileScreenEvents.AvatarSelected -> {
        stateEmitter(state.copy(avatar = event.bytes))
      }
      CreateProfileScreenEvents.AvatarCleared -> {
        stateEmitter(state.copy(avatar = null))
      }
      CreateProfileScreenEvents.WhoCanFindMeClicked -> {
        parentEventEmitter.navigateTo(RegistrationRoute.PhoneNumberDiscoverability(state.discoverableByPhoneNumber))
      }
      is CreateProfileScreenEvents.DiscoverabilityChanged -> {
        stateEmitter(state.copy(discoverableByPhoneNumber = event.discoverable))
      }
      CreateProfileScreenEvents.NextClicked -> {
        if (state.isSubmitting || !state.isFormValid) {
          return
        }
        stateEmitter(state.copy(isSubmitting = true))
        submitProfile(state, parentEventEmitter, repository, stateEmitter)
      }
      CreateProfileScreenEvents.ConsumeOneTimeEvent -> {
        stateEmitter(state.copy(oneTimeEvent = null))
      }
    }
  }

  private suspend fun submitProfile(state: CreateProfileState) {
    submitProfile(state, parentEventEmitter, repository) { _state.value = it }
  }

  private suspend fun submitProfile(
    state: CreateProfileState,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    repository: RegistrationRepository,
    stateEmitter: (CreateProfileState) -> Unit
  ) {
    val result = repository.setProfile(
      givenName = state.givenName.trim(),
      familyName = state.familyName.trim(),
      avatar = state.avatar,
      discoverableByPhoneNumber = state.discoverableByPhoneNumber
    )
    when (result) {
      is RequestResult.Success -> {
        Log.i(TAG, "[submitProfile] Profile saved.")
        parentEventEmitter(RegistrationFlowEvent.RegistrationComplete)
      }
      is RequestResult.NonSuccess -> {
        Log.w(TAG, "[submitProfile] Profile save failed: ${result.error}")
        stateEmitter(state.copy(isSubmitting = false, oneTimeEvent = CreateProfileState.OneTimeEvent.UploadFailed))
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "[submitProfile] Network error saving profile.", result.networkError)
        stateEmitter(state.copy(isSubmitting = false, oneTimeEvent = CreateProfileState.OneTimeEvent.UploadFailed))
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "[submitProfile] Application error saving profile.", result.cause)
        stateEmitter(state.copy(isSubmitting = false, oneTimeEvent = CreateProfileState.OneTimeEvent.UploadFailed))
      }
    }
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return CreateProfileViewModel(repository, parentEventEmitter) as T
    }
  }
}

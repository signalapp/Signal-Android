/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.username

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.UsernameEditMode
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.usernameedit.UsernameEditAction
import org.signal.appsettings.usernameedit.UsernameEditEvent
import org.signal.appsettings.usernameedit.UsernameEditState
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.Result
import org.signal.core.util.UsernameUtil
import org.signal.core.util.UsernameUtil.InvalidReason
import org.signal.core.util.logging.Log
import org.signal.libsignal.usernames.Username
import org.thoughtcrime.securesms.profiles.manage.UsernameEditStateMachine
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.UsernameDeleteResult
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.UsernameSetResult
import org.thoughtcrime.securesms.profiles.manage.UsernameState
import org.whispersystems.signalservice.api.util.Usernames

/**
 * Lets the user choose, change, or delete their username. As they type a nickname, a discriminator is reserved for it
 * after a short pause, unless they've typed one themselves.
 *
 * A username is made up of a nickname and a discriminator, written `nickname.discriminator`. Unsaved edits are kept in
 * [savedStateHandle] so they survive process death.
 */
class UsernameEditViewModel(
  private val mode: UsernameEditMode,
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit,
  private val savedStateHandle: SavedStateHandle
) : EventDrivenViewModel<UsernameEditEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(UsernameEditViewModel::class)

    @VisibleForTesting
    const val RESERVATION_DEBOUNCE_MILLIS: Long = 500

    private const val KEY_NICKNAME = "nickname"
    private const val KEY_DISCRIMINATOR = "discriminator"
    private const val KEY_DISCRIMINATOR_ENTERED_BY_USER = "discriminator_entered_by_user"
  }

  private val currentNickname: String
    get() = UsernameEditRepository.getUsername()?.split(Usernames.DELIMITER)?.first() ?: ""

  private val currentDiscriminator: String
    get() = UsernameEditRepository.getUsername()?.split(Usernames.DELIMITER)?.last() ?: ""

  private var usernameState: UsernameState = UsernameEditRepository.getUsername()?.let { UsernameState.Set(Username(it)) } ?: UsernameState.NoUsername
  private var input: UsernameEditStateMachine.State = UsernameEditStateMachine.NoUserEntry(
    nickname = currentNickname,
    discriminator = currentDiscriminator,
    stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM
  )
  private var reservation: Job? = null

  /** What [usernameState] was before the reservation in flight, to go back to if that reservation is cancelled. */
  private var beforeReservation: UsernameState = usernameState

  private val _state = MutableStateFlow(render(UsernameEditState()))
  private val _actions = Channel<UsernameEditAction>(Channel.BUFFERED)

  val state: StateFlow<UsernameEditState> = _state.asStateFlow()
  val actions: Flow<UsernameEditAction> = _actions.receiveAsFlow()

  init {
    val savedNickname: String? = savedStateHandle[KEY_NICKNAME]
    if (savedNickname != null) {
      if (savedNickname != input.nickname) {
        onNicknameChanged(savedNickname)
      }
      if (savedStateHandle[KEY_DISCRIMINATOR_ENTERED_BY_USER] ?: false) {
        onDiscriminatorChanged(savedStateHandle[KEY_DISCRIMINATOR] ?: "")
      }
    } else if (mode == UsernameEditMode.RECOVERY) {
      onNicknameChanged(currentNickname)
      onDiscriminatorChanged(currentDiscriminator)
    }
  }

  override suspend fun processEvent(event: UsernameEditEvent) {
    when (event) {
      UsernameEditEvent.NavigateBackClicked -> close()
      is UsernameEditEvent.NicknameChanged -> onNicknameChanged(event.nickname)
      is UsernameEditEvent.DiscriminatorChanged -> onDiscriminatorChanged(event.discriminator.take(UsernameUtil.MAX_DISCRIMINATOR_LENGTH))
      UsernameEditEvent.SubmitClicked -> {
        if (isSameUsernameRecovery()) {
          _state.update { it.copy(dialog = UsernameEditState.Dialog.ConfirmSameUsernameRecovery) }
        } else {
          submit(userConfirmedReset = false)
        }
      }
      UsernameEditEvent.ResetConfirmed, UsernameEditEvent.SameUsernameRecoveryConfirmed -> {
        _state.update { it.copy(dialog = UsernameEditState.Dialog.None) }
        submit(userConfirmedReset = true)
      }
      UsernameEditEvent.DeleteClicked -> delete()
      UsernameEditEvent.DialogDismissed -> _state.update { it.copy(dialog = UsernameEditState.Dialog.None) }
      is UsernameEditEvent.LearnMoreClicked -> _actions.send(UsernameEditAction.OpenBrowserLink(event.url))
      UsernameEditEvent.SnackbarDismissed -> _state.update { it.copy(snackbar = UsernameEditState.Snackbar.None) }
    }
  }

  private fun onNicknameChanged(nickname: String) {
    onInputChanged(cleared = nickname.isBlank()) { it.onUserChangedNickname(nickname) }
  }

  private fun onDiscriminatorChanged(discriminator: String) {
    onInputChanged(cleared = discriminator.isBlank()) { it.onUserChangedDiscriminator(discriminator) }
  }

  /**
   * Applies something the user typed. Clearing either half of an existing username offers to delete it; anything else
   * waits for the user to pause before reserving what they typed.
   */
  private fun onInputChanged(cleared: Boolean, change: (UsernameEditStateMachine.State) -> UsernameEditStateMachine.State) {
    if (usernameState is UsernameState.Loading) {
      usernameState = beforeReservation
    }

    val offerDelete = cleared && UsernameEditRepository.getUsername() != null
    if (offerDelete) {
      usernameState = UsernameState.NoUsername
    }

    input = change(input)
    saveInput()
    update { it.copy(button = if (offerDelete) UsernameEditState.Button.DELETE else UsernameEditState.Button.SUBMIT_DISABLED, error = null) }

    val snapshot = input
    reservation?.cancel()
    reservation = viewModelScope.launch {
      delay(RESERVATION_DEBOUNCE_MILLIS)
      reserve(snapshot)
    }
  }

  /** Reserves a username for [state], what the user had typed when they paused. */
  private suspend fun reserve(state: UsernameEditStateMachine.State) {
    val nickname = state.nickname
    if (nickname.isBlank() || state is UsernameEditStateMachine.NoUserEntry || state.stateModifier == UsernameEditStateMachine.StateModifier.SYSTEM) {
      return
    }

    val nicknameInvalidReason = UsernameUtil.checkNickname(nickname)
    if (nicknameInvalidReason != null) {
      update { it.copy(button = UsernameEditState.Button.SUBMIT_DISABLED, error = nicknameInvalidReason.toNicknameError()) }
      return
    }

    if (isCaseChange(state)) {
      val discriminator = UsernameEditRepository.getUsername()?.split(Usernames.DELIMITER)?.lastOrNull() ?: error("Unexpected case change, no discriminator!")
      usernameState = UsernameState.CaseChange(Username("$nickname${Usernames.DELIMITER}$discriminator"))
      input = input.onSystemChangedDiscriminator(discriminator)
      update { it.copy(button = UsernameEditState.Button.SUBMIT, error = null) }
      return
    }

    val isDiscriminatorSetByUser = state.isDiscriminatorSetByUser()
    val discriminator = if (isDiscriminatorSetByUser) state.discriminator else null

    val discriminatorInvalidReason = UsernameUtil.checkDiscriminator(discriminator)
    if (isDiscriminatorSetByUser && discriminatorInvalidReason != null) {
      update { it.copy(button = UsernameEditState.Button.SUBMIT_DISABLED, error = discriminatorInvalidReason.toDiscriminatorError()) }
      return
    }

    beforeReservation = usernameState
    usernameState = UsernameState.Loading
    update { it.copy(button = UsernameEditState.Button.SUBMIT_DISABLED, error = null) }

    when (val result = UsernameEditRepository.reserveUsername(nickname, discriminator)) {
      is Result.Success -> {
        val reserved = result.success
        usernameState = reserved

        val reservedDiscriminator = reserved.getDiscriminator()
        if (!isDiscriminatorSetByUser && reservedDiscriminator != null) {
          input = input.onSystemChangedDiscriminator(reservedDiscriminator)
        }

        update { it.copy(button = UsernameEditState.Button.SUBMIT) }
      }

      is Result.Failure -> when (result.failure) {
        UsernameSetResult.SUCCESS -> error("A failed reservation can't be a success.")

        UsernameSetResult.USERNAME_INVALID -> {
          usernameState = UsernameState.NoUsername
          update { it.copy(button = UsernameEditState.Button.SUBMIT_DISABLED, error = UsernameEditState.UsernameError.INVALID_GENERIC) }
        }

        UsernameSetResult.USERNAME_UNAVAILABLE -> {
          usernameState = if (discriminator != null) {
            UsernameState.CaseChange(Username("$nickname${Usernames.DELIMITER}$discriminator"))
          } else {
            UsernameState.NoUsername
          }
          val error = if (isDiscriminatorSetByUser) UsernameEditState.UsernameError.DISCRIMINATOR_NOT_AVAILABLE else UsernameEditState.UsernameError.TAKEN
          update { it.copy(button = UsernameEditState.Button.SUBMIT_DISABLED, error = error) }
        }

        UsernameSetResult.NETWORK_ERROR -> {
          usernameState = UsernameState.NoUsername
          update { it.copy(button = UsernameEditState.Button.SUBMIT, snackbar = UsernameEditState.Snackbar.NetworkFailure) }
        }

        UsernameSetResult.RATE_LIMIT_ERROR -> {
          usernameState = UsernameState.NoUsername
          update { it.copy(button = UsernameEditState.Button.SUBMIT, snackbar = UsernameEditState.Snackbar.RateLimited) }
        }

        UsernameSetResult.CANDIDATE_GENERATION_ERROR -> {
          usernameState = UsernameState.NoUsername
          update { it.copy(button = UsernameEditState.Button.SUBMIT_DISABLED, error = UsernameEditState.UsernameError.TAKEN) }
        }
      }
    }
  }

  /**
   * @param userConfirmedReset True once the user has agreed, through [UsernameEditState.Dialog.ConfirmReset], that changing
   *   their username gives them a new link.
   */
  private suspend fun submit(userConfirmedReset: Boolean) {
    if (!UsernameEditRepository.isNetworkConnected()) {
      _state.update { it.copy(snackbar = UsernameEditState.Snackbar.NetworkFailure) }
      return
    }

    val state = usernameState
    val isCaseChange = isCaseChange(input)

    if (!isCaseChange && !UsernameEditRepository.getUsername().isNullOrBlank() && !userConfirmedReset) {
      _state.update { it.copy(dialog = UsernameEditState.Dialog.ConfirmReset) }
      return
    }

    if (state !is UsernameState.Reserved && state !is UsernameState.CaseChange) {
      Log.w(TAG, "Username was submitted, current state is invalid! State: ${state.javaClass.simpleName}")
      update { it.copy(button = UsernameEditState.Button.SUBMIT_DISABLED, error = null) }
      return
    }

    if (state.requireUsername().username == UsernameEditRepository.getUsername() && mode != UsernameEditMode.RECOVERY) {
      Log.d(TAG, "Username was submitted, but was identical to the current username. Ignoring.")
      update { it.copy(button = UsernameEditState.Button.SUBMIT_DISABLED, error = null) }
      return
    }

    val invalidReason = UsernameUtil.checkNickname(state.getNickname())
    if (invalidReason != null) {
      Log.w(TAG, "Username was submitted, but did not pass validity checks. Reason: $invalidReason")
      update { it.copy(button = UsernameEditState.Button.SUBMIT_DISABLED, error = invalidReason.toNicknameError()) }
      return
    }

    update { it.copy(button = UsernameEditState.Button.SUBMIT_LOADING, error = null) }

    val result = if (isCaseChange) {
      UsernameEditRepository.updateUsernameDisplayForCurrentLink(state.requireUsername())
    } else {
      UsernameEditRepository.confirmUsernameAndCreateNewLink(state.requireUsername())
    }

    val nickname = state.getNickname()

    when (result) {
      UsernameSetResult.SUCCESS -> {
        UsernameEditRepository.markUsernameSet()
        update { it.copy(button = UsernameEditState.Button.SUBMIT_DISABLED) }
        _actions.send(UsernameEditAction.UsernameSaved)
        close()
      }

      UsernameSetResult.USERNAME_INVALID -> {
        update { it.copy(button = UsernameEditState.Button.SUBMIT_DISABLED, error = UsernameEditState.UsernameError.INVALID_GENERIC, snackbar = UsernameEditState.Snackbar.Invalid) }
        nickname?.let { onNicknameChanged(it) }
      }

      UsernameSetResult.CANDIDATE_GENERATION_ERROR, UsernameSetResult.USERNAME_UNAVAILABLE -> {
        update { it.copy(button = UsernameEditState.Button.SUBMIT_DISABLED, error = UsernameEditState.UsernameError.TAKEN, snackbar = UsernameEditState.Snackbar.Taken) }
        nickname?.let { onNicknameChanged(it) }
      }

      UsernameSetResult.NETWORK_ERROR -> update { it.copy(button = UsernameEditState.Button.SUBMIT, snackbar = UsernameEditState.Snackbar.NetworkFailure) }

      UsernameSetResult.RATE_LIMIT_ERROR -> update { it.copy(button = UsernameEditState.Button.SUBMIT, snackbar = UsernameEditState.Snackbar.RateLimited) }
    }
  }

  private suspend fun delete() {
    update { it.copy(button = UsernameEditState.Button.DELETE_LOADING, error = null) }

    when (UsernameEditRepository.deleteUsernameAndLink()) {
      UsernameDeleteResult.SUCCESS -> {
        update { it.copy(button = UsernameEditState.Button.DELETE_DISABLED) }
        _actions.send(UsernameEditAction.UsernameDeleted)
        parentEventEmitter.navigateBack()
      }

      UsernameDeleteResult.NETWORK_ERROR -> update { it.copy(button = UsernameEditState.Button.DELETE, snackbar = UsernameEditState.Snackbar.NetworkFailure) }
    }
  }

  /** Recovery is its own activity, so leaving it closes that rather than going back to whatever is beneath. */
  private suspend fun close() {
    if (mode == UsernameEditMode.RECOVERY) {
      _actions.send(UsernameEditAction.Finish)
    } else {
      parentEventEmitter.navigateBack()
    }
  }

  private fun isSameUsernameRecovery(): Boolean {
    val state = usernameState
    return mode == UsernameEditMode.RECOVERY &&
      state is UsernameState.Reserved &&
      state.requireUsername().username.lowercase() == UsernameEditRepository.getUsername()?.lowercase()
  }

  /** A change to only the case of the current nickname keeps the user's discriminator and link. */
  private fun isCaseChange(state: UsernameEditStateMachine.State): Boolean {
    if (mode == UsernameEditMode.RECOVERY || state.isDiscriminatorSetByUser()) {
      return false
    }

    return state.nickname.lowercase() == UsernameEditRepository.getUsername()?.split(Usernames.DELIMITER)?.firstOrNull()?.lowercase()
  }

  private fun saveInput() {
    savedStateHandle[KEY_NICKNAME] = input.nickname
    savedStateHandle[KEY_DISCRIMINATOR] = input.discriminator
    savedStateHandle[KEY_DISCRIMINATOR_ENTERED_BY_USER] = input.isDiscriminatorSetByUser()
  }

  /** Applies [change], then refreshes everything the state derives from [input] and [usernameState]. */
  private fun update(change: (UsernameEditState) -> UsernameEditState) {
    _state.update { render(change(it)) }
  }

  /** While a username is being reserved, the one shown above the field stays as it was until there's a new one. */
  private fun render(state: UsernameEditState): UsernameEditState {
    return state.copy(
      nickname = input.nickname,
      discriminator = input.discriminator,
      showDiscriminator = usernameState.getDiscriminator() != null || UsernameEditRepository.getUsername() != null,
      username = usernameState.usernameOrNull() ?: state.username.takeIf { usernameState is UsernameState.Loading },
      reserving = usernameState.isInProgress
    )
  }

  private fun UsernameEditStateMachine.State.isDiscriminatorSetByUser(): Boolean {
    return this is UsernameEditStateMachine.UserEnteredDiscriminator || this is UsernameEditStateMachine.UserEnteredNicknameAndDiscriminator
  }

  private fun UsernameState.usernameOrNull(): String? {
    return if (this is UsernameState.Loading || this is UsernameState.NoUsername) null else requireUsername().username
  }

  private fun InvalidReason.toNicknameError(): UsernameEditState.UsernameError {
    return when (this) {
      InvalidReason.TOO_SHORT -> UsernameEditState.UsernameError.TOO_SHORT
      InvalidReason.TOO_LONG -> UsernameEditState.UsernameError.TOO_LONG
      InvalidReason.STARTS_WITH_NUMBER -> UsernameEditState.UsernameError.CANNOT_START_WITH_NUMBER
      InvalidReason.INVALID_CHARACTERS -> UsernameEditState.UsernameError.INVALID_CHARACTERS
      InvalidReason.INVALID_NUMBER,
      InvalidReason.INVALID_NUMBER_00,
      InvalidReason.INVALID_NUMBER_PREFIX_0 -> error("Unexpected reason $this")
    }
  }

  private fun InvalidReason.toDiscriminatorError(): UsernameEditState.UsernameError {
    return when (this) {
      InvalidReason.TOO_SHORT -> UsernameEditState.UsernameError.DISCRIMINATOR_TOO_SHORT
      InvalidReason.TOO_LONG -> UsernameEditState.UsernameError.DISCRIMINATOR_TOO_LONG
      InvalidReason.INVALID_CHARACTERS -> UsernameEditState.UsernameError.DISCRIMINATOR_HAS_INVALID_CHARACTERS
      InvalidReason.INVALID_NUMBER_00 -> UsernameEditState.UsernameError.DISCRIMINATOR_CANNOT_BE_00
      InvalidReason.INVALID_NUMBER_PREFIX_0 -> UsernameEditState.UsernameError.DISCRIMINATOR_CANNOT_START_WITH_0
      else -> UsernameEditState.UsernameError.INVALID_GENERIC
    }
  }
}

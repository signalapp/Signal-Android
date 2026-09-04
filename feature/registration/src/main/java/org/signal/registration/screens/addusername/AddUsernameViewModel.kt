/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.addusername

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.UsernameUtil
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.network.service.UsernameService.ConfirmUsernameError
import org.signal.network.service.UsernameService.ReserveUsernameError
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository
import org.whispersystems.signalservice.api.util.discriminator
import kotlin.time.Duration.Companion.milliseconds

/**
 * View model for [AddUsernameScreen].
 *
 * As the user types, we debounce their input and then validate it locally. If it's valid, we reserve a username on the
 * service, which is what lets us show the discriminator while they type and detect taken nicknames early. The
 * discriminator is normally assigned by the service, but the user can type their own, in which case we reserve that
 * exact pairing instead. Tapping "next" confirms the reservation, making it the account's actual username.
 */
@OptIn(FlowPreview::class)
class AddUsernameViewModel(
  private val repository: RegistrationRepository,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<AddUsernameScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(AddUsernameViewModel::class)

    private val ENTRY_DEBOUNCE = 500.milliseconds
  }

  private val _state = MutableStateFlow(AddUsernameState())
  val state: StateFlow<AddUsernameState> = _state.asStateFlow()

  private val entryChanges = MutableSharedFlow<AddUsernameScreenEvents.EntrySettled>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  /** The in-flight reservation request. Only one may be live at a time -- starting a new one cancels the old one. */
  private var reserveJob: Job? = null

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)

    entryChanges
      .distinctUntilChanged()
      .debounce(ENTRY_DEBOUNCE)
      .onEach { onEvent(it) }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: AddUsernameScreenEvents) {
    applyEvent(_state.value, event, parentEventEmitter) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(
    state: AddUsernameState,
    event: AddUsernameScreenEvents,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (AddUsernameState) -> Unit
  ) {
    when (event) {
      is AddUsernameScreenEvents.UsernameChanged -> applyUsernameChanged(state, event.value, stateEmitter)
      is AddUsernameScreenEvents.DiscriminatorChanged -> applyDiscriminatorChanged(state, event.value, stateEmitter)
      is AddUsernameScreenEvents.EntrySettled -> applyEntrySettled(state, event, stateEmitter)
      is AddUsernameScreenEvents.ReservationCompleted -> applyReservationCompleted(state, event, stateEmitter)
      is AddUsernameScreenEvents.LearnMoreClicked -> stateEmitter(state.copy(dialogs = state.dialogs.copy(learnMore = true)))
      is AddUsernameScreenEvents.LearnMoreDialogDismissed -> stateEmitter(state.copy(dialogs = state.dialogs.copy(learnMore = false)))
      is AddUsernameScreenEvents.SkipClicked -> stateEmitter(state.copy(dialogs = state.dialogs.copy(confirmSkip = true)))
      is AddUsernameScreenEvents.SkipConfirmed -> applySkipConfirmed(parentEventEmitter)
      is AddUsernameScreenEvents.SkipDialogDismissed -> stateEmitter(state.copy(dialogs = state.dialogs.copy(confirmSkip = false)))
      is AddUsernameScreenEvents.NextClicked -> applyNextClicked(state, parentEventEmitter, stateEmitter)
      is AddUsernameScreenEvents.NetworkErrorDialogDismissed -> applyDialogDismissed(state, stateEmitter) { it.copy(networkError = false) }
      is AddUsernameScreenEvents.UnknownErrorDialogDismissed -> applyDialogDismissed(state, stateEmitter) { it.copy(unknownError = false) }
      is AddUsernameScreenEvents.UsernameUnavailableDialogDismissed -> applyDialogDismissed(state, stateEmitter) { it.copy(usernameUnavailable = false) }
      is AddUsernameScreenEvents.RateLimitedDialogDismissed -> applyDialogDismissed(state, stateEmitter) { it.copy(rateLimited = false) }
      is AddUsernameScreenEvents.ReservationLapsedDialogDismissed -> applyDialogDismissed(state, stateEmitter) { it.copy(reservationLapsed = false) }
    }
  }

  private fun applyUsernameChanged(state: AddUsernameState, username: String, stateEmitter: (AddUsernameState) -> Unit) {
    if (username == state.username) {
      return
    }

    reserveJob?.cancel()

    val updated = state.copy(
      username = username,
      validationError = null,
      reservation = null,
      isReserving = false
    )

    stateEmitter(updated)
    scheduleReservation(updated)
  }

  /**
   * A blank discriminator hands control back to the service, matching the behavior of clearing the field in the app's
   * username editor.
   */
  private fun applyDiscriminatorChanged(state: AddUsernameState, discriminator: String, stateEmitter: (AddUsernameState) -> Unit) {
    if (discriminator == state.discriminator) {
      return
    }

    reserveJob?.cancel()

    val updated = state.copy(
      discriminator = discriminator,
      isDiscriminatorUserSet = discriminator.isNotBlank(),
      validationError = null,
      reservation = null,
      isReserving = false
    )

    stateEmitter(updated)
    scheduleReservation(updated)
  }

  private fun scheduleReservation(state: AddUsernameState) {
    if (state.username.isNotBlank()) {
      entryChanges.tryEmit(AddUsernameScreenEvents.EntrySettled(state.username, state.requestedDiscriminator))
    }
  }

  private fun applyEntrySettled(state: AddUsernameState, event: AddUsernameScreenEvents.EntrySettled, stateEmitter: (AddUsernameState) -> Unit) {
    if (event.nickname != state.username || event.discriminator != state.requestedDiscriminator || event.nickname.isBlank()) {
      return
    }

    val nicknameError = checkNickname(event.nickname)
    if (nicknameError != null) {
      stateEmitter(state.copy(validationError = nicknameError))
      return
    }

    val discriminatorError = event.discriminator?.let { checkDiscriminator(it) }
    if (discriminatorError != null) {
      stateEmitter(state.copy(validationError = discriminatorError))
      return
    }

    stateEmitter(state.copy(isReserving = true))

    reserveJob?.cancel()
    reserveJob = viewModelScope.launch {
      val result = repository.reserveUsername(event.nickname, event.discriminator)
      onEvent(AddUsernameScreenEvents.ReservationCompleted(event.nickname, event.discriminator, result))
    }
  }

  private fun applyReservationCompleted(
    state: AddUsernameState,
    event: AddUsernameScreenEvents.ReservationCompleted,
    stateEmitter: (AddUsernameState) -> Unit
  ) {
    if (event.nickname != state.username || event.discriminator != state.requestedDiscriminator) {
      return
    }

    when (val result = event.result) {
      is RequestResult.Success -> {
        Log.i(TAG, "Successfully reserved a username.")
        stateEmitter(
          state.copy(
            isReserving = false,
            reservation = result.result,
            discriminator = result.result.discriminator,
            showDiscriminator = true
          )
        )
      }

      is RequestResult.NonSuccess -> when (result.error) {
        is ReserveUsernameError.NicknameInvalid, is ReserveUsernameError.NotAvailable -> {
          Log.w(TAG, "Could not reserve a username: ${result.error}")
          val error = if (event.discriminator != null) {
            AddUsernameState.ValidationError.DISCRIMINATOR_NOT_AVAILABLE
          } else {
            AddUsernameState.ValidationError.NOT_AVAILABLE
          }
          stateEmitter(state.copy(isReserving = false, validationError = error))
        }

        is ReserveUsernameError.RateLimited -> {
          Log.w(TAG, "Rate limited while reserving a username.")
          stateEmitter(state.copy(isReserving = false, dialogs = state.dialogs.copy(rateLimited = true)))
        }
      }

      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "Network error while reserving a username.", result.networkError)
        stateEmitter(state.copy(isReserving = false, dialogs = state.dialogs.copy(networkError = true)))
      }

      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Application error while reserving a username.", result.cause)
        stateEmitter(state.copy(isReserving = false, dialogs = state.dialogs.copy(unknownError = true)))
      }
    }
  }

  private fun applySkipConfirmed(parentEventEmitter: (RegistrationFlowEvent) -> Unit) {
    Log.i(TAG, "Skipping username creation.")
    parentEventEmitter(RegistrationFlowEvent.RegistrationComplete)
  }

  private suspend fun applyNextClicked(
    state: AddUsernameState,
    parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    stateEmitter: (AddUsernameState) -> Unit
  ) {
    val reservation = state.reservation
    if (!state.isSubmittable || reservation == null) {
      return
    }

    stateEmitter(state.copy(showSpinner = true))

    when (val result = repository.confirmUsername(reservation)) {
      is RequestResult.Success -> {
        Log.i(TAG, "Username confirmed.")
        parentEventEmitter(RegistrationFlowEvent.RegistrationComplete)
      }

      is RequestResult.NonSuccess -> when (result.error) {
        is ConfirmUsernameError.ReservationInvalid -> {
          Log.w(TAG, "The reservation has lapsed or was never made.")
          stateEmitter(state.copy(showSpinner = false, reservation = null, dialogs = state.dialogs.copy(reservationLapsed = true)))
        }

        is ConfirmUsernameError.NotAvailable -> {
          Log.w(TAG, "The reserved username is no longer available.")
          stateEmitter(state.copy(showSpinner = false, reservation = null, dialogs = state.dialogs.copy(usernameUnavailable = true)))
        }

        is ConfirmUsernameError.BadRequest, is ConfirmUsernameError.GenerationFailed -> {
          Log.w(TAG, "Failed to confirm the username: ${result.error}")
          stateEmitter(state.copy(showSpinner = false, dialogs = state.dialogs.copy(unknownError = true)))
        }

        is ConfirmUsernameError.RateLimited -> {
          Log.w(TAG, "Rate limited while confirming the username.")
          stateEmitter(state.copy(showSpinner = false, dialogs = state.dialogs.copy(rateLimited = true)))
        }
      }

      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "Network error while confirming the username.", result.networkError)
        stateEmitter(state.copy(showSpinner = false, dialogs = state.dialogs.copy(networkError = true)))
      }

      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Application error while confirming the username.", result.cause)
        stateEmitter(state.copy(showSpinner = false, dialogs = state.dialogs.copy(unknownError = true)))
      }
    }
  }

  /**
   * Clears the dismissed dialog, then restarts the reserve flow if an earlier failure left a valid nickname without a
   * reservation. Nothing retries while a dialog is up -- recovery is always in response to the user's dismissal.
   */
  private fun applyDialogDismissed(
    state: AddUsernameState,
    stateEmitter: (AddUsernameState) -> Unit,
    clearDialog: (AddUsernameState.Dialogs) -> AddUsernameState.Dialogs
  ) {
    stateEmitter(state.copy(dialogs = clearDialog(state.dialogs)))

    if (state.username.isNotBlank() && state.validationError == null && state.reservation == null && !state.isReserving) {
      onEvent(AddUsernameScreenEvents.EntrySettled(state.username, state.requestedDiscriminator))
    }
  }

  private fun checkNickname(nickname: String): AddUsernameState.ValidationError? {
    return when (UsernameUtil.checkNickname(nickname)) {
      null -> null
      UsernameUtil.InvalidReason.TOO_SHORT -> AddUsernameState.ValidationError.TOO_SHORT
      UsernameUtil.InvalidReason.TOO_LONG -> AddUsernameState.ValidationError.TOO_LONG
      UsernameUtil.InvalidReason.STARTS_WITH_NUMBER -> AddUsernameState.ValidationError.CANNOT_START_WITH_DIGIT
      else -> AddUsernameState.ValidationError.INVALID_CHARACTERS
    }
  }

  private fun checkDiscriminator(discriminator: String): AddUsernameState.ValidationError? {
    return when (UsernameUtil.checkDiscriminator(discriminator)) {
      null -> null
      UsernameUtil.InvalidReason.TOO_SHORT -> AddUsernameState.ValidationError.DISCRIMINATOR_TOO_SHORT
      UsernameUtil.InvalidReason.TOO_LONG -> AddUsernameState.ValidationError.DISCRIMINATOR_TOO_LONG
      UsernameUtil.InvalidReason.INVALID_NUMBER_00 -> AddUsernameState.ValidationError.DISCRIMINATOR_CANNOT_BE_00
      UsernameUtil.InvalidReason.INVALID_NUMBER_PREFIX_0 -> AddUsernameState.ValidationError.DISCRIMINATOR_CANNOT_START_WITH_ZERO
      else -> AddUsernameState.ValidationError.DISCRIMINATOR_INVALID_CHARACTERS
    }
  }
}

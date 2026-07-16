/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.verificationcode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.registration.NetworkController
import org.signal.registration.PendingRestoreOption
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute
import org.signal.registration.screens.util.navigateBack
import org.signal.registration.screens.util.navigateTo
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class VerificationCodeViewModel(
  private val repository: RegistrationRepository,
  private val parentState: StateFlow<RegistrationFlowState>,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
  smsCodeEvents: Flow<String> = emptyFlow(),
  private val clock: () -> Long = { System.currentTimeMillis() }
) : EventDrivenViewModel<VerificationCodeScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(VerificationCodeViewModel::class)

    private const val CODE_LENGTH = VerificationCodeState.CODE_LENGTH

    /**
     * How old the in-progress registration data can be before we assume the verification session has expired and
     * restart the flow. Checked whenever the screen is foregrounded.
     */
    private val IN_PROGRESS_DATA_TIMEOUT = 15.minutes

    /**
     * Cold [Flow] of verification codes automatically retrieved from incoming SMS messages via the Play Services SMS
     * retriever. Registers a [BroadcastReceiver] for [SmsRetriever.SMS_RETRIEVED_ACTION] while collected, and
     * unregisters it when collection stops.
     */
    fun smsCodeFlow(context: Context): Flow<String> = callbackFlow {
      val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          if (intent.action != SmsRetriever.SMS_RETRIEVED_ACTION) {
            return
          }

          val status = intent.extras?.get(SmsRetriever.EXTRA_STATUS) as? Status
          when (status?.statusCode) {
            CommonStatusCodes.SUCCESS -> {
              val code = VerificationCodeParser.parse(intent.extras?.getString(SmsRetriever.EXTRA_SMS_MESSAGE))
              if (code != null) {
                Log.i(TAG, "Received verification code via SMS retriever.")
                trySend(code)
              } else {
                Log.w(TAG, "Could not parse verification code from retrieved SMS.")
              }
            }
            CommonStatusCodes.TIMEOUT -> Log.w(TAG, "Timed out waiting for the verification SMS to arrive.")
            else -> Log.w(TAG, "SMS retriever broadcast had an unexpected status code: ${status?.statusCode}")
          }
        }
      }

      ContextCompat.registerReceiver(context, receiver, IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION), SmsRetriever.SEND_PERMISSION, null, ContextCompat.RECEIVER_EXPORTED)
      awaitClose { context.unregisterReceiver(receiver) }
    }
  }

  private val _state = MutableStateFlow(VerificationCodeState())
  val state: StateFlow<VerificationCodeState> = _state.asStateFlow()

  private var nextSmsAvailableAt: Duration = 0.seconds
  private var nextCallAvailableAt: Duration = 0.seconds

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)

    parentState
      .onEach { onEvent(VerificationCodeScreenEvents.ParentStateChanged(it)) }
      .launchIn(viewModelScope)

    viewModelScope.launch {
      smsCodeEvents.collect { code ->
        onEvent(VerificationCodeScreenEvents.CodeAutoFilled(code))
      }
    }
  }

  override suspend fun processEvent(event: VerificationCodeScreenEvents) {
    applyEvent(_state.value, event) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(state: VerificationCodeState, event: VerificationCodeScreenEvents, stateEmitter: (VerificationCodeState) -> Unit) {
    val result = when (event) {
      is VerificationCodeScreenEvents.ParentStateChanged -> applyParentState(state, event.parentState)
      is VerificationCodeScreenEvents.CodeEntered -> submitCode(state, event.code, stateEmitter)
      is VerificationCodeScreenEvents.DigitChanged -> applyDigitChanged(state, event.index, event.value, stateEmitter)
      is VerificationCodeScreenEvents.CodeAutoFilled -> state.copy(autoFillCode = event.code)
      is VerificationCodeScreenEvents.ConsumeAutoFillCode -> state.copy(autoFillCode = null)
      is VerificationCodeScreenEvents.WrongNumber -> state.also { parentEventEmitter.navigateTo(RegistrationRoute.PhoneNumberEntry) }
      is VerificationCodeScreenEvents.ResendSms -> applyResendCode(state, NetworkController.VerificationCodeTransport.SMS)
      is VerificationCodeScreenEvents.CallMe -> applyResendCode(state, NetworkController.VerificationCodeTransport.VOICE)
      is VerificationCodeScreenEvents.HavingTrouble -> state.copy(showContactSupportSheet = true)
      is VerificationCodeScreenEvents.DismissContactSupport -> state.copy(showContactSupportSheet = false)
      is VerificationCodeScreenEvents.NetworkErrorSnackbarDismissed -> state.copy(snackbars = state.snackbars.copy(networkError = false))
      is VerificationCodeScreenEvents.UnknownErrorSnackbarDismissed -> state.copy(snackbars = state.snackbars.copy(unknownError = false))
      is VerificationCodeScreenEvents.RateLimitedSnackbarDismissed -> state.copy(snackbars = state.snackbars.copy(rateLimitedRetryAfter = null))
      is VerificationCodeScreenEvents.UnableToSendSmsSnackbarDismissed -> state.copy(snackbars = state.snackbars.copy(unableToSendSms = false))
      is VerificationCodeScreenEvents.CouldNotRequestCodeWithSelectedTransportSnackbarDismissed -> state.copy(snackbars = state.snackbars.copy(couldNotRequestCodeWithSelectedTransport = false))
      is VerificationCodeScreenEvents.IncorrectVerificationCodeSnackbarDismissed -> state.copy(snackbars = state.snackbars.copy(incorrectVerificationCode = false))
      is VerificationCodeScreenEvents.RegistrationErrorSnackbarDismissed -> state.copy(snackbars = state.snackbars.copy(registrationError = false))
      is VerificationCodeScreenEvents.CountdownTick -> applyCountdownTick(state)
      is VerificationCodeScreenEvents.Foregrounded -> applyForegrounded(state)
    }
    stateEmitter(result)
  }

  /**
   * If the in-progress registration data has grown older than [IN_PROGRESS_DATA_TIMEOUT], the verification session has
   * likely expired server-side. Rather than let the user enter a code only to fail, restart the flow from the beginning.
   */
  private suspend fun applyForegrounded(state: VerificationCodeState): VerificationCodeState {
    val lastUpdated = repository.getInProgressRegistrationDataLastUpdated() ?: return state
    val age = (clock() - lastUpdated).milliseconds

    if (age >= IN_PROGRESS_DATA_TIMEOUT) {
      Log.w(TAG, "[Foregrounded] In-progress registration data is stale (${age.inWholeMilliseconds}ms old). Restarting the flow.")
      parentEventEmitter(RegistrationFlowEvent.ResetState)
    }

    return state
  }

  private fun applyParentState(state: VerificationCodeState, parentState: RegistrationFlowState): VerificationCodeState {
    if (parentState.sessionMetadata == null || parentState.sessionE164 == null) {
      Log.w(TAG, "Parent state is missing session metadata or e164! Resetting.")
      parentEventEmitter(RegistrationFlowEvent.ResetState)
      return state
    }

    val sessionChanged = state.sessionMetadata?.id != parentState.sessionMetadata.id

    val rateLimits = if (sessionChanged) {
      computeRateLimits(parentState.sessionMetadata)
    } else {
      state.rateLimits
    }

    return state.copy(
      sessionMetadata = parentState.sessionMetadata,
      e164 = parentState.sessionE164,
      rateLimits = rateLimits
    )
  }

  /**
   * Decrements countdown timers by 1 second, ensuring they don't go below 0.
   */
  private fun applyCountdownTick(state: VerificationCodeState): VerificationCodeState {
    return state.copy(
      rateLimits = SmsAndCallRateLimits(
        smsResendTimeRemaining = (state.rateLimits.smsResendTimeRemaining - 1.seconds).coerceAtLeast(0.seconds),
        callRequestTimeRemaining = (state.rateLimits.callRequestTimeRemaining - 1.seconds).coerceAtLeast(0.seconds)
      )
    )
  }

  /**
   * Interprets the raw [value] reported by the digit field at [index] and updates the digits and focus accordingly:
   *
   * - an empty [value] is a backspace, deleting a digit and moving focus back
   * - a single digit is recorded and focus advances, submitting once the full code is present
   * - multi-character input (e.g. a pasted "123-456" or an auto-filled SMS code) populates every field at once and
   *   submits, all in this single reducer pass
   */
  private suspend fun applyDigitChanged(
    state: VerificationCodeState,
    index: Int,
    value: String,
    stateEmitter: (VerificationCodeState) -> Unit
  ): VerificationCodeState {
    check(index in state.digits.indices) { "[DigitChanged] Out of bounds index $index." }

    if (value.isEmpty()) {
      return deleteDigit(state, index)
    }

    val currentValue = state.digits[index]
    val remainder = if (currentValue.isNotEmpty()) value.replaceFirst(currentValue, "") else value
    val addedDigits = remainder.filter { it.isDigit() }

    return when {
      addedDigits.isEmpty() -> state

      addedDigits.length == 1 -> {
        val updated = state.copy(
          digits = state.digits.toMutableList().also { it[index] = addedDigits },
          focusedDigitIndex = (index + 1).coerceAtMost(CODE_LENGTH - 1)
        )

        if (updated.isComplete && !updated.isSubmittingCode) {
          submitCode(updated, updated.code, stateEmitter)
        } else {
          updated
        }
      }

      else -> applyFullCode(state, addedDigits, stateEmitter)
    }
  }

  /**
   * Populates every digit field from a full pasted or auto-filled [code] in a single reducer pass and submits it.
   * Multi-character input that isn't a complete code is ignored.
   */
  private suspend fun applyFullCode(
    state: VerificationCodeState,
    code: String,
    stateEmitter: (VerificationCodeState) -> Unit
  ): VerificationCodeState {
    if (code.length != CODE_LENGTH) {
      Log.w(TAG, "[DigitChanged] Ignoring multi-character input containing ${code.length} digits.")
      return state
    }

    val updated = state.copy(
      digits = code.map { it.toString() },
      focusedDigitIndex = CODE_LENGTH - 1
    )

    return if (!updated.isSubmittingCode) {
      submitCode(updated, updated.code, stateEmitter)
    } else {
      updated
    }
  }

  /**
   * Deletes the digit at [index] (or the previous one, if [index] is already empty), shifts any following digits left
   * to fill the gap, and moves focus back.
   */
  private fun deleteDigit(state: VerificationCodeState, index: Int): VerificationCodeState {
    val deleteAt = if (state.digits[index].isNotEmpty()) index else index - 1
    if (deleteAt < 0) {
      return state
    }

    val newDigits = state.digits.toMutableList().apply {
      for (j in deleteAt until CODE_LENGTH - 1) {
        this[j] = this[j + 1]
      }
      this[CODE_LENGTH - 1] = ""
    }

    return state.copy(digits = newDigits, focusedDigitIndex = (index - 1).coerceAtLeast(0))
  }

  /**
   * Emits an intermediate submitting state and then runs the submission, clearing the submitting flag when done.
   */
  private suspend fun submitCode(state: VerificationCodeState, code: String, stateEmitter: (VerificationCodeState) -> Unit): VerificationCodeState {
    stateEmitter(state.copy(isSubmittingCode = true))
    return applyCodeEntered(state, code).copy(isSubmittingCode = false)
  }

  private suspend fun applyCodeEntered(inputState: VerificationCodeState, code: String): VerificationCodeState {
    var state = inputState
    var sessionMetadata = state.sessionMetadata ?: return state.also {
      parentEventEmitter(RegistrationFlowEvent.ResetState)
    }

    // TODO should we be checking on whether we need to do more captcha stuff?

    val result = repository.submitVerificationCode(sessionMetadata.id, code)

    sessionMetadata = when (result) {
      is RequestResult.Success -> {
        result.result
      }
      is RequestResult.NonSuccess -> {
        when (val error = result.error) {
          is NetworkController.SubmitVerificationCodeError.InvalidSessionIdOrVerificationCode -> {
            Log.w(TAG, "[SubmitCode] Invalid sessionId or verification code entered. This is distinct from an *incorrect* verification code. Body: ${error.message}")
            val newAttempts = state.incorrectCodeAttempts + 1
            return state.copy(snackbars = state.snackbars.copy(incorrectVerificationCode = true), incorrectCodeAttempts = newAttempts, digits = VerificationCodeState.emptyDigits(), focusedDigitIndex = 0)
          }
          is NetworkController.SubmitVerificationCodeError.SessionNotFound -> {
            Log.w(TAG, "[SubmitCode] Session not found: ${error.message}. Navigating back to phone number entry.")
            parentEventEmitter.navigateBack()
            return state
          }
          is NetworkController.SubmitVerificationCodeError.SessionAlreadyVerifiedOrNoCodeRequested -> {
            if (error.session.verified) {
              Log.i(TAG, "[SubmitCode] Session already had number verified, continuing with registration.")
              error.session
            } else {
              Log.w(TAG, "[SubmitCode] No code was requested for this session? Need to have user re-submit.")
              parentEventEmitter.navigateBack()
              return state
            }
          }
          is NetworkController.SubmitVerificationCodeError.RateLimited -> {
            Log.w(TAG, "[SubmitCode] Rate limited  (retryAfter: ${error.retryAfter}).")
            return state.copy(snackbars = state.snackbars.copy(rateLimitedRetryAfter = error.retryAfter))
          }
        }
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "[SubmitCode] Network error.", result.networkError)
        return state.copy(snackbars = state.snackbars.copy(networkError = true))
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "[SubmitCode] Unknown error when submitting verification code.", result.cause)
        return state.copy(snackbars = state.snackbars.copy(unknownError = true))
      }
    }

    state = state.copy(sessionMetadata = sessionMetadata)

    if (!sessionMetadata.verified) {
      Log.w(TAG, "[SubmitCode] Verification code was incorrect.")
      val newAttempts = state.incorrectCodeAttempts + 1
      return state.copy(snackbars = state.snackbars.copy(incorrectVerificationCode = true), incorrectCodeAttempts = newAttempts, digits = VerificationCodeState.emptyDigits(), focusedDigitIndex = 0)
    }

    // Attempt to register
    val registerResult = repository.registerAccountWithSession(e164 = state.e164, sessionId = sessionMetadata.id, skipDeviceTransfer = true)

    return when (registerResult) {
      is RequestResult.Success -> {
        val (response, keyMaterial) = registerResult.result

        parentEventEmitter(RegistrationFlowEvent.Registered(keyMaterial.accountEntropyPool, response.storageCapable))

        val pendingRestore = pendingRestoreNavigation()
        when {
          pendingRestore != null -> {
            Log.i(TAG, "[Register] A restore was deferred until after SMS verification. Resuming it now.")
            parentEventEmitter.navigateTo(pendingRestore)
          }
          response.reregistration && parentState.value.pendingRestoreOption == null -> parentEventEmitter.navigateTo(RegistrationRoute.ArchiveRestoreSelection.forPostRegisterWithPinUnknown())
          response.storageCapable -> parentEventEmitter.navigateTo(RegistrationRoute.PinEntryForSvrRestore)
          else -> parentEventEmitter.navigateTo(RegistrationRoute.PinCreate)
        }
        state
      }
      is RequestResult.NonSuccess -> {
        when (val error = registerResult.error) {
          is NetworkController.RegisterAccountError.SessionNotFoundOrNotVerified -> {
            Log.w(TAG, "[Register] Session not found or not verified: ${error.message}. Navigating back to phone number entry.")
            parentEventEmitter.navigateBack()
            state
          }
          is NetworkController.RegisterAccountError.DeviceTransferPossible -> {
            error("[Register] Got told a device transfer is possible. We should never get into this state. Resetting.")
          }
          is NetworkController.RegisterAccountError.RegistrationLock -> {
            Log.w(TAG, "[Register] Reglocked.")
            parentEventEmitter.navigateTo(
              RegistrationRoute.PinEntryForRegistrationLock(
                timeRemaining = error.data.timeRemaining,
                svrCredentials = error.data.svr2Credentials
              )
            )
            state
          }
          is NetworkController.RegisterAccountError.RateLimited -> {
            Log.w(TAG, "[Register] Rate limited (retryAfter: ${error.retryAfter}).")
            state.copy(snackbars = state.snackbars.copy(rateLimitedRetryAfter = error.retryAfter))
          }
          is NetworkController.RegisterAccountError.InvalidRequest -> {
            Log.w(TAG, "[Register] Invalid request when registering account: ${error.message}")
            state.copy(snackbars = state.snackbars.copy(registrationError = true))
          }
          is NetworkController.RegisterAccountError.RegistrationRecoveryPasswordIncorrect -> {
            error("[Register] Got told the registration recovery password incorrect. We don't use the RRP in this flow, and should never get this error. Resetting. Message: ${error.message}")
          }
        }
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "[Register] Network error.", registerResult.networkError)
        state.copy(snackbars = state.snackbars.copy(networkError = true))
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "[Register] Unknown error when registering account.", registerResult.cause)
        state.copy(snackbars = state.snackbars.copy(unknownError = true))
      }
    }
  }

  /**
   * If the user pre-selected a restore (see [RegistrationFlowState.pendingRestoreOption]) and it hasn't run yet,
   * returns the restore screen to resume it now that the account is registered; otherwise null. Used to pick a
   * restore back up after it was deferred to SMS verification (e.g. a local backup that belongs to a different account).
   */
  private fun pendingRestoreNavigation(): RegistrationRoute? {
    val aep = parentState.value.unverifiedRestoredAep ?: return null
    return when (parentState.value.pendingRestoreOption) {
      PendingRestoreOption.LocalBackup -> RegistrationRoute.LocalBackupRestore(isPreRegistration = false, aep = aep)
      PendingRestoreOption.RemoteBackup -> RegistrationRoute.RemoteRestore(aep)
      null -> null
    }
  }

  private suspend fun applyResendCode(
    state: VerificationCodeState,
    transport: NetworkController.VerificationCodeTransport
  ): VerificationCodeState {
    if (state.sessionMetadata == null) {
      parentEventEmitter(RegistrationFlowEvent.ResetState)
      return state
    }

    val result = repository.requestVerificationCode(
      sessionId = state.sessionMetadata.id,
      smsAutoRetrieveCodeSupported = repository.registerSmsListener(),
      transport = transport
    )

    return when (result) {
      is RequestResult.Success -> {
        Log.i(TAG, "[RequestCode][$transport] Successfully requested verification code.")
        parentEventEmitter(RegistrationFlowEvent.SessionUpdated(result.result))
        state.copy(
          sessionMetadata = result.result,
          rateLimits = computeRateLimits(result.result)
        )
      }
      is RequestResult.NonSuccess -> {
        when (val error = result.error) {
          is NetworkController.RequestVerificationCodeError.InvalidRequest -> {
            Log.w(TAG, "[RequestCode][$transport] Invalid request: ${error.message}")
            state.copy(snackbars = state.snackbars.copy(unknownError = true))
          }
          is NetworkController.RequestVerificationCodeError.RateLimited -> {
            Log.w(TAG, "[RequestCode][$transport] Rate limited (retryAfter: ${error.retryAfter}).")
            parentEventEmitter(RegistrationFlowEvent.SessionUpdated(error.session))
            state.copy(
              snackbars = state.snackbars.copy(rateLimitedRetryAfter = error.retryAfter),
              sessionMetadata = error.session,
              rateLimits = computeRateLimits(error.session)
            )
          }
          is NetworkController.RequestVerificationCodeError.CouldNotFulfillWithRequestedTransport -> {
            Log.w(TAG, "[RequestCode][$transport] Could not fulfill with requested transport.")
            parentEventEmitter(RegistrationFlowEvent.SessionUpdated(error.session))
            state.copy(
              snackbars = state.snackbars.copy(couldNotRequestCodeWithSelectedTransport = true),
              sessionMetadata = error.session,
              rateLimits = computeRateLimits(error.session)
            )
          }
          is NetworkController.RequestVerificationCodeError.InvalidSessionId -> {
            Log.w(TAG, "[RequestCode][$transport] Invalid session ID: ${error.message}. Navigating back to phone number entry.")
            parentEventEmitter.navigateBack()
            state
          }
          is NetworkController.RequestVerificationCodeError.MissingRequestInformationOrAlreadyVerified -> {
            Log.w(TAG, "[RequestCode][$transport] Missing request information or already verified.")
            parentEventEmitter(RegistrationFlowEvent.SessionUpdated(error.session))
            state.copy(
              snackbars = state.snackbars.copy(unableToSendSms = true),
              sessionMetadata = error.session,
              rateLimits = computeRateLimits(error.session)
            )
          }
          is NetworkController.RequestVerificationCodeError.SessionNotFound -> {
            Log.w(TAG, "[RequestCode][$transport] Session not found: ${error.message}. Navigating back to phone number entry.")
            parentEventEmitter.navigateBack()
            state
          }
          is NetworkController.RequestVerificationCodeError.ThirdPartyServiceError -> {
            Log.w(TAG, "[RequestCode][$transport] Third party service error. ${error.data}")
            state.copy(snackbars = state.snackbars.copy(unableToSendSms = true))
          }
        }
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "[RequestCode][$transport] Network error.", result.networkError)
        state.copy(snackbars = state.snackbars.copy(networkError = true))
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "[RequestCode][$transport] Unknown application error.", result.cause)
        state.copy(snackbars = state.snackbars.copy(unknownError = true))
      }
    }
  }

  private fun computeRateLimits(session: NetworkController.SessionMetadata): SmsAndCallRateLimits {
    val now = clock().milliseconds
    nextSmsAvailableAt = now + (session.nextSms?.seconds ?: nextSmsAvailableAt)
    nextCallAvailableAt = now + (session.nextCall?.seconds ?: nextCallAvailableAt)

    return SmsAndCallRateLimits(
      smsResendTimeRemaining = (nextSmsAvailableAt - clock().milliseconds).coerceAtLeast(0.seconds),
      callRequestTimeRemaining = (nextCallAvailableAt - clock().milliseconds).coerceAtLeast(0.seconds)
    )
  }

  /**
   * @param smsCodeEvents The stream of auto-retrieved verification codes. Tests can inject codes directly; production
   *   should use the [Context]-based constructor, which builds a real SMS retriever flow.
   */
  class Factory(
    private val repository: RegistrationRepository,
    private val parentState: StateFlow<RegistrationFlowState>,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit,
    private val smsCodeEvents: Flow<String>
  ) : ViewModelProvider.Factory {

    /**
     * Builds a real SMS retriever flow from [context]. Prefer the application context.
     */
    constructor(
      context: Context,
      repository: RegistrationRepository,
      parentState: StateFlow<RegistrationFlowState>,
      parentEventEmitter: (RegistrationFlowEvent) -> Unit
    ) : this(repository, parentState, parentEventEmitter, smsCodeFlow(context))

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return VerificationCodeViewModel(repository, parentState, parentEventEmitter, smsCodeEvents) as T
    }
  }
}

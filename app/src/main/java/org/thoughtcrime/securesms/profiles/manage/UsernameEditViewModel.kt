package org.thoughtcrime.securesms.profiles.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import org.signal.core.util.Result
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.signal.libsignal.usernames.Username
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.UsernameDeleteResult
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.UsernameSetResult
import org.thoughtcrime.securesms.util.NetworkUtil
import org.thoughtcrime.securesms.util.UsernameUtil.InvalidReason
import org.thoughtcrime.securesms.util.UsernameUtil.checkDiscriminator
import org.thoughtcrime.securesms.util.UsernameUtil.checkNickname
import org.thoughtcrime.securesms.util.rx.RxStore
import org.whispersystems.signalservice.api.util.Usernames
import java.util.concurrent.TimeUnit

/**
 * Manages the state around username updates.
 *
 *
 * A note on naming conventions:
 * Usernames are made up of two discrete components, a nickname and a discriminator. They are formatted thusly:
 *
 * nickname.discriminator
 */
internal class UsernameEditViewModel private constructor(private val mode: UsernameEditMode) : ViewModel() {
  private val events: PublishSubject<Event> = PublishSubject.create()
  private val disposables: CompositeDisposable = CompositeDisposable()

  private val uiState: RxStore<State> = RxStore(
    defaultValue = State(
      buttonState = ButtonState.SUBMIT_DISABLED,
      usernameStatus = UsernameStatus.NONE,
      usernameState = SignalStore.account.username?.let { UsernameState.Set(Username(it)) } ?: UsernameState.NoUsername
    ),
    scheduler = Schedulers.computation()
  )

  private val stateMachineStore = RxStore<UsernameEditStateMachine.State>(
    defaultValue = UsernameEditStateMachine.NoUserEntry(
      nickname = SignalStore.account.username?.split(Usernames.DELIMITER)?.first() ?: "",
      discriminator = SignalStore.account.username?.split(Usernames.DELIMITER)?.last() ?: "",
      stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM
    ),
    scheduler = Schedulers.computation()
  )

  val usernameInputState: Flowable<UsernameEditStateMachine.State> = stateMachineStore.stateFlowable.observeOn(AndroidSchedulers.mainThread())

  init {
    disposables += stateMachineStore
      .stateFlowable
      .filter { it.stateModifier == UsernameEditStateMachine.StateModifier.USER }
      .debounce(NICKNAME_PUBLISHER_DEBOUNCE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
      .subscribeBy(onNext = this::onUsernameStateUpdateDebounced)

    if (mode == UsernameEditMode.RECOVERY) {
      onNicknameUpdated(SignalStore.account.username?.split(Usernames.DELIMITER)?.first() ?: "")
      onDiscriminatorUpdated(SignalStore.account.username?.split(Usernames.DELIMITER)?.last() ?: "")
    }
  }

  override fun onCleared() {
    super.onCleared()
    disposables.clear()
    uiState.dispose()
  }

  fun onNicknameUpdated(nickname: String) {
    uiState.update { state: State ->
      if (nickname.isBlank() && SignalStore.account.username != null) {
        return@update State(
          buttonState = ButtonState.DELETE,
          usernameStatus = UsernameStatus.NONE,
          usernameState = UsernameState.NoUsername
        )
      }

      State(
        buttonState = ButtonState.SUBMIT_DISABLED,
        usernameStatus = UsernameStatus.NONE,
        usernameState = state.usernameState
      )
    }

    stateMachineStore.update {
      it.onUserChangedNickname(nickname)
    }
  }

  fun onDiscriminatorUpdated(discriminator: String) {
    uiState.update { state: State ->
      if (discriminator.isBlank() && SignalStore.account.username != null) {
        return@update State(
          buttonState = ButtonState.DELETE,
          usernameStatus = UsernameStatus.NONE,
          usernameState = UsernameState.NoUsername
        )
      }

      State(
        buttonState = ButtonState.SUBMIT_DISABLED,
        usernameStatus = UsernameStatus.NONE,
        usernameState = state.usernameState
      )
    }

    stateMachineStore.update {
      it.onUserChangedDiscriminator(discriminator)
    }
  }

  fun onUsernameSkipped() {
    SignalStore.uiHints.markHasSetOrSkippedUsernameCreation()
    events.onNext(Event.SKIPPED)
  }

  fun isSameUsernameRecovery(): Boolean {
    val usernameState = uiState.state.usernameState
    return mode == UsernameEditMode.RECOVERY &&
      usernameState is UsernameState.Reserved &&
      usernameState.requireUsername().username.lowercase() == SignalStore.account.username?.lowercase()
  }

  /**
   * @param userConfirmedResetOk True if the user is submitting this after confirming that they're ok with resetting their username via [Event.NEEDS_CONFIRM_RESET].
   */
  fun onUsernameSubmitted(userConfirmedResetOk: Boolean) {
    if (!NetworkUtil.isConnected(AppDependencies.application)) {
      events.onNext(Event.NETWORK_FAILURE)
      return
    }

    val editState = stateMachineStore.state
    val usernameState = uiState.state.usernameState
    val isCaseChange = isCaseChange(editState)

    if (!isCaseChange && SignalStore.account.username.isNotNullOrBlank() && !userConfirmedResetOk) {
      events.onNext(Event.NEEDS_CONFIRM_RESET)
      return
    }

    if (usernameState !is UsernameState.Reserved && usernameState !is UsernameState.CaseChange) {
      Log.w(TAG, "Username was submitted, current state is invalid! State: ${usernameState.javaClass.simpleName}")
      uiState.update { it.copy(buttonState = ButtonState.SUBMIT_DISABLED, usernameStatus = UsernameStatus.NONE) }
      return
    }

    if (usernameState.requireUsername().username == SignalStore.account.username && mode != UsernameEditMode.RECOVERY) {
      Log.d(TAG, "Username was submitted, but was identical to the current username. Ignoring.")
      uiState.update { it.copy(buttonState = ButtonState.SUBMIT_DISABLED, usernameStatus = UsernameStatus.NONE) }
      return
    }

    val invalidReason: InvalidReason? = checkNickname(usernameState.getNickname())
    if (invalidReason != null) {
      Log.w(TAG, "Username was submitted, but did not pass validity checks. Reason: $invalidReason")
      uiState.update { it.copy(buttonState = ButtonState.SUBMIT_DISABLED, usernameStatus = mapNicknameError(invalidReason)) }
      return
    }

    uiState.update { it.copy(buttonState = ButtonState.SUBMIT_LOADING, usernameStatus = UsernameStatus.NONE) }

    val usernameConfirmOperation: Single<UsernameSetResult> = if (isCaseChange) {
      UsernameRepository.updateUsernameDisplayForCurrentLink(usernameState.requireUsername())
    } else {
      val reservation = usernameState as UsernameState.Reserved
      UsernameRepository.confirmUsernameAndCreateNewLink(reservation.requireUsername())
    }

    disposables += usernameConfirmOperation.subscribe { result: UsernameSetResult ->
      val nickname = usernameState.getNickname()

      when (result) {
        UsernameSetResult.SUCCESS -> {
          SignalStore.uiHints.markHasSetOrSkippedUsernameCreation()
          uiState.update { State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE, it.usernameState) }
          events.onNext(Event.SUBMIT_SUCCESS)
        }

        UsernameSetResult.USERNAME_INVALID -> {
          uiState.update { State(ButtonState.SUBMIT_DISABLED, UsernameStatus.INVALID_GENERIC, it.usernameState) }
          events.onNext(Event.SUBMIT_FAIL_INVALID)
          nickname?.let { onNicknameUpdated(it) }
        }

        UsernameSetResult.CANDIDATE_GENERATION_ERROR, UsernameSetResult.USERNAME_UNAVAILABLE -> {
          uiState.update { State(ButtonState.SUBMIT_DISABLED, UsernameStatus.TAKEN, it.usernameState) }
          events.onNext(Event.SUBMIT_FAIL_TAKEN)
          nickname?.let { onNicknameUpdated(it) }
        }

        UsernameSetResult.NETWORK_ERROR -> {
          uiState.update { State(ButtonState.SUBMIT, UsernameStatus.NONE, it.usernameState) }
          events.onNext(Event.NETWORK_FAILURE)
        }

        UsernameSetResult.RATE_LIMIT_ERROR -> {
          uiState.update { State(ButtonState.SUBMIT, UsernameStatus.NONE, it.usernameState) }
          events.onNext(Event.RATE_LIMIT_EXCEEDED)
        }
      }
    }
  }

  fun onUsernameDeleted() {
    uiState.update { state: State -> State(ButtonState.DELETE_LOADING, UsernameStatus.NONE, state.usernameState) }

    disposables += UsernameRepository.deleteUsernameAndLink().subscribe { result: UsernameDeleteResult ->
      when (result) {
        UsernameDeleteResult.SUCCESS -> {
          uiState.update { state: State -> State(ButtonState.DELETE_DISABLED, UsernameStatus.NONE, state.usernameState) }
          events.onNext(Event.DELETE_SUCCESS)
        }

        UsernameDeleteResult.NETWORK_ERROR -> {
          uiState.update { state: State -> State(ButtonState.DELETE, UsernameStatus.NONE, state.usernameState) }
          events.onNext(Event.NETWORK_FAILURE)
        }
      }
    }
  }

  fun getUiState(): Flowable<State> {
    return uiState.stateFlowable.observeOn(AndroidSchedulers.mainThread())
  }

  fun getEvents(): Observable<Event> {
    return events.observeOn(AndroidSchedulers.mainThread())
  }

  private fun isCaseChange(state: UsernameEditStateMachine.State): Boolean {
    if (mode == UsernameEditMode.RECOVERY) {
      return false
    }

    if (state is UsernameEditStateMachine.UserEnteredDiscriminator || state is UsernameEditStateMachine.UserEnteredNicknameAndDiscriminator) {
      return false
    }

    val newLower = state.nickname.lowercase()
    val oldLower = SignalStore.account.username?.split(Usernames.DELIMITER)?.firstOrNull()?.lowercase()

    return newLower == oldLower
  }

  /** Triggered when the debounced nickname event stream fires. */
  private fun onUsernameStateUpdateDebounced(state: UsernameEditStateMachine.State) {
    val nickname = state.nickname
    if (nickname.isBlank()) {
      return
    }

    if (state is UsernameEditStateMachine.NoUserEntry || state.stateModifier == UsernameEditStateMachine.StateModifier.SYSTEM) {
      return
    }

    val invalidReason: InvalidReason? = checkNickname(nickname)
    if (invalidReason != null) {
      uiState.update { uiState ->
        uiState.copy(
          buttonState = ButtonState.SUBMIT_DISABLED,
          usernameStatus = mapNicknameError(invalidReason)
        )
      }
      return
    }

    if (isCaseChange(state)) {
      val discriminator = SignalStore.account.username?.split(Usernames.DELIMITER)?.lastOrNull() ?: error("Unexpected case change, no discriminator!")
      uiState.update {
        State(
          buttonState = ButtonState.SUBMIT,
          usernameStatus = UsernameStatus.NONE,
          usernameState = UsernameState.CaseChange(Username("${state.nickname}${Usernames.DELIMITER}$discriminator"))
        )
      }

      stateMachineStore.update { s -> s.onSystemChangedDiscriminator(discriminator) }
      return
    }

    val isDiscriminatorSetByUser = state is UsernameEditStateMachine.UserEnteredDiscriminator || state is UsernameEditStateMachine.UserEnteredNicknameAndDiscriminator
    val discriminator = if (isDiscriminatorSetByUser) {
      state.discriminator
    } else {
      null
    }

    val discriminatorInvalidReason = checkDiscriminator(discriminator)
    if (isDiscriminatorSetByUser && discriminatorInvalidReason != null) {
      uiState.update { uiState ->
        uiState.copy(
          buttonState = ButtonState.SUBMIT_DISABLED,
          usernameStatus = mapDiscriminatorError(discriminatorInvalidReason)
        )
      }
      return
    }

    uiState.update { State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE, UsernameState.Loading) }

    disposables += UsernameRepository.reserveUsername(nickname, discriminator).subscribe { result: Result<UsernameState.Reserved, UsernameSetResult> ->
      result.either(
        onSuccess = { reserved: UsernameState.Reserved ->
          uiState.update { State(ButtonState.SUBMIT, UsernameStatus.NONE, reserved) }

          val d = reserved.getDiscriminator()
          if (!isDiscriminatorSetByUser && d != null) {
            stateMachineStore.update { s -> s.onSystemChangedDiscriminator(d) }
          }
        },
        onFailure = { failure: UsernameSetResult ->
          when (failure) {
            UsernameSetResult.SUCCESS -> {
              throw AssertionError()
            }

            UsernameSetResult.USERNAME_INVALID -> {
              uiState.update { State(ButtonState.SUBMIT_DISABLED, UsernameStatus.INVALID_GENERIC, UsernameState.NoUsername) }
            }

            UsernameSetResult.USERNAME_UNAVAILABLE -> {
              val status = if (isDiscriminatorSetByUser) {
                UsernameStatus.DISCRIMINATOR_NOT_AVAILABLE
              } else {
                UsernameStatus.TAKEN
              }

              uiState.update {
                State(
                  ButtonState.SUBMIT_DISABLED,
                  status,
                  usernameState = UsernameState.CaseChange(Username("${state.nickname}${Usernames.DELIMITER}$discriminator"))
                )
              }
            }

            UsernameSetResult.NETWORK_ERROR -> {
              uiState.update { State(ButtonState.SUBMIT, UsernameStatus.NONE, UsernameState.NoUsername) }
              events.onNext(Event.NETWORK_FAILURE)
            }

            UsernameSetResult.RATE_LIMIT_ERROR -> {
              uiState.update { State(ButtonState.SUBMIT, UsernameStatus.NONE, UsernameState.NoUsername) }
              events.onNext(Event.RATE_LIMIT_EXCEEDED)
            }

            UsernameSetResult.CANDIDATE_GENERATION_ERROR -> {
              // TODO -- Retry
              uiState.update { State(ButtonState.SUBMIT_DISABLED, UsernameStatus.TAKEN, UsernameState.NoUsername) }
            }
          }
        }
      )
    }
  }

  data class State(
    @JvmField val buttonState: ButtonState,
    @JvmField val usernameStatus: UsernameStatus,
    @JvmField val usernameState: UsernameState
  )

  enum class UsernameStatus {
    NONE,
    TAKEN,
    TOO_SHORT,
    TOO_LONG,
    CANNOT_START_WITH_NUMBER,
    INVALID_CHARACTERS,
    INVALID_GENERIC,
    DISCRIMINATOR_NOT_AVAILABLE,
    DISCRIMINATOR_TOO_SHORT,
    DISCRIMINATOR_TOO_LONG,
    DISCRIMINATOR_HAS_INVALID_CHARACTERS,
    DISCRIMINATOR_CANNOT_BE_00,
    DISCRIMINATOR_CANNOT_START_WITH_0
  }

  enum class ButtonState {
    SUBMIT,
    SUBMIT_DISABLED,
    SUBMIT_LOADING,
    DELETE,
    DELETE_LOADING,
    DELETE_DISABLED
  }

  enum class Event {
    NETWORK_FAILURE,
    SUBMIT_SUCCESS,
    DELETE_SUCCESS,
    SUBMIT_FAIL_INVALID,
    SUBMIT_FAIL_TAKEN,
    SKIPPED,
    NEEDS_CONFIRM_RESET,
    RATE_LIMIT_EXCEEDED
  }

  class Factory(private val mode: UsernameEditMode) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(UsernameEditViewModel(mode))!!
    }
  }

  companion object {
    private val TAG = Log.tag(UsernameEditViewModel::class.java)

    private const val NICKNAME_PUBLISHER_DEBOUNCE_TIMEOUT_MILLIS: Long = 500

    private fun mapNicknameError(invalidReason: InvalidReason): UsernameStatus {
      return when (invalidReason) {
        InvalidReason.TOO_SHORT -> UsernameStatus.TOO_SHORT
        InvalidReason.TOO_LONG -> UsernameStatus.TOO_LONG
        InvalidReason.STARTS_WITH_NUMBER -> UsernameStatus.CANNOT_START_WITH_NUMBER
        InvalidReason.INVALID_CHARACTERS -> UsernameStatus.INVALID_CHARACTERS
        InvalidReason.INVALID_NUMBER,
        InvalidReason.INVALID_NUMBER_00,
        InvalidReason.INVALID_NUMBER_PREFIX_0 -> error("Unexpected reason $invalidReason")
      }
    }

    private fun mapDiscriminatorError(invalidReason: InvalidReason): UsernameStatus {
      return when (invalidReason) {
        InvalidReason.TOO_SHORT -> UsernameStatus.DISCRIMINATOR_TOO_SHORT
        InvalidReason.TOO_LONG -> UsernameStatus.DISCRIMINATOR_TOO_LONG
        InvalidReason.INVALID_CHARACTERS -> UsernameStatus.DISCRIMINATOR_HAS_INVALID_CHARACTERS
        InvalidReason.INVALID_NUMBER_00 -> UsernameStatus.DISCRIMINATOR_CANNOT_BE_00
        InvalidReason.INVALID_NUMBER_PREFIX_0 -> UsernameStatus.DISCRIMINATOR_CANNOT_START_WITH_0
        else -> UsernameStatus.INVALID_GENERIC
      }
    }
  }
}

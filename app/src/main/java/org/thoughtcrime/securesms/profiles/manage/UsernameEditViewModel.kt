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
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.UsernameDeleteResult
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.UsernameSetResult
import org.thoughtcrime.securesms.util.UsernameUtil.InvalidReason
import org.thoughtcrime.securesms.util.UsernameUtil.checkDiscriminator
import org.thoughtcrime.securesms.util.UsernameUtil.checkUsername
import org.thoughtcrime.securesms.util.rx.RxStore
import java.util.concurrent.TimeUnit

/**
 * Manages the state around username updates.
 *
 *
 * A note on naming conventions:
 * Usernames are made up of two discrete components, a nickname and a discriminator. They are formatted thusly:
 *
 * [nickname].[discriminator]
 *
 * The nickname is user-controlled, whereas the discriminator is controlled by the server.
 */
internal class UsernameEditViewModel private constructor(private val isInRegistration: Boolean) : ViewModel() {
  private val events: PublishSubject<Event> = PublishSubject.create()
  private val disposables: CompositeDisposable = CompositeDisposable()

  private val uiState: RxStore<State> = RxStore(
    defaultValue = State(
      buttonState = ButtonState.SUBMIT_DISABLED,
      usernameStatus = UsernameStatus.NONE,
      username = SignalStore.account().username?.let { UsernameState.Set(it) } ?: UsernameState.NoUsername
    ),
    scheduler = Schedulers.computation()
  )

  private val stateMachineStore = RxStore<UsernameEditStateMachine.State>(
    defaultValue = UsernameEditStateMachine.NoUserEntry(
      nickname = SignalStore.account().username?.split(UsernameState.DELIMITER)?.first() ?: "",
      discriminator = SignalStore.account().username?.split(UsernameState.DELIMITER)?.last() ?: "",
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
  }

  override fun onCleared() {
    super.onCleared()
    disposables.clear()
    uiState.dispose()
  }

  fun onNicknameUpdated(nickname: String) {
    uiState.update { state: State ->
      if (nickname.isBlank() && SignalStore.account().username != null) {
        return@update State(
          buttonState = if (isInRegistration) ButtonState.SUBMIT_DISABLED else ButtonState.DELETE,
          usernameStatus = UsernameStatus.NONE,
          username = UsernameState.NoUsername
        )
      }

      val invalidReason: InvalidReason? = checkUsername(nickname)

      if (invalidReason != null) {
        // We only want to show actual errors after debouncing. But we also don't want to allow users to submit names with errors.
        // So we disable submit, but we don't show an error yet.
        State(
          buttonState = ButtonState.SUBMIT_DISABLED,
          usernameStatus = UsernameStatus.NONE,
          username = state.username
        )
      } else {
        State(
          buttonState = ButtonState.SUBMIT_DISABLED,
          usernameStatus = UsernameStatus.NONE,
          username = state.username
        )
      }
    }

    stateMachineStore.update {
      it.onUserChangedNickname(nickname)
    }
  }

  fun onDiscriminatorUpdated(discriminator: String) {
    uiState.update { state: State ->
      if (discriminator.isBlank() && SignalStore.account().username != null) {
        return@update State(
          buttonState = if (isInRegistration) ButtonState.SUBMIT_DISABLED else ButtonState.DELETE,
          usernameStatus = UsernameStatus.NONE,
          username = UsernameState.NoUsername
        )
      }

      State(
        buttonState = ButtonState.SUBMIT_DISABLED,
        usernameStatus = UsernameStatus.NONE,
        username = state.username
      )
    }

    stateMachineStore.update {
      it.onUserChangedDiscriminator(discriminator)
    }
  }

  fun onUsernameSkipped() {
    SignalStore.uiHints().markHasSetOrSkippedUsernameCreation()
    events.onNext(Event.SKIPPED)
  }

  fun onUsernameSubmitted() {
    val state = stateMachineStore.state

    if (isCaseChange(state)) {
      handleUserConfirmation(UsernameRepository::updateUsername)
    } else {
      handleUserConfirmation(UsernameRepository::confirmUsername)
    }
  }

  private inline fun <reified T : UsernameState> handleUserConfirmation(
    repositoryWorkerMethod: (T) -> Single<UsernameSetResult>
  ) {
    val usernameState = uiState.state.username
    if (usernameState !is T) {
      uiState.update { State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE, it.username) }
      return
    }

    if (usernameState.requireUsername() == SignalStore.account().username) {
      uiState.update { State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE, it.username) }
      return
    }

    val invalidReason = checkUsername(usernameState.getNickname())
    if (invalidReason != null) {
      uiState.update { State(ButtonState.SUBMIT_DISABLED, mapNicknameError(invalidReason), it.username) }
      return
    }

    uiState.update { State(ButtonState.SUBMIT_LOADING, UsernameStatus.NONE, it.username) }

    disposables += repositoryWorkerMethod(usernameState).subscribe { result: UsernameSetResult ->
      val nickname = usernameState.getNickname()

      when (result) {
        UsernameSetResult.SUCCESS -> {
          SignalStore.uiHints().markHasSetOrSkippedUsernameCreation()
          uiState.update { State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE, it.username) }
          events.onNext(Event.SUBMIT_SUCCESS)
        }

        UsernameSetResult.USERNAME_INVALID -> {
          uiState.update { State(ButtonState.SUBMIT_DISABLED, UsernameStatus.INVALID_GENERIC, it.username) }
          events.onNext(Event.SUBMIT_FAIL_INVALID)
          nickname?.let { onNicknameUpdated(it) }
        }

        UsernameSetResult.CANDIDATE_GENERATION_ERROR, UsernameSetResult.USERNAME_UNAVAILABLE -> {
          uiState.update { State(ButtonState.SUBMIT_DISABLED, UsernameStatus.TAKEN, it.username) }
          events.onNext(Event.SUBMIT_FAIL_TAKEN)
          nickname?.let { onNicknameUpdated(it) }
        }

        UsernameSetResult.NETWORK_ERROR -> {
          uiState.update { State(ButtonState.SUBMIT, UsernameStatus.NONE, it.username) }
          events.onNext(Event.NETWORK_FAILURE)
        }
      }
    }
  }

  fun onUsernameDeleted() {
    uiState.update { state: State -> State(ButtonState.DELETE_LOADING, UsernameStatus.NONE, state.username) }

    disposables += UsernameRepository.deleteUsername().subscribe { result: UsernameDeleteResult ->
      when (result) {
        UsernameDeleteResult.SUCCESS -> {
          uiState.update { state: State -> State(ButtonState.DELETE_DISABLED, UsernameStatus.NONE, state.username) }
          events.onNext(Event.DELETE_SUCCESS)
        }

        UsernameDeleteResult.NETWORK_ERROR -> {
          uiState.update { state: State -> State(ButtonState.DELETE, UsernameStatus.NONE, state.username) }
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
    if (state is UsernameEditStateMachine.UserEnteredDiscriminator || state is UsernameEditStateMachine.UserEnteredNicknameAndDiscriminator) {
      return false
    }

    val newLower = state.nickname.lowercase()
    val oldLower = SignalStore.account().username?.split(UsernameState.DELIMITER)?.firstOrNull()?.lowercase()

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

    if (isCaseChange(state)) {
      val discriminator = SignalStore.account().username?.split(UsernameState.DELIMITER)?.lastOrNull() ?: error("Unexpected case change, no discriminator!")
      uiState.update {
        State(
          buttonState = ButtonState.SUBMIT,
          usernameStatus = UsernameStatus.NONE,
          username = UsernameState.CaseChange("${state.nickname}${UsernameState.DELIMITER}$discriminator")
        )
      }

      stateMachineStore.update { s -> s.onSystemChangedDiscriminator(discriminator) }
      return
    }

    val invalidReason: InvalidReason? = checkUsername(nickname)
    if (invalidReason != null) {
      uiState.update { uiState ->
        State(
          buttonState = ButtonState.SUBMIT_DISABLED,
          usernameStatus = mapNicknameError(invalidReason),
          username = uiState.username
        )
      }
      return
    }

    uiState.update { State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE, UsernameState.Loading) }

    val isDiscriminatorSetByUser = state is UsernameEditStateMachine.UserEnteredDiscriminator || state is UsernameEditStateMachine.UserEnteredNicknameAndDiscriminator
    val discriminator = if (isDiscriminatorSetByUser) {
      state.discriminator
    } else {
      null
    }

    val discriminatorInvalidReason = checkDiscriminator(discriminator)
    if (isDiscriminatorSetByUser && discriminatorInvalidReason != null) {
      uiState.update { s ->
        State(
          buttonState = ButtonState.SUBMIT_DISABLED,
          usernameStatus = mapDiscriminatorError(discriminatorInvalidReason),
          username = s.username
        )
      }
      return
    }

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

              uiState.update { State(ButtonState.SUBMIT_DISABLED, status, UsernameState.NoUsername) }
            }

            UsernameSetResult.NETWORK_ERROR -> {
              uiState.update { State(ButtonState.SUBMIT, UsernameStatus.NONE, UsernameState.NoUsername) }
              events.onNext(Event.NETWORK_FAILURE)
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

  class State(
    @JvmField val buttonState: ButtonState,
    @JvmField val usernameStatus: UsernameStatus,
    @JvmField val username: UsernameState
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
    DISCRIMINATOR_HAS_INVALID_CHARACTERS
  }

  enum class ButtonState {
    SUBMIT, SUBMIT_DISABLED, SUBMIT_LOADING, DELETE, DELETE_LOADING, DELETE_DISABLED
  }

  enum class Event {
    NETWORK_FAILURE, SUBMIT_SUCCESS, DELETE_SUCCESS, SUBMIT_FAIL_INVALID, SUBMIT_FAIL_TAKEN, SKIPPED
  }

  class Factory(private val isInRegistration: Boolean) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(UsernameEditViewModel(isInRegistration))!!
    }
  }

  companion object {
    private const val NICKNAME_PUBLISHER_DEBOUNCE_TIMEOUT_MILLIS: Long = 1000

    private fun mapNicknameError(invalidReason: InvalidReason): UsernameStatus {
      return when (invalidReason) {
        InvalidReason.TOO_SHORT -> UsernameStatus.TOO_SHORT
        InvalidReason.TOO_LONG -> UsernameStatus.TOO_LONG
        InvalidReason.STARTS_WITH_NUMBER -> UsernameStatus.CANNOT_START_WITH_NUMBER
        InvalidReason.INVALID_CHARACTERS -> UsernameStatus.INVALID_CHARACTERS
        else -> UsernameStatus.INVALID_GENERIC
      }
    }

    private fun mapDiscriminatorError(invalidReason: InvalidReason): UsernameStatus {
      return when (invalidReason) {
        InvalidReason.TOO_SHORT -> UsernameStatus.DISCRIMINATOR_TOO_SHORT
        InvalidReason.TOO_LONG -> UsernameStatus.DISCRIMINATOR_TOO_LONG
        InvalidReason.INVALID_CHARACTERS -> UsernameStatus.DISCRIMINATOR_HAS_INVALID_CHARACTERS
        else -> UsernameStatus.INVALID_GENERIC
      }
    }
  }
}

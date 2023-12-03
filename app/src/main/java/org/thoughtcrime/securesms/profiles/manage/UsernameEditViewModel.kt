package org.thoughtcrime.securesms.profiles.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.processors.PublishProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import org.signal.core.util.Result
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.UsernameDeleteResult
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.UsernameSetResult
import org.thoughtcrime.securesms.util.UsernameUtil.InvalidReason
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
  private val nicknamePublisher: PublishProcessor<String> = PublishProcessor.create()
  private val disposables: CompositeDisposable = CompositeDisposable()

  private val uiState: RxStore<State> = RxStore(
    defaultValue = State(
      buttonState = ButtonState.SUBMIT_DISABLED,
      usernameStatus = UsernameStatus.NONE,
      username = SignalStore.account().username?.let { UsernameState.Set(it) } ?: UsernameState.NoUsername
    ),
    scheduler = Schedulers.computation()
  )

  init {
    disposables += nicknamePublisher
      .debounce(NICKNAME_PUBLISHER_DEBOUNCE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
      .subscribe { nickname: String -> onNicknameUpdatedDebounced(nickname) }
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

    nicknamePublisher.onNext(nickname)
  }

  fun onUsernameSkipped() {
    SignalStore.uiHints().markHasSetOrSkippedUsernameCreation()
    events.onNext(Event.SKIPPED)
  }

  fun onUsernameSubmitted() {
    val usernameState = uiState.state.username
    if (usernameState !is UsernameState.Reserved) {
      uiState.update { State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE, it.username) }
      return
    }

    if (usernameState.username == SignalStore.account().username) {
      uiState.update { State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE, it.username) }
      return
    }

    val invalidReason = checkUsername(usernameState.getNickname())
    if (invalidReason != null) {
      uiState.update { State(ButtonState.SUBMIT_DISABLED, mapUsernameError(invalidReason), it.username) }
      return
    }

    uiState.update { State(ButtonState.SUBMIT_LOADING, UsernameStatus.NONE, it.username) }

    disposables += UsernameRepository.confirmUsername(usernameState).subscribe { result: UsernameSetResult ->
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

  /** Triggered when the debounced nickname event stream fires. */
  private fun onNicknameUpdatedDebounced(nickname: String) {
    if (nickname.isBlank()) {
      return
    }

    val invalidReason: InvalidReason? = checkUsername(nickname)
    if (invalidReason != null) {
      uiState.update { state ->
        State(
          buttonState = ButtonState.SUBMIT_DISABLED,
          usernameStatus = mapUsernameError(invalidReason),
          username = state.username
        )
      }
      return
    }

    uiState.update { State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE, UsernameState.Loading) }

    disposables += UsernameRepository.reserveUsername(nickname).subscribe { result: Result<UsernameState.Reserved, UsernameSetResult> ->
      result.either(
        onSuccess = { reserved: UsernameState.Reserved ->
          uiState.update { State(ButtonState.SUBMIT, UsernameStatus.NONE, reserved) }
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
              uiState.update { State(ButtonState.SUBMIT_DISABLED, UsernameStatus.TAKEN, UsernameState.NoUsername) }
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
    NONE, TAKEN, TOO_SHORT, TOO_LONG, CANNOT_START_WITH_NUMBER, INVALID_CHARACTERS, INVALID_GENERIC
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

    private fun mapUsernameError(invalidReason: InvalidReason): UsernameStatus {
      return when (invalidReason) {
        InvalidReason.TOO_SHORT -> UsernameStatus.TOO_SHORT
        InvalidReason.TOO_LONG -> UsernameStatus.TOO_LONG
        InvalidReason.STARTS_WITH_NUMBER -> UsernameStatus.CANNOT_START_WITH_NUMBER
        InvalidReason.INVALID_CHARACTERS -> UsernameStatus.INVALID_CHARACTERS
        else -> UsernameStatus.INVALID_GENERIC
      }
    }
  }
}

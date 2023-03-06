package org.thoughtcrime.securesms.profiles.manage;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.UsernameUtil;
import org.thoughtcrime.securesms.util.UsernameUtil.InvalidReason;
import org.thoughtcrime.securesms.util.rx.RxStore;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * Manages the state around username updates.
 * <p>
 * A note on naming conventions:
 * <p>
 * Usernames are made up of two discrete components, a nickname and a discriminator. They are formatted thusly:
 * <p>
 * [nickname]#[discriminator]
 * <p>
 * The nickname is user-controlled, whereas the discriminator is controlled by the server.
 */
class UsernameEditViewModel extends ViewModel {

  private static final long NICKNAME_PUBLISHER_DEBOUNCE_TIMEOUT_MILLIS = 500;

  private final PublishSubject<Event>    events;
  private final UsernameEditRepository   repo;
  private final RxStore<State>           uiState;
  private final PublishProcessor<String> nicknamePublisher;
  private final CompositeDisposable      disposables;
  private final boolean                  isInRegistration;

  private UsernameEditViewModel(boolean isInRegistration) {
    this.repo              = new UsernameEditRepository();
    this.uiState           = new RxStore<>(new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE, Recipient.self().getUsername().<UsernameState>map(UsernameState.Set::new)
                                                                                                                .orElse(UsernameState.NoUsername.INSTANCE)), Schedulers.computation());
    this.events            = PublishSubject.create();
    this.nicknamePublisher = PublishProcessor.create();
    this.disposables       = new CompositeDisposable();
    this.isInRegistration  = isInRegistration;

    Disposable disposable = nicknamePublisher.debounce(NICKNAME_PUBLISHER_DEBOUNCE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                                             .subscribe(this::onNicknameChanged);
    disposables.add(disposable);
  }

  @Override
  protected void onCleared() {
    super.onCleared();
    disposables.clear();
    uiState.dispose();
  }

  void onNicknameUpdated(@NonNull String nickname) {
    uiState.update(state -> {
      if (TextUtils.isEmpty(nickname) && Recipient.self().getUsername().isPresent()) {
        return new State(isInRegistration ? ButtonState.SUBMIT_DISABLED : ButtonState.DELETE, UsernameStatus.NONE, UsernameState.NoUsername.INSTANCE);
      }

      Optional<InvalidReason> invalidReason = UsernameUtil.checkUsername(nickname);

      return invalidReason.map(reason -> new State(ButtonState.SUBMIT_DISABLED, mapUsernameError(reason), state.usernameState))
                          .orElseGet(() -> new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE, state.usernameState));
    });

    nicknamePublisher.onNext(nickname);
  }

  void onUsernameSkipped() {
    SignalStore.uiHints().markHasSetOrSkippedUsernameCreation();
    events.onNext(Event.SKIPPED);
  }

  void onUsernameSubmitted() {
    UsernameState usernameState = uiState.getState().getUsername();

    if (!(usernameState instanceof UsernameState.Reserved)) {
      uiState.update(state -> new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE, state.usernameState));
      return;
    }

    if (Objects.equals(usernameState.getUsername(), Recipient.self().getUsername().orElse(null))) {
      uiState.update(state -> new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE, state.usernameState));
      return;
    }

    Optional<InvalidReason> invalidReason = UsernameUtil.checkUsername(usernameState.getNickname());

    if (invalidReason.isPresent()) {
      uiState.update(state -> new State(ButtonState.SUBMIT_DISABLED, mapUsernameError(invalidReason.get()), state.usernameState));
      return;
    }

    uiState.update(state -> new State(ButtonState.SUBMIT_LOADING, UsernameStatus.NONE, state.usernameState));

    Disposable confirmUsernameDisposable = repo.confirmUsername((UsernameState.Reserved) usernameState)
                                               .subscribe(result -> {
                                                 String nickname = usernameState.getNickname();

                                                 switch (result) {
                                                   case SUCCESS:
                                                     SignalStore.uiHints().markHasSetOrSkippedUsernameCreation();
                                                     uiState.update(state -> new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE, state.usernameState));
                                                     events.onNext(Event.SUBMIT_SUCCESS);
                                                     break;
                                                   case USERNAME_INVALID:
                                                     uiState.update(state -> new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.INVALID_GENERIC, state.usernameState));
                                                     events.onNext(Event.SUBMIT_FAIL_INVALID);

                                                     if (nickname != null) {
                                                       onNicknameUpdated(nickname);
                                                     }
                                                     break;
                                                   case CANDIDATE_GENERATION_ERROR:
                                                   case USERNAME_UNAVAILABLE:
                                                     uiState.update(state -> new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.TAKEN, state.usernameState));
                                                     events.onNext(Event.SUBMIT_FAIL_TAKEN);

                                                     if (nickname != null) {
                                                       onNicknameUpdated(nickname);
                                                     }
                                                     break;
                                                   case NETWORK_ERROR:
                                                     uiState.update(state -> new State(ButtonState.SUBMIT, UsernameStatus.NONE, state.usernameState));
                                                     events.onNext(Event.NETWORK_FAILURE);
                                                     break;
                                                 }
                                               });

    disposables.add(confirmUsernameDisposable);
  }

  void onUsernameDeleted() {
    uiState.update(state -> new State(ButtonState.DELETE_LOADING, UsernameStatus.NONE, state.usernameState));

    Disposable deletionDisposable = repo.deleteUsername().subscribe(result -> {
      switch (result) {
        case SUCCESS:
          uiState.update(state -> new State(ButtonState.DELETE_DISABLED, UsernameStatus.NONE, state.usernameState));
          events.onNext(Event.DELETE_SUCCESS);
          break;
        case NETWORK_ERROR:
          uiState.update(state -> new State(ButtonState.DELETE, UsernameStatus.NONE, state.usernameState));
          events.onNext(Event.NETWORK_FAILURE);
          break;
      }
    });

    disposables.add(deletionDisposable);
  }

  @NonNull Flowable<State> getUiState() {
    return uiState.getStateFlowable().observeOn(AndroidSchedulers.mainThread());
  }

  @NonNull Observable<Event> getEvents() {
    return events.observeOn(AndroidSchedulers.mainThread());
  }

  private void onNicknameChanged(@NonNull String nickname) {
    if (TextUtils.isEmpty(nickname)) {
      return;
    }

    uiState.update(state -> new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE, UsernameState.Loading.INSTANCE));
    Disposable reserveDisposable = repo.reserveUsername(nickname).subscribe(result -> {
      result.either(
          reserved -> {
            uiState.update(state -> new State(ButtonState.SUBMIT, UsernameStatus.NONE, reserved));
            return null;
          },
          failure -> {
            switch (failure) {
              case SUCCESS:
                throw new AssertionError();
              case USERNAME_INVALID:
                uiState.update(state -> new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.INVALID_GENERIC, UsernameState.NoUsername.INSTANCE));
                break;
              case USERNAME_UNAVAILABLE:
                uiState.update(state -> new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.TAKEN, UsernameState.NoUsername.INSTANCE));
                break;
              case NETWORK_ERROR:
                uiState.update(state -> new State(ButtonState.SUBMIT, UsernameStatus.NONE, UsernameState.NoUsername.INSTANCE));
                events.onNext(Event.NETWORK_FAILURE);
                break;
              case CANDIDATE_GENERATION_ERROR:
                // TODO -- Retry
                uiState.update(state -> new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.TAKEN, UsernameState.NoUsername.INSTANCE));
                break;
            }

            return null;
          });
    });

    disposables.add(reserveDisposable);
  }

  private static UsernameStatus mapUsernameError(@NonNull InvalidReason invalidReason) {
    switch (invalidReason) {
      case TOO_SHORT:
        return UsernameStatus.TOO_SHORT;
      case TOO_LONG:
        return UsernameStatus.TOO_LONG;
      case STARTS_WITH_NUMBER:
        return UsernameStatus.CANNOT_START_WITH_NUMBER;
      case INVALID_CHARACTERS:
        return UsernameStatus.INVALID_CHARACTERS;
      default:
        return UsernameStatus.INVALID_GENERIC;
    }
  }

  static class State {
    private final ButtonState    buttonState;
    private final UsernameStatus usernameStatus;
    private final UsernameState  usernameState;

    private State(@NonNull ButtonState buttonState,
                  @NonNull UsernameStatus usernameStatus,
                  @NonNull UsernameState usernameState)
    {
      this.buttonState    = buttonState;
      this.usernameStatus = usernameStatus;
      this.usernameState  = usernameState;
    }

    @NonNull ButtonState getButtonState() {
      return buttonState;
    }

    @NonNull UsernameStatus getUsernameStatus() {
      return usernameStatus;
    }

    @NonNull UsernameState getUsername() {
      return usernameState;
    }
  }

  enum UsernameStatus {
    NONE, TAKEN, TOO_SHORT, TOO_LONG, CANNOT_START_WITH_NUMBER, INVALID_CHARACTERS, INVALID_GENERIC
  }

  enum ButtonState {
    SUBMIT, SUBMIT_DISABLED, SUBMIT_LOADING, DELETE, DELETE_LOADING, DELETE_DISABLED
  }

  enum Event {
    NETWORK_FAILURE, SUBMIT_SUCCESS, DELETE_SUCCESS, SUBMIT_FAIL_INVALID, SUBMIT_FAIL_TAKEN, SKIPPED
  }

  static class Factory implements ViewModelProvider.Factory {

    private final boolean isInRegistration;

    Factory(boolean isInRegistration) {
      this.isInRegistration = isInRegistration;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new UsernameEditViewModel(isInRegistration));
    }
  }
}

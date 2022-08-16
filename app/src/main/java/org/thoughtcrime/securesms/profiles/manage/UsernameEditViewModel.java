package org.thoughtcrime.securesms.profiles.manage;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.UsernameUtil;
import org.thoughtcrime.securesms.util.UsernameUtil.InvalidReason;
import org.thoughtcrime.securesms.util.rx.RxStore;

import java.util.Optional;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;


class UsernameEditViewModel extends ViewModel {

  private static final String TAG = Log.tag(UsernameEditViewModel.class);

  private final Application            application;
  private final SingleLiveEvent<Event> events;
  private final UsernameEditRepository repo;
  private final RxStore<State>         uiState;

  private UsernameEditViewModel() {
    this.application = ApplicationDependencies.getApplication();
    this.repo        = new UsernameEditRepository();
    this.uiState     = new RxStore<>(new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE, UsernameSuffix.NONE), Schedulers.computation());
    this.events      = new SingleLiveEvent<>();
  }

  void onUsernameUpdated(@NonNull String username) {
    uiState.update(state -> {
      if (TextUtils.isEmpty(username) && Recipient.self().getUsername().isPresent()) {
        return new State(ButtonState.DELETE, UsernameStatus.NONE, state.usernameSuffix);
      }

      if (username.equals(Recipient.self().getUsername().orElse(null))) {
        return new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE, state.usernameSuffix);
      }

      Optional<InvalidReason> invalidReason = UsernameUtil.checkUsername(username);

      return invalidReason.map(reason -> new State(ButtonState.SUBMIT_DISABLED, mapUsernameError(reason), state.usernameSuffix))
                          .orElseGet(() -> new State(ButtonState.SUBMIT, UsernameStatus.NONE, state.usernameSuffix));
    });
  }

  void onUsernameSubmitted(@NonNull String username) {
    if (username.equals(Recipient.self().getUsername().orElse(null))) {
      uiState.update(state -> new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE, state.usernameSuffix));
      return;
    }

    Optional<InvalidReason> invalidReason = UsernameUtil.checkUsername(username);

    if (invalidReason.isPresent()) {
      uiState.update(state -> new State(ButtonState.SUBMIT_DISABLED, mapUsernameError(invalidReason.get()), state.usernameSuffix));
      return;
    }

    uiState.update(state -> new State(ButtonState.SUBMIT_LOADING, UsernameStatus.NONE, state.usernameSuffix));

    repo.setUsername(username, (result) -> {
      ThreadUtil.runOnMain(() -> {
        switch (result) {
          case SUCCESS:
            uiState.update(state -> new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE, state.usernameSuffix));
            events.postValue(Event.SUBMIT_SUCCESS);
            break;
          case USERNAME_INVALID:
            uiState.update(state -> new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.INVALID_GENERIC, state.usernameSuffix));
            events.postValue(Event.SUBMIT_FAIL_INVALID);
            break;
          case USERNAME_UNAVAILABLE:
            uiState.update(state -> new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.TAKEN, state.usernameSuffix));
            events.postValue(Event.SUBMIT_FAIL_TAKEN);
            break;
          case NETWORK_ERROR:
            uiState.update(state -> new State(ButtonState.SUBMIT, UsernameStatus.NONE, state.usernameSuffix));
            events.postValue(Event.NETWORK_FAILURE);
            break;
        }
      });
    });
  }

  void onUsernameDeleted() {
    uiState.update(state -> new State(ButtonState.DELETE_LOADING, UsernameStatus.NONE, state.usernameSuffix));

    repo.deleteUsername((result) -> {
      ThreadUtil.runOnMain(() -> {
        switch (result) {
          case SUCCESS:
            uiState.update(state -> new State(ButtonState.DELETE_DISABLED, UsernameStatus.NONE, state.usernameSuffix));
            events.postValue(Event.DELETE_SUCCESS);
            break;
          case NETWORK_ERROR:
            uiState.update(state -> new State(ButtonState.DELETE, UsernameStatus.NONE, state.usernameSuffix));
            events.postValue(Event.NETWORK_FAILURE);
            break;
        }
      });
    });
  }

  @NonNull Flowable<State> getUiState() {
    return uiState.getStateFlowable().observeOn(AndroidSchedulers.mainThread());
  }

  @NonNull LiveData<Event> getEvents() {
    return events;
  }

  private static UsernameStatus mapUsernameError(@NonNull InvalidReason invalidReason) {
    switch (invalidReason) {
      case TOO_SHORT:          return UsernameStatus.TOO_SHORT;
      case TOO_LONG:           return UsernameStatus.TOO_LONG;
      case STARTS_WITH_NUMBER: return UsernameStatus.CANNOT_START_WITH_NUMBER;
      case INVALID_CHARACTERS: return UsernameStatus.INVALID_CHARACTERS;
      default:                 return UsernameStatus.INVALID_GENERIC;
    }
  }

  static class State {
    private final ButtonState    buttonState;
    private final UsernameStatus usernameStatus;
    private final UsernameSuffix usernameSuffix;

    private State(@NonNull ButtonState buttonState,
                  @NonNull UsernameStatus usernameStatus,
                  @NonNull UsernameSuffix usernameSuffix)
    {
      this.buttonState    = buttonState;
      this.usernameStatus = usernameStatus;
      this.usernameSuffix = usernameSuffix;
    }

    @NonNull ButtonState getButtonState() {
      return buttonState;
    }

    @NonNull UsernameStatus getUsernameStatus() {
      return usernameStatus;
    }

    @NonNull UsernameSuffix getUsernameSuffix() {
      return usernameSuffix;
    }
  }

  enum UsernameStatus {
    NONE, AVAILABLE, TAKEN, TOO_SHORT, TOO_LONG, CANNOT_START_WITH_NUMBER, INVALID_CHARACTERS, INVALID_GENERIC
  }

  enum ButtonState {
    SUBMIT, SUBMIT_DISABLED, SUBMIT_LOADING, DELETE, DELETE_LOADING, DELETE_DISABLED
  }

  enum Event {
    NETWORK_FAILURE, SUBMIT_SUCCESS, DELETE_SUCCESS, SUBMIT_FAIL_INVALID, SUBMIT_FAIL_TAKEN
  }

  static class Factory extends ViewModelProvider.NewInstanceFactory {
    @Override
    public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new UsernameEditViewModel());
    }
  }
}

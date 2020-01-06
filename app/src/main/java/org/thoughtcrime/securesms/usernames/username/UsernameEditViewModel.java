package org.thoughtcrime.securesms.usernames.username;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.Debouncer;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.UsernameUtil;
import org.thoughtcrime.securesms.util.UsernameUtil.InvalidReason;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

class UsernameEditViewModel extends ViewModel {

  private static final String TAG = Log.tag(UsernameEditViewModel.class);

  private final Application            application;
  private final MutableLiveData<State> uiState;
  private final SingleLiveEvent<Event> events;
  private final UsernameEditRepository repo;

  private UsernameEditViewModel() {
    this.application = ApplicationDependencies.getApplication();
    this.repo        = new UsernameEditRepository();
    this.uiState     = new MutableLiveData<>();
    this.events      = new SingleLiveEvent<>();

    uiState.setValue(new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE));
  }

  void onUsernameUpdated(@NonNull String username) {
    if (TextUtils.isEmpty(username) && TextSecurePreferences.getLocalUsername(application) != null) {
      uiState.setValue(new State(ButtonState.DELETE, UsernameStatus.NONE));
      return;
    }

    if (username.equals(TextSecurePreferences.getLocalUsername(application))) {
      uiState.setValue(new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE));
      return;
    }

    Optional<InvalidReason> invalidReason = UsernameUtil.checkUsername(username);

    if (invalidReason.isPresent()) {
      uiState.setValue(new State(ButtonState.SUBMIT_DISABLED, mapUsernameError(invalidReason.get())));
      return;
    }

    uiState.setValue(new State(ButtonState.SUBMIT, UsernameStatus.NONE));
  }

  void onUsernameSubmitted(@NonNull String username) {
    if (username.equals(TextSecurePreferences.getLocalUsername(application))) {
      uiState.setValue(new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE));
      return;
    }

    Optional<InvalidReason> invalidReason = UsernameUtil.checkUsername(username);

    if (invalidReason.isPresent()) {
      uiState.setValue(new State(ButtonState.SUBMIT_DISABLED, mapUsernameError(invalidReason.get())));
      return;
    }

    uiState.setValue(new State(ButtonState.SUBMIT_LOADING, UsernameStatus.NONE));

    repo.setUsername(username, (result) -> {
      Util.runOnMain(() -> {
        switch (result) {
          case SUCCESS:
            uiState.setValue(new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.NONE));
            events.postValue(Event.SUBMIT_SUCCESS);
            break;
          case USERNAME_INVALID:
            uiState.setValue(new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.INVALID_GENERIC));
            events.postValue(Event.SUBMIT_FAIL_INVALID);
            break;
          case USERNAME_UNAVAILABLE:
            uiState.setValue(new State(ButtonState.SUBMIT_DISABLED, UsernameStatus.TAKEN));
            events.postValue(Event.SUBMIT_FAIL_TAKEN);
            break;
          case NETWORK_ERROR:
            uiState.setValue(new State(ButtonState.SUBMIT, UsernameStatus.NONE));
            events.postValue(Event.NETWORK_FAILURE);
            break;
        }
      });
    });
  }

  void onUsernameDeleted() {
    uiState.setValue(new State(ButtonState.DELETE_LOADING, UsernameStatus.NONE));

    repo.deleteUsername((result) -> {
      Util.runOnMain(() -> {
        switch (result) {
          case SUCCESS:
            uiState.postValue(new State(ButtonState.DELETE_DISABLED, UsernameStatus.NONE));
            events.postValue(Event.DELETE_SUCCESS);
            break;
          case NETWORK_ERROR:
            uiState.postValue(new State(ButtonState.DELETE, UsernameStatus.NONE));
            events.postValue(Event.NETWORK_FAILURE);
            break;
        }
      });
    });
  }

  @NonNull LiveData<State> getUiState() {
    return uiState;
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

    private State(@NonNull ButtonState buttonState,
                  @NonNull UsernameStatus usernameStatus)
    {
      this.buttonState    = buttonState;
      this.usernameStatus = usernameStatus;
    }

    @NonNull ButtonState getButtonState() {
      return buttonState;
    }

    @NonNull UsernameStatus getUsernameStatus() {
      return usernameStatus;
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

package org.thoughtcrime.securesms.pin;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.v2.KbsConstants;
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;
import org.thoughtcrime.securesms.util.SingleLiveEvent;

public class PinRestoreViewModel extends ViewModel {

  private final PinRestoreRepository                 repo;
  private final DefaultValueLiveData<TriesRemaining> triesRemaining;
  private final SingleLiveEvent<Event>               event;

  private volatile PinRestoreRepository.TokenData tokenData;

  public PinRestoreViewModel() {
    this.repo           = new PinRestoreRepository();
    this.triesRemaining = new DefaultValueLiveData<>(new TriesRemaining(10, false));
    this.event          = new SingleLiveEvent<>();

    repo.getToken(token -> {
      if (token.isPresent()) {
        updateTokenData(token.get(), false);
      } else {
        event.postValue(Event.NETWORK_ERROR);
      }
    });
  }

  void onPinSubmitted(@NonNull String pin, @NonNull PinKeyboardType pinKeyboardType) {
    int trimmedLength = pin.replace(" ", "").length();

    if (trimmedLength == 0) {
      event.postValue(Event.EMPTY_PIN);
      return;
    }

    if (trimmedLength < KbsConstants.MINIMUM_PIN_LENGTH) {
      event.postValue(Event.PIN_TOO_SHORT);
      return;
    }

    if (tokenData != null) {
      repo.submitPin(pin, tokenData, result -> {

        switch (result.getResult()) {
          case SUCCESS:
            SignalStore.pinValues().setKeyboardType(pinKeyboardType);
            SignalStore.storageServiceValues().setNeedsAccountRestore(false);
            event.postValue(Event.SUCCESS);
            break;
          case LOCKED:
            event.postValue(Event.PIN_LOCKED);
            break;
          case INCORRECT:
            event.postValue(Event.PIN_INCORRECT);
            updateTokenData(result.getTokenData(), true);
            break;
          case NETWORK_ERROR:
            event.postValue(Event.NETWORK_ERROR);
            break;
        }
      });
    } else {
      repo.getToken(token -> {
        if (token.isPresent()) {
          updateTokenData(token.get(), false);
          onPinSubmitted(pin, pinKeyboardType);
        } else {
          event.postValue(Event.NETWORK_ERROR);
        }
      });
    }
  }

  @NonNull DefaultValueLiveData<TriesRemaining> getTriesRemaining() {
    return triesRemaining;
  }

  @NonNull LiveData<Event> getEvent() {
    return event;
  }

  private void updateTokenData(@NonNull PinRestoreRepository.TokenData tokenData, boolean incorrectGuess) {
    this.tokenData = tokenData;
    triesRemaining.postValue(new TriesRemaining(tokenData.getTriesRemaining(), incorrectGuess));
  }

  enum Event {
    SUCCESS, EMPTY_PIN, PIN_TOO_SHORT, PIN_INCORRECT, PIN_LOCKED, NETWORK_ERROR
  }

  static class TriesRemaining {
    private final int     triesRemaining;
    private final boolean hasIncorrectGuess;

    TriesRemaining(int triesRemaining, boolean hasIncorrectGuess) {
      this.triesRemaining    = triesRemaining;
      this.hasIncorrectGuess = hasIncorrectGuess;
    }

    public int getCount() {
      return triesRemaining;
    }

    public boolean hasIncorrectGuess() {
      return hasIncorrectGuess;
    }
  }
}

package org.thoughtcrime.securesms.lock.v2;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.whispersystems.signalservice.api.kbs.PinValidityChecker;
import org.whispersystems.signalservice.api.util.Preconditions;

public final class CreateSvrPinViewModel extends ViewModel implements BaseSvrPinViewModel {

  private final MutableLiveData<SvrPin>          userEntry = new MutableLiveData<>(SvrPin.EMPTY);
  private final MutableLiveData<PinKeyboardType> keyboard  = new MutableLiveData<>(PinKeyboardType.NUMERIC);
  private final SingleLiveEvent<NavigationEvent> events    = new SingleLiveEvent<>();
  private final SingleLiveEvent<PinErrorEvent>   errors    = new SingleLiveEvent<>();

  @Override
  public LiveData<SvrPin> getUserEntry() {
    return userEntry;
  }

  @Override
  public LiveData<PinKeyboardType> getKeyboard() {
    return keyboard;
  }

  LiveData<NavigationEvent> getNavigationEvents() { return events; }

  LiveData<PinErrorEvent> getErrorEvents() { return errors; }

  @Override
  @MainThread
  public void setUserEntry(String userEntry) {
    this.userEntry.setValue(SvrPin.from(userEntry));
  }

  @Override
  @MainThread
  public void toggleAlphaNumeric() {
    this.keyboard.setValue(Preconditions.checkNotNull(this.keyboard.getValue()).getOther());
  }

  @Override
  @MainThread
  public void confirm() {
    SvrPin          pin      = Preconditions.checkNotNull(this.getUserEntry().getValue());
    PinKeyboardType keyboard = Preconditions.checkNotNull(this.getKeyboard().getValue());

    if (PinValidityChecker.valid(pin.toString())) {
      events.setValue(new NavigationEvent(pin, keyboard));
    } else {
      errors.setValue(PinErrorEvent.WEAK_PIN);
    }
  }

  static final class NavigationEvent {
    private final SvrPin          userEntry;
    private final PinKeyboardType keyboard;

    NavigationEvent(@NonNull SvrPin userEntry, @NonNull PinKeyboardType keyboard) {
      this.userEntry = userEntry;
      this.keyboard  = keyboard;
    }

    SvrPin getUserEntry() {
      return userEntry;
    }

    PinKeyboardType getKeyboard() {
      return keyboard;
    }
  }

  enum PinErrorEvent {
    WEAK_PIN
  }
}

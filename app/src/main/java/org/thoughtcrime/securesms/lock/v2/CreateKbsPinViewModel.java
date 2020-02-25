package org.thoughtcrime.securesms.lock.v2;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.core.util.Preconditions;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.thoughtcrime.securesms.util.SingleLiveEvent;

public final class CreateKbsPinViewModel extends ViewModel implements BaseKbsPinViewModel {

  private final MutableLiveData<KbsPin>          userEntry = new MutableLiveData<>(KbsPin.EMPTY);
  private final MutableLiveData<PinKeyboardType> keyboard  = new MutableLiveData<>(PinKeyboardType.NUMERIC);
  private final SingleLiveEvent<NavigationEvent> events    = new SingleLiveEvent<>();

  @Override
  public LiveData<KbsPin> getUserEntry() {
    return userEntry;
  }

  @Override
  public LiveData<PinKeyboardType> getKeyboard() {
    return keyboard;
  }

  LiveData<NavigationEvent> getNavigationEvents() { return events; }

  @Override
  @MainThread
  public void setUserEntry(String userEntry) {
    this.userEntry.setValue(KbsPin.from(userEntry));
  }

  @Override
  @MainThread
  public void toggleAlphaNumeric() {
    this.keyboard.setValue(Preconditions.checkNotNull(this.keyboard.getValue()).getOther());
  }

  @Override
  @MainThread
  public void confirm() {
    events.setValue(new NavigationEvent(Preconditions.checkNotNull(this.getUserEntry().getValue()),
                                        Preconditions.checkNotNull(this.getKeyboard().getValue())));
  }

  static final class NavigationEvent {
    private final KbsPin          userEntry;
    private final PinKeyboardType keyboard;

    NavigationEvent(@NonNull KbsPin userEntry, @NonNull PinKeyboardType keyboard) {
      this.userEntry = userEntry;
      this.keyboard  = keyboard;
    }

    KbsPin getUserEntry() {
      return userEntry;
    }

    PinKeyboardType getKeyboard() {
      return keyboard;
    }
  }
}

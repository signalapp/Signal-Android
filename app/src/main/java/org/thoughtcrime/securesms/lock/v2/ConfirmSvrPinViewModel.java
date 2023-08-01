package org.thoughtcrime.securesms.lock.v2;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.pin.SvrRepository;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;
import org.whispersystems.signalservice.api.svr.SecureValueRecovery;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

final class ConfirmSvrPinViewModel extends ViewModel implements BaseSvrPinViewModel {

  private final DefaultValueLiveData<SvrPin>          userEntry     = new DefaultValueLiveData<>(SvrPin.EMPTY);
  private final DefaultValueLiveData<PinKeyboardType> keyboard      = new DefaultValueLiveData<>(PinKeyboardType.NUMERIC);
  private final DefaultValueLiveData<SaveAnimation>   saveAnimation = new DefaultValueLiveData<>(SaveAnimation.NONE);
  private final DefaultValueLiveData<LabelState>      label         = new DefaultValueLiveData<>(LabelState.EMPTY);

  private final SvrPin pinToConfirm;

  private final CompositeDisposable disposables = new CompositeDisposable();

  private ConfirmSvrPinViewModel(@NonNull SvrPin pinToConfirm, @NonNull PinKeyboardType keyboard) {
    this.keyboard.setValue(keyboard);
    this.pinToConfirm = pinToConfirm;
  }

  LiveData<SaveAnimation> getSaveAnimation() {
    return Transformations.distinctUntilChanged(saveAnimation);
  }

  LiveData<LabelState> getLabel() {
    return Transformations.distinctUntilChanged(label);
  }

  @Override
  public void confirm() {
    SvrPin userEntry = this.userEntry.getValue();

    if (pinToConfirm.toString().equals(userEntry.toString())) {
      this.label.setValue(LabelState.CREATING_PIN);
      this.saveAnimation.setValue(SaveAnimation.LOADING);

      disposables.add(
          Single.fromCallable(() -> SvrRepository.setPin(pinToConfirm.toString(), this.keyboard.getValue()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                  if (result instanceof SecureValueRecovery.BackupResponse.Success) {
                    this.saveAnimation.setValue(SaveAnimation.SUCCESS);
                  } else {
                    this.saveAnimation.setValue(SaveAnimation.FAILURE);
                  }
                })
      );
    } else {
      this.userEntry.setValue(SvrPin.EMPTY);
      this.label.setValue(LabelState.PIN_DOES_NOT_MATCH);
    }
  }

  @Override
  public LiveData<SvrPin> getUserEntry() {
    return userEntry;
  }

  @Override
  public LiveData<PinKeyboardType> getKeyboard() {
    return keyboard;
  }

  @MainThread
  public void setUserEntry(String userEntry) {
    this.userEntry.setValue(SvrPin.from(userEntry));
  }

  @MainThread
  public void toggleAlphaNumeric() {
    this.keyboard.setValue(this.keyboard.getValue().getOther());
  }

  @Override
  protected void onCleared() {
    disposables.clear();
  }

  enum LabelState {
    RE_ENTER_PIN,
    PIN_DOES_NOT_MATCH,
    CREATING_PIN,
    EMPTY
  }

  enum SaveAnimation {
    NONE,
    LOADING,
    SUCCESS,
    FAILURE
  }

  static final class Factory implements ViewModelProvider.Factory {

    private final SvrPin          pinToConfirm;
    private final PinKeyboardType keyboard;

    Factory(@NonNull SvrPin pinToConfirm, @NonNull PinKeyboardType keyboard) {
      this.pinToConfirm = pinToConfirm;
      this.keyboard     = keyboard;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection unchecked
      return (T) new ConfirmSvrPinViewModel(pinToConfirm, keyboard);
    }
  }
}

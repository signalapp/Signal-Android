package org.thoughtcrime.securesms.profiles.manage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import org.signal.core.util.StringUtil;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.util.SingleLiveEvent;

public final class EditProfileNameViewModel extends ViewModel {

  private final EditProfileRepository      repository;
  private final MutableLiveData<SaveState> saveState;
  private final SingleLiveEvent<Event>     events;

  public EditProfileNameViewModel() {
    this.repository = new EditProfileRepository();
    this.saveState  = new MutableLiveData<>(SaveState.IDLE);
    this.events     = new SingleLiveEvent<>();
  }

  void onGivenNameChanged(@NonNull String text) {
    if (StringUtil.isVisuallyEmpty(text)) {
      saveState.setValue(SaveState.DISABLED);
    } else {
      saveState.setValue(SaveState.IDLE);
    }
  }

  @NonNull LiveData<SaveState> getSaveState() {
    return Transformations.distinctUntilChanged(saveState);
  }

  @NonNull LiveData<Event> getEvents() {
    return events;
  }

  void onSaveClicked(@NonNull Context context, @NonNull String givenName, @NonNull String familyName) {
    saveState.setValue(SaveState.IN_PROGRESS);
    repository.setName(context, ProfileName.fromParts(givenName, familyName), result -> {
      switch (result) {
        case SUCCESS:
          saveState.postValue(SaveState.DONE);
          break;
        case FAILURE_NETWORK:
          saveState.postValue(SaveState.IDLE);
          events.postValue(Event.NETWORK_FAILURE);
          break;
      }
    });
  }

  enum SaveState {
    IDLE, IN_PROGRESS, DONE, DISABLED
  }

  enum Event {
    NETWORK_FAILURE
  }
}

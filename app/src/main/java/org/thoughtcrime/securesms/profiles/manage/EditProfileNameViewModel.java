package org.thoughtcrime.securesms.profiles.manage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.util.SingleLiveEvent;

public final class EditProfileNameViewModel extends ViewModel {

  private final ManageProfileRepository    repository;
  private final MutableLiveData<SaveState> saveState;
  private final SingleLiveEvent<Event>     events;

  public EditProfileNameViewModel() {
    this.repository = new ManageProfileRepository();
    this.saveState  = new MutableLiveData<>(SaveState.IDLE);
    this.events     = new SingleLiveEvent<>();
  }

  @NonNull LiveData<SaveState> getSaveState() {
    return saveState;
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
    IDLE, IN_PROGRESS, DONE
  }

  enum Event {
    NETWORK_FAILURE
  }
}

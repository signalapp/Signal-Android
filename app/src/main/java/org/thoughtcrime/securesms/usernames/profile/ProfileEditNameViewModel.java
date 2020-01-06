package org.thoughtcrime.securesms.usernames.profile;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.util.SingleLiveEvent;

class ProfileEditNameViewModel extends ViewModel {

  private final ProfileEditNameRepository repo;
  private final SingleLiveEvent<Event>    events;
  private final MutableLiveData<Boolean> loading;

  private ProfileEditNameViewModel() {
    this.repo    = new ProfileEditNameRepository();
    this.events  = new SingleLiveEvent<>();
    this.loading = new MutableLiveData<>();
  }

  void onSubmitPressed(@NonNull String profileName) {
    loading.setValue(true);

    repo.setProfileName(profileName, result -> {
      switch (result) {
        case SUCCESS:
          events.postValue(Event.SUCCESS);
          break;
        case NETWORK_FAILURE:
          events.postValue(Event.NETWORK_FAILURE);
          break;
      }

      loading.postValue(false);
    });
  }

  @NonNull LiveData<Event> getEvents() {
    return events;
  }

  @NonNull LiveData<Boolean> isLoading() {
    return loading;
  }

  enum Event {
    SUCCESS, NETWORK_FAILURE
  }

  static class Factory extends ViewModelProvider.NewInstanceFactory {
    @Override
    public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new ProfileEditNameViewModel());
    }
  }
}

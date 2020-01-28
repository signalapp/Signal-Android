package org.thoughtcrime.securesms.profiles.edit;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.util.livedata.LiveDataPair;
import org.whispersystems.libsignal.util.guava.Optional;

class EditProfileViewModel extends ViewModel {

  private final MutableLiveData<String>           givenName           = new MutableLiveData<>();
  private final MutableLiveData<String>           familyName          = new MutableLiveData<>();
  private final LiveData<ProfileName>             internalProfileName = Transformations.map(new LiveDataPair<>(givenName, familyName),
                                                                                            pair -> ProfileName.fromParts(pair.first(), pair.second()));
  private final MutableLiveData<byte[]>           internalAvatar      = new MutableLiveData<>();
  private final MutableLiveData<Optional<String>> internalUsername    = new MutableLiveData<>();
  private final EditProfileRepository             repository;

  private EditProfileViewModel(@NonNull EditProfileRepository repository, boolean hasInstanceState) {
    this.repository = repository;

    repository.getCurrentUsername(internalUsername::postValue);

    if (!hasInstanceState) {
      repository.getCurrentProfileName(name -> {
        givenName.setValue(name.getGivenName());
        familyName.setValue(name.getFamilyName());
      });
      repository.getCurrentAvatar(internalAvatar::setValue);
    }
  }

  public LiveData<String> givenName() {
    return Transformations.distinctUntilChanged(givenName);
  }

  public LiveData<String> familyName() {
    return Transformations.distinctUntilChanged(familyName);
  }

  public LiveData<ProfileName> profileName() {
    return Transformations.distinctUntilChanged(internalProfileName);
  }

  public LiveData<byte[]> avatar() {
    return Transformations.distinctUntilChanged(internalAvatar);
  }

  public LiveData<Optional<String>> username() {
    return internalUsername;
  }

  public boolean hasAvatar() {
    return internalAvatar.getValue() != null;
  }

  @MainThread
  public byte[] getAvatarSnapshot() {
    return internalAvatar.getValue();
  }

  public void setGivenName(String givenName) {
    this.givenName.setValue(givenName);
  }

  public void setFamilyName(String familyName) {
    this.familyName.setValue(familyName);
  }

  public void setAvatar(byte[] avatar) {
    internalAvatar.setValue(avatar);
  }

  public void submitProfile(Consumer<EditProfileRepository.UploadResult> uploadResultConsumer) {
    ProfileName profileName = internalProfileName.getValue();
    if (profileName == null) {
      return;
    }

    repository.uploadProfile(profileName, internalAvatar.getValue(), uploadResultConsumer);
  }

  static class Factory implements ViewModelProvider.Factory {

    private final EditProfileRepository repository;
    private final boolean hasInstanceState;

    Factory(@NonNull EditProfileRepository repository, boolean hasInstanceState) {
      this.repository       = repository;
      this.hasInstanceState = hasInstanceState;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection unchecked
      return (T) new EditProfileViewModel(repository, hasInstanceState);
    }
  }
}

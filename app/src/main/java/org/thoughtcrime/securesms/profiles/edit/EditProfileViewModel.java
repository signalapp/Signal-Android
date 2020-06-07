package org.thoughtcrime.securesms.profiles.edit;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.util.livedata.LiveDataPair;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Objects;

class EditProfileViewModel extends ViewModel {

  private final MutableLiveData<String>           givenName           = new MutableLiveData<>();
  private final MutableLiveData<String>           familyName          = new MutableLiveData<>();
  private final LiveData<ProfileName>             internalProfileName = Transformations.map(new LiveDataPair<>(givenName, familyName),
                                                                                            pair -> ProfileName.fromParts(pair.first(), pair.second()));
  private final MutableLiveData<byte[]>           internalAvatar      = new MutableLiveData<>();
  private final MutableLiveData<byte[]>           originalAvatar      = new MutableLiveData<>();
  private final MutableLiveData<Optional<String>> internalUsername    = new MutableLiveData<>();
  private final MutableLiveData<String>           originalDisplayName = new MutableLiveData<>();
  private final LiveData<Boolean>                 isFormValid         = Transformations.map(givenName, name -> !name.isEmpty());
  private final EditProfileRepository             repository;
  private final GroupId                           groupId;

  private EditProfileViewModel(@NonNull EditProfileRepository repository, boolean hasInstanceState, @Nullable GroupId groupId) {
    this.repository  = repository;
    this.groupId     = groupId;

    repository.getCurrentUsername(internalUsername::postValue);

    if (!hasInstanceState) {
      if (groupId != null) {
        repository.getCurrentDisplayName(originalDisplayName::setValue);
        repository.getCurrentName(givenName::setValue);
      } else {
        repository.getCurrentProfileName(name -> {
          givenName.setValue(name.getGivenName());
          familyName.setValue(name.getFamilyName());
        });
      }

      repository.getCurrentAvatar(value -> {
        internalAvatar.setValue(value);
        originalAvatar.setValue(value);
      });
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

  public LiveData<Boolean> isFormValid() {
    return Transformations.distinctUntilChanged(isFormValid);
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

  public boolean isGroup() {
    return groupId != null;
  }

  public boolean canRemoveProfilePhoto() {
    return hasAvatar();
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
    ProfileName profileName = isGroup() ? ProfileName.EMPTY : internalProfileName.getValue();
    String      displayName = isGroup() ? givenName.getValue() : "";

    if (profileName == null || displayName == null) {
      return;
    }

    byte[] oldAvatar      = originalAvatar.getValue();
    byte[] newAvatar      = internalAvatar.getValue();
    String oldDisplayName = isGroup() ? originalDisplayName.getValue() : null;

    repository.uploadProfile(profileName,
                             displayName,
                             !Objects.equals(oldDisplayName, displayName),
                             newAvatar,
                             oldAvatar != newAvatar,
                             uploadResultConsumer);
  }

  static class Factory implements ViewModelProvider.Factory {

    private final EditProfileRepository repository;
    private final boolean               hasInstanceState;
    private final GroupId               groupId;

    Factory(@NonNull EditProfileRepository repository, boolean hasInstanceState, @Nullable GroupId groupId) {
      this.repository       = repository;
      this.hasInstanceState = hasInstanceState;
      this.groupId          = groupId;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection unchecked
      return (T) new EditProfileViewModel(repository, hasInstanceState, groupId);
    }
  }
}

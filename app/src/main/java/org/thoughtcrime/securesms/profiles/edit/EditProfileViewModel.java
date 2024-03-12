package org.thoughtcrime.securesms.profiles.edit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.conversation.colors.AvatarColor;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues;
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.profiles.edit.EditProfileRepository.UploadResult;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.signal.core.util.StringUtil;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

import java.util.Arrays;
import java.util.Objects;

class EditProfileViewModel extends ViewModel {

  private static final String TAG = Log.tag(EditProfileViewModel.class);

  private final MutableLiveData<String>       givenName           = new MutableLiveData<>();
  private final MutableLiveData<String>       familyName          = new MutableLiveData<>();
  private final LiveData<String>              trimmedGivenName    = Transformations.map(givenName, StringUtil::trimToVisualBounds);
  private final LiveData<String>              trimmedFamilyName   = Transformations.map(familyName, StringUtil::trimToVisualBounds);
  private final LiveData<ProfileName>         internalProfileName = LiveDataUtil.combineLatest(trimmedGivenName, trimmedFamilyName, ProfileName::fromParts);
  private final MutableLiveData<byte[]>       internalAvatar      = new MutableLiveData<>();
  private final MutableLiveData<byte[]>       originalAvatar      = new MutableLiveData<>();
  private final MutableLiveData<String>       originalDisplayName = new MutableLiveData<>();
  private final SingleLiveEvent<UploadResult> uploadResult        = new SingleLiveEvent<>();
  private final MutableLiveData<AvatarColor>  avatarColor         = new MutableLiveData<>();
  private final LiveData<Boolean>             isFormValid;
  private final EditProfileRepository         repository;
  private final GroupId                       groupId;
  private       String                        originalDescription;
  private       Media                         avatarMedia;

  private EditProfileViewModel(@NonNull EditProfileRepository repository, boolean hasInstanceState, @Nullable GroupId groupId) {
    this.repository  = repository;
    this.groupId     = groupId;
    this.isFormValid = groupId != null && groupId.isMms() ? LiveDataUtil.just(true)
                                                          : Transformations.map(trimmedGivenName, s -> s.length() > 0);

    if (!hasInstanceState) {
      if (groupId != null) {
        repository.getCurrentDisplayName(originalDisplayName::setValue);
        repository.getCurrentName(givenName::setValue);
        repository.getCurrentDescription(d -> {
          originalDescription = d;
          familyName.setValue(d);
        });
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

      repository.getCurrentAvatarColor(avatarColor::setValue);
    }
  }

  public LiveData<AvatarColor> avatarColor() {
    return Transformations.distinctUntilChanged(avatarColor);
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

  public boolean hasAvatar() {
    return internalAvatar.getValue() != null;
  }

  public boolean isGroup() {
    return groupId != null;
  }

  public @Nullable Media getAvatarMedia() {
    return avatarMedia;
  }

  public void setAvatarMedia(@Nullable Media avatarMedia) {
    this.avatarMedia = avatarMedia;
  }

  public @Nullable GroupId getGroupId() {
    return groupId;
  }

  public SingleLiveEvent<UploadResult> getUploadResult() {
    return uploadResult;
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

  public void submitProfile() {
    ProfileName profileName = isGroup() ? ProfileName.EMPTY : internalProfileName.getValue();
    String      displayName = isGroup() ? givenName.getValue() : "";
    String      description = isGroup() ? familyName.getValue() : "";

    if (profileName == null || displayName == null) {
      return;
    }

    byte[] oldAvatar      = originalAvatar.getValue();
    byte[] newAvatar      = internalAvatar.getValue();
    String oldDisplayName = isGroup() ? originalDisplayName.getValue() : null;
    String oldDescription = isGroup() ? originalDescription : null;

    if (!isGroup() && SignalStore.phoneNumberPrivacy().getPhoneNumberDiscoverabilityMode() == PhoneNumberDiscoverabilityMode.UNDECIDED) {
      Log.i(TAG, "Phone number discoverability mode is still UNDECIDED. Setting to DISCOVERABLE.");
      SignalStore.phoneNumberPrivacy().setPhoneNumberDiscoverabilityMode(PhoneNumberDiscoverabilityMode.DISCOVERABLE);
    }

    repository.uploadProfile(profileName,
                             displayName,
                             !Objects.equals(StringUtil.stripBidiProtection(oldDisplayName), displayName),
                             description,
                             !Objects.equals(StringUtil.stripBidiProtection(oldDescription), description),
                             newAvatar,
                             !Arrays.equals(oldAvatar, newAvatar),
                             uploadResult::postValue);
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

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection unchecked
      return (T) new EditProfileViewModel(repository, hasInstanceState, groupId);
    }
  }
}

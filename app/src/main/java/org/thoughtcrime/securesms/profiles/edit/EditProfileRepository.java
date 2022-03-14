package org.thoughtcrime.securesms.profiles.edit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import org.thoughtcrime.securesms.conversation.colors.AvatarColor;
import org.thoughtcrime.securesms.profiles.ProfileName;

import java.util.Optional;


interface EditProfileRepository {

  void getCurrentAvatarColor(@NonNull Consumer<AvatarColor> avatarColorConsumer);

  void getCurrentProfileName(@NonNull Consumer<ProfileName> profileNameConsumer);

  void getCurrentAvatar(@NonNull Consumer<byte[]> avatarConsumer);

  void getCurrentDisplayName(@NonNull Consumer<String> displayNameConsumer);

  void getCurrentName(@NonNull Consumer<String> nameConsumer);

  void getCurrentDescription(@NonNull Consumer<String> descriptionConsumer);

  void uploadProfile(@NonNull ProfileName profileName,
                     @NonNull String displayName,
                     boolean displayNameChanged,
                     @NonNull String description,
                     boolean descriptionChanged,
                     @Nullable byte[] avatar,
                     boolean avatarChanged,
                     @NonNull Consumer<UploadResult> uploadResultConsumer);

  void getCurrentUsername(@NonNull Consumer<Optional<String>> callback);

  enum UploadResult {
    SUCCESS,
    ERROR_IO,
    ERROR_BAD_RECIPIENT
  }
}

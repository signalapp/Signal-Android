package org.thoughtcrime.securesms.profiles.edit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import org.thoughtcrime.securesms.profiles.ProfileName;
import org.whispersystems.libsignal.util.guava.Optional;

interface EditProfileRepository {

  void getCurrentProfileName(@NonNull Consumer<ProfileName> profileNameConsumer);

  void getCurrentAvatar(@NonNull Consumer<byte[]> avatarConsumer);

  void getCurrentDisplayName(@NonNull Consumer<String> displayNameConsumer);

  void uploadProfile(@NonNull ProfileName profileName, @Nullable String displayName, @Nullable byte[] avatar, boolean avatarChanged, @NonNull Consumer<UploadResult> uploadResultConsumer);

  void getCurrentUsername(@NonNull Consumer<Optional<String>> callback);

  enum UploadResult {
    SUCCESS,
    ERROR_IO,
    ERROR_BAD_RECIPIENT
  }
}

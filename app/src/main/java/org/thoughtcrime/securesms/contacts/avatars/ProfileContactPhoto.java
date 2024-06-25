package org.thoughtcrime.securesms.contacts.avatars;


import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.model.ProfileAvatarFileDetails;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Objects;

public class ProfileContactPhoto implements ContactPhoto {

  private final @NonNull Recipient                recipient;
  private final @NonNull String                   avatarObject;
  private final @NonNull ProfileAvatarFileDetails profileAvatarFileDetails;

  public ProfileContactPhoto(@NonNull Recipient recipient) {
    this.recipient                = recipient;
    this.avatarObject             = recipient.getProfileAvatar() == null ? "" : recipient.getProfileAvatar();
    this.profileAvatarFileDetails = recipient.getProfileAvatarFileDetails();
  }

  @Override
  public @NonNull InputStream openInputStream(Context context) throws IOException {
    return AvatarHelper.getAvatar(context, recipient.getId());
  }

  @Override
  public @Nullable Uri getUri(@NonNull Context context) {
    return null;
  }

  @Override
  public boolean isProfilePhoto() {
    return true;
  }

  @Override
  public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
    messageDigest.update(recipient.getId().serialize().getBytes());
    messageDigest.update(avatarObject.getBytes());
    messageDigest.update(profileAvatarFileDetails.getDiskCacheKeyBytes());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProfileContactPhoto that = (ProfileContactPhoto) o;
    return recipient.equals(that.recipient) &&
           avatarObject.equals(that.avatarObject) &&
           profileAvatarFileDetails.equals(that.profileAvatarFileDetails);
  }

  @Override
  public int hashCode() {
    return Objects.hash(recipient, avatarObject, profileAvatarFileDetails);
  }

  public @NonNull Recipient getRecipient() {
    return recipient;
  }
}

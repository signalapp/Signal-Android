package org.thoughtcrime.securesms.contacts.avatars;


import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

public class ProfileContactPhoto implements ContactPhoto {

  private final @NonNull RecipientId recipient;
  private final @NonNull String      avatarObject;

  public ProfileContactPhoto(@NonNull RecipientId recipient, @NonNull String avatarObject) {
    this.recipient    = recipient;
    this.avatarObject = avatarObject;
  }

  @Override
  public @NonNull InputStream openInputStream(Context context) throws IOException {
    return AvatarHelper.getInputStreamFor(context, recipient);
  }

  @Override
  public @Nullable Uri getUri(@NonNull Context context) {
    File avatarFile = AvatarHelper.getAvatarFile(context, recipient);
    return avatarFile.exists() ? Uri.fromFile(avatarFile) : null;
  }

  @Override
  public boolean isProfilePhoto() {
    return true;
  }

  @Override
  public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
    messageDigest.update(recipient.serialize().getBytes());
    messageDigest.update(avatarObject.getBytes());
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof ProfileContactPhoto)) return false;

    ProfileContactPhoto that = (ProfileContactPhoto)other;

    return this.recipient.equals(that.recipient) && this.avatarObject.equals(that.avatarObject);
  }

  @Override
  public int hashCode() {
    return recipient.hashCode() ^ avatarObject.hashCode();
  }
}

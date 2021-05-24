package org.session.libsession.avatars;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.session.libsession.utilities.Address;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

public class ProfileContactPhoto implements ContactPhoto {

  private final @NonNull
  Address address;
  public  final @NonNull String  avatarObject;

  public ProfileContactPhoto(@NonNull Address address, @NonNull String avatarObject) {
    this.address      = address;
    this.avatarObject = avatarObject;
  }

  @Override
  public InputStream openInputStream(Context context) throws IOException {
    return AvatarHelper.getInputStreamFor(context, address);
  }

  @Override
  public @Nullable Uri getUri(@NonNull Context context) {
    return Uri.fromFile(AvatarHelper.getAvatarFile(context, address));
  }

  @Override
  public boolean isProfilePhoto() {
    return true;
  }

  @Override
  public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
    messageDigest.update(address.serialize().getBytes());
    messageDigest.update(avatarObject.getBytes());
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof ProfileContactPhoto)) return false;

    ProfileContactPhoto that = (ProfileContactPhoto)other;

    return this.address.equals(that.address) && this.avatarObject.equals(that.avatarObject);
  }

  @Override
  public int hashCode() {
    return address.hashCode() ^ avatarObject.hashCode();
  }
}

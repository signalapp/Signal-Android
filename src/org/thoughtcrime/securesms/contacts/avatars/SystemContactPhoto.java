package org.thoughtcrime.securesms.contacts.avatars;


import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.util.Conversions;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.MessageDigest;

public class SystemContactPhoto implements ContactPhoto {

  private final @NonNull Address address;
  private final @NonNull Uri     contactPhotoUri;
  private final          long    lastModifiedTime;

  public SystemContactPhoto(@NonNull Address address, @NonNull Uri contactPhotoUri, long lastModifiedTime) {
    this.address          = address;
    this.contactPhotoUri  = contactPhotoUri;
    this.lastModifiedTime = lastModifiedTime;
  }

  @Override
  public InputStream openInputStream(Context context) throws FileNotFoundException {
    return context.getContentResolver().openInputStream(contactPhotoUri);
  }

  @Nullable
  @Override
  public Uri getUri(@NonNull Context context) {
    return contactPhotoUri;
  }

  @Override
  public boolean isProfilePhoto() {
    return false;
  }

  @Override
  public void updateDiskCacheKey(MessageDigest messageDigest) {
    messageDigest.update(address.serialize().getBytes());
    messageDigest.update(contactPhotoUri.toString().getBytes());
    messageDigest.update(Conversions.longToByteArray(lastModifiedTime));
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof SystemContactPhoto)) return false;

    SystemContactPhoto that = (SystemContactPhoto)other;

    return this.address.equals(that.address) && this.contactPhotoUri.equals(that.contactPhotoUri) && this.lastModifiedTime == that.lastModifiedTime;
  }

  @Override
  public int hashCode() {
    return address.hashCode() ^ contactPhotoUri.hashCode() ^ (int)lastModifiedTime;
  }

}

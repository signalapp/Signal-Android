package org.thoughtcrime.securesms.contacts.avatars;


import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.Conversions;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.MessageDigest;

public class SystemContactPhoto implements ContactPhoto {

  private final RecipientId recipientId;
  private final Uri         contactPhotoUri;
  private final long        lastModifiedTime;

  public SystemContactPhoto(@NonNull RecipientId recipientId, @NonNull Uri contactPhotoUri, long lastModifiedTime) {
    this.recipientId = recipientId;
    this.contactPhotoUri  = contactPhotoUri;
    this.lastModifiedTime = lastModifiedTime;
  }

  @Override
  public InputStream openInputStream(Context context) throws FileNotFoundException {
    return context.getContentResolver().openInputStream(contactPhotoUri);
  }

  @Override
  public @Nullable Uri getUri(@NonNull Context context) {
    return contactPhotoUri;
  }

  @Override
  public boolean isProfilePhoto() {
    return false;
  }

  @Override
  public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
    messageDigest.update(recipientId.serialize().getBytes());
    messageDigest.update(contactPhotoUri.toString().getBytes());
    messageDigest.update(Conversions.longToByteArray(lastModifiedTime));
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof SystemContactPhoto)) return false;

    SystemContactPhoto that = (SystemContactPhoto)other;

    return this.recipientId.equals(that.recipientId) && this.contactPhotoUri.equals(that.contactPhotoUri) && this.lastModifiedTime == that.lastModifiedTime;
  }

  @Override
  public int hashCode() {
    return recipientId.hashCode() ^ contactPhotoUri.hashCode() ^ (int)lastModifiedTime;
  }

}

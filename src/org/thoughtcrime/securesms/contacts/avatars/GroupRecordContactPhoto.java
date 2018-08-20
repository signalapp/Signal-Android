package org.thoughtcrime.securesms.contacts.avatars;


import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.util.Conversions;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

public class GroupRecordContactPhoto implements ContactPhoto {

  private final @NonNull Address address;
  private final          long avatarId;

  public GroupRecordContactPhoto(@NonNull Address address, long avatarId) {
    this.address  = address;
    this.avatarId = avatarId;
  }

  @Override
  public InputStream openInputStream(Context context) throws IOException {
    GroupDatabase                       groupDatabase = DatabaseFactory.getGroupDatabase(context);
    Optional<GroupDatabase.GroupRecord> groupRecord   = groupDatabase.getGroup(address.toGroupString());

    if (groupRecord.isPresent() && groupRecord.get().getAvatar() != null) {
      return new ByteArrayInputStream(groupRecord.get().getAvatar());
    }

    throw new IOException("Couldn't load avatar for group: " + address.toGroupString());
  }

  @Override
  public @Nullable Uri getUri(@NonNull Context context) {
    return null;
  }

  @Override
  public boolean isProfilePhoto() {
    return false;
  }

  @Override
  public void updateDiskCacheKey(MessageDigest messageDigest) {
    messageDigest.update(address.serialize().getBytes());
    messageDigest.update(Conversions.longToByteArray(avatarId));
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof GroupRecordContactPhoto)) return false;

    GroupRecordContactPhoto that = (GroupRecordContactPhoto)other;
    return this.address.equals(that.address) && this.avatarId == that.avatarId;
  }

  @Override
  public int hashCode() {
    return this.address.hashCode() ^ (int) avatarId;
  }

}

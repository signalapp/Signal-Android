package org.session.libsession.avatars;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.session.libsession.messaging.MessagingModuleConfiguration;
import org.session.libsession.database.StorageProtocol;
import org.session.libsession.utilities.Address;
import org.session.libsession.utilities.GroupRecord;
import org.session.libsession.utilities.Conversions;
import org.session.libsignal.utilities.guava.Optional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

public class GroupRecordContactPhoto implements ContactPhoto {

  private final @NonNull
  Address address;
  private final          long avatarId;

  public GroupRecordContactPhoto(@NonNull Address address, long avatarId) {
    this.address  = address;
    this.avatarId = avatarId;
  }

  @Override
  public InputStream openInputStream(Context context) throws IOException {
    StorageProtocol groupDatabase = MessagingModuleConfiguration.shared.getStorage();
    Optional<GroupRecord> groupRecord   = Optional.of(groupDatabase.getGroup(address.toGroupString()));

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
  public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
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

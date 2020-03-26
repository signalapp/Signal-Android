package org.thoughtcrime.securesms.groups;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.util.Hex;

import java.io.IOException;

public final class GroupId {

  private static final String ENCODED_SIGNAL_GROUP_PREFIX = "__textsecure_group__!";
  private static final String ENCODED_MMS_GROUP_PREFIX    = "__signal_mms_group__!";

  private final String encodedId;

  private GroupId(@NonNull String encodedId) {
    this.encodedId = encodedId;
  }

  public static @NonNull GroupId v1(byte[] gv1GroupIdBytes) {
    return new GroupId(ENCODED_SIGNAL_GROUP_PREFIX + Hex.toStringCondensed(gv1GroupIdBytes));
  }

  public static @NonNull GroupId mms(byte[] mmsGroupIdBytes) {
    return new GroupId(ENCODED_MMS_GROUP_PREFIX + Hex.toStringCondensed(mmsGroupIdBytes));
  }

  public static @NonNull GroupId parse(@NonNull String encodedGroupId) {
    try {
      if (!isEncodedGroup(encodedGroupId)) {
        throw new IOException("Invalid encoding");
      }

      byte[] bytes = extractDecodedId(encodedGroupId);
      return isMmsGroup(encodedGroupId) ? mms(bytes) : v1(bytes);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  public static @Nullable GroupId parseNullable(@Nullable String encodedGroupId) {
    if (encodedGroupId == null) {
      return null;
    }

    return parse(encodedGroupId);
  }

  public static boolean isEncodedGroup(@NonNull String groupId) {
    return groupId.startsWith(ENCODED_SIGNAL_GROUP_PREFIX) || groupId.startsWith(ENCODED_MMS_GROUP_PREFIX);
  }

  private static byte[] extractDecodedId(@NonNull String encodedGroupId) throws IOException {
    return Hex.fromStringCondensed(encodedGroupId.split("!", 2)[1]);
  }

  private static boolean isMmsGroup(@NonNull String groupId) {
    return groupId.startsWith(ENCODED_MMS_GROUP_PREFIX);
  }

  public byte[] getDecodedId() {
    try {
      return extractDecodedId(encodedId);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  public boolean isMmsGroup() {
    return isMmsGroup(encodedId);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj instanceof GroupId) {
      return ((GroupId) obj).encodedId.equals(encodedId);
    }

    return false;
  }

  @Override
  public int hashCode() {
    return encodedId.hashCode();
  }

  @NonNull
  @Override
  public String toString() {
    return encodedId;
  }
}

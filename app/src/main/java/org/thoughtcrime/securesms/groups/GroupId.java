package org.thoughtcrime.securesms.groups;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.zkgroup.groups.GroupIdentifier;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.security.SecureRandom;

public abstract class GroupId {

  private static final String ENCODED_SIGNAL_GROUP_PREFIX = "__textsecure_group__!";
  private static final String ENCODED_MMS_GROUP_PREFIX    = "__signal_mms_group__!";
  private static final int    MMS_BYTE_LENGTH             = 16;
  private static final int    V1_MMS_BYTE_LENGTH          = 16;
  private static final int    V2_BYTE_LENGTH              = GroupIdentifier.SIZE;
  private static final int    V2_ENCODED_LENGTH           = ENCODED_SIGNAL_GROUP_PREFIX.length() + V2_BYTE_LENGTH * 2;

  private final String encodedId;

  private GroupId(@NonNull String prefix, @NonNull byte[] bytes) {
    this.encodedId = prefix + Hex.toStringCondensed(bytes);
  }

  public static @NonNull GroupId.Mms mms(byte[] mmsGroupIdBytes) {
    return new GroupId.Mms(mmsGroupIdBytes);
  }

  public static @NonNull GroupId.V1 v1(byte[] gv1GroupIdBytes) {
    if (gv1GroupIdBytes.length == V2_BYTE_LENGTH) {
      throw new AssertionError();
    }
    return new GroupId.V1(gv1GroupIdBytes);
  }

  public static GroupId.V1 createV1(@NonNull SecureRandom secureRandom) {
    return v1(Util.getSecretBytes(secureRandom, V1_MMS_BYTE_LENGTH));
  }

  public static GroupId.Mms createMms(@NonNull SecureRandom secureRandom) {
    return mms(Util.getSecretBytes(secureRandom, MMS_BYTE_LENGTH));
  }

  public static GroupId.V2 v2(@NonNull byte[] bytes) {
    if (bytes.length != V2_BYTE_LENGTH) {
      throw new AssertionError();
    }
    return new GroupId.V2(bytes);
  }

  public static GroupId.V2 v2(@NonNull GroupIdentifier groupIdentifier) {
    return v2(groupIdentifier.serialize());
  }

  public static GroupId.V2 v2(@NonNull GroupMasterKey masterKey) {
    return v2(GroupSecretParams.deriveFromMasterKey(masterKey)
                               .getPublicParams()
                               .getGroupIdentifier());
  }

  public static @NonNull GroupId parse(@NonNull String encodedGroupId) {
    try {
      if (!isEncodedGroup(encodedGroupId)) {
        throw new IOException("Invalid encoding");
      }

      byte[] bytes = extractDecodedId(encodedGroupId);

           if (encodedGroupId.startsWith(ENCODED_MMS_GROUP_PREFIX)) return mms(bytes);
      else if (encodedGroupId.length() == V2_ENCODED_LENGTH)        return v2(bytes);
      else                                                          return v1(bytes);

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

  public byte[] getDecodedId() {
    try {
      return extractDecodedId(encodedId);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
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

  public abstract boolean isMms();

  public abstract boolean isV1();

  public abstract boolean isV2();

  public abstract boolean isPush();

  public GroupId.Mms requireMms() {
    if (this instanceof GroupId.Mms) return (GroupId.Mms) this;
    throw new AssertionError();
  }

  public GroupId.V1 requireV1() {
    if (this instanceof GroupId.V1) return (GroupId.V1) this;
    throw new AssertionError();
  }

  public GroupId.V2 requireV2() {
    if (this instanceof GroupId.V2) return (GroupId.V2) this;
    throw new AssertionError();
  }

  public GroupId.Push requirePush() {
    if (this instanceof GroupId.Push) return (GroupId.Push) this;
    throw new AssertionError();
  }

  public static final class Mms extends GroupId {

    private Mms(@NonNull byte[] bytes) {
      super(ENCODED_MMS_GROUP_PREFIX, bytes);
    }

    @Override
    public boolean isMms() {
      return true;
    }

    @Override
    public boolean isV1() {
      return false;
    }

    @Override
    public boolean isV2() {
      return false;
    }

    @Override
    public boolean isPush() {
      return false;
    }
  }

  public static abstract class Push extends GroupId {
    private Push(@NonNull byte[] bytes) {
      super(ENCODED_SIGNAL_GROUP_PREFIX, bytes);
    }

    @Override
    public boolean isMms() {
      return false;
    }

    @Override
    public boolean isPush() {
      return true;
    }
  }

  public static final class V1 extends GroupId.Push {

    private V1(@NonNull byte[] bytes) {
      super(bytes);
    }

    @Override
    public boolean isV1() {
    return true;
  }

    @Override
    public boolean isV2() {
    return false;
  }
  }

  public static final class V2 extends GroupId.Push {

    private V2(@NonNull byte[] bytes) {
      super(bytes);
    }

    @Override
    public boolean isV1() {
      return false;
    }

    @Override
    public boolean isV2() {
      return true;
    }
  }
}

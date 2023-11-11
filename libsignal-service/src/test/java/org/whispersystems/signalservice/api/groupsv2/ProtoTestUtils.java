package org.whispersystems.signalservice.api.groupsv2;

import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.RequestingMember;
import org.signal.storageservice.protos.groups.local.DecryptedApproveMember;
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.util.Util;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

import okio.Buffer;
import okio.ByteString;

final class ProtoTestUtils {

  static ProfileKey randomProfileKey() {
    byte[] contents = new byte[32];
    new SecureRandom().nextBytes(contents);
    try {
      return new ProfileKey(contents);
    } catch (InvalidInputException e) {
      throw new AssertionError();
    }
  }

  /**
   * Emulates encryption by creating a unique {@link ByteString} that won't equal a byte string created from the {@link UUID}.
   */
  static ByteString encrypt(UUID uuid) {
    byte[] uuidBytes = UuidUtil.toByteArray(uuid);
    return ByteString.of(Arrays.copyOf(uuidBytes, uuidBytes.length + 1));
  }

  /**
   * Emulates a presentation by concatenating the uuid and profile key which makes it suitable for
   * equality assertions in these tests.
   */
  static ByteString presentation(UUID uuid, ProfileKey profileKey) {
    byte[] uuidBytes       = UuidUtil.toByteArray(uuid);
    byte[] profileKeyBytes = profileKey.serialize();
    byte[] concat          = new byte[uuidBytes.length + profileKeyBytes.length];

    System.arraycopy(uuidBytes, 0, concat, 0, uuidBytes.length);
    System.arraycopy(profileKeyBytes, 0, concat, uuidBytes.length, profileKeyBytes.length);

    return ByteString.of(concat);
  }

  /**
   * Emulates a presentation by concatenating the uuid and profile key which makes it suitable for
   * equality assertions in these tests.
   */
  static ByteString presentation(ByteString uuid, ByteString profileKey) {
    try (Buffer buffer = new Buffer()) {
      buffer.write(uuid);
      buffer.write(profileKey);
      return buffer.readByteString();
    }
  }

  static DecryptedModifyMemberRole promoteAdmin(UUID member) {
    return new DecryptedModifyMemberRole.Builder()
        .aciBytes(UuidUtil.toByteString(member))
        .role(Member.Role.ADMINISTRATOR)
        .build();
  }

  static DecryptedModifyMemberRole demoteAdmin(UUID member) {
    return new DecryptedModifyMemberRole.Builder()
        .aciBytes(UuidUtil.toByteString(member))
        .role(Member.Role.DEFAULT)
        .build();
  }

  static Member encryptedMember(UUID uuid, ProfileKey profileKey) {
    return new Member.Builder()
        .presentation(presentation(uuid, profileKey))
        .build();
  }

  static RequestingMember encryptedRequestingMember(UUID uuid, ProfileKey profileKey) {
    return new RequestingMember.Builder()
        .presentation(presentation(uuid, profileKey))
        .build();
  }

  static DecryptedMember member(UUID uuid) {
    return new DecryptedMember.Builder()
        .aciBytes(UuidUtil.toByteString(uuid))
        .role(Member.Role.DEFAULT)
        .build();
  }

  static DecryptedMember member(UUID uuid, ByteString profileKey, int joinedAtRevision) {
    return new DecryptedMember.Builder()
        .aciBytes(UuidUtil.toByteString(uuid))
        .role(Member.Role.DEFAULT)
        .joinedAtRevision(joinedAtRevision)
        .profileKey(profileKey)
        .build();
  }

  static DecryptedPendingMemberRemoval pendingMemberRemoval(UUID uuid) {
    return new DecryptedPendingMemberRemoval.Builder()
        .serviceIdBytes(UuidUtil.toByteString(uuid))
        .serviceIdCipherText(encrypt(uuid))
        .build();
  }

  static DecryptedPendingMember pendingMember(UUID uuid) {
    return new DecryptedPendingMember.Builder()
        .serviceIdBytes(UuidUtil.toByteString(uuid))
        .serviceIdCipherText(encrypt(uuid))
        .role(Member.Role.DEFAULT)
        .build();
  }

  static DecryptedRequestingMember requestingMember(UUID uuid) {
    return requestingMember(uuid, newProfileKey());
  }

  static DecryptedRequestingMember requestingMember(UUID uuid, ProfileKey profileKey) {
    return new DecryptedRequestingMember.Builder()
        .aciBytes(UuidUtil.toByteString(uuid))
        .profileKey(ByteString.of(profileKey.serialize()))
        .build();
  }

  static DecryptedBannedMember bannedMember(UUID uuid) {
    return new DecryptedBannedMember.Builder()
        .serviceIdBytes(UuidUtil.toByteString(uuid))
        .build();
  }

  static DecryptedApproveMember approveMember(UUID uuid) {
    return approve(uuid, Member.Role.DEFAULT);
  }

  static DecryptedApproveMember approveAdmin(UUID uuid) {
    return approve(uuid, Member.Role.ADMINISTRATOR);
  }

  private static DecryptedApproveMember approve(UUID uuid, Member.Role role) {
    return new DecryptedApproveMember.Builder()
        .aciBytes(UuidUtil.toByteString(uuid))
        .role(role)
        .build();
  }

  static DecryptedMember member(UUID uuid, ProfileKey profileKey) {
    return withProfileKey(member(uuid), profileKey);
  }

  static DecryptedMember pendingPniAciMember(UUID uuid, UUID pni, ProfileKey profileKey) {
    return new DecryptedMember.Builder()
        .aciBytes(UuidUtil.toByteString(uuid))
        .pniBytes(UuidUtil.toByteString(pni))
        .profileKey(ByteString.of(profileKey.serialize()))
        .build();
  }

  static DecryptedMember pendingPniAciMember(ByteString uuid, ByteString pni, ByteString profileKey) {
    return new DecryptedMember.Builder()
        .aciBytes(uuid)
        .pniBytes(pni)
        .profileKey(profileKey)
        .build();
  }

  static DecryptedMember admin(UUID uuid, ProfileKey profileKey) {
    return withProfileKey(admin(uuid), profileKey);
  }

  static DecryptedMember admin(UUID uuid) {
    return new DecryptedMember.Builder()
        .aciBytes(UuidUtil.toByteString(uuid))
        .role(Member.Role.ADMINISTRATOR)
        .build();
  }

  static DecryptedMember withProfileKey(DecryptedMember member, ProfileKey profileKey) {
    return member.newBuilder()
        .profileKey(ByteString.of(profileKey.serialize()))
        .build();
  }

  static DecryptedMember asAdmin(DecryptedMember member) {
    return new DecryptedMember.Builder()
        .aciBytes(member.aciBytes)
        .role(Member.Role.ADMINISTRATOR)
        .build();
  }

  static DecryptedMember asMember(DecryptedMember member) {
    return new DecryptedMember.Builder()
        .aciBytes(member.aciBytes)
        .role(Member.Role.DEFAULT)
        .build();
  }

  public static ProfileKey newProfileKey() {
    try {
      return new ProfileKey(Util.getSecretBytes(32));
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }
  }
}

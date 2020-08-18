package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.RequestingMember;
import org.signal.storageservice.protos.groups.local.DecryptedApproveMember;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.util.Util;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

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
    return ByteString.copyFrom(Arrays.copyOf(uuidBytes, uuidBytes.length + 1));
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

    return ByteString.copyFrom(concat);
  }

  static DecryptedModifyMemberRole promoteAdmin(UUID member) {
    return DecryptedModifyMemberRole.newBuilder()
                                    .setUuid(UuidUtil.toByteString(member))
                                    .setRole(Member.Role.ADMINISTRATOR)
                                    .build();
  }

  static DecryptedModifyMemberRole demoteAdmin(UUID member) {
    return DecryptedModifyMemberRole.newBuilder()
                                    .setUuid(UuidUtil.toByteString(member))
                                    .setRole(Member.Role.DEFAULT)
                                    .build();
  }

  static Member encryptedMember(UUID uuid, ProfileKey profileKey) {
    return Member.newBuilder()
                 .setPresentation(presentation(uuid, profileKey))
                 .build();
  }

  static RequestingMember encryptedRequestingMember(UUID uuid, ProfileKey profileKey) {
    return RequestingMember.newBuilder()
                           .setPresentation(presentation(uuid, profileKey))
                           .build();
  }

  static DecryptedMember member(UUID uuid) {
    return DecryptedMember.newBuilder()
                          .setUuid(UuidUtil.toByteString(uuid))
                          .setRole(Member.Role.DEFAULT)
                          .build();
  }

  static DecryptedMember member(UUID uuid, ByteString profileKey, int joinedAtRevision) {
    return DecryptedMember.newBuilder()
                          .setUuid(UuidUtil.toByteString(uuid))
                          .setRole(Member.Role.DEFAULT)
                          .setJoinedAtRevision(joinedAtRevision)
                          .setProfileKey(profileKey)
                          .build();
  }

  static DecryptedPendingMemberRemoval pendingMemberRemoval(UUID uuid) {
    return DecryptedPendingMemberRemoval.newBuilder()
                                        .setUuid(UuidUtil.toByteString(uuid))
                                        .setUuidCipherText(encrypt(uuid))
                                        .build();
  }

  static DecryptedPendingMember pendingMember(UUID uuid) {
    return DecryptedPendingMember.newBuilder()
                                 .setUuid(UuidUtil.toByteString(uuid))
                                 .setUuidCipherText(encrypt(uuid))
                                 .setRole(Member.Role.DEFAULT)
                                 .build();
  }

  static DecryptedRequestingMember requestingMember(UUID uuid) {
    return requestingMember(uuid, newProfileKey());
  }

  static DecryptedRequestingMember requestingMember(UUID uuid, ProfileKey profileKey) {
    return DecryptedRequestingMember.newBuilder()
                                    .setUuid(UuidUtil.toByteString(uuid))
                                    .setProfileKey(ByteString.copyFrom(profileKey.serialize()))
                                    .build();
  }

  static DecryptedApproveMember approveMember(UUID uuid) {
    return approve(uuid, Member.Role.DEFAULT);
  }

  static DecryptedApproveMember approveAdmin(UUID uuid) {
    return approve(uuid, Member.Role.ADMINISTRATOR);
  }

  private static DecryptedApproveMember approve(UUID uuid, Member.Role role) {
    return DecryptedApproveMember.newBuilder()
                                 .setUuid(UuidUtil.toByteString(uuid))
                                 .setRole(role)
                                 .build();
  }

  static DecryptedMember member(UUID uuid, ProfileKey profileKey) {
    return withProfileKey(member(uuid), profileKey);
  }

  static DecryptedMember admin(UUID uuid, ProfileKey profileKey) {
    return withProfileKey(admin(uuid), profileKey);
  }

  static DecryptedMember admin(UUID uuid) {
    return DecryptedMember.newBuilder()
                          .setUuid(UuidUtil.toByteString(uuid))
                          .setRole(Member.Role.ADMINISTRATOR)
                          .build();
  }

  static DecryptedMember withProfileKey(DecryptedMember member, ProfileKey profileKey) {
    return DecryptedMember.newBuilder(member)
                          .setProfileKey(ByteString.copyFrom(profileKey.serialize()))
                          .build();
  }

  static DecryptedMember asAdmin(DecryptedMember member) {
    return DecryptedMember.newBuilder()
                          .setUuid(member.getUuid())
                          .setRole(Member.Role.ADMINISTRATOR)
                          .build();
  }

  static DecryptedMember asMember(DecryptedMember member) {
    return DecryptedMember.newBuilder()
                          .setUuid(member.getUuid())
                          .setRole(Member.Role.DEFAULT)
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

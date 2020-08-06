package org.thoughtcrime.securesms.groups.v2;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.storageservice.protos.groups.local.DecryptedString;
import org.signal.storageservice.protos.groups.local.DecryptedTimer;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.UUID;

public final class ChangeBuilder {

  private final DecryptedGroupChange.Builder builder;

  public static ChangeBuilder changeBy(@NonNull UUID editor) {
    return new ChangeBuilder(editor);
  }

  public static ChangeBuilder changeByUnknown() {
    return new ChangeBuilder();
  }

  ChangeBuilder(@NonNull UUID editor) {
    builder = DecryptedGroupChange.newBuilder()
                                  .setEditor(UuidUtil.toByteString(editor));
  }

  ChangeBuilder() {
    builder = DecryptedGroupChange.newBuilder();
  }

  public ChangeBuilder addMember(@NonNull UUID newMember) {
    builder.addNewMembers(DecryptedMember.newBuilder()
                                         .setUuid(UuidUtil.toByteString(newMember)));
    return this;
  }

  public ChangeBuilder addMember(@NonNull UUID newMember, @NonNull ProfileKey profileKey) {
    builder.addNewMembers(DecryptedMember.newBuilder()
                                         .setUuid(UuidUtil.toByteString(newMember))
                                         .setProfileKey(ByteString.copyFrom(profileKey.serialize())));
    return this;
  }

  public ChangeBuilder deleteMember(@NonNull UUID removedMember) {
    builder.addDeleteMembers(UuidUtil.toByteString(removedMember));
    return this;
  }

  public ChangeBuilder promoteToAdmin(@NonNull UUID member) {
    builder.addModifyMemberRoles(DecryptedModifyMemberRole.newBuilder()
                                                          .setRole(Member.Role.ADMINISTRATOR)
                                                          .setUuid(UuidUtil.toByteString(member)));
    return this;
  }

  public ChangeBuilder demoteToMember(@NonNull UUID member) {
    builder.addModifyMemberRoles(DecryptedModifyMemberRole.newBuilder()
                                                          .setRole(Member.Role.DEFAULT)
                                                          .setUuid(UuidUtil.toByteString(member)));
    return this;
  }

  public ChangeBuilder invite(@NonNull UUID potentialMember) {
    return inviteBy(potentialMember, UuidUtil.UNKNOWN_UUID);
  }

  public ChangeBuilder inviteBy(@NonNull UUID potentialMember, @NonNull UUID inviter) {
    builder.addNewPendingMembers(DecryptedPendingMember.newBuilder()
                                                       .setUuid(UuidUtil.toByteString(potentialMember))
                                                       .setAddedByUuid(UuidUtil.toByteString(inviter)));
    return this;
  }

  public ChangeBuilder uninvite(@NonNull UUID pendingMember) {
    builder.addDeletePendingMembers(DecryptedPendingMemberRemoval.newBuilder()
                                                                 .setUuid(UuidUtil.toByteString(pendingMember)));
    return this;
  }

  public ChangeBuilder promote(@NonNull UUID pendingMember) {
    builder.addPromotePendingMembers(DecryptedMember.newBuilder().setUuid(UuidUtil.toByteString(pendingMember)));
    return this;
  }

  public ChangeBuilder profileKeyUpdate(@NonNull UUID member, @NonNull ProfileKey profileKey) {
    return profileKeyUpdate(member, profileKey.serialize());
  }

  public ChangeBuilder profileKeyUpdate(@NonNull UUID member, @NonNull byte[] profileKey) {
    builder.addModifiedProfileKeys(DecryptedMember.newBuilder()
                                                  .setUuid(UuidUtil.toByteString(member))
                                                  .setProfileKey(ByteString.copyFrom(profileKey)));
    return this;
  }

  public ChangeBuilder promote(@NonNull UUID pendingMember, @NonNull ProfileKey profileKey) {
    builder.addPromotePendingMembers(DecryptedMember.newBuilder()
                                                    .setUuid(UuidUtil.toByteString(pendingMember))
                                                    .setProfileKey(ByteString.copyFrom(profileKey.serialize())));
    return this;
  }

  public ChangeBuilder title(@NonNull String newTitle) {
    builder.setNewTitle(DecryptedString.newBuilder()
                                       .setValue(newTitle));
    return this;
  }

  public ChangeBuilder avatar(@NonNull String newAvatar) {
    builder.setNewAvatar(DecryptedString.newBuilder()
                                        .setValue(newAvatar));
    return this;
  }

  public ChangeBuilder timer(int duration) {
    builder.setNewTimer(DecryptedTimer.newBuilder()
                                      .setDuration(duration));
    return this;
  }

  public ChangeBuilder attributeAccess(@NonNull AccessControl.AccessRequired accessRequired) {
    builder.setNewAttributeAccess(accessRequired);
    return this;
  }

  public ChangeBuilder membershipAccess(@NonNull AccessControl.AccessRequired accessRequired) {
    builder.setNewMemberAccess(accessRequired);
    return this;
  }

  public DecryptedGroupChange build() {
    return builder.build();
  }
}

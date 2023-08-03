package org.thoughtcrime.securesms.groups.v2;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;

import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedApproveMember;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;
import org.signal.storageservice.protos.groups.local.DecryptedString;
import org.signal.storageservice.protos.groups.local.DecryptedTimer;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;

public final class ChangeBuilder {

            private final DecryptedGroupChange.Builder builder;
  @Nullable private final ACI                          editor;

  public static ChangeBuilder changeBy(@NonNull ACI editor) {
    return new ChangeBuilder(editor);
  }

  public static ChangeBuilder changeByUnknown() {
    return new ChangeBuilder();
  }

  ChangeBuilder(@NonNull ACI editor) {
    this.editor  = editor;
    this.builder = DecryptedGroupChange.newBuilder()
                                       .setEditorServiceIdBytes(editor.toByteString());
  }

  ChangeBuilder() {
    this.editor  = null;
    this.builder = DecryptedGroupChange.newBuilder();
  }

  public ChangeBuilder addMember(@NonNull ACI newMember) {
    builder.addNewMembers(DecryptedMember.newBuilder()
                                         .setAciBytes(newMember.toByteString()));
    return this;
  }

  public ChangeBuilder addMember(@NonNull ACI newMember, @NonNull ProfileKey profileKey) {
    builder.addNewMembers(DecryptedMember.newBuilder()
                                         .setAciBytes(newMember.toByteString())
                                         .setProfileKey(ByteString.copyFrom(profileKey.serialize())));
    return this;
  }

  public ChangeBuilder deleteMember(@NonNull ACI removedMember) {
    builder.addDeleteMembers(removedMember.toByteString());
    return this;
  }

  public ChangeBuilder promoteToAdmin(@NonNull ACI member) {
    builder.addModifyMemberRoles(DecryptedModifyMemberRole.newBuilder()
                                                          .setRole(Member.Role.ADMINISTRATOR)
                                                          .setAciBytes(member.toByteString()));
    return this;
  }

  public ChangeBuilder demoteToMember(@NonNull ACI member) {
    builder.addModifyMemberRoles(DecryptedModifyMemberRole.newBuilder()
                                                          .setRole(Member.Role.DEFAULT)
                                                          .setAciBytes(member.toByteString()));
    return this;
  }

  public ChangeBuilder invite(@NonNull ACI potentialMember) {
    return inviteBy(potentialMember, ACI.UNKNOWN);
  }

  public ChangeBuilder inviteBy(@NonNull ACI potentialMember, @NonNull ACI inviter) {
    builder.addNewPendingMembers(DecryptedPendingMember.newBuilder()
                                                       .setServiceIdBytes(potentialMember.toByteString())
                                                       .setAddedByAci(inviter.toByteString()));
    return this;
  }

  public ChangeBuilder uninvite(@NonNull ACI pendingMember) {
    builder.addDeletePendingMembers(DecryptedPendingMemberRemoval.newBuilder()
                                                                 .setServiceIdBytes(pendingMember.toByteString()));
    return this;
  }

  public ChangeBuilder promote(@NonNull ACI pendingMember) {
    builder.addPromotePendingMembers(DecryptedMember.newBuilder().setAciBytes(pendingMember.toByteString()));
    return this;
  }

  public ChangeBuilder profileKeyUpdate(@NonNull ACI member, @NonNull ProfileKey profileKey) {
    return profileKeyUpdate(member, profileKey.serialize());
  }

  public ChangeBuilder profileKeyUpdate(@NonNull ACI member, @NonNull byte[] profileKey) {
    builder.addModifiedProfileKeys(DecryptedMember.newBuilder()
                                                  .setAciBytes(member.toByteString())
                                                  .setProfileKey(ByteString.copyFrom(profileKey)));
    return this;
  }

  public ChangeBuilder promote(@NonNull ACI pendingMember, @NonNull ProfileKey profileKey) {
    builder.addPromotePendingMembers(DecryptedMember.newBuilder()
                                                    .setAciBytes(pendingMember.toByteString())
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

  public ChangeBuilder inviteLinkAccess(@NonNull AccessControl.AccessRequired accessRequired) {
    builder.setNewInviteLinkAccess(accessRequired);
    return this;
  }

  public ChangeBuilder resetGroupLink() {
    builder.setNewInviteLinkPassword(ByteString.copyFrom(GroupLinkPassword.createNew().serialize()));
    return this;
  }

  public ChangeBuilder requestJoin() {
    if (editor == null) throw new AssertionError();
    return requestJoin(editor, newProfileKey());
  }

  public ChangeBuilder requestJoin(@NonNull ACI requester) {
    return requestJoin(requester, newProfileKey());
  }

  public ChangeBuilder requestJoin(@NonNull ProfileKey profileKey) {
    if (editor == null) throw new AssertionError();
    return requestJoin(editor, profileKey);
  }

  public ChangeBuilder requestJoin(@NonNull ACI requester, @NonNull ProfileKey profileKey) {
    builder.addNewRequestingMembers(DecryptedRequestingMember.newBuilder()
                                                             .setAciBytes(requester.toByteString())
                                                             .setProfileKey(ByteString.copyFrom(profileKey.serialize())));
    return this;
  }

  public ChangeBuilder approveRequest(@NonNull ACI approvedMember) {
    builder.addPromoteRequestingMembers(DecryptedApproveMember.newBuilder()
                                                              .setRole(Member.Role.DEFAULT)
                                                              .setAciBytes(approvedMember.toByteString()));
    return this;
  }

  public ChangeBuilder denyRequest(@NonNull ACI approvedMember) {
    builder.addDeleteRequestingMembers(approvedMember.toByteString());
    return this;
  }

  public DecryptedGroupChange build() {
    return builder.build();
  }

  private static ProfileKey newProfileKey() {
    try {
      return new ProfileKey(Util.getSecretBytes(32));
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }
  }
}

package org.thoughtcrime.securesms.groups.v2;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

import kotlin.collections.CollectionsKt;
import okio.ByteString;

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
    this.builder = new DecryptedGroupChange.Builder().editorServiceIdBytes(editor.toByteString());
  }

  ChangeBuilder() {
    this.editor  = null;
    this.builder = new DecryptedGroupChange.Builder();
  }

  public ChangeBuilder addMember(@NonNull ACI newMember) {
    builder.newMembers(CollectionsKt.plus(builder.newMembers, new DecryptedMember.Builder().aciBytes(newMember.toByteString()).build()));
    return this;
  }

  public ChangeBuilder addMember(@NonNull ACI newMember, @NonNull ProfileKey profileKey) {
    builder.newMembers(CollectionsKt.plus(builder.newMembers, new DecryptedMember.Builder().aciBytes(newMember.toByteString()).profileKey(ByteString.of(profileKey.serialize())).build()));
    return this;
  }

  public ChangeBuilder deleteMember(@NonNull ACI removedMember) {
    builder.deleteMembers(CollectionsKt.plus(builder.deleteMembers, removedMember.toByteString()));
    return this;
  }

  public ChangeBuilder promoteToAdmin(@NonNull ACI member) {
    builder.modifyMemberRoles(CollectionsKt.plus(builder.modifyMemberRoles, new DecryptedModifyMemberRole.Builder().role(Member.Role.ADMINISTRATOR).aciBytes(member.toByteString()).build()));
    return this;
  }

  public ChangeBuilder demoteToMember(@NonNull ACI member) {
    builder.modifyMemberRoles(CollectionsKt.plus(builder.modifyMemberRoles, new DecryptedModifyMemberRole.Builder().role(Member.Role.DEFAULT).aciBytes(member.toByteString()).build()));
    return this;
  }

  public ChangeBuilder invite(@NonNull ACI potentialMember) {
    return inviteBy(potentialMember, ACI.UNKNOWN);
  }

  public ChangeBuilder inviteBy(@NonNull ACI potentialMember, @NonNull ACI inviter) {
    builder.newPendingMembers(CollectionsKt.plus(builder.newPendingMembers, new DecryptedPendingMember.Builder().serviceIdBytes(potentialMember.toByteString()).addedByAci(inviter.toByteString()).build()));
    return this;
  }

  public ChangeBuilder uninvite(@NonNull ACI pendingMember) {
    builder.deletePendingMembers(CollectionsKt.plus(builder.deletePendingMembers, new DecryptedPendingMemberRemoval.Builder().serviceIdBytes(pendingMember.toByteString()).build()));
    return this;
  }

  public ChangeBuilder promote(@NonNull ACI pendingMember) {
    builder.promotePendingMembers(CollectionsKt.plus(builder.promotePendingMembers, new DecryptedMember.Builder().aciBytes(pendingMember.toByteString()).build()));
    return this;
  }

  public ChangeBuilder profileKeyUpdate(@NonNull ACI member, @NonNull ProfileKey profileKey) {
    return profileKeyUpdate(member, profileKey.serialize());
  }

  public ChangeBuilder profileKeyUpdate(@NonNull ACI member, @NonNull byte[] profileKey) {
    builder.modifiedProfileKeys(CollectionsKt.plus(builder.modifiedProfileKeys, new DecryptedMember.Builder().aciBytes(member.toByteString()).profileKey(ByteString.of(profileKey)).build()));
    return this;
  }

  public ChangeBuilder promote(@NonNull ACI pendingMember, @NonNull ProfileKey profileKey) {
    builder.promotePendingMembers(CollectionsKt.plus(builder.promotePendingMembers, new DecryptedMember.Builder().aciBytes(pendingMember.toByteString()).profileKey(ByteString.of(profileKey.serialize())).build()));
    return this;
  }

  public ChangeBuilder title(@NonNull String newTitle) {
    builder.newTitle(new DecryptedString.Builder().value_(newTitle).build());
    return this;
  }

  public ChangeBuilder avatar(@NonNull String newAvatar) {
    builder.newAvatar(new DecryptedString.Builder().value_(newAvatar).build());
    return this;
  }

  public ChangeBuilder timer(int duration) {
    builder.newTimer(new DecryptedTimer.Builder().duration(duration).build());
    return this;
  }

  public ChangeBuilder attributeAccess(@NonNull AccessControl.AccessRequired accessRequired) {
    builder.newAttributeAccess(accessRequired);
    return this;
  }

  public ChangeBuilder membershipAccess(@NonNull AccessControl.AccessRequired accessRequired) {
    builder.newMemberAccess(accessRequired);
    return this;
  }

  public ChangeBuilder inviteLinkAccess(@NonNull AccessControl.AccessRequired accessRequired) {
    builder.newInviteLinkAccess(accessRequired);
    return this;
  }

  public ChangeBuilder resetGroupLink() {
    builder.newInviteLinkPassword(ByteString.of(GroupLinkPassword.createNew().serialize()));
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
    builder.newRequestingMembers(CollectionsKt.plus(builder.newRequestingMembers, new DecryptedRequestingMember.Builder().aciBytes(requester.toByteString()).profileKey(ByteString.of(profileKey.serialize())).build()));
    return this;
  }

  public ChangeBuilder approveRequest(@NonNull ACI approvedMember) {
    builder.promoteRequestingMembers(CollectionsKt.plus(builder.promoteRequestingMembers, new DecryptedApproveMember.Builder().role(Member.Role.DEFAULT).aciBytes(approvedMember.toByteString()).build()));
    return this;
  }

  public ChangeBuilder denyRequest(@NonNull ACI approvedMember) {
    builder.deleteRequestingMembers(CollectionsKt.plus(builder.deleteRequestingMembers, approvedMember.toByteString()));
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

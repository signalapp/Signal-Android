package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;

import java.util.ArrayList;
import java.util.List;

final class DecryptedGroupChangeActionsBuilderChangeSetModifier implements ChangeSetModifier {
  private final DecryptedGroupChange.Builder result;

  public DecryptedGroupChangeActionsBuilderChangeSetModifier(DecryptedGroupChange.Builder result) {
    this.result = result;
  }

  @Override
  public void removeAddMembers(int i) {
    result.removeNewMembers(i);
  }

  @Override
  public void moveAddToPromote(int i) {
    DecryptedMember addMemberAction = result.getNewMembersList().get(i);
    result.removeNewMembers(i);
    result.addPromotePendingMembers(addMemberAction);
  }

  @Override
  public void removeDeleteMembers(int i) {
    List<ByteString> newList = removeIndexFromByteStringList(result.getDeleteMembersList(), i);

    result.clearDeleteMembers()
          .addAllDeleteMembers(newList);
  }

  @Override
  public void removeModifyMemberRoles(int i) {
    result.removeModifyMemberRoles(i);
  }

  @Override
  public void removeModifyMemberProfileKeys(int i) {
    result.removeModifiedProfileKeys(i);
  }

  @Override
  public void removeAddPendingMembers(int i) {
    result.removeNewPendingMembers(i);
  }

  @Override
  public void removeDeletePendingMembers(int i) {
    result.removeDeletePendingMembers(i);
  }

  @Override
  public void removePromotePendingMembers(int i) {
    result.removePromotePendingMembers(i);
  }

  @Override
  public void clearModifyTitle() {
    result.clearNewTitle();
  }

  @Override
  public void clearModifyAvatar() {
    result.clearNewAvatar();
  }

  @Override
  public void clearModifyDisappearingMessagesTimer() {
    result.clearNewTimer();
  }

  @Override
  public void clearModifyAttributesAccess() {
    result.clearNewAttributeAccess();
  }

  @Override
  public void clearModifyMemberAccess() {
    result.clearNewMemberAccess();
  }

  @Override
  public void clearModifyAddFromInviteLinkAccess() {
    result.clearNewInviteLinkAccess();
  }

  @Override
  public void removeAddRequestingMembers(int i) {
    result.removeNewRequestingMembers(i);
  }

  @Override
  public void moveAddRequestingMembersToPromote(int i) {
    DecryptedRequestingMember addMemberAction = result.getNewRequestingMembersList().get(i);
    result.removeNewRequestingMembers(i);

    DecryptedMember build = DecryptedMember.newBuilder()
                                           .setUuid(addMemberAction.getUuid())
                                           .setProfileKey(addMemberAction.getProfileKey())
                                           .setRole(Member.Role.DEFAULT).build();

    result.addPromotePendingMembers(0, build);
  }

  @Override
  public void removeDeleteRequestingMembers(int i) {
    List<ByteString> newList = removeIndexFromByteStringList(result.getDeleteRequestingMembersList(), i);

    result.clearDeleteRequestingMembers()
          .addAllDeleteRequestingMembers(newList);
  }

  @Override
  public void removePromoteRequestingMembers(int i) {
    result.removePromoteRequestingMembers(i);
  }

  private static List<ByteString> removeIndexFromByteStringList(List<ByteString> byteStrings, int i) {
    List<ByteString> modifiedList = new ArrayList<>(byteStrings);

    modifiedList.remove(i);

    return modifiedList;
  }
}

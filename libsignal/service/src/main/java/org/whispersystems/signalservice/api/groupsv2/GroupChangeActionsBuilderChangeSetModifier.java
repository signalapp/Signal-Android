package org.whispersystems.signalservice.api.groupsv2;

import org.signal.storageservice.protos.groups.GroupChange;

final class GroupChangeActionsBuilderChangeSetModifier implements ChangeSetModifier {
  private final GroupChange.Actions.Builder result;

  public GroupChangeActionsBuilderChangeSetModifier(GroupChange.Actions.Builder result) {
    this.result = result;
  }

  @Override
  public void removeAddMembers(int i) {
    result.removeAddMembers(i);
  }

  @Override
  public void moveAddToPromote(int i) {
    GroupChange.Actions.AddMemberAction addMemberAction = result.getAddMembersList().get(i);
    result.removeAddMembers(i);
    result.addPromotePendingMembers(GroupChange.Actions.PromotePendingMemberAction.newBuilder().setPresentation(addMemberAction.getAdded().getPresentation()));
  }

  @Override
  public void removeDeleteMembers(int i) {
    result.removeDeleteMembers(i);
  }

  @Override
  public void removeModifyMemberRoles(int i) {
    result.removeModifyMemberRoles(i);
  }

  @Override
  public void removeModifyMemberProfileKeys(int i) {
    result.removeModifyMemberProfileKeys(i);
  }

  @Override
  public void removeAddPendingMembers(int i) {
    result.removeAddPendingMembers(i);
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
    result.clearModifyTitle();
  }

  @Override
  public void clearModifyAvatar() {
    result.clearModifyAvatar();
  }

  @Override
  public void clearModifyDisappearingMessagesTimer() {
    result.clearModifyDisappearingMessagesTimer();
  }

  @Override
  public void clearModifyAttributesAccess() {
    result.clearModifyAttributesAccess();
  }

  @Override
  public void clearModifyMemberAccess() {
    result.clearModifyMemberAccess();
  }

  @Override
  public void clearModifyAddFromInviteLinkAccess() {
    result.clearModifyAddFromInviteLinkAccess();
  }

  @Override
  public void removeAddRequestingMembers(int i) {
    result.removeAddRequestingMembers(i);
  }

  @Override
  public void moveAddRequestingMembersToPromote(int i) {
    GroupChange.Actions.AddRequestingMemberAction addMemberAction = result.getAddRequestingMembersList().get(i);
    result.removeAddRequestingMembers(i);
    result.addPromotePendingMembers(0, GroupChange.Actions.PromotePendingMemberAction.newBuilder().setPresentation(addMemberAction.getAdded().getPresentation()));
  }

  @Override
  public void removeDeleteRequestingMembers(int i) {
    result.removeDeleteRequestingMembers(i);
  }

  @Override
  public void removePromoteRequestingMembers(int i) {
    result.removePromoteRequestingMembers(i);
  }
}

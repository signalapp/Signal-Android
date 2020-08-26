package org.whispersystems.signalservice.api.groupsv2;

public interface ChangeSetModifier {
  void removeAddMembers(int i);

  void moveAddToPromote(int i);

  void removeDeleteMembers(int i);

  void removeModifyMemberRoles(int i);

  void removeModifyMemberProfileKeys(int i);

  void removeAddPendingMembers(int i);

  void removeDeletePendingMembers(int i);

  void removePromotePendingMembers(int i);

  void clearModifyTitle();

  void clearModifyAvatar();

  void clearModifyDisappearingMessagesTimer();

  void clearModifyAttributesAccess();

  void clearModifyMemberAccess();

  void clearModifyAddFromInviteLinkAccess();

  void removeAddRequestingMembers(int i);

  void moveAddRequestingMembersToPromote(int i);

  void removeDeleteRequestingMembers(int i);

  void removePromoteRequestingMembers(int i);
}

package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedApproveMember;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;

import java.util.HashMap;
import java.util.List;

public final class GroupChangeUtil {

  private GroupChangeUtil() {
  }

  /**
   * True iff there are no change actions.
   */
  public static boolean changeIsEmpty(GroupChange.Actions change) {
    return change.getAddMembersCount()               == 0 && // field 3
           change.getDeleteMembersCount()            == 0 && // field 4
           change.getModifyMemberRolesCount()        == 0 && // field 5
           change.getModifyMemberProfileKeysCount()  == 0 && // field 6
           change.getAddPendingMembersCount()        == 0 && // field 7
           change.getDeletePendingMembersCount()     == 0 && // field 8
           change.getPromotePendingMembersCount()    == 0 && // field 9
           !change.hasModifyTitle()                       && // field 10
           !change.hasModifyAvatar()                      && // field 11
           !change.hasModifyDisappearingMessagesTimer()   && // field 12
           !change.hasModifyAttributesAccess()            && // field 13
           !change.hasModifyMemberAccess()                && // field 14
           !change.hasModifyAddFromInviteLinkAccess()     && // field 15
           change.getAddRequestingMembersCount()     == 0 && // field 16
           change.getDeleteRequestingMembersCount()  == 0 && // field 17
           change.getPromoteRequestingMembersCount() == 0 && // field 18
           !change.hasModifyInviteLinkPassword();            // field 19
  }

  /**
   * Given the latest group state and a conflicting change, decides which changes to carry forward
   * and returns a new group change which could be empty.
   * <p>
   * Titles, avatars, and other settings are carried forward if they are different. Last writer wins.
   * <p>
   * Membership additions and removals also respect last writer wins and are removed if they have
   * already been applied. e.g. you add someone but they are already added.
   * <p>
   * Membership additions will be altered to {@link GroupChange.Actions.PromotePendingMemberAction}
   * if someone has invited them since.
   *
   * @param groupState        Latest group state in plaintext.
   * @param conflictingChange The potentially conflicting change in plaintext.
   * @param encryptedChange   Encrypted version of the {@param conflictingChange}.
   * @return A new change builder.
   */
  public static GroupChange.Actions.Builder resolveConflict(DecryptedGroup groupState,
                                                            DecryptedGroupChange conflictingChange,
                                                            GroupChange.Actions encryptedChange)
  {
    GroupChange.Actions.Builder result = GroupChange.Actions.newBuilder(encryptedChange);

    resolveConflict(groupState, conflictingChange, new GroupChangeActionsBuilderChangeSetModifier(result));

    return result;
  }

  /**
   * Given the latest group state and a conflicting change, decides which changes to carry forward
   * and returns a new group change which could be empty.
   * <p>
   * Titles, avatars, and other settings are carried forward if they are different. Last writer wins.
   * <p>
   * Membership additions and removals also respect last writer wins and are removed if they have
   * already been applied. e.g. you add someone but they are already added.
   * <p>
   * Membership additions will be altered to {@link DecryptedGroupChange} promotes if someone has
   * invited them since.
   *
   * @param groupState        Latest group state in plaintext.
   * @param conflictingChange The potentially conflicting change in plaintext.
   * @return A new change builder.
   */
  public static DecryptedGroupChange.Builder resolveConflict(DecryptedGroup groupState,
                                                             DecryptedGroupChange conflictingChange)
  {
    DecryptedGroupChange.Builder result = DecryptedGroupChange.newBuilder(conflictingChange);

    resolveConflict(groupState, conflictingChange, new DecryptedGroupChangeActionsBuilderChangeSetModifier(result));

    return result;
  }

  private static void resolveConflict(DecryptedGroup groupState,
                                      DecryptedGroupChange conflictingChange,
                                      ChangeSetModifier changeSetModifier)
  {
    HashMap<ByteString, DecryptedMember>           fullMembersByUuid       = new HashMap<>(groupState.getMembersCount());
    HashMap<ByteString, DecryptedPendingMember>    pendingMembersByUuid    = new HashMap<>(groupState.getPendingMembersCount());
    HashMap<ByteString, DecryptedRequestingMember> requestingMembersByUuid = new HashMap<>(groupState.getMembersCount());

    for (DecryptedMember member : groupState.getMembersList()) {
      fullMembersByUuid.put(member.getUuid(), member);
    }

    for (DecryptedPendingMember member : groupState.getPendingMembersList()) {
      pendingMembersByUuid.put(member.getUuid(), member);
    }

    for (DecryptedRequestingMember member : groupState.getRequestingMembersList()) {
      requestingMembersByUuid.put(member.getUuid(), member);
    }

    resolveField3AddMembers                      (conflictingChange, changeSetModifier, fullMembersByUuid, pendingMembersByUuid);
    resolveField4DeleteMembers                   (conflictingChange, changeSetModifier, fullMembersByUuid);
    resolveField5ModifyMemberRoles               (conflictingChange, changeSetModifier, fullMembersByUuid);
    resolveField6ModifyProfileKeys               (conflictingChange, changeSetModifier, fullMembersByUuid);
    resolveField7AddPendingMembers               (conflictingChange, changeSetModifier, fullMembersByUuid, pendingMembersByUuid);
    resolveField8DeletePendingMembers            (conflictingChange, changeSetModifier, pendingMembersByUuid);
    resolveField9PromotePendingMembers           (conflictingChange, changeSetModifier, pendingMembersByUuid);
    resolveField10ModifyTitle                    (groupState, conflictingChange, changeSetModifier);
    resolveField11ModifyAvatar                   (groupState, conflictingChange, changeSetModifier);
    resolveField12modifyDisappearingMessagesTimer(groupState, conflictingChange, changeSetModifier);
    resolveField13modifyAttributesAccess         (groupState, conflictingChange, changeSetModifier);
    resolveField14modifyAttributesAccess         (groupState, conflictingChange, changeSetModifier);
    resolveField15modifyAddFromInviteLinkAccess  (groupState, conflictingChange, changeSetModifier);
    resolveField16AddRequestingMembers           (conflictingChange, changeSetModifier, fullMembersByUuid, pendingMembersByUuid);
    resolveField17DeleteMembers                  (conflictingChange, changeSetModifier, requestingMembersByUuid);
    resolveField18PromoteRequestingMembers       (conflictingChange, changeSetModifier, requestingMembersByUuid);
  }

  private static void resolveField3AddMembers(DecryptedGroupChange conflictingChange, ChangeSetModifier result, HashMap<ByteString, DecryptedMember> fullMembersByUuid, HashMap<ByteString, DecryptedPendingMember> pendingMembersByUuid) {
    List<DecryptedMember> newMembersList = conflictingChange.getNewMembersList();

    for (int i = newMembersList.size() - 1; i >= 0; i--) {
      DecryptedMember member = newMembersList.get(i);

      if (fullMembersByUuid.containsKey(member.getUuid())) {
        result.removeAddMembers(i);
      } else if (pendingMembersByUuid.containsKey(member.getUuid())) {
        result.moveAddToPromote(i);
      }
    }
  }

  private static void resolveField4DeleteMembers(DecryptedGroupChange conflictingChange, ChangeSetModifier result, HashMap<ByteString, DecryptedMember> fullMembersByUuid) {
    List<ByteString> deletedMembersList = conflictingChange.getDeleteMembersList();

    for (int i = deletedMembersList.size() - 1; i >= 0; i--) {
      ByteString member = deletedMembersList.get(i);

      if (!fullMembersByUuid.containsKey(member)) {
        result.removeDeleteMembers(i);
      }
    }
  }

  private static void resolveField5ModifyMemberRoles(DecryptedGroupChange conflictingChange, ChangeSetModifier result, HashMap<ByteString, DecryptedMember> fullMembersByUuid) {
    List<DecryptedModifyMemberRole> modifyRolesList = conflictingChange.getModifyMemberRolesList();

    for (int i = modifyRolesList.size() - 1; i >= 0; i--) {
      DecryptedModifyMemberRole modifyRoleAction = modifyRolesList.get(i);
      DecryptedMember           memberInGroup    = fullMembersByUuid.get(modifyRoleAction.getUuid());

      if (memberInGroup == null || memberInGroup.getRole() == modifyRoleAction.getRole()) {
        result.removeModifyMemberRoles(i);
      }
    }
  }

  private static void resolveField6ModifyProfileKeys(DecryptedGroupChange conflictingChange, ChangeSetModifier result, HashMap<ByteString, DecryptedMember> fullMembersByUuid) {
    List<DecryptedMember> modifyProfileKeysList = conflictingChange.getModifiedProfileKeysList();

    for (int i = modifyProfileKeysList.size() - 1; i >= 0; i--) {
      DecryptedMember member        = modifyProfileKeysList.get(i);
      DecryptedMember memberInGroup = fullMembersByUuid.get(member.getUuid());

      if (memberInGroup == null || member.getProfileKey().equals(memberInGroup.getProfileKey())) {
        result.removeModifyMemberProfileKeys(i);
      }
    }
  }

  private static void resolveField7AddPendingMembers(DecryptedGroupChange conflictingChange, ChangeSetModifier result, HashMap<ByteString, DecryptedMember> fullMembersByUuid, HashMap<ByteString, DecryptedPendingMember> pendingMembersByUuid) {
    List<DecryptedPendingMember> newPendingMembersList = conflictingChange.getNewPendingMembersList();

    for (int i = newPendingMembersList.size() - 1; i >= 0; i--) {
      DecryptedPendingMember member = newPendingMembersList.get(i);

      if (fullMembersByUuid.containsKey(member.getUuid()) || pendingMembersByUuid.containsKey(member.getUuid())) {
        result.removeAddPendingMembers(i);
      }
    }
  }

  private static void resolveField8DeletePendingMembers(DecryptedGroupChange conflictingChange, ChangeSetModifier result, HashMap<ByteString, DecryptedPendingMember> pendingMembersByUuid) {
    List<DecryptedPendingMemberRemoval> deletePendingMembersList = conflictingChange.getDeletePendingMembersList();

    for (int i = deletePendingMembersList.size() - 1; i >= 0; i--) {
      DecryptedPendingMemberRemoval member = deletePendingMembersList.get(i);

      if (!pendingMembersByUuid.containsKey(member.getUuid())) {
        result.removeDeletePendingMembers(i);
      }
    }
  }

  private static void resolveField9PromotePendingMembers(DecryptedGroupChange conflictingChange, ChangeSetModifier result, HashMap<ByteString, DecryptedPendingMember> pendingMembersByUuid) {
    List<DecryptedMember> promotePendingMembersList = conflictingChange.getPromotePendingMembersList();

    for (int i = promotePendingMembersList.size() - 1; i >= 0; i--) {
      DecryptedMember member = promotePendingMembersList.get(i);
      
      if (!pendingMembersByUuid.containsKey(member.getUuid())) {
        result.removePromotePendingMembers(i);
      }
    }
  }

  private static void resolveField10ModifyTitle(DecryptedGroup groupState, DecryptedGroupChange conflictingChange, ChangeSetModifier result) {
    if (conflictingChange.hasNewTitle() && conflictingChange.getNewTitle().getValue().equals(groupState.getTitle())) {
      result.clearModifyTitle();
    }
  }

  private static void resolveField11ModifyAvatar(DecryptedGroup groupState, DecryptedGroupChange conflictingChange, ChangeSetModifier result) {
    if (conflictingChange.hasNewAvatar() && conflictingChange.getNewAvatar().getValue().equals(groupState.getAvatar())) {
      result.clearModifyAvatar();
    }
  }

  private static void resolveField12modifyDisappearingMessagesTimer(DecryptedGroup groupState, DecryptedGroupChange conflictingChange, ChangeSetModifier result) {
    if (conflictingChange.hasNewTimer() && conflictingChange.getNewTimer().getDuration() == groupState.getDisappearingMessagesTimer().getDuration()) {
      result.clearModifyDisappearingMessagesTimer();
    }
  }

  private static void resolveField13modifyAttributesAccess(DecryptedGroup groupState, DecryptedGroupChange conflictingChange, ChangeSetModifier result) {
    if (conflictingChange.getNewAttributeAccess() == groupState.getAccessControl().getAttributes()) {
      result.clearModifyAttributesAccess();
    }
  }

  private static void resolveField14modifyAttributesAccess(DecryptedGroup groupState, DecryptedGroupChange conflictingChange, ChangeSetModifier result) {
    if (conflictingChange.getNewMemberAccess() == groupState.getAccessControl().getMembers()) {
      result.clearModifyMemberAccess();
    }
  }

  private static void resolveField15modifyAddFromInviteLinkAccess(DecryptedGroup groupState, DecryptedGroupChange conflictingChange, ChangeSetModifier result) {
    if (conflictingChange.getNewInviteLinkAccess() == groupState.getAccessControl().getAddFromInviteLink()) {
      result.clearModifyAddFromInviteLinkAccess();
    }
  }

  private static void resolveField16AddRequestingMembers(DecryptedGroupChange conflictingChange, ChangeSetModifier result, HashMap<ByteString, DecryptedMember> fullMembersByUuid, HashMap<ByteString, DecryptedPendingMember> pendingMembersByUuid) {
    List<DecryptedRequestingMember> newMembersList = conflictingChange.getNewRequestingMembersList();

    for (int i = newMembersList.size() - 1; i >= 0; i--) {
      DecryptedRequestingMember member = newMembersList.get(i);

      if (fullMembersByUuid.containsKey(member.getUuid())) {
        result.removeAddRequestingMembers(i);
      } else if (pendingMembersByUuid.containsKey(member.getUuid())) {
        result.moveAddRequestingMembersToPromote(i);        
      }
    }
  }

  private static void resolveField17DeleteMembers(DecryptedGroupChange conflictingChange,
                                                  ChangeSetModifier result,
                                                  HashMap<ByteString, DecryptedRequestingMember> requestingMembers)
  {
    List<ByteString> deletedMembersList = conflictingChange.getDeleteRequestingMembersList();

    for (int i = deletedMembersList.size() - 1; i >= 0; i--) {
      ByteString member = deletedMembersList.get(i);

      if (!requestingMembers.containsKey(member)) {
        result.removeDeleteRequestingMembers(i);
      }
    }
  }

  private static void resolveField18PromoteRequestingMembers(DecryptedGroupChange conflictingChange,
                                                             ChangeSetModifier result,
                                                             HashMap<ByteString, DecryptedRequestingMember> requestingMembersByUuid)
  {
    List<DecryptedApproveMember> promoteRequestingMembersList = conflictingChange.getPromoteRequestingMembersList();

    for (int i = promoteRequestingMembersList.size() - 1; i >= 0; i--) {
      DecryptedApproveMember member = promoteRequestingMembersList.get(i);

      if (!requestingMembersByUuid.containsKey(member.getUuid())) {
        result.removePromoteRequestingMembers(i);
      }
    }
  }
}

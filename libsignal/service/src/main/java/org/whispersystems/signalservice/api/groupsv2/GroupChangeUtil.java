package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;

import java.util.HashMap;
import java.util.List;

public final class GroupChangeUtil {

  private GroupChangeUtil() {
  }

  /**
   * The maximum field we know about here.
   */
  static final int CHANGE_ACTION_MAX_FIELD = 14;

  /**
   * True iff there are no change actions.
   */
  public static boolean changeIsEmpty(GroupChange.Actions change) {
    return change.getAddMembersCount()              == 0 && // field 3
           change.getDeleteMembersCount()           == 0 && // field 4
           change.getModifyMemberRolesCount()       == 0 && // field 5
           change.getModifyMemberProfileKeysCount() == 0 && // field 6
           change.getAddPendingMembersCount()       == 0 && // field 7
           change.getDeletePendingMembersCount()    == 0 && // field 8
           change.getPromotePendingMembersCount()   == 0 && // field 9
           !change.hasModifyTitle()                      && // field 10
           !change.hasModifyAvatar()                     && // field 11
           !change.hasModifyDisappearingMessagesTimer()  && // field 12
           !change.hasModifyAttributesAccess()           && // field 13
           !change.hasModifyMemberAccess();                 // field 14
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
    GroupChange.Actions.Builder                 result               = GroupChange.Actions.newBuilder(encryptedChange);
    HashMap<ByteString, DecryptedMember>        fullMembersByUuid    = new HashMap<>(groupState.getMembersCount());
    HashMap<ByteString, DecryptedPendingMember> pendingMembersByUuid = new HashMap<>(groupState.getPendingMembersCount());

    for (DecryptedMember member : groupState.getMembersList()) {
      fullMembersByUuid.put(member.getUuid(), member);
    }

    for (DecryptedPendingMember member : groupState.getPendingMembersList()) {
      pendingMembersByUuid.put(member.getUuid(), member);
    }

    resolveField3AddMembers                      (conflictingChange, result, fullMembersByUuid, pendingMembersByUuid);
    resolveField4DeleteMembers                   (conflictingChange, result, fullMembersByUuid);
    resolveField5ModifyMemberRoles               (conflictingChange, result, fullMembersByUuid);
    resolveField6ModifyProfileKeys               (conflictingChange, result, fullMembersByUuid);
    resolveField7AddPendingMembers               (conflictingChange, result, fullMembersByUuid, pendingMembersByUuid);
    resolveField8DeletePendingMembers            (conflictingChange, result, pendingMembersByUuid);
    resolveField9PromotePendingMembers           (conflictingChange, result, pendingMembersByUuid);
    resolveField10ModifyTitle                    (groupState, conflictingChange, result);
    resolveField11ModifyAvatar                   (groupState, conflictingChange, result);
    resolveField12modifyDisappearingMessagesTimer(groupState, conflictingChange, result);
    resolveField13modifyAttributesAccess         (groupState, conflictingChange, result);
    resolveField14modifyAttributesAccess         (groupState, conflictingChange, result);
    
    return result;
  }

  private static void resolveField3AddMembers(DecryptedGroupChange conflictingChange, GroupChange.Actions.Builder result, HashMap<ByteString, DecryptedMember> fullMembersByUuid, HashMap<ByteString, DecryptedPendingMember> pendingMembersByUuid) {
    List<DecryptedMember> newMembersList = conflictingChange.getNewMembersList();

    for (int i = newMembersList.size() - 1; i >= 0; i--) {
      DecryptedMember member = newMembersList.get(i);

      if (fullMembersByUuid.containsKey(member.getUuid())) {
        result.removeAddMembers(i);
      } else if (pendingMembersByUuid.containsKey(member.getUuid())) {
        GroupChange.Actions.AddMemberAction addMemberAction = result.getAddMembersList().get(i);
        result.removeAddMembers(i);
        result.addPromotePendingMembers(GroupChange.Actions.PromotePendingMemberAction.newBuilder().setPresentation(addMemberAction.getAdded().getPresentation()));
      }
    }
  }

  private static void resolveField4DeleteMembers(DecryptedGroupChange conflictingChange, GroupChange.Actions.Builder result, HashMap<ByteString, DecryptedMember> fullMembersByUuid) {
    List<ByteString> deletedMembersList = conflictingChange.getDeleteMembersList();

    for (int i = deletedMembersList.size() - 1; i >= 0; i--) {
      ByteString member = deletedMembersList.get(i);

      if (!fullMembersByUuid.containsKey(member)) {
        result.removeDeleteMembers(i);
      }
    }
  }

  private static void resolveField5ModifyMemberRoles(DecryptedGroupChange conflictingChange, GroupChange.Actions.Builder result, HashMap<ByteString, DecryptedMember> fullMembersByUuid) {
    List<DecryptedModifyMemberRole> modifyRolesList = conflictingChange.getModifyMemberRolesList();

    for (int i = modifyRolesList.size() - 1; i >= 0; i--) {
      DecryptedModifyMemberRole modifyRoleAction = modifyRolesList.get(i);
      DecryptedMember           memberInGroup    = fullMembersByUuid.get(modifyRoleAction.getUuid());

      if (memberInGroup == null || memberInGroup.getRole() == modifyRoleAction.getRole()) {
        result.removeModifyMemberRoles(i);
      }
    }
  }

  private static void resolveField6ModifyProfileKeys(DecryptedGroupChange conflictingChange, GroupChange.Actions.Builder result, HashMap<ByteString, DecryptedMember> fullMembersByUuid) {
    List<DecryptedMember> modifyProfileKeysList = conflictingChange.getModifiedProfileKeysList();

    for (int i = modifyProfileKeysList.size() - 1; i >= 0; i--) {
      DecryptedMember member        = modifyProfileKeysList.get(i);
      DecryptedMember memberInGroup = fullMembersByUuid.get(member.getUuid());

      if (memberInGroup == null || member.getProfileKey().equals(memberInGroup.getProfileKey())) {
        result.removeModifyMemberProfileKeys(i);
      }
    }
  }

  private static void resolveField7AddPendingMembers(DecryptedGroupChange conflictingChange, GroupChange.Actions.Builder result, HashMap<ByteString, DecryptedMember> fullMembersByUuid, HashMap<ByteString, DecryptedPendingMember> pendingMembersByUuid) {
    List<DecryptedPendingMember> newPendingMembersList = conflictingChange.getNewPendingMembersList();

    for (int i = newPendingMembersList.size() - 1; i >= 0; i--) {
      DecryptedPendingMember member = newPendingMembersList.get(i);

      if (fullMembersByUuid.containsKey(member.getUuid()) || pendingMembersByUuid.containsKey(member.getUuid())) {
        result.removeAddPendingMembers(i);
      }
    }
  }

  private static void resolveField8DeletePendingMembers(DecryptedGroupChange conflictingChange, GroupChange.Actions.Builder result, HashMap<ByteString, DecryptedPendingMember> pendingMembersByUuid) {
    List<DecryptedPendingMemberRemoval> deletePendingMembersList = conflictingChange.getDeletePendingMembersList();

    for (int i = deletePendingMembersList.size() - 1; i >= 0; i--) {
      DecryptedPendingMemberRemoval member = deletePendingMembersList.get(i);

      if (!pendingMembersByUuid.containsKey(member.getUuid())) {
        result.removeDeletePendingMembers(i);
      }
    }
  }

  private static void resolveField9PromotePendingMembers(DecryptedGroupChange conflictingChange, GroupChange.Actions.Builder result, HashMap<ByteString, DecryptedPendingMember> pendingMembersByUuid) {
    List<DecryptedMember> promotePendingMembersList = conflictingChange.getPromotePendingMembersList();

    for (int i = promotePendingMembersList.size() - 1; i >= 0; i--) {
      DecryptedMember member = promotePendingMembersList.get(i);
      
      if (!pendingMembersByUuid.containsKey(member.getUuid())) {
        result.removePromotePendingMembers(i);
      }
    }
  }

  private static void resolveField10ModifyTitle(DecryptedGroup groupState, DecryptedGroupChange conflictingChange, GroupChange.Actions.Builder result) {
    if (conflictingChange.hasNewTitle() && conflictingChange.getNewTitle().getValue().equals(groupState.getTitle())) {
      result.clearModifyTitle();
    }
  }

  private static void resolveField11ModifyAvatar(DecryptedGroup groupState, DecryptedGroupChange conflictingChange, GroupChange.Actions.Builder result) {
    if (conflictingChange.hasNewAvatar() && conflictingChange.getNewAvatar().getValue().equals(groupState.getAvatar())) {
      result.clearModifyAvatar();
    }
  }

  private static void resolveField12modifyDisappearingMessagesTimer(DecryptedGroup groupState, DecryptedGroupChange conflictingChange, GroupChange.Actions.Builder result) {
    if (conflictingChange.hasNewTimer() && conflictingChange.getNewTimer().getDuration() == groupState.getDisappearingMessagesTimer().getDuration()) {
      result.clearModifyDisappearingMessagesTimer();
    }
  }

  private static void resolveField13modifyAttributesAccess(DecryptedGroup groupState, DecryptedGroupChange conflictingChange, GroupChange.Actions.Builder result) {
    if (conflictingChange.getNewAttributeAccess() == groupState.getAccessControl().getAttributes()) {
      result.clearModifyAttributesAccess();
    }
  }

  private static void resolveField14modifyAttributesAccess(DecryptedGroup groupState, DecryptedGroupChange conflictingChange, GroupChange.Actions.Builder result) {
    if (conflictingChange.getNewMemberAccess() == groupState.getAccessControl().getMembers()) {
      result.clearModifyMemberAccess();
    }
  }
}

package org.whispersystems.signalservice.api.groupsv2;

import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedApproveMember;
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;

import java.util.HashMap;
import java.util.List;

import okio.ByteString;

public final class GroupChangeUtil {

  private GroupChangeUtil() {
  }

  /**
   * True iff there are no change actions.
   */
  public static boolean changeIsEmpty(GroupChange.Actions change) {
    return change.addMembers.size() == 0 &&                     // field 3
           change.deleteMembers.size() == 0 &&                  // field 4
           change.modifyMemberRoles.size() == 0 &&              // field 5
           change.modifyMemberProfileKeys.size() == 0 &&        // field 6
           change.addPendingMembers.size() == 0 &&              // field 7
           change.deletePendingMembers.size() == 0 &&           // field 8
           change.promotePendingMembers.size() == 0 &&          // field 9
           change.modifyTitle == null &&                        // field 10
           change.modifyAvatar == null &&                       // field 11
           change.modifyDisappearingMessagesTimer == null &&    // field 12
           change.modifyAttributesAccess == null &&             // field 13
           change.modifyMemberAccess == null &&                 // field 14
           change.modifyAddFromInviteLinkAccess == null &&      // field 15
           change.addRequestingMembers.size() == 0 &&           // field 16
           change.deleteRequestingMembers.size() == 0 &&        // field 17
           change.promoteRequestingMembers.size() == 0 &&       // field 18
           change.modifyInviteLinkPassword == null &&           // field 19
           change.modifyDescription == null &&                  // field 20
           change.modifyAnnouncementsOnly == null &&            // field 21
           change.addBannedMembers.size() == 0 &&               // field 22
           change.deleteBannedMembers.size() == 0 &&            // field 23
           change.promotePendingPniAciMembers.size() == 0;      // field 24
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
    GroupChange.Actions.Builder result = encryptedChange.newBuilder();

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
    DecryptedGroupChange.Builder result = conflictingChange.newBuilder();

    resolveConflict(groupState, conflictingChange, new DecryptedGroupChangeActionsBuilderChangeSetModifier(result));

    return result;
  }

  private static void resolveConflict(DecryptedGroup groupState,
                                      DecryptedGroupChange conflictingChange,
                                      ChangeSetModifier changeSetModifier)
  {
    HashMap<ByteString, DecryptedMember>           fullMembersByUuid         = new HashMap<>(groupState.members.size());
    HashMap<ByteString, DecryptedPendingMember>    pendingMembersByServiceId = new HashMap<>(groupState.pendingMembers.size());
    HashMap<ByteString, DecryptedRequestingMember> requestingMembersByUuid   = new HashMap<>(groupState.members.size());
    HashMap<ByteString, DecryptedBannedMember>     bannedMembersByServiceId  = new HashMap<>(groupState.bannedMembers.size());

    for (DecryptedMember member : groupState.members) {
      fullMembersByUuid.put(member.aciBytes, member);
    }

    for (DecryptedPendingMember member : groupState.pendingMembers) {
      pendingMembersByServiceId.put(member.serviceIdBytes, member);
    }

    for (DecryptedRequestingMember member : groupState.requestingMembers) {
      requestingMembersByUuid.put(member.aciBytes, member);
    }

    for (DecryptedBannedMember member : groupState.bannedMembers) {
      bannedMembersByServiceId.put(member.serviceIdBytes, member);
    }

    resolveField3AddMembers                      (conflictingChange, changeSetModifier, fullMembersByUuid, pendingMembersByServiceId);
    resolveField4DeleteMembers                   (conflictingChange, changeSetModifier, fullMembersByUuid);
    resolveField5ModifyMemberRoles               (conflictingChange, changeSetModifier, fullMembersByUuid);
    resolveField6ModifyProfileKeys               (conflictingChange, changeSetModifier, fullMembersByUuid);
    resolveField7AddPendingMembers               (conflictingChange, changeSetModifier, fullMembersByUuid, pendingMembersByServiceId);
    resolveField8DeletePendingMembers            (conflictingChange, changeSetModifier, pendingMembersByServiceId);
    resolveField9PromotePendingMembers           (conflictingChange, changeSetModifier, pendingMembersByServiceId);
    resolveField10ModifyTitle                    (groupState, conflictingChange, changeSetModifier);
    resolveField11ModifyAvatar                   (groupState, conflictingChange, changeSetModifier);
    resolveField12modifyDisappearingMessagesTimer(groupState, conflictingChange, changeSetModifier);
    resolveField13modifyAttributesAccess         (groupState, conflictingChange, changeSetModifier);
    resolveField14modifyAttributesAccess         (groupState, conflictingChange, changeSetModifier);
    resolveField15modifyAddFromInviteLinkAccess  (groupState, conflictingChange, changeSetModifier);
    resolveField16AddRequestingMembers           (conflictingChange, changeSetModifier, fullMembersByUuid, pendingMembersByServiceId);
    resolveField17DeleteMembers                  (conflictingChange, changeSetModifier, requestingMembersByUuid);
    resolveField18PromoteRequestingMembers       (conflictingChange, changeSetModifier, requestingMembersByUuid);
    resolveField20ModifyDescription              (groupState, conflictingChange, changeSetModifier);
    resolveField21ModifyAnnouncementsOnly        (groupState, conflictingChange, changeSetModifier);
    resolveField22AddBannedMembers               (conflictingChange, changeSetModifier, bannedMembersByServiceId);
    resolveField23DeleteBannedMembers            (conflictingChange, changeSetModifier, bannedMembersByServiceId);
    resolveField24PromotePendingPniAciMembers    (conflictingChange, changeSetModifier, fullMembersByUuid);
  }

  private static void resolveField3AddMembers(DecryptedGroupChange conflictingChange, ChangeSetModifier result, HashMap<ByteString, DecryptedMember> fullMembersByUuid, HashMap<ByteString, DecryptedPendingMember> pendingMembersByServiceId) {
    List<DecryptedMember> newMembersList = conflictingChange.newMembers;

    for (int i = newMembersList.size() - 1; i >= 0; i--) {
      DecryptedMember member = newMembersList.get(i);

      if (fullMembersByUuid.containsKey(member.aciBytes)) {
        result.removeAddMembers(i);
      } else if (pendingMembersByServiceId.containsKey(member.aciBytes) || pendingMembersByServiceId.containsKey(member.pniBytes)) {
        result.moveAddToPromote(i);
      }
    }
  }

  private static void resolveField4DeleteMembers(DecryptedGroupChange conflictingChange, ChangeSetModifier result, HashMap<ByteString, DecryptedMember> fullMembersByUuid) {
    List<ByteString> deletedMembersList = conflictingChange.deleteMembers;

    for (int i = deletedMembersList.size() - 1; i >= 0; i--) {
      ByteString member = deletedMembersList.get(i);

      if (!fullMembersByUuid.containsKey(member)) {
        result.removeDeleteMembers(i);
      }
    }
  }

  private static void resolveField5ModifyMemberRoles(DecryptedGroupChange conflictingChange, ChangeSetModifier result, HashMap<ByteString, DecryptedMember> fullMembersByUuid) {
    List<DecryptedModifyMemberRole> modifyRolesList = conflictingChange.modifyMemberRoles;

    for (int i = modifyRolesList.size() - 1; i >= 0; i--) {
      DecryptedModifyMemberRole modifyRoleAction = modifyRolesList.get(i);
      DecryptedMember           memberInGroup    = fullMembersByUuid.get(modifyRoleAction.aciBytes);

      if (memberInGroup == null || memberInGroup.role == modifyRoleAction.role) {
        result.removeModifyMemberRoles(i);
      }
    }
  }

  private static void resolveField6ModifyProfileKeys(DecryptedGroupChange conflictingChange, ChangeSetModifier result, HashMap<ByteString, DecryptedMember> fullMembersByUuid) {
    List<DecryptedMember> modifyProfileKeysList = conflictingChange.modifiedProfileKeys;

    for (int i = modifyProfileKeysList.size() - 1; i >= 0; i--) {
      DecryptedMember member        = modifyProfileKeysList.get(i);
      DecryptedMember memberInGroup = fullMembersByUuid.get(member.aciBytes);

      if (memberInGroup == null || member.profileKey.equals(memberInGroup.profileKey)) {
        result.removeModifyMemberProfileKeys(i);
      }
    }
  }

  private static void resolveField7AddPendingMembers(DecryptedGroupChange conflictingChange, ChangeSetModifier result, HashMap<ByteString, DecryptedMember> fullMembersByUuid, HashMap<ByteString, DecryptedPendingMember> pendingMembersByServiceId) {
    List<DecryptedPendingMember> newPendingMembersList = conflictingChange.newPendingMembers;

    for (int i = newPendingMembersList.size() - 1; i >= 0; i--) {
      DecryptedPendingMember member = newPendingMembersList.get(i);

      if (fullMembersByUuid.containsKey(member.serviceIdBytes) || pendingMembersByServiceId.containsKey(member.serviceIdBytes)) {
        result.removeAddPendingMembers(i);
      }
    }
  }

  private static void resolveField8DeletePendingMembers(DecryptedGroupChange conflictingChange, ChangeSetModifier result, HashMap<ByteString, DecryptedPendingMember> pendingMembersByServiceId) {
    List<DecryptedPendingMemberRemoval> deletePendingMembersList = conflictingChange.deletePendingMembers;

    for (int i = deletePendingMembersList.size() - 1; i >= 0; i--) {
      DecryptedPendingMemberRemoval member = deletePendingMembersList.get(i);

      if (!pendingMembersByServiceId.containsKey(member.serviceIdBytes)) {
        result.removeDeletePendingMembers(i);
      }
    }
  }

  private static void resolveField9PromotePendingMembers(DecryptedGroupChange conflictingChange, ChangeSetModifier result, HashMap<ByteString, DecryptedPendingMember> pendingMembersByServiceId) {
    List<DecryptedMember> promotePendingMembersList = conflictingChange.promotePendingMembers;

    for (int i = promotePendingMembersList.size() - 1; i >= 0; i--) {
      DecryptedMember member = promotePendingMembersList.get(i);
      
      if (!pendingMembersByServiceId.containsKey(member.aciBytes) && !pendingMembersByServiceId.containsKey(member.pniBytes)) {
        result.removePromotePendingMembers(i);
      }
    }
  }

  private static void resolveField10ModifyTitle(DecryptedGroup groupState, DecryptedGroupChange conflictingChange, ChangeSetModifier result) {
    if (conflictingChange.newTitle != null && conflictingChange.newTitle.value_.equals(groupState.title)) {
      result.clearModifyTitle();
    }
  }

  private static void resolveField11ModifyAvatar(DecryptedGroup groupState, DecryptedGroupChange conflictingChange, ChangeSetModifier result) {
    if (conflictingChange.newAvatar != null && conflictingChange.newAvatar.value_.equals(groupState.avatar)) {
      result.clearModifyAvatar();
    }
  }

  private static void resolveField12modifyDisappearingMessagesTimer(DecryptedGroup groupState, DecryptedGroupChange conflictingChange, ChangeSetModifier result) {
    if (groupState.disappearingMessagesTimer != null && conflictingChange.newTimer != null && conflictingChange.newTimer.duration == groupState.disappearingMessagesTimer.duration) {
      result.clearModifyDisappearingMessagesTimer();
    }
  }

  private static void resolveField13modifyAttributesAccess(DecryptedGroup groupState, DecryptedGroupChange conflictingChange, ChangeSetModifier result) {
    if (groupState.accessControl != null && conflictingChange.newAttributeAccess == groupState.accessControl.attributes) {
      result.clearModifyAttributesAccess();
    }
  }

  private static void resolveField14modifyAttributesAccess(DecryptedGroup groupState, DecryptedGroupChange conflictingChange, ChangeSetModifier result) {
    if (groupState.accessControl != null && conflictingChange.newMemberAccess == groupState.accessControl.members) {
      result.clearModifyMemberAccess();
    }
  }

  private static void resolveField15modifyAddFromInviteLinkAccess(DecryptedGroup groupState, DecryptedGroupChange conflictingChange, ChangeSetModifier result) {
    if (groupState.accessControl != null && conflictingChange.newInviteLinkAccess == groupState.accessControl.addFromInviteLink) {
      result.clearModifyAddFromInviteLinkAccess();
    }
  }

  private static void resolveField16AddRequestingMembers(DecryptedGroupChange conflictingChange, ChangeSetModifier result, HashMap<ByteString, DecryptedMember> fullMembersByUuid, HashMap<ByteString, DecryptedPendingMember> pendingMembersByServiceId) {
    List<DecryptedRequestingMember> newMembersList = conflictingChange.newRequestingMembers;

    for (int i = newMembersList.size() - 1; i >= 0; i--) {
      DecryptedRequestingMember member = newMembersList.get(i);

      if (fullMembersByUuid.containsKey(member.aciBytes)) {
        result.removeAddRequestingMembers(i);
      } else if (pendingMembersByServiceId.containsKey(member.aciBytes)) {
        result.moveAddRequestingMembersToPromote(i);
      }
    }
  }

  private static void resolveField17DeleteMembers(DecryptedGroupChange conflictingChange,
                                                  ChangeSetModifier result,
                                                  HashMap<ByteString, DecryptedRequestingMember> requestingMembers)
  {
    List<ByteString> deletedMembersList = conflictingChange.deleteRequestingMembers;

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
    List<DecryptedApproveMember> promoteRequestingMembersList = conflictingChange.promoteRequestingMembers;

    for (int i = promoteRequestingMembersList.size() - 1; i >= 0; i--) {
      DecryptedApproveMember member = promoteRequestingMembersList.get(i);

      if (!requestingMembersByUuid.containsKey(member.aciBytes)) {
        result.removePromoteRequestingMembers(i);
      }
    }
  }

  private static void resolveField20ModifyDescription(DecryptedGroup groupState, DecryptedGroupChange conflictingChange, ChangeSetModifier result) {
    if (conflictingChange.newDescription != null && conflictingChange.newDescription.value_.equals(groupState.description)) {
      result.clearModifyDescription();
    }
  }

  private static void resolveField21ModifyAnnouncementsOnly(DecryptedGroup groupState, DecryptedGroupChange conflictingChange, ChangeSetModifier result) {
    if (conflictingChange.newIsAnnouncementGroup.equals(groupState.isAnnouncementGroup)) {
      result.clearModifyAnnouncementsOnly();
    }
  }

  private static void resolveField22AddBannedMembers(DecryptedGroupChange conflictingChange, ChangeSetModifier result, HashMap<ByteString, DecryptedBannedMember> bannedMembersByServiceId) {
    List<DecryptedBannedMember> newBannedMembersList = conflictingChange.newBannedMembers;

    for (int i = newBannedMembersList.size() - 1; i >= 0; i--) {
      DecryptedBannedMember member = newBannedMembersList.get(i);

      if (bannedMembersByServiceId.containsKey(member.serviceIdBytes)) {
        result.removeAddBannedMembers(i);
      }
    }
  }

  private static void resolveField23DeleteBannedMembers(DecryptedGroupChange conflictingChange, ChangeSetModifier result, HashMap<ByteString, DecryptedBannedMember> bannedMembersByServiceId) {
    List<DecryptedBannedMember> deleteBannedMembersList = conflictingChange.deleteBannedMembers;

    for (int i = deleteBannedMembersList.size() - 1; i >= 0; i--) {
      DecryptedBannedMember member = deleteBannedMembersList.get(i);

      if (!bannedMembersByServiceId.containsKey(member.serviceIdBytes)) {
        result.removeDeleteBannedMembers(i);
      }
    }
  }

  private static void resolveField24PromotePendingPniAciMembers(DecryptedGroupChange conflictingChange, ChangeSetModifier result, HashMap<ByteString, DecryptedMember> fullMembersByAci) {
    List<DecryptedMember> promotePendingPniAciMembersList = conflictingChange.promotePendingPniAciMembers;

    for (int i = promotePendingPniAciMembersList.size() - 1; i >= 0; i--) {
      DecryptedMember member = promotePendingPniAciMembersList.get(i);

      if (fullMembersByAci.containsKey(member.aciBytes)) {
        result.removePromotePendingPniAciMembers(i);
      }
    }
  }
}

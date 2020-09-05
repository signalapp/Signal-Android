package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedApproveMember;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DecryptedGroupUtil {

  private static final String TAG = DecryptedGroupUtil.class.getSimpleName();

  public static ArrayList<UUID> toUuidList(Collection<DecryptedMember> membersList) {
    ArrayList<UUID> uuidList = new ArrayList<>(membersList.size());

    for (DecryptedMember member : membersList) {
      uuidList.add(toUuid(member));
    }

    return uuidList;
  }

  public static ArrayList<UUID> membersToUuidList(Collection<DecryptedMember> membersList) {
    ArrayList<UUID> uuidList = new ArrayList<>(membersList.size());

    for (DecryptedMember member : membersList) {
      UUID uuid = toUuid(member);

      if (!UuidUtil.UNKNOWN_UUID.equals(uuid)) {
        uuidList.add(uuid);
      }
    }

    return uuidList;
  }

  public static Set<ByteString> membersToUuidByteStringSet(Collection<DecryptedMember> membersList) {
    Set<ByteString> uuidList = new HashSet<>(membersList.size());

    for (DecryptedMember member : membersList) {
      uuidList.add(member.getUuid());
    }

    return uuidList;
  }

  /**
   * Can return non-decryptable member UUIDs as {@link UuidUtil#UNKNOWN_UUID}.
   */
  public static ArrayList<UUID> pendingToUuidList(Collection<DecryptedPendingMember> membersList) {
    ArrayList<UUID> uuidList = new ArrayList<>(membersList.size());

    for (DecryptedPendingMember member : membersList) {
      uuidList.add(toUuid(member));
    }

    return uuidList;
  }

  /**
   * Will not return any non-decryptable member UUIDs.
   */
  public static ArrayList<UUID> removedMembersUuidList(DecryptedGroupChange groupChange) {
    List<ByteString> deletedMembers = groupChange.getDeleteMembersList();
    ArrayList<UUID>  uuidList       = new ArrayList<>(deletedMembers.size());

    for (ByteString member : deletedMembers) {
       UUID uuid = toUuid(member);

      if (!UuidUtil.UNKNOWN_UUID.equals(uuid)) {
        uuidList.add(uuid);
      }
    }

    return uuidList;
  }

  /**
   * Will not return any non-decryptable member UUIDs.
   */
  public static ArrayList<UUID> removedPendingMembersUuidList(DecryptedGroupChange groupChange) {
    List<DecryptedPendingMemberRemoval> deletedPendingMembers = groupChange.getDeletePendingMembersList();
    ArrayList<UUID>                     uuidList              = new ArrayList<>(deletedPendingMembers.size());

    for (DecryptedPendingMemberRemoval member : deletedPendingMembers) {
      UUID uuid = toUuid(member.getUuid());

      if(!UuidUtil.UNKNOWN_UUID.equals(uuid)) {
        uuidList.add(uuid);
      }
    }

    return uuidList;
  }

  /**
   * Will not return any non-decryptable member UUIDs.
   */
  public static ArrayList<UUID> removedRequestingMembersUuidList(DecryptedGroupChange groupChange) {
    List<ByteString> deleteRequestingMembers = groupChange.getDeleteRequestingMembersList();
    ArrayList<UUID>  uuidList                = new ArrayList<>(deleteRequestingMembers.size());

    for (ByteString member : deleteRequestingMembers) {
      UUID uuid = toUuid(member);

      if(!UuidUtil.UNKNOWN_UUID.equals(uuid)) {
        uuidList.add(uuid);
      }
    }

    return uuidList;
  }

  public static UUID toUuid(DecryptedMember member) {
    return toUuid(member.getUuid());
  }

  public static UUID toUuid(DecryptedPendingMember member) {
    return toUuid(member.getUuid());
  }

  private static UUID toUuid(ByteString memberUuid) {
    return UuidUtil.fromByteStringOrUnknown(memberUuid);
  }

  /**
   * The UUID of the member that made the change.
   */
  public static Optional<UUID> editorUuid(DecryptedGroupChange change) {
    return Optional.fromNullable(change != null ? UuidUtil.fromByteStringOrNull(change.getEditor()) : null);
  }

  public static Optional<DecryptedMember> findMemberByUuid(Collection<DecryptedMember> members, UUID uuid) {
    ByteString uuidBytes = UuidUtil.toByteString(uuid);

    for (DecryptedMember member : members) {
      if (uuidBytes.equals(member.getUuid())) {
        return Optional.of(member);
      }
    }

    return Optional.absent();
  }

  public static Optional<DecryptedPendingMember> findPendingByUuid(Collection<DecryptedPendingMember> members, UUID uuid) {
    ByteString uuidBytes = UuidUtil.toByteString(uuid);

    for (DecryptedPendingMember member : members) {
      if (uuidBytes.equals(member.getUuid())) {
        return Optional.of(member);
      }
    }

    return Optional.absent();
  }

  private static int findPendingIndexByUuidCipherText(List<DecryptedPendingMember> members, ByteString cipherText) {
    for (int i = 0; i < members.size(); i++) {
      DecryptedPendingMember member = members.get(i);
      if (cipherText.equals(member.getUuidCipherText())) {
        return i;
      }
    }

    return -1;
  }

  private static int findPendingIndexByUuid(List<DecryptedPendingMember> members, ByteString uuid) {
    for (int i = 0; i < members.size(); i++) {
      DecryptedPendingMember member = members.get(i);
      if (uuid.equals(member.getUuid())) {
        return i;
      }
    }

    return -1;
  }

  public static Optional<DecryptedRequestingMember> findRequestingByUuid(Collection<DecryptedRequestingMember> members, UUID uuid) {
    ByteString uuidBytes = UuidUtil.toByteString(uuid);

    for (DecryptedRequestingMember member : members) {
      if (uuidBytes.equals(member.getUuid())) {
        return Optional.of(member);
      }
    }

    return Optional.absent();
  }

  public static boolean isPendingOrRequesting(DecryptedGroup group, UUID uuid) {
    return findPendingByUuid(group.getPendingMembersList(), uuid).isPresent() ||
           findRequestingByUuid(group.getRequestingMembersList(), uuid).isPresent();
  }

  /**
   * Removes the uuid from the full members of a group.
   * <p>
   * Generally not expected to have to do this, just in the case of leaving a group where you cannot
   * get the new group state as you are not in the group any longer.
   */
  public static DecryptedGroup removeMember(DecryptedGroup group, UUID uuid, int revision) {
    DecryptedGroup.Builder     builder          = DecryptedGroup.newBuilder(group);
    ByteString                 uuidString       = UuidUtil.toByteString(uuid);
    boolean                    removed          = false;
    ArrayList<DecryptedMember> decryptedMembers = new ArrayList<>(builder.getMembersList());
    Iterator<DecryptedMember>  membersList      = decryptedMembers.iterator();

    while (membersList.hasNext()) {
      if (uuidString.equals(membersList.next().getUuid())) {
        membersList.remove();
        removed = true;
      }
    }

    if (removed) {
      return builder.clearMembers()
                    .addAllMembers(decryptedMembers)
                    .setRevision(revision)
                    .build();
    } else {
      return group;
    }
  }

  public static DecryptedGroup apply(DecryptedGroup group, DecryptedGroupChange change)
      throws NotAbleToApplyGroupV2ChangeException
  {
    if (change.getRevision() != group.getRevision() + 1) {
      throw new NotAbleToApplyGroupV2ChangeException();
    }

    return applyWithoutRevisionCheck(group, change);
  }

  public static DecryptedGroup applyWithoutRevisionCheck(DecryptedGroup group, DecryptedGroupChange change)
      throws NotAbleToApplyGroupV2ChangeException
  {
    DecryptedGroup.Builder builder = DecryptedGroup.newBuilder(group)
                                                   .setRevision(change.getRevision());

    applyAddMemberAction(builder, change.getNewMembersList());

    applyDeleteMemberActions(builder, change.getDeleteMembersList());

    applyModifyMemberRoleActions(builder, change.getModifyMemberRolesList());

    applyModifyMemberProfileKeyActions(builder, change.getModifiedProfileKeysList());

    applyAddPendingMemberActions(builder, change.getNewPendingMembersList());

    applyDeletePendingMemberActions(builder, change.getDeletePendingMembersList());

    applyPromotePendingMemberActions(builder, change.getPromotePendingMembersList());

    applyModifyTitleAction(builder, change);

    applyModifyAvatarAction(builder, change);

    applyModifyDisappearingMessagesTimerAction(builder, change);

    applyModifyAttributesAccessControlAction(builder, change);

    applyModifyMembersAccessControlAction(builder, change);

    applyModifyAddFromInviteLinkAccessControlAction(builder, change);

    applyAddRequestingMembers(builder, change.getNewRequestingMembersList());

    applyDeleteRequestingMembers(builder, change.getDeleteRequestingMembersList());

    applyPromoteRequestingMemberActions(builder, change.getPromoteRequestingMembersList());

    applyInviteLinkPassword(builder, change);

    return builder.build();
  }

  private static void applyAddMemberAction(DecryptedGroup.Builder builder, List<DecryptedMember> newMembersList) {
    builder.addAllMembers(newMembersList);

    removePendingAndRequestingMembersNowInGroup(builder);
  }

  protected static void applyDeleteMemberActions(DecryptedGroup.Builder builder, List<ByteString> deleteMembersList) {
    for (ByteString removedMember : deleteMembersList) {
      int index = indexOfUuid(builder.getMembersList(), removedMember);

      if (index == -1) {
        Log.w(TAG, "Deleted member on change not found in group");
        continue;
      }

      builder.removeMembers(index);
    }
  }

  private static void applyModifyMemberRoleActions(DecryptedGroup.Builder builder, List<DecryptedModifyMemberRole> modifyMemberRolesList) throws NotAbleToApplyGroupV2ChangeException {
    for (DecryptedModifyMemberRole modifyMemberRole : modifyMemberRolesList) {
      int index = indexOfUuid(builder.getMembersList(), modifyMemberRole.getUuid());

      if (index == -1) {
        throw new NotAbleToApplyGroupV2ChangeException();
      }

      Member.Role role = modifyMemberRole.getRole();

      ensureKnownRole(role);

      builder.setMembers(index, DecryptedMember.newBuilder(builder.getMembers(index))
                                               .setRole(role));
    }
  }

  private static void applyModifyMemberProfileKeyActions(DecryptedGroup.Builder builder, List<DecryptedMember> modifiedProfileKeysList) throws NotAbleToApplyGroupV2ChangeException {
    for (DecryptedMember modifyProfileKey : modifiedProfileKeysList) {
      int index = indexOfUuid(builder.getMembersList(), modifyProfileKey.getUuid());

      if (index == -1) {
        throw new NotAbleToApplyGroupV2ChangeException();
      }

      builder.setMembers(index, withNewProfileKey(builder.getMembers(index), modifyProfileKey.getProfileKey()));
    }
  }

  private static void applyAddPendingMemberActions(DecryptedGroup.Builder builder, List<DecryptedPendingMember> newPendingMembersList) throws NotAbleToApplyGroupV2ChangeException {
    Set<ByteString> fullMemberSet            = getMemberUuidSet(builder.getMembersList());
    Set<ByteString> pendingMemberCipherTexts = getPendingMemberCipherTextSet(builder.getPendingMembersList());

    for (DecryptedPendingMember pendingMember : newPendingMembersList) {
      if (fullMemberSet.contains(pendingMember.getUuid())) {
        throw new NotAbleToApplyGroupV2ChangeException();
      }

      if (!pendingMemberCipherTexts.contains(pendingMember.getUuidCipherText())) {
        builder.addPendingMembers(pendingMember);
      }
    }
  }

  protected static void applyDeletePendingMemberActions(DecryptedGroup.Builder builder, List<DecryptedPendingMemberRemoval> deletePendingMembersList) {
    for (DecryptedPendingMemberRemoval removedMember : deletePendingMembersList) {
      int index = findPendingIndexByUuidCipherText(builder.getPendingMembersList(), removedMember.getUuidCipherText());

      if (index == -1) {
        Log.w(TAG, "Deleted pending member on change not found in group");
        continue;
      }

      builder.removePendingMembers(index);
    }
  }

  protected static void applyPromotePendingMemberActions(DecryptedGroup.Builder builder, List<DecryptedMember> promotePendingMembersList) throws NotAbleToApplyGroupV2ChangeException {
    for (DecryptedMember newMember : promotePendingMembersList) {
      int index = findPendingIndexByUuid(builder.getPendingMembersList(), newMember.getUuid());

      if (index == -1) {
        throw new NotAbleToApplyGroupV2ChangeException();
      }

      builder.removePendingMembers(index);
      builder.addMembers(newMember);
    }
  }

  protected static void applyModifyTitleAction(DecryptedGroup.Builder builder, DecryptedGroupChange change) {
    if (change.hasNewTitle()) {
      builder.setTitle(change.getNewTitle().getValue());
    }
  }

  protected static void applyModifyAvatarAction(DecryptedGroup.Builder builder, DecryptedGroupChange change) {
    if (change.hasNewAvatar()) {
      builder.setAvatar(change.getNewAvatar().getValue());
    }
  }

  protected static void applyModifyDisappearingMessagesTimerAction(DecryptedGroup.Builder builder, DecryptedGroupChange change) {
    if (change.hasNewTimer()) {
      builder.setDisappearingMessagesTimer(change.getNewTimer());
    }
  }

  protected static void applyModifyAttributesAccessControlAction(DecryptedGroup.Builder builder, DecryptedGroupChange change) {
    AccessControl.AccessRequired newAccessLevel = change.getNewAttributeAccess();

    if (newAccessLevel != AccessControl.AccessRequired.UNKNOWN) {
      builder.setAccessControl(AccessControl.newBuilder(builder.getAccessControl())
                                            .setAttributesValue(change.getNewAttributeAccessValue()));
    }
  }

  protected static void applyModifyMembersAccessControlAction(DecryptedGroup.Builder builder, DecryptedGroupChange change) {
    AccessControl.AccessRequired newAccessLevel = change.getNewMemberAccess();

    if (newAccessLevel != AccessControl.AccessRequired.UNKNOWN) {
      builder.setAccessControl(AccessControl.newBuilder(builder.getAccessControl())
                                            .setMembersValue(change.getNewMemberAccessValue()));
    }
  }

  protected static void applyModifyAddFromInviteLinkAccessControlAction(DecryptedGroup.Builder builder, DecryptedGroupChange change) {
    AccessControl.AccessRequired newAccessLevel = change.getNewInviteLinkAccess();

    if (newAccessLevel != AccessControl.AccessRequired.UNKNOWN) {
      builder.setAccessControl(AccessControl.newBuilder(builder.getAccessControl())
                                            .setAddFromInviteLink(newAccessLevel));
    }
  }

  private static void applyAddRequestingMembers(DecryptedGroup.Builder builder, List<DecryptedRequestingMember> newRequestingMembers) {
    builder.addAllRequestingMembers(newRequestingMembers);
  }

  private static void applyDeleteRequestingMembers(DecryptedGroup.Builder builder, List<ByteString> deleteRequestingMembersList) {
    for (ByteString removedMember : deleteRequestingMembersList) {
      int index = indexOfUuidInRequestingList(builder.getRequestingMembersList(), removedMember);

      if (index == -1) {
        Log.w(TAG, "Deleted member on change not found in group");
        continue;
      }

      builder.removeRequestingMembers(index);
    }
  }

  private static void applyPromoteRequestingMemberActions(DecryptedGroup.Builder builder, List<DecryptedApproveMember> promoteRequestingMembers) throws NotAbleToApplyGroupV2ChangeException {
    for (DecryptedApproveMember approvedMember : promoteRequestingMembers) {
      int index = indexOfUuidInRequestingList(builder.getRequestingMembersList(), approvedMember.getUuid());

      if (index == -1) {
        Log.w(TAG, "Deleted member on change not found in group");
        continue;
      }

      DecryptedRequestingMember requestingMember = builder.getRequestingMembers(index);
      Member.Role               role             = approvedMember.getRole();

      ensureKnownRole(role);

      builder.removeRequestingMembers(index)
             .addMembers(DecryptedMember.newBuilder()
                                        .setUuid(approvedMember.getUuid())
                                        .setProfileKey(requestingMember.getProfileKey())
                                        .setRole(role));
    }
  }

  private static void applyInviteLinkPassword(DecryptedGroup.Builder builder, DecryptedGroupChange change) {
    if (!change.getNewInviteLinkPassword().isEmpty()) {
      builder.setInviteLinkPassword(change.getNewInviteLinkPassword());
    }
  }

  private static DecryptedMember withNewProfileKey(DecryptedMember member, ByteString profileKey) {
    return DecryptedMember.newBuilder(member)
                          .setProfileKey(profileKey)
                          .build();
  }

  private static Set<ByteString> getMemberUuidSet(List<DecryptedMember> membersList) {
    Set<ByteString> memberUuids = new HashSet<>(membersList.size());

    for (DecryptedMember members : membersList) {
      memberUuids.add(members.getUuid());
    }

    return memberUuids;
  }

    private static Set<ByteString> getPendingMemberCipherTextSet(List<DecryptedPendingMember> pendingMemberList) {
    Set<ByteString> pendingMemberCipherTexts = new HashSet<>(pendingMemberList.size());

    for (DecryptedPendingMember pendingMember : pendingMemberList) {
      pendingMemberCipherTexts.add(pendingMember.getUuidCipherText());
    }

    return pendingMemberCipherTexts;
  }

  private static void removePendingAndRequestingMembersNowInGroup(DecryptedGroup.Builder builder) {
    Set<ByteString> allMembers = membersToUuidByteStringSet(builder.getMembersList());

    for (int i = builder.getPendingMembersCount() - 1; i >= 0; i--) {
      DecryptedPendingMember pendingMember = builder.getPendingMembers(i);
      if (allMembers.contains(pendingMember.getUuid())) {
        builder.removePendingMembers(i);
      }
    }

    for (int i = builder.getRequestingMembersCount() - 1; i >= 0; i--) {
      DecryptedRequestingMember requestingMember = builder.getRequestingMembers(i);
      if (allMembers.contains(requestingMember.getUuid())) {
        builder.removeRequestingMembers(i);
      }
    }
  }

  private static void ensureKnownRole(Member.Role role) throws NotAbleToApplyGroupV2ChangeException {
    if (role != Member.Role.ADMINISTRATOR && role != Member.Role.DEFAULT) {
      throw new NotAbleToApplyGroupV2ChangeException();
    }
  }

  private static int indexOfUuid(List<DecryptedMember> memberList, ByteString uuid) {
    for (int i = 0; i < memberList.size(); i++) {
      if (uuid.equals(memberList.get(i).getUuid())) return i;
    }
    return -1;
  }

  private static int indexOfUuidInRequestingList(List<DecryptedRequestingMember> memberList, ByteString uuid) {
    for (int i = 0; i < memberList.size(); i++) {
      if (uuid.equals(memberList.get(i).getUuid())) return i;
    }
    return -1;
  }

  public static Optional<UUID> findInviter(List<DecryptedPendingMember> pendingMembersList, UUID uuid) {
    return Optional.fromNullable(findPendingByUuid(pendingMembersList, uuid).transform(DecryptedPendingMember::getAddedByUuid)
                                                                            .transform(UuidUtil::fromByteStringOrNull)
                                                                            .orNull());
  }

  public static boolean changeIsEmpty(DecryptedGroupChange change) {
    return change.getModifiedProfileKeysCount()   == 0 && // field 6
           changeIsEmptyExceptForProfileKeyChanges(change);
  }

  public static boolean changeIsEmptyExceptForProfileKeyChanges(DecryptedGroupChange change) {
    return change.getNewMembersCount()               == 0 && // field 3
           change.getDeleteMembersCount()            == 0 && // field 4
           change.getModifyMemberRolesCount()        == 0 && // field 5
           change.getNewPendingMembersCount()        == 0 && // field 7
           change.getDeletePendingMembersCount()     == 0 && // field 8
           change.getPromotePendingMembersCount()    == 0 && // field 9
           !change.hasNewTitle()                          && // field 10
           !change.hasNewAvatar()                         && // field 11
           !change.hasNewTimer()                          && // field 12
           isSet(change.getNewAttributeAccess())          && // field 13
           isSet(change.getNewMemberAccess())             && // field 14
           isSet(change.getNewInviteLinkAccess())         && // field 15
           change.getNewRequestingMembersCount()     == 0 && // field 16
           change.getDeleteRequestingMembersCount()  == 0 && // field 17
           change.getPromoteRequestingMembersCount() == 0 && // field 18
           change.getNewInviteLinkPassword().size()  == 0;   // field 19
  }

  static boolean isSet(AccessControl.AccessRequired newAttributeAccess) {
    return newAttributeAccess == AccessControl.AccessRequired.UNKNOWN;
  }

}

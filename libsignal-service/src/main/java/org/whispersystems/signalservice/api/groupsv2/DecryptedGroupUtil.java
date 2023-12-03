package org.whispersystems.signalservice.api.groupsv2;

import org.signal.libsignal.protocol.logging.Log;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedApproveMember;
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;
import org.signal.storageservice.protos.groups.local.EnabledState;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceIds;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import okio.ByteString;

public final class DecryptedGroupUtil {

  private static final String TAG = DecryptedGroupUtil.class.getSimpleName();

  public static ArrayList<ACI> toAciListWithUnknowns(Collection<DecryptedMember> membersList) {
    ArrayList<ACI> serviceIdList = new ArrayList<>(membersList.size());

    for (DecryptedMember member : membersList) {
      serviceIdList.add(ACI.parseOrUnknown(member.aciBytes));
    }

    return serviceIdList;
  }

  /** Converts the list of members to ACI's, filtering out unknown ACI's. */
  public static ArrayList<ACI> toAciList(Collection<DecryptedMember> membersList) {
    ArrayList<ACI> serviceIdList = new ArrayList<>(membersList.size());

    for (DecryptedMember member : membersList) {
      ACI aci = ACI.parseOrNull(member.aciBytes);

      if (aci != null) {
        serviceIdList.add(aci);
      }
    }

    return serviceIdList;
  }

  public static Set<ByteString> membersToAciByteStringSet(Collection<DecryptedMember> membersList) {
    Set<ByteString> aciList = new HashSet<>(membersList.size());

    for (DecryptedMember member : membersList) {
      aciList.add(member.aciBytes);
    }

    return aciList;
  }

  /**
   * Can return non-decryptable member ACIs as unknown ACIs.
   */
  public static ArrayList<ServiceId> pendingToServiceIdList(Collection<DecryptedPendingMember> membersList) {
    ArrayList<ServiceId> serviceIdList = new ArrayList<>(membersList.size());

    for (DecryptedPendingMember member : membersList) {
      ServiceId serviceId = ServiceId.parseOrNull(member.serviceIdBytes);
      if (serviceId != null) {
        serviceIdList.add(serviceId);
      } else {
        serviceIdList.add(ACI.UNKNOWN);
      }
    }

    return serviceIdList;
  }

  /**
   * Will not return any non-decryptable member ACIs.
   */
  public static ArrayList<ServiceId> removedMembersServiceIdList(DecryptedGroupChange groupChange) {
    List<ByteString>     deletedMembers = groupChange.deleteMembers;
    ArrayList<ServiceId> serviceIdList  = new ArrayList<>(deletedMembers.size());

    for (ByteString member : deletedMembers) {
      ServiceId serviceId = ServiceId.parseOrNull(member);

      if (serviceId != null) {
        serviceIdList.add(serviceId);
      }
    }

    return serviceIdList;
  }

  /**
   * Will not return any non-decryptable member ACIs.
   */
  public static ArrayList<ServiceId> removedPendingMembersServiceIdList(DecryptedGroupChange groupChange) {
    List<DecryptedPendingMemberRemoval> deletedPendingMembers = groupChange.deletePendingMembers;
    ArrayList<ServiceId>                serviceIdList         = new ArrayList<>(deletedPendingMembers.size());

    for (DecryptedPendingMemberRemoval member : deletedPendingMembers) {
      ServiceId serviceId = ServiceId.parseOrNull(member.serviceIdBytes);

      if(serviceId != null) {
        serviceIdList.add(serviceId);
      }
    }

    return serviceIdList;
  }

  /**
   * Will not return any non-decryptable member ACIs.
   */
  public static ArrayList<ServiceId> removedRequestingMembersServiceIdList(DecryptedGroupChange groupChange) {
    List<ByteString>     deleteRequestingMembers = groupChange.deleteRequestingMembers;
    ArrayList<ServiceId> serviceIdList           = new ArrayList<>(deleteRequestingMembers.size());

    for (ByteString member : deleteRequestingMembers) {
      ServiceId serviceId = ServiceId.parseOrNull(member);

      if(serviceId != null) {
        serviceIdList.add(serviceId);
      }
    }

    return serviceIdList;
  }

  public static Set<ServiceId> bannedMembersToServiceIdSet(Collection<DecryptedBannedMember> membersList) {
    Set<ServiceId> serviceIdSet = new HashSet<>(membersList.size());

    for (DecryptedBannedMember member : membersList) {
      ServiceId serviceId = ServiceId.parseOrNull(member.serviceIdBytes);
      if (serviceId != null) {
        serviceIdSet.add(serviceId);
      }
    }

    return serviceIdSet;
  }

  /**
   * The ACI of the member that made the change.
   */
  public static Optional<ServiceId> editorServiceId(DecryptedGroupChange change) {
    return Optional.ofNullable(change != null ? ServiceId.parseOrNull(change.editorServiceIdBytes) : null);
  }

  public static Optional<DecryptedMember> findMemberByAci(Collection<DecryptedMember> members, ACI aci) {
    ByteString aciBytes = aci.toByteString();

    for (DecryptedMember member : members) {
      if (aciBytes.equals(member.aciBytes)) {
        return Optional.of(member);
      }
    }

    return Optional.empty();
  }

  public static Optional<DecryptedPendingMember> findPendingByServiceId(Collection<DecryptedPendingMember> members, ServiceId serviceId) {
    ByteString serviceIdBinary = serviceId.toByteString();

    for (DecryptedPendingMember member : members) {
      if (serviceIdBinary.equals(member.serviceIdBytes)) {
        return Optional.of(member);
      }
    }

    return Optional.empty();
  }

  public static Optional<DecryptedPendingMember> findPendingByServiceIds(Collection<DecryptedPendingMember> members, ServiceIds serviceIds) {
    for (DecryptedPendingMember member : members) {
      if (serviceIds.matches(member.serviceIdBytes)) {
        return Optional.of(member);
      }
    }

    return Optional.empty();
  }

  private static int findPendingIndexByServiceIdCipherText(List<DecryptedPendingMember> members, ByteString cipherText) {
    for (int i = 0; i < members.size(); i++) {
      DecryptedPendingMember member = members.get(i);
      if (cipherText.equals(member.serviceIdCipherText)) {
        return i;
      }
    }

    return -1;
  }

  private static int findPendingIndexByServiceId(List<DecryptedPendingMember> members, ByteString serviceIdBinary) {
    for (int i = 0; i < members.size(); i++) {
      DecryptedPendingMember member = members.get(i);
      if (serviceIdBinary.equals(member.serviceIdBytes)) {
        return i;
      }
    }

    return -1;
  }

  public static Optional<DecryptedRequestingMember> findRequestingByAci(Collection<DecryptedRequestingMember> members, ACI aci) {
    ByteString aciBytes = aci.toByteString();

    for (DecryptedRequestingMember member : members) {
      if (aciBytes.equals(member.aciBytes)) {
        return Optional.of(member);
      }
    }

    return Optional.empty();
  }

  public static Optional<DecryptedRequestingMember> findRequestingByServiceIds(Collection<DecryptedRequestingMember> members, ServiceIds serviceIds) {
    for (DecryptedRequestingMember member : members) {
      if (serviceIds.matches(member.aciBytes)) {
        return Optional.of(member);
      }
    }

    return Optional.empty();
  }

  public static boolean isPendingOrRequesting(DecryptedGroup group, ServiceIds serviceIds) {
    return findPendingByServiceIds(group.pendingMembers, serviceIds).isPresent() ||
           findRequestingByServiceIds(group.requestingMembers, serviceIds).isPresent();
  }

  public static boolean isRequesting(DecryptedGroup group, ACI aci) {
    return findRequestingByAci(group.requestingMembers, aci).isPresent();
  }

  /**
   * Removes the aci from the full members of a group.
   * <p>
   * Generally not expected to have to do this, just in the case of leaving a group where you cannot
   * get the new group state as you are not in the group any longer.
   */
  public static DecryptedGroup removeMember(DecryptedGroup group, ACI aci, int revision) {
    DecryptedGroup.Builder     builder          = group.newBuilder();
    ByteString                 aciByteString    = aci.toByteString();
    boolean                    removed          = false;
    ArrayList<DecryptedMember> decryptedMembers = new ArrayList<>(builder.members);
    Iterator<DecryptedMember>  membersList      = decryptedMembers.iterator();

    while (membersList.hasNext()) {
      if (aciByteString.equals(membersList.next().aciBytes)) {
        membersList.remove();
        removed = true;
      }
    }

    if (removed) {
      return builder.members(decryptedMembers)
                    .revision(revision)
                    .build();
    } else {
      return group;
    }
  }

  public static DecryptedGroup apply(DecryptedGroup group, DecryptedGroupChange change)
      throws NotAbleToApplyGroupV2ChangeException
  {
    if (change.revision != group.revision + 1) {
      throw new NotAbleToApplyGroupV2ChangeException();
    }

    return applyWithoutRevisionCheck(group, change);
  }

  public static DecryptedGroup applyWithoutRevisionCheck(DecryptedGroup group, DecryptedGroupChange change)
      throws NotAbleToApplyGroupV2ChangeException
  {
    DecryptedGroup.Builder builder = group.newBuilder()
                                          .revision(change.revision);

    applyAddMemberAction(builder, change.newMembers);

    applyDeleteMemberActions(builder, change.deleteMembers);

    applyModifyMemberRoleActions(builder, change.modifyMemberRoles);

    applyModifyMemberProfileKeyActions(builder, change.modifiedProfileKeys);

    applyAddPendingMemberActions(builder, change.newPendingMembers);

    applyDeletePendingMemberActions(builder, change.deletePendingMembers);

    applyPromotePendingMemberActions(builder, change.promotePendingMembers);

    applyModifyTitleAction(builder, change);

    applyModifyDescriptionAction(builder, change);

    applyModifyIsAnnouncementGroupAction(builder, change);

    applyModifyAvatarAction(builder, change);

    applyModifyDisappearingMessagesTimerAction(builder, change);

    applyModifyAttributesAccessControlAction(builder, change);

    applyModifyMembersAccessControlAction(builder, change);

    applyModifyAddFromInviteLinkAccessControlAction(builder, change);

    applyAddRequestingMembers(builder, change.newRequestingMembers);

    applyDeleteRequestingMembers(builder, change.deleteRequestingMembers);

    applyPromoteRequestingMemberActions(builder, change.promoteRequestingMembers);

    applyInviteLinkPassword(builder, change);

    applyAddBannedMembersActions(builder, change.newBannedMembers);

    applyDeleteBannedMembersActions(builder, change.deleteBannedMembers);

    applyPromotePendingPniAciMemberActions(builder, change.promotePendingPniAciMembers);

    return builder.build();
  }

  private static void applyAddMemberAction(DecryptedGroup.Builder builder, List<DecryptedMember> newMembersList) {
    if (newMembersList.isEmpty()) return;

    LinkedHashMap<ByteString, DecryptedMember> members = new LinkedHashMap<>();

    for (DecryptedMember member : builder.members) {
      members.put(member.aciBytes, member);
    }

    for (DecryptedMember member : newMembersList) {
      members.put(member.aciBytes, member);
    }

    builder.members(new ArrayList<>(members.values()));

    removePendingAndRequestingMembersNowInGroup(builder);
  }

  private static void applyDeleteMemberActions(DecryptedGroup.Builder builder, List<ByteString> deleteMembersList) {
    List<DecryptedMember> members = new ArrayList<>(builder.members);

    for (ByteString removedMember : deleteMembersList) {
      int index = indexOfAci(members, removedMember);

      if (index == -1) {
        Log.w(TAG, "Deleted member on change not found in group");
        continue;
      }

      members.remove(index);
    }

    builder.members(members);
  }

  private static void applyModifyMemberRoleActions(DecryptedGroup.Builder builder, List<DecryptedModifyMemberRole> modifyMemberRolesList) throws NotAbleToApplyGroupV2ChangeException {
    List<DecryptedMember> members = new ArrayList<>(builder.members);

    for (DecryptedModifyMemberRole modifyMemberRole : modifyMemberRolesList) {
      int index = indexOfAci(members, modifyMemberRole.aciBytes);

      if (index == -1) {
        throw new NotAbleToApplyGroupV2ChangeException();
      }

      Member.Role role = modifyMemberRole.role;

      ensureKnownRole(role);

      members.set(index, members.get(index).newBuilder().role(role).build());
    }

    builder.members(members);
  }

  private static void applyModifyMemberProfileKeyActions(DecryptedGroup.Builder builder, List<DecryptedMember> modifiedProfileKeysList) throws NotAbleToApplyGroupV2ChangeException {
    List<DecryptedMember> members = new ArrayList<>(builder.members);

    for (DecryptedMember modifyProfileKey : modifiedProfileKeysList) {
      int index = indexOfAci(members, modifyProfileKey.aciBytes);

      if (index == -1) {
        throw new NotAbleToApplyGroupV2ChangeException();
      }

      members.set(index, withNewProfileKey(members.get(index), modifyProfileKey.profileKey));
    }

    builder.members(members);
  }

  private static void applyAddPendingMemberActions(DecryptedGroup.Builder builder, List<DecryptedPendingMember> newPendingMembersList) throws NotAbleToApplyGroupV2ChangeException {
    Set<ByteString>              fullMemberSet            = getMemberAciSet(builder.members);
    Set<ByteString>              pendingMemberCipherTexts = getPendingMemberCipherTextSet(builder.pendingMembers);
    List<DecryptedPendingMember> pendingMembers           = new ArrayList<>(builder.pendingMembers);

    for (DecryptedPendingMember pendingMember : newPendingMembersList) {
      if (fullMemberSet.contains(pendingMember.serviceIdBytes)) {
        throw new NotAbleToApplyGroupV2ChangeException();
      }

      if (!pendingMemberCipherTexts.contains(pendingMember.serviceIdCipherText)) {
        pendingMembers.add(pendingMember);
      }
    }

    builder.pendingMembers(pendingMembers);
  }

  private static void applyDeletePendingMemberActions(DecryptedGroup.Builder builder, List<DecryptedPendingMemberRemoval> deletePendingMembersList) {
    List<DecryptedPendingMember> pendingMembers = new ArrayList<>(builder.pendingMembers);

    for (DecryptedPendingMemberRemoval removedMember : deletePendingMembersList) {
      int index = findPendingIndexByServiceIdCipherText(pendingMembers, removedMember.serviceIdCipherText);

      if (index == -1) {
        Log.w(TAG, "Deleted pending member on change not found in group");
        continue;
      }

      pendingMembers.remove(index);
    }

    builder.pendingMembers(pendingMembers);
  }

  private static void applyPromotePendingMemberActions(DecryptedGroup.Builder builder, List<DecryptedMember> promotePendingMembersList) throws NotAbleToApplyGroupV2ChangeException {
    List<DecryptedMember>        members        = new ArrayList<>(builder.members);
    List<DecryptedPendingMember> pendingMembers = new ArrayList<>(builder.pendingMembers);

    for (DecryptedMember newMember : promotePendingMembersList) {
      int index = findPendingIndexByServiceId(pendingMembers, newMember.aciBytes);

      if (index == -1) {
        throw new NotAbleToApplyGroupV2ChangeException();
      }

      pendingMembers.remove(index);
      members.add(newMember);
    }

    builder.pendingMembers(pendingMembers);
    builder.members(members);
  }

  private static void applyModifyTitleAction(DecryptedGroup.Builder builder, DecryptedGroupChange change) {
    if (change.newTitle != null) {
      builder.title(change.newTitle.value_);
    }
  }

  private static void applyModifyDescriptionAction(DecryptedGroup.Builder builder, DecryptedGroupChange change) {
    if (change.newDescription != null) {
      builder.description(change.newDescription.value_);
    }
  }

  private static void applyModifyIsAnnouncementGroupAction(DecryptedGroup.Builder builder, DecryptedGroupChange change) {
    if (change.newIsAnnouncementGroup != EnabledState.UNKNOWN) {
      builder.isAnnouncementGroup(change.newIsAnnouncementGroup);
    }
  }

  private static void applyModifyAvatarAction(DecryptedGroup.Builder builder, DecryptedGroupChange change) {
    if (change.newAvatar != null) {
      builder.avatar(change.newAvatar.value_);
    }
  }

  private static void applyModifyDisappearingMessagesTimerAction(DecryptedGroup.Builder builder, DecryptedGroupChange change) {
    if (change.newTimer != null) {
      builder.disappearingMessagesTimer(change.newTimer);
    }
  }

  private static void applyModifyAttributesAccessControlAction(DecryptedGroup.Builder builder, DecryptedGroupChange change) {
    AccessControl.AccessRequired newAccessLevel = change.newAttributeAccess;

    if (newAccessLevel != AccessControl.AccessRequired.UNKNOWN) {
      AccessControl.Builder accessControlBuilder = builder.accessControl != null ? builder.accessControl.newBuilder() : new AccessControl.Builder();
      builder.accessControl(accessControlBuilder.attributes(change.newAttributeAccess).build());
    }
  }

  private static void applyModifyMembersAccessControlAction(DecryptedGroup.Builder builder, DecryptedGroupChange change) {
    AccessControl.AccessRequired newAccessLevel = change.newMemberAccess;

    if (newAccessLevel != AccessControl.AccessRequired.UNKNOWN) {
      AccessControl.Builder accessControlBuilder = builder.accessControl != null ? builder.accessControl.newBuilder() : new AccessControl.Builder();
      builder.accessControl(accessControlBuilder.members(change.newMemberAccess).build());
    }
  }

  private static void applyModifyAddFromInviteLinkAccessControlAction(DecryptedGroup.Builder builder, DecryptedGroupChange change) {
    AccessControl.AccessRequired newAccessLevel = change.newInviteLinkAccess;

    if (newAccessLevel != AccessControl.AccessRequired.UNKNOWN) {
      AccessControl.Builder accessControlBuilder = builder.accessControl != null ? builder.accessControl.newBuilder() : new AccessControl.Builder();
      builder.accessControl(accessControlBuilder.addFromInviteLink(newAccessLevel).build());
    }
  }

  private static void applyAddRequestingMembers(DecryptedGroup.Builder builder, List<DecryptedRequestingMember> newRequestingMembers) {
    List<DecryptedRequestingMember> requestingMembers = new ArrayList<>(builder.requestingMembers);
    requestingMembers.addAll(newRequestingMembers);
    builder.requestingMembers(requestingMembers);
  }

  private static void applyDeleteRequestingMembers(DecryptedGroup.Builder builder, List<ByteString> deleteRequestingMembersList) {
    List<DecryptedRequestingMember> requestingMembers = new ArrayList<>(builder.requestingMembers);
    for (ByteString removedMember : deleteRequestingMembersList) {
      int index = indexOfAciInRequestingList(requestingMembers, removedMember);

      if (index == -1) {
        Log.w(TAG, "Deleted member on change not found in group");
        continue;
      }

      requestingMembers.remove(index);
    }
    builder.requestingMembers(requestingMembers);
  }

  private static void applyPromoteRequestingMemberActions(DecryptedGroup.Builder builder, List<DecryptedApproveMember> promoteRequestingMembers) throws NotAbleToApplyGroupV2ChangeException {
    List<DecryptedMember>           members           = new ArrayList<>(builder.members);
    List<DecryptedRequestingMember> requestingMembers = new ArrayList<>(builder.requestingMembers);

    for (DecryptedApproveMember approvedMember : promoteRequestingMembers) {
      int index = indexOfAciInRequestingList(requestingMembers, approvedMember.aciBytes);

      if (index == -1) {
        Log.w(TAG, "Deleted member on change not found in group");
        continue;
      }

      DecryptedRequestingMember requestingMember = requestingMembers.get(index);
      Member.Role               role             = approvedMember.role;

      ensureKnownRole(role);

      requestingMembers.remove(index);
      members.add(new DecryptedMember.Builder()
                                     .aciBytes(approvedMember.aciBytes)
                                     .profileKey(requestingMember.profileKey)
                                     .role(role)
                                     .build());
    }

    builder.members(members);
    builder.requestingMembers(requestingMembers);
  }

  private static void applyInviteLinkPassword(DecryptedGroup.Builder builder, DecryptedGroupChange change) {
    if (change.newInviteLinkPassword.size() > 0) {
      builder.inviteLinkPassword(change.newInviteLinkPassword);
    }
  }

  private static void applyAddBannedMembersActions(DecryptedGroup.Builder builder, List<DecryptedBannedMember> newBannedMembersList) {
    Set<ByteString>             bannedMemberServiceIdSet = getBannedMemberServiceIdSet(builder.bannedMembers);
    List<DecryptedBannedMember> bannedMembers            = new ArrayList<>(builder.bannedMembers);

    for (DecryptedBannedMember member : newBannedMembersList) {
      if (bannedMemberServiceIdSet.contains(member.serviceIdBytes)) {
        Log.w(TAG, "Banned member already in banned list");
      } else {
        bannedMembers.add(member);
      }
    }

    builder.bannedMembers(bannedMembers);
  }

  private static void applyDeleteBannedMembersActions(DecryptedGroup.Builder builder, List<DecryptedBannedMember> deleteMembersList) {
    List<DecryptedBannedMember> bannedMembers = new ArrayList<>(builder.bannedMembers);

    for (DecryptedBannedMember removedMember : deleteMembersList) {
      int index = indexOfServiceIdInBannedMemberList(bannedMembers, removedMember.serviceIdBytes);

      if (index == -1) {
        Log.w(TAG, "Deleted banned member on change not found in banned list");
        continue;
      }

      bannedMembers.remove(index);
    }

    builder.bannedMembers(bannedMembers);
  }

  private static void applyPromotePendingPniAciMemberActions(DecryptedGroup.Builder builder, List<DecryptedMember> promotePendingPniAciMembersList) throws NotAbleToApplyGroupV2ChangeException {
    List<DecryptedMember>        members        = new ArrayList<>(builder.members);
    List<DecryptedPendingMember> pendingMembers = new ArrayList<>(builder.pendingMembers);

    for (DecryptedMember newMember : promotePendingPniAciMembersList) {
      int index = findPendingIndexByServiceId(pendingMembers, newMember.pniBytes);

      if (index == -1) {
        throw new NotAbleToApplyGroupV2ChangeException();
      }

      pendingMembers.remove(index);
      members.add(newMember);
    }

    builder.members(members);
    builder.pendingMembers(pendingMembers);
  }

  private static DecryptedMember withNewProfileKey(DecryptedMember member, ByteString profileKey) {
    return member.newBuilder()
                 .profileKey(profileKey)
                 .build();
  }

  private static Set<ByteString> getMemberAciSet(List<DecryptedMember> membersList) {
    Set<ByteString> memberAcis = new HashSet<>(membersList.size());

    for (DecryptedMember members : membersList) {
      memberAcis.add(members.aciBytes);
    }

    return memberAcis;
  }

    private static Set<ByteString> getPendingMemberCipherTextSet(List<DecryptedPendingMember> pendingMemberList) {
    Set<ByteString> pendingMemberCipherTexts = new HashSet<>(pendingMemberList.size());

    for (DecryptedPendingMember pendingMember : pendingMemberList) {
      pendingMemberCipherTexts.add(pendingMember.serviceIdCipherText);
    }

    return pendingMemberCipherTexts;
  }

  private static Set<ByteString> getBannedMemberServiceIdSet(List<DecryptedBannedMember> bannedMemberList) {
    Set<ByteString> memberServiceIds = new HashSet<>(bannedMemberList.size());

    for (DecryptedBannedMember member : bannedMemberList) {
      memberServiceIds.add(member.serviceIdBytes);
    }

    return memberServiceIds;
  }

  private static void removePendingAndRequestingMembersNowInGroup(DecryptedGroup.Builder builder) {
    Set<ByteString> allMembers = membersToAciByteStringSet(builder.members);

    List<DecryptedPendingMember> pendingMembers = new ArrayList<>(builder.pendingMembers);
    for (int i = pendingMembers.size() - 1; i >= 0; i--) {
      DecryptedPendingMember pendingMember = pendingMembers.get(i);
      if (allMembers.contains(pendingMember.serviceIdBytes)) {
        pendingMembers.remove(i);
      }
    }
    builder.pendingMembers(pendingMembers);

    List<DecryptedRequestingMember> requestingMembers = new ArrayList<>(builder.requestingMembers);
    for (int i = requestingMembers.size() - 1; i >= 0; i--) {
      DecryptedRequestingMember requestingMember = requestingMembers.get(i);
      if (allMembers.contains(requestingMember.aciBytes)) {
        requestingMembers.remove(i);
      }
    }
    builder.requestingMembers(requestingMembers);
  }

  private static void ensureKnownRole(Member.Role role) throws NotAbleToApplyGroupV2ChangeException {
    if (role != Member.Role.ADMINISTRATOR && role != Member.Role.DEFAULT) {
      throw new NotAbleToApplyGroupV2ChangeException();
    }
  }

  private static int indexOfAci(List<DecryptedMember> memberList, ByteString aci) {
    for (int i = 0; i < memberList.size(); i++) {
      if (aci.equals(memberList.get(i).aciBytes)) {
        return i;
      }
    }
    return -1;
  }

  private static int indexOfAciInRequestingList(List<DecryptedRequestingMember> memberList, ByteString aci) {
    for (int i = 0; i < memberList.size(); i++) {
      if (aci.equals(memberList.get(i).aciBytes)) {
        return i;
      }
    }
    return -1;
  }

  private static int indexOfServiceIdInBannedMemberList(List<DecryptedBannedMember> memberList, ByteString serviceIdBinary) {
    for (int i = 0; i < memberList.size(); i++) {
      if (serviceIdBinary.equals(memberList.get(i).serviceIdBytes)) {
        return i;
      }
    }
    return -1;
  }

  public static boolean changeIsEmpty(DecryptedGroupChange change) {
    return change.modifiedProfileKeys.size() == 0 && // field 6
           changeIsEmptyExceptForProfileKeyChanges(change);
  }

  /*
   * When updating this, update {@link #changeIsEmptyExceptForBanChangesAndOptionalProfileKeyChanges(DecryptedGroupChange)}
   */
  public static boolean changeIsEmptyExceptForProfileKeyChanges(DecryptedGroupChange change) {
    return change.newMembers.size() == 0 &&                // field 3
           change.deleteMembers.size() == 0 &&             // field 4
           change.modifyMemberRoles.size() == 0 &&         // field 5
           change.newPendingMembers.size() == 0 &&         // field 7
           change.deletePendingMembers.size() == 0 &&      // field 8
           change.promotePendingMembers.size() == 0 &&     // field 9
           change.newTitle == null &&                      // field 10
           change.newAvatar == null &&                     // field 11
           change.newTimer == null &&                      // field 12
           isEmpty(change.newAttributeAccess) &&           // field 13
           isEmpty(change.newMemberAccess) &&              // field 14
           isEmpty(change.newInviteLinkAccess) &&          // field 15
           change.newRequestingMembers.size() == 0 &&      // field 16
           change.deleteRequestingMembers.size() == 0 &&   // field 17
           change.promoteRequestingMembers.size() == 0 &&  // field 18
           change.newInviteLinkPassword.size() == 0 &&     // field 19
           change.newDescription == null &&                // field 20
           isEmpty(change.newIsAnnouncementGroup) &&       // field 21
           change.newBannedMembers.size() == 0 &&          // field 22
           change.deleteBannedMembers.size() == 0 &&       // field 23
           change.promotePendingPniAciMembers.size() == 0; // field 24
  }

  public static boolean changeIsEmptyExceptForBanChangesAndOptionalProfileKeyChanges(DecryptedGroupChange change) {
    return (change.newBannedMembers.size() != 0 || change.deleteBannedMembers.size() != 0) &&
           change.newMembers.size() == 0 &&                // field 3
           change.deleteMembers.size() == 0 &&             // field 4
           change.modifyMemberRoles.size() == 0 &&         // field 5
           change.newPendingMembers.size() == 0 &&         // field 7
           change.deletePendingMembers.size() == 0 &&      // field 8
           change.promotePendingMembers.size() == 0 &&     // field 9
           change.newTitle == null &&                      // field 10
           change.newAvatar == null &&                     // field 11
           change.newTimer == null &&                      // field 12
           isEmpty(change.newAttributeAccess) &&           // field 13
           isEmpty(change.newMemberAccess) &&              // field 14
           isEmpty(change.newInviteLinkAccess) &&          // field 15
           change.newRequestingMembers.size() == 0 &&      // field 16
           change.deleteRequestingMembers.size() == 0 &&   // field 17
           change.promoteRequestingMembers.size() == 0 &&  // field 18
           change.newInviteLinkPassword.size() == 0 &&     // field 19
           change.newDescription == null &&                // field 20
           isEmpty(change.newIsAnnouncementGroup) &&       // field 21
           change.promotePendingPniAciMembers.size() == 0; // field 24
  }

  static boolean isEmpty(AccessControl.AccessRequired newAttributeAccess) {
    return newAttributeAccess == AccessControl.AccessRequired.UNKNOWN;
  }

  static boolean isEmpty(EnabledState enabledState) {
    return enabledState == EnabledState.UNKNOWN;
  }

  public static boolean changeIsSilent(DecryptedGroupChange plainGroupChange) {
    return changeIsEmptyExceptForProfileKeyChanges(plainGroupChange) || changeIsEmptyExceptForBanChangesAndOptionalProfileKeyChanges(plainGroupChange);
  }
}

package org.whispersystems.signalservice.api.groupsv2;

import org.signal.storageservice.protos.groups.local.DecryptedApproveMember;
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;
import org.signal.storageservice.protos.groups.local.DecryptedString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import okio.ByteString;

public final class GroupChangeReconstruct {

  /**
   * Given a {@param fromState} and a {@param toState} creates a {@link DecryptedGroupChange} that would take the {@param fromState} to the {@param toState}.
   */
  public static DecryptedGroupChange reconstructGroupChange(DecryptedGroup fromState, DecryptedGroup toState) {
    DecryptedGroupChange.Builder builder = new DecryptedGroupChange.Builder()
                                                                   .revision(toState.revision);

    if (!fromState.title.equals(toState.title)) {
      builder.newTitle(new DecryptedString.Builder().value_(toState.title).build());
    }

    if (!fromState.description.equals(toState.description)) {
      builder.newDescription(new DecryptedString.Builder().value_(toState.description).build());
    }

    if (!fromState.isAnnouncementGroup.equals(toState.isAnnouncementGroup)) {
      builder.newIsAnnouncementGroup(toState.isAnnouncementGroup);
    }

    if (!fromState.avatar.equals(toState.avatar)) {
      builder.newAvatar(new DecryptedString.Builder().value_(toState.avatar).build());
    }

    if (!Objects.equals(fromState.disappearingMessagesTimer, toState.disappearingMessagesTimer)) {
      builder.newTimer(toState.disappearingMessagesTimer);
    }

    if (fromState.accessControl == null || (toState.accessControl != null && !fromState.accessControl.attributes.equals(toState.accessControl.attributes))) {
      if (toState.accessControl != null) {
        builder.newAttributeAccess(toState.accessControl.attributes);
      }
    }

    if (fromState.accessControl == null || (toState.accessControl != null && !fromState.accessControl.members.equals(toState.accessControl.members))) {
      if (toState.accessControl != null) {
        builder.newMemberAccess(toState.accessControl.members);
      }
    }

    Set<ByteString> fromStateMemberAcis = membersToSetOfAcis(fromState.members);
    Set<ByteString> toStateMemberAcis   = membersToSetOfAcis(toState.members);

    Set<ByteString> pendingMembersListA = pendingMembersToSetOfServiceIds(fromState.pendingMembers);
    Set<ByteString> pendingMembersListB = pendingMembersToSetOfServiceIds(toState.pendingMembers);

    Set<ByteString> requestingMembersListA = requestingMembersToSetOfAcis(fromState.requestingMembers);
    Set<ByteString> requestingMembersListB = requestingMembersToSetOfAcis(toState.requestingMembers);

    Set<ByteString> bannedMembersListA = bannedMembersToSetOfServiceIds(fromState.bannedMembers);
    Set<ByteString> bannedMembersListB = bannedMembersToSetOfServiceIds(toState.bannedMembers);

    Set<ByteString> removedPendingMemberServiceIds = subtract(pendingMembersListA, pendingMembersListB);
    Set<ByteString> removedRequestingMemberAcis    = subtract(requestingMembersListA, requestingMembersListB);
    Set<ByteString> newPendingMemberServiceIds     = subtract(pendingMembersListB, pendingMembersListA);
    Set<ByteString> newRequestingMemberAcis        = subtract(requestingMembersListB, requestingMembersListA);
    Set<ByteString> removedMemberAcis              = subtract(fromStateMemberAcis, toStateMemberAcis);
    Set<ByteString> newMemberAcis                  = subtract(toStateMemberAcis, fromStateMemberAcis);
    Set<ByteString> removedBannedMemberServiceIds  = subtract(bannedMembersListA, bannedMembersListB);
    Set<ByteString> newBannedMemberServiceIds      = subtract(bannedMembersListB, bannedMembersListA);

    Set<ByteString>                addedByInvitationAcis         = intersect(newMemberAcis, removedPendingMemberServiceIds);
    Set<ByteString>                addedByRequestApprovalAcis    = intersect(newMemberAcis, removedRequestingMemberAcis);
    Set<DecryptedMember>           addedMembersByInvitation      = intersectByAci(toState.members, addedByInvitationAcis);
    Set<DecryptedMember>           addedMembersByRequestApproval = intersectByAci(toState.members, addedByRequestApprovalAcis);
    Set<DecryptedMember>           addedMembers                  = intersectByAci(toState.members, subtract(newMemberAcis, addedByInvitationAcis, addedByRequestApprovalAcis));
    Set<DecryptedPendingMember>    uninvitedMembers              = intersectPendingByServiceId(fromState.pendingMembers, subtract(removedPendingMemberServiceIds, addedByInvitationAcis));
    Set<DecryptedRequestingMember> rejectedRequestMembers        = intersectRequestingByAci(fromState.requestingMembers, subtract(removedRequestingMemberAcis, addedByRequestApprovalAcis));


    builder.deleteMembers(intersectByAci(fromState.members, removedMemberAcis).stream()
                                                                              .map(m -> m.aciBytes)
                                                                              .collect(Collectors.toList()));

    builder.newMembers(new ArrayList<>(addedMembers));

    builder.promotePendingMembers(new ArrayList<>(addedMembersByInvitation));

    builder.deletePendingMembers(uninvitedMembers.stream()
                                                 .map(uninvitedMember -> new DecryptedPendingMemberRemoval.Builder()
                                                                                                          .serviceIdBytes(uninvitedMember.serviceIdBytes)
                                                                                                          .serviceIdCipherText(uninvitedMember.serviceIdCipherText)
                                                                                                          .build())
                                                 .collect(Collectors.toList()));

    builder.newPendingMembers(new ArrayList<>(intersectPendingByServiceId(toState.pendingMembers, newPendingMemberServiceIds)));

    Set<ByteString>                        consistentMemberAcis      = intersect(fromStateMemberAcis, toStateMemberAcis);
    Set<DecryptedMember>                   changedMembers            = intersectByAci(subtract(toState.members, fromState.members), consistentMemberAcis);
    Map<ByteString, DecryptedMember>       membersAciMap             = mapByAci(fromState.members);
    Map<ByteString, DecryptedBannedMember> bannedMembersServiceIdMap = bannedServiceIdMap(toState.bannedMembers);

    List<DecryptedModifyMemberRole> modifiedMemberRoles = new ArrayList<>(changedMembers.size());
    List<DecryptedMember>           modifiedProfileKeys = new ArrayList<>(changedMembers.size());
    for (DecryptedMember newState : changedMembers) {
      DecryptedMember oldState = membersAciMap.get(newState.aciBytes);
      if (oldState.role != newState.role) {
        modifiedMemberRoles.add(new DecryptedModifyMemberRole.Builder()
                                                             .aciBytes(newState.aciBytes)
                                                             .role(newState.role)
                                                             .build());
      }

      if (!oldState.profileKey.equals(newState.profileKey)) {
        modifiedProfileKeys.add(newState);
      }
    }
    builder.modifyMemberRoles(modifiedMemberRoles);
    builder.modifiedProfileKeys(modifiedProfileKeys);

    if (fromState.accessControl == null || (toState.accessControl != null && !fromState.accessControl.addFromInviteLink.equals(toState.accessControl.addFromInviteLink))) {
      if (toState.accessControl != null) {
        builder.newInviteLinkAccess(toState.accessControl.addFromInviteLink);
      }
    }

    builder.newRequestingMembers(new ArrayList<>(intersectRequestingByAci(toState.requestingMembers, newRequestingMemberAcis)));

    builder.deleteRequestingMembers(rejectedRequestMembers.stream().map(requestingMember -> requestingMember.aciBytes).collect(Collectors.toList()));

    builder.promoteRequestingMembers(addedMembersByRequestApproval.stream()
                                                                  .map(member -> new DecryptedApproveMember.Builder()
                                                                                                           .aciBytes(member.aciBytes)
                                                                                                           .role(member.role)
                                                                                                           .build())
                                                                  .collect(Collectors.toList()));

    if (!fromState.inviteLinkPassword.equals(toState.inviteLinkPassword)) {
      builder.newInviteLinkPassword(toState.inviteLinkPassword);
    }

    builder.deleteBannedMembers(removedBannedMemberServiceIds.stream().map(serviceIdBinary -> new DecryptedBannedMember.Builder().serviceIdBytes(serviceIdBinary).build()).collect(Collectors.toList()));

    builder.newBannedMembers(newBannedMemberServiceIds.stream()
                                                      .map(serviceIdBinary -> {
                                                        DecryptedBannedMember.Builder newBannedBuilder = new DecryptedBannedMember.Builder().serviceIdBytes(serviceIdBinary);
                                                        DecryptedBannedMember         bannedMember     = bannedMembersServiceIdMap.get(serviceIdBinary);
                                                        if (bannedMember != null) {
                                                          newBannedBuilder.timestamp(bannedMember.timestamp);
                                                        }

                                                        return newBannedBuilder.build();
                                                      })
                                                      .collect(Collectors.toList()));

    return builder.build();
  }

  private static Map<ByteString, DecryptedMember> mapByAci(List<DecryptedMember> membersList) {
    Map<ByteString, DecryptedMember> map = new LinkedHashMap<>(membersList.size());
    for (DecryptedMember member : membersList) {
      map.put(member.aciBytes, member);
    }
    return map;
  }

  private static Map<ByteString, DecryptedBannedMember> bannedServiceIdMap(List<DecryptedBannedMember> membersList) {
    Map<ByteString, DecryptedBannedMember> map = new LinkedHashMap<>(membersList.size());
    for (DecryptedBannedMember member : membersList) {
      map.put(member.serviceIdBytes, member);
    }
    return map;
  }

  private static Set<DecryptedMember> intersectByAci(Collection<DecryptedMember> members, Set<ByteString> acis) {
    Set<DecryptedMember> result = new LinkedHashSet<>(members.size());
    for (DecryptedMember member : members) {
      if (acis.contains(member.aciBytes))
        result.add(member);
    }
    return result;
  }

  private static Set<DecryptedPendingMember> intersectPendingByServiceId(Collection<DecryptedPendingMember> members, Set<ByteString> serviceIds) {
    Set<DecryptedPendingMember> result = new LinkedHashSet<>(members.size());
    for (DecryptedPendingMember member : members) {
      if (serviceIds.contains(member.serviceIdBytes))
        result.add(member);
    }
    return result;
  }

  private static Set<DecryptedRequestingMember> intersectRequestingByAci(Collection<DecryptedRequestingMember> members, Set<ByteString> acis) {
    Set<DecryptedRequestingMember> result = new LinkedHashSet<>(members.size());
    for (DecryptedRequestingMember member : members) {
      if (acis.contains(member.aciBytes))
        result.add(member);
    }
    return result;
  }

  private static Set<ByteString> pendingMembersToSetOfServiceIds(Collection<DecryptedPendingMember> pendingMembers) {
    Set<ByteString> serviceIds = new LinkedHashSet<>(pendingMembers.size());
    for (DecryptedPendingMember pendingMember : pendingMembers) {
      serviceIds.add(pendingMember.serviceIdBytes);
    }
    return serviceIds;
  }

  private static Set<ByteString> requestingMembersToSetOfAcis(Collection<DecryptedRequestingMember> requestingMembers) {
    Set<ByteString> acis = new LinkedHashSet<>(requestingMembers.size());
    for (DecryptedRequestingMember requestingMember : requestingMembers) {
      acis.add(requestingMember.aciBytes);
    }
    return acis;
  }

  private static Set<ByteString> membersToSetOfAcis(Collection<DecryptedMember> members) {
    Set<ByteString> acis = new LinkedHashSet<>(members.size());
    for (DecryptedMember member : members) {
      acis.add(member.aciBytes);
    }
    return acis;
  }

  private static Set<ByteString> bannedMembersToSetOfServiceIds(Collection<DecryptedBannedMember> bannedMembers) {
    Set<ByteString> serviceIds = new LinkedHashSet<>(bannedMembers.size());
    for (DecryptedBannedMember bannedMember : bannedMembers) {
      serviceIds.add(bannedMember.serviceIdBytes);
    }
    return serviceIds;
  }

  private static <T> Set<T> subtract(Collection<T> a, Collection<T> b) {
    Set<T> result = new LinkedHashSet<>(a);
    result.removeAll(b);
    return result;
  }

  private static <T> Set<T> subtract(Collection<T> a, Collection<T> b, Collection<T> c) {
    Set<T> result = new LinkedHashSet<>(a);
    result.removeAll(b);
    result.removeAll(c);
    return result;
  }

  private static <T> Set<T> intersect(Collection<T> a, Collection<T> b) {
    Set<T> result = new LinkedHashSet<>(a);
    result.retainAll(b);
    return result;
  }
}

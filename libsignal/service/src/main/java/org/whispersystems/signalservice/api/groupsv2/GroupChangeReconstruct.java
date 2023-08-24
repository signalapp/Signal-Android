package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GroupChangeReconstruct {

  /**
   * Given a {@param fromState} and a {@param toState} creates a {@link DecryptedGroupChange} that would take the {@param fromState} to the {@param toState}.
   */
  public static DecryptedGroupChange reconstructGroupChange(DecryptedGroup fromState, DecryptedGroup toState) {
    DecryptedGroupChange.Builder builder = DecryptedGroupChange.newBuilder()
                                                               .setRevision(toState.getRevision());

    if (!fromState.getTitle().equals(toState.getTitle())) {
      builder.setNewTitle(DecryptedString.newBuilder().setValue(toState.getTitle()));
    }

    if (!fromState.getDescription().equals(toState.getDescription())) {
      builder.setNewDescription(DecryptedString.newBuilder().setValue(toState.getDescription()));
    }

    if (!fromState.getIsAnnouncementGroup().equals(toState.getIsAnnouncementGroup())) {
      builder.setNewIsAnnouncementGroup(toState.getIsAnnouncementGroup());
    }

    if (!fromState.getAvatar().equals(toState.getAvatar())) {
      builder.setNewAvatar(DecryptedString.newBuilder().setValue(toState.getAvatar()));
    }

    if (!fromState.getDisappearingMessagesTimer().equals(toState.getDisappearingMessagesTimer())) {
      builder.setNewTimer(toState.getDisappearingMessagesTimer());
    }

    if (!fromState.getAccessControl().getAttributes().equals(toState.getAccessControl().getAttributes())) {
      builder.setNewAttributeAccess(toState.getAccessControl().getAttributes());
    }

    if (!fromState.getAccessControl().getMembers().equals(toState.getAccessControl().getMembers())) {
      builder.setNewMemberAccess(toState.getAccessControl().getMembers());
    }

    Set<ByteString> fromStateMemberAcis = membersToSetOfAcis(fromState.getMembersList());
    Set<ByteString> toStateMemberAcis   = membersToSetOfAcis(toState.getMembersList());

    Set<ByteString> pendingMembersListA = pendingMembersToSetOfServiceIds(fromState.getPendingMembersList());
    Set<ByteString> pendingMembersListB = pendingMembersToSetOfServiceIds(toState.getPendingMembersList());
    
    Set<ByteString> requestingMembersListA = requestingMembersToSetOfAcis(fromState.getRequestingMembersList());
    Set<ByteString> requestingMembersListB = requestingMembersToSetOfAcis(toState.getRequestingMembersList());

    Set<ByteString> bannedMembersListA = bannedMembersToSetOfServiceIds(fromState.getBannedMembersList());
    Set<ByteString> bannedMembersListB = bannedMembersToSetOfServiceIds(toState.getBannedMembersList());

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
    Set<DecryptedMember>           addedMembersByInvitation      = intersectByAci(toState.getMembersList(), addedByInvitationAcis);
    Set<DecryptedMember>           addedMembersByRequestApproval = intersectByAci(toState.getMembersList(), addedByRequestApprovalAcis);
    Set<DecryptedMember>           addedMembers                  = intersectByAci(toState.getMembersList(), subtract(newMemberAcis, addedByInvitationAcis, addedByRequestApprovalAcis));
    Set<DecryptedPendingMember>    uninvitedMembers              = intersectPendingByServiceId(fromState.getPendingMembersList(), subtract(removedPendingMemberServiceIds, addedByInvitationAcis));
    Set<DecryptedRequestingMember> rejectedRequestMembers        = intersectRequestingByAci(fromState.getRequestingMembersList(), subtract(removedRequestingMemberAcis, addedByRequestApprovalAcis));

    for (DecryptedMember member : intersectByAci(fromState.getMembersList(), removedMemberAcis)) {
      builder.addDeleteMembers(member.getAciBytes());
    }

    for (DecryptedMember member : addedMembers) {
      builder.addNewMembers(member);
    }

    for (DecryptedMember member : addedMembersByInvitation) {
      builder.addPromotePendingMembers(member);
    }

    for (DecryptedPendingMember uninvitedMember : uninvitedMembers) {
      builder.addDeletePendingMembers(DecryptedPendingMemberRemoval.newBuilder()
                                                                   .setServiceIdBytes(uninvitedMember.getServiceIdBytes())
                                                                   .setServiceIdCipherText(uninvitedMember.getServiceIdCipherText()));
    }

    for (DecryptedPendingMember invitedMember : intersectPendingByServiceId(toState.getPendingMembersList(), newPendingMemberServiceIds)) {
      builder.addNewPendingMembers(invitedMember);
    }

    Set<ByteString>                        consistentMemberAcis      = intersect(fromStateMemberAcis, toStateMemberAcis);
    Set<DecryptedMember>                   changedMembers            = intersectByAci(subtract(toState.getMembersList(), fromState.getMembersList()), consistentMemberAcis);
    Map<ByteString, DecryptedMember>       membersAciMap             = mapByAci(fromState.getMembersList());
    Map<ByteString, DecryptedBannedMember> bannedMembersServiceIdMap = bannedServiceIdMap(toState.getBannedMembersList());

    for (DecryptedMember newState : changedMembers) {
      DecryptedMember oldState = membersAciMap.get(newState.getAciBytes());
      if (oldState.getRole() != newState.getRole()) {
        builder.addModifyMemberRoles(DecryptedModifyMemberRole.newBuilder()
                                                              .setAciBytes(newState.getAciBytes())
                                                              .setRole(newState.getRole()));
      }

      if (!oldState.getProfileKey().equals(newState.getProfileKey())) {
        builder.addModifiedProfileKeys(newState);
      }
    }

    if (!fromState.getAccessControl().getAddFromInviteLink().equals(toState.getAccessControl().getAddFromInviteLink())) {
      builder.setNewInviteLinkAccess(toState.getAccessControl().getAddFromInviteLink());
    }

    for (DecryptedRequestingMember requestingMember : intersectRequestingByAci(toState.getRequestingMembersList(), newRequestingMemberAcis)) {
      builder.addNewRequestingMembers(requestingMember);
    }
    
    for (DecryptedRequestingMember requestingMember : rejectedRequestMembers) {
      builder.addDeleteRequestingMembers(requestingMember.getAciBytes());
    }

    for (DecryptedMember member : addedMembersByRequestApproval) {
      builder.addPromoteRequestingMembers(DecryptedApproveMember.newBuilder()
                                                                .setAciBytes(member.getAciBytes())
                                                                .setRole(member.getRole()));
    }

    if (!fromState.getInviteLinkPassword().equals(toState.getInviteLinkPassword())) {
      builder.setNewInviteLinkPassword(toState.getInviteLinkPassword());
    }

    for (ByteString serviceIdBinary : removedBannedMemberServiceIds) {
      builder.addDeleteBannedMembers(DecryptedBannedMember.newBuilder().setServiceIdBytes(serviceIdBinary).build());
    }

    for (ByteString serviceIdBinary : newBannedMemberServiceIds) {
      DecryptedBannedMember.Builder newBannedBuilder = DecryptedBannedMember.newBuilder().setServiceIdBytes(serviceIdBinary);
      DecryptedBannedMember         bannedMember     = bannedMembersServiceIdMap.get(serviceIdBinary);
      if (bannedMember != null) {
        newBannedBuilder.setTimestamp(bannedMember.getTimestamp());
      }

      builder.addNewBannedMembers(newBannedBuilder);
    }

    return builder.build();
  }

  private static Map<ByteString, DecryptedMember> mapByAci(List<DecryptedMember> membersList) {
    Map<ByteString, DecryptedMember> map = new LinkedHashMap<>(membersList.size());
    for (DecryptedMember member : membersList) {
      map.put(member.getAciBytes(), member);
    }
    return map;
  }

  private static Map<ByteString, DecryptedBannedMember> bannedServiceIdMap(List<DecryptedBannedMember> membersList) {
    Map<ByteString, DecryptedBannedMember> map = new LinkedHashMap<>(membersList.size());
    for (DecryptedBannedMember member : membersList) {
      map.put(member.getServiceIdBytes(), member);
    }
    return map;
  }

  private static Set<DecryptedMember> intersectByAci(Collection<DecryptedMember> members, Set<ByteString> acis) {
    Set<DecryptedMember> result = new LinkedHashSet<>(members.size());
    for (DecryptedMember member : members) {
      if (acis.contains(member.getAciBytes()))
        result.add(member);
    }
    return result;
  }

  private static Set<DecryptedPendingMember> intersectPendingByServiceId(Collection<DecryptedPendingMember> members, Set<ByteString> serviceIds) {
    Set<DecryptedPendingMember> result = new LinkedHashSet<>(members.size());
    for (DecryptedPendingMember member : members) {
      if (serviceIds.contains(member.getServiceIdBytes()))
        result.add(member);
    }
    return result;
  }
  
  private static Set<DecryptedRequestingMember> intersectRequestingByAci(Collection<DecryptedRequestingMember> members, Set<ByteString> acis) {
    Set<DecryptedRequestingMember> result = new LinkedHashSet<>(members.size());
    for (DecryptedRequestingMember member : members) {
      if (acis.contains(member.getAciBytes()))
        result.add(member);
    }
    return result;
  }

  private static Set<ByteString> pendingMembersToSetOfServiceIds(Collection<DecryptedPendingMember> pendingMembers) {
    Set<ByteString> serviceIds = new LinkedHashSet<>(pendingMembers.size());
    for (DecryptedPendingMember pendingMember : pendingMembers) {
      serviceIds.add(pendingMember.getServiceIdBytes());
    }
    return serviceIds;
  }

  private static Set<ByteString> requestingMembersToSetOfAcis(Collection<DecryptedRequestingMember> requestingMembers) {
    Set<ByteString> acis = new LinkedHashSet<>(requestingMembers.size());
    for (DecryptedRequestingMember requestingMember : requestingMembers) {
      acis.add(requestingMember.getAciBytes());
    }
    return acis;
  }

  private static Set<ByteString> membersToSetOfAcis(Collection<DecryptedMember> members) {
    Set<ByteString> acis = new LinkedHashSet<>(members.size());
    for (DecryptedMember member : members) {
      acis.add(member.getAciBytes());
    }
    return acis;
  }

  private static Set<ByteString> bannedMembersToSetOfServiceIds(Collection<DecryptedBannedMember> bannedMembers) {
    Set<ByteString> serviceIds = new LinkedHashSet<>(bannedMembers.size());
    for (DecryptedBannedMember bannedMember : bannedMembers) {
      serviceIds.add(bannedMember.getServiceIdBytes());
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

package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval;
import org.signal.zkgroup.util.UUIDUtil;
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

  public static Set<UUID> toUuidSet(Collection<DecryptedMember> membersList) {
    HashSet<UUID> uuids = new HashSet<>(membersList.size());

    for (DecryptedMember member : membersList) {
      uuids.add(toUuid(member));
    }

    return uuids;
  }

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
      uuidList.add(toUuid(member));
    }

    return uuidList;
  }

  public static ArrayList<UUID> pendingToUuidList(Collection<DecryptedPendingMember> membersList) {
    ArrayList<UUID> uuidList = new ArrayList<>(membersList.size());

    for (DecryptedPendingMember member : membersList) {
      uuidList.add(toUuid(member));
    }

    return uuidList;
  }

  public static ArrayList<UUID> removedMembersUuidList(DecryptedGroupChange groupChange) {
    ArrayList<UUID> uuidList = new ArrayList<>(groupChange.getDeleteMembersCount());

    for (ByteString member : groupChange.getDeleteMembersList()) {
      uuidList.add(toUuid(member));
    }

    return uuidList;
  }

  public static UUID toUuid(DecryptedMember member) {
    return toUuid(member.getUuid());
  }

  public static UUID toUuid(DecryptedPendingMember member) {
    return toUuid(member.getUuid());
  }

  private static UUID toUuid(ByteString member) {
    return UUIDUtil.deserialize(member.toByteArray());
  }

  /**
   * The UUID of the member that made the change.
   */
  public static UUID editorUuid(DecryptedGroupChange change) {
    return change != null ? UuidUtil.fromByteStringOrUnknown(change.getEditor()) : UuidUtil.UNKNOWN_UUID;
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

  public static Optional<DecryptedMember> firstMember(Collection<DecryptedMember> members) {
    Iterator<DecryptedMember> iterator = members.iterator();

    if (iterator.hasNext()) {
      return Optional.of(iterator.next());
    } else {
      return Optional.absent();
    }
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
                    .setVersion(revision)
                    .build();
    } else {
      return group;
    }
  }

  public static DecryptedGroup apply(DecryptedGroup group, DecryptedGroupChange change)
      throws NotAbleToApplyChangeException
  {
    if (change.getVersion() != group.getVersion() + 1) {
      throw new NotAbleToApplyChangeException();
    }

    DecryptedGroup.Builder builder = DecryptedGroup.newBuilder(group);

    builder.addAllMembers(change.getNewMembersList());

    for (ByteString removedMember : change.getDeleteMembersList()) {
      int index = indexOfUuid(builder.getMembersList(), removedMember);

      if (index == -1) {
        throw new NotAbleToApplyChangeException();
      }

      builder.removeMembers(index);
    }

    for (DecryptedModifyMemberRole modifyMemberRole : change.getModifyMemberRolesList()) {
      int index = indexOfUuid(builder.getMembersList(), modifyMemberRole.getUuid());

      if (index == -1) {
        throw new NotAbleToApplyChangeException();
      }

      builder.setMembers(index, DecryptedMember.newBuilder(builder.getMembers(index)).setRole(modifyMemberRole.getRole()).build());
    }

    for (DecryptedMember modifyProfileKey : change.getModifiedProfileKeysList()) {
      int index = indexOfUuid(builder.getMembersList(), modifyProfileKey.getUuid());

      if (index == -1) {
        throw new NotAbleToApplyChangeException();
      }

      builder.setMembers(index, modifyProfileKey);
    }

    for (DecryptedPendingMemberRemoval removedMember : change.getDeletePendingMembersList()) {
      int index = findPendingIndexByUuidCipherText(builder.getPendingMembersList(), removedMember.getUuidCipherText());

      if (index == -1) {
        throw new NotAbleToApplyChangeException();
      }

      builder.removePendingMembers(index);
    }

    for (DecryptedMember newMember : change.getPromotePendingMembersList()) {
      int index = findPendingIndexByUuid(builder.getPendingMembersList(), newMember.getUuid());

      if (index == -1) {
        throw new NotAbleToApplyChangeException();
      }

      builder.removePendingMembers(index);
      builder.addMembers(newMember);
    }

    builder.addAllPendingMembers(change.getNewPendingMembersList());

    if (change.hasNewTitle()) {
      builder.setTitle(change.getNewTitle().getValue());
    }

    if (change.hasNewAvatar()) {
      builder.setAvatar(change.getNewAvatar().getValue());
    }

    if (change.hasNewTimer()) {
      builder.setDisappearingMessagesTimer(change.getNewTimer());
    }

    if (change.getNewAttributeAccess() != AccessControl.AccessRequired.UNKNOWN) {
      builder.setAccessControl(AccessControl.newBuilder(builder.getAccessControl())
             .setAttributesValue(change.getNewAttributeAccessValue())
             .build());
    }

    if (change.getNewMemberAccess() != AccessControl.AccessRequired.UNKNOWN) {
      builder.setAccessControl(AccessControl.newBuilder(builder.getAccessControl())
             .setMembersValue(change.getNewMemberAccessValue())
             .build());
    }

    return builder.setVersion(change.getVersion()).build();
  }

  private static int indexOfUuid(List<DecryptedMember> memberList, ByteString uuid) {
    for (int i = 0; i < memberList.size(); i++) {
      if(uuid.equals(memberList.get(i).getUuid())) return i;
    }
    return -1;
  }

  public static Optional<UUID> findInviter(List<DecryptedPendingMember> pendingMembersList, UUID uuid) {
    return Optional.fromNullable(findPendingByUuid(pendingMembersList, uuid).transform(DecryptedPendingMember::getAddedByUuid)
                                                                            .transform(UuidUtil::fromByteStringOrNull)
                                                                            .orNull());
  }

  public static class NotAbleToApplyChangeException extends Throwable {
  }
}

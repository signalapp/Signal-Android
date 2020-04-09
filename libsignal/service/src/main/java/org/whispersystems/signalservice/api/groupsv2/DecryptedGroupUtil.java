package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.zkgroup.util.UUIDUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
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

  public static UUID toUuid(DecryptedMember member) {
    return UUIDUtil.deserialize(member.getUuid().toByteArray());
  }

  public static UUID toUuid(DecryptedPendingMember member) {
    return UUIDUtil.deserialize(member.getUuid().toByteArray());
  }

  /**
   * The UUID of the member that made the change.
   */
  public static UUID editorUuid(DecryptedGroupChange change) {
    return UuidUtil.fromByteString(change.getEditor());
  }

  public static Optional<DecryptedMember> findMemberByUuid(Collection<DecryptedMember> members, UUID uuid) {
    ByteString uuidBytes = ByteString.copyFrom(UUIDUtil.serialize(uuid));

    for (DecryptedMember member : members) {
      if (uuidBytes.equals(member.getUuid())) {
        return Optional.of(member);
      }
    }

    return Optional.absent();
  }

  public static Optional<DecryptedPendingMember> findPendingByUuid(Collection<DecryptedPendingMember> members, UUID uuid) {
    ByteString uuidBytes = ByteString.copyFrom(UUIDUtil.serialize(uuid));

    for (DecryptedPendingMember member : members) {
      if (uuidBytes.equals(member.getUuid())) {
        return Optional.of(member);
      }
    }

    return Optional.absent();
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
}

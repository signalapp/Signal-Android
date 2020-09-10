package org.thoughtcrime.securesms.storage;

import androidx.annotation.NonNull;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.groups.BadGroupIdException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

final class GroupV1ConflictMerger implements StorageSyncHelper.ConflictMerger<SignalGroupV1Record> {

  private final Map<GroupId, SignalGroupV1Record> localByGroupId;

  GroupV1ConflictMerger(@NonNull Collection<SignalGroupV1Record> localOnly) {
    localByGroupId = Stream.of(localOnly).collect(Collectors.toMap(g -> GroupId.v1orThrow(g.getGroupId()), g -> g));
  }

  @Override
  public @NonNull Optional<SignalGroupV1Record> getMatching(@NonNull SignalGroupV1Record record) {
    return Optional.fromNullable(localByGroupId.get(GroupId.v1orThrow(record.getGroupId())));
  }

  @Override
  public @NonNull Collection<SignalGroupV1Record> getInvalidEntries(@NonNull Collection<SignalGroupV1Record> remoteRecords) {
    return Stream.of(remoteRecords)
                 .filterNot(GroupV1ConflictMerger::isValidGroupId)
                 .toList();
  }

  @Override
  public @NonNull SignalGroupV1Record merge(@NonNull SignalGroupV1Record remote, @NonNull SignalGroupV1Record local, @NonNull StorageSyncHelper.KeyGenerator keyGenerator) {
    byte[]  unknownFields  = remote.serializeUnknownFields();
    boolean blocked        = remote.isBlocked();
    boolean profileSharing = remote.isProfileSharingEnabled() || local.isProfileSharingEnabled();
    boolean archived       = remote.isArchived();

    boolean matchesRemote = Arrays.equals(unknownFields, remote.serializeUnknownFields()) && blocked == remote.isBlocked() && profileSharing == remote.isProfileSharingEnabled() && archived == remote.isArchived();
    boolean matchesLocal  = Arrays.equals(unknownFields, local.serializeUnknownFields())  && blocked == local.isBlocked()  && profileSharing == local.isProfileSharingEnabled()  && archived == local.isArchived();

    if (matchesRemote) {
      return remote;
    } else if (matchesLocal) {
      return local;
    } else {
      return new SignalGroupV1Record.Builder(keyGenerator.generate(), remote.getGroupId())
                                    .setUnknownFields(unknownFields)
                                    .setBlocked(blocked)
                                    .setProfileSharingEnabled(blocked)
                                    .build();
    }
  }

  private static boolean isValidGroupId(@NonNull SignalGroupV1Record record) {
    try {
      GroupId.v1Exact(record.getGroupId());
      return true;
    } catch (BadGroupIdException e) {
      return false;
    }
  }
}

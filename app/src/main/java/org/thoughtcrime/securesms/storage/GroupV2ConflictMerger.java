package org.thoughtcrime.securesms.storage;

import androidx.annotation.NonNull;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.zkgroup.groups.GroupMasterKey;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

class GroupV2ConflictMerger implements StorageSyncHelper.ConflictMerger<SignalGroupV2Record> {

  private final Map<GroupMasterKey, SignalGroupV2Record> localByGroupId;

  GroupV2ConflictMerger(@NonNull Collection<SignalGroupV2Record> localOnly) {
    localByGroupId = Stream.of(localOnly).collect(Collectors.toMap(SignalGroupV2Record::getMasterKey, g -> g));
  }

  @Override
  public @NonNull Optional<SignalGroupV2Record> getMatching(@NonNull SignalGroupV2Record record) {
    return Optional.fromNullable(localByGroupId.get(record.getMasterKey()));
  }

  @Override
  public @NonNull Collection<SignalGroupV2Record> getInvalidEntries(@NonNull Collection<SignalGroupV2Record> remoteRecords) {
    return Collections.emptySet();
  }

  @Override
  public @NonNull SignalGroupV2Record merge(@NonNull SignalGroupV2Record remote, @NonNull SignalGroupV2Record local, @NonNull StorageSyncHelper.KeyGenerator keyGenerator) {
    boolean blocked        = remote.isBlocked();
    boolean profileSharing = remote.isProfileSharingEnabled() || local.isProfileSharingEnabled();
    boolean archived       = remote.isArchived();

    boolean matchesRemote = blocked == remote.isBlocked() && profileSharing == remote.isProfileSharingEnabled() && archived == remote.isArchived();
    boolean matchesLocal  = blocked == local.isBlocked()  && profileSharing == local.isProfileSharingEnabled()  && archived == local.isArchived();

    if (matchesRemote) {
      return remote;
    } else if (matchesLocal) {
      return local;
    } else {
      return new SignalGroupV2Record.Builder(keyGenerator.generate(), remote.getMasterKey())
                                    .setBlocked(blocked)
                                    .setProfileSharingEnabled(blocked)
                                    .build();
    }
  }
}

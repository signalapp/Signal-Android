package org.thoughtcrime.securesms.storage;

import androidx.annotation.NonNull;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.util.GroupUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

class GroupV1ConflictMerger implements StorageSyncHelper.ConflictMerger<SignalGroupV1Record> {

  private final Map<String, SignalGroupV1Record> localByGroupId;

  GroupV1ConflictMerger(@NonNull Collection<SignalGroupV1Record> localOnly) {
    localByGroupId = Stream.of(localOnly).collect(Collectors.toMap(g -> GroupUtil.getEncodedId(g.getGroupId(), false), g -> g));
  }

  @Override
  public @NonNull Optional<SignalGroupV1Record> getMatching(@NonNull SignalGroupV1Record record) {
    return Optional.fromNullable(localByGroupId.get(GroupUtil.getEncodedId(record.getGroupId(), false)));
  }

  @Override
  public @NonNull Collection<SignalGroupV1Record> getInvalidEntries(@NonNull Collection<SignalGroupV1Record> remoteRecords) {
    return Collections.emptySet();
  }

  @Override
  public @NonNull SignalGroupV1Record merge(@NonNull SignalGroupV1Record remote, @NonNull SignalGroupV1Record local, @NonNull StorageSyncHelper.KeyGenerator keyGenerator) {
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
      return new SignalGroupV1Record.Builder(keyGenerator.generate(), remote.getGroupId())
                                    .setBlocked(blocked)
                                    .setProfileSharingEnabled(blocked)
                                    .build();
    }
  }
}

package org.thoughtcrime.securesms.storage;

import androidx.annotation.NonNull;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.google.protobuf.ByteString;

import org.signal.zkgroup.groups.GroupMasterKey;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

final class GroupV2ConflictMerger implements StorageSyncHelper.ConflictMerger<SignalGroupV2Record> {

  private final Map<ByteString, SignalGroupV2Record> localByMasterKeyBytes;

  GroupV2ConflictMerger(@NonNull Collection<SignalGroupV2Record> localOnly) {
    localByMasterKeyBytes = Stream.of(localOnly).collect(Collectors.toMap((SignalGroupV2Record signalGroupV2Record) -> ByteString.copyFrom(signalGroupV2Record.getMasterKeyBytes()), g -> g));
  }

  @Override
  public @NonNull Optional<SignalGroupV2Record> getMatching(@NonNull SignalGroupV2Record record) {
    return Optional.fromNullable(localByMasterKeyBytes.get(ByteString.copyFrom(record.getMasterKeyBytes())));
  }

  @Override
  public @NonNull Collection<SignalGroupV2Record> getInvalidEntries(@NonNull Collection<SignalGroupV2Record> remoteRecords) {
    return Stream.of(remoteRecords)
                 .filterNot(GroupV2ConflictMerger::isValidMasterKey)
                 .toList();
  }

  @Override
  public @NonNull SignalGroupV2Record merge(@NonNull SignalGroupV2Record remote, @NonNull SignalGroupV2Record local, @NonNull StorageSyncHelper.KeyGenerator keyGenerator) {
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
      return new SignalGroupV2Record.Builder(keyGenerator.generate(), remote.getMasterKeyBytes())
                                    .setUnknownFields(unknownFields)
                                    .setBlocked(blocked)
                                    .setProfileSharingEnabled(blocked)
                                    .build();
    }
  }

  private static boolean isValidMasterKey(@NonNull SignalGroupV2Record record) {
    return record.getMasterKeyBytes().length == GroupMasterKey.SIZE;
  }
}

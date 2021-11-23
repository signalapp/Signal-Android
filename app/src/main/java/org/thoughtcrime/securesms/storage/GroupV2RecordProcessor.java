package org.thoughtcrime.securesms.storage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupsV1MigrationUtil;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

public final class GroupV2RecordProcessor extends DefaultStorageRecordProcessor<SignalGroupV2Record> {

  private static final String TAG = Log.tag(GroupV2RecordProcessor.class);

  private final Context                     context;
  private final RecipientDatabase           recipientDatabase;
  private final GroupDatabase               groupDatabase;
  private final Map<GroupId.V2, GroupId.V1> gv1GroupsByExpectedGv2Id;

  public GroupV2RecordProcessor(@NonNull Context context) {
    this(context, SignalDatabase.recipients(), SignalDatabase.groups());
  }

  GroupV2RecordProcessor(@NonNull Context context, @NonNull RecipientDatabase recipientDatabase, @NonNull GroupDatabase groupDatabase) {
    this.context                  = context;
    this.recipientDatabase        = recipientDatabase;
    this.groupDatabase            = groupDatabase;
    this.gv1GroupsByExpectedGv2Id = groupDatabase.getAllExpectedV2Ids();
  }

  @Override
  boolean isInvalid(@NonNull SignalGroupV2Record remote) {
    return remote.getMasterKeyBytes().length != GroupMasterKey.SIZE;
  }

  @Override
  @NonNull Optional<SignalGroupV2Record> getMatching(@NonNull SignalGroupV2Record record, @NonNull StorageKeyGenerator keyGenerator) {
    GroupId.V2 groupId = GroupId.v2(record.getMasterKeyOrThrow());

    Optional<RecipientId> recipientId = recipientDatabase.getByGroupId(groupId);

    return recipientId.transform(recipientDatabase::getRecipientSettingsForSync)
                      .transform(settings -> {
                        if (settings.getSyncExtras().getGroupMasterKey() != null) {
                          return StorageSyncModels.localToRemoteRecord(settings);
                        } else {
                          Log.w(TAG, "No local master key. Assuming it matches remote since the groupIds match. Enqueuing a fetch to fix the bad state.");
                          groupDatabase.fixMissingMasterKey(record.getMasterKeyOrThrow());
                          return StorageSyncModels.localToRemoteRecord(settings, record.getMasterKeyOrThrow());
                        }
                      })
                      .transform(r -> r.getGroupV2().get());
  }

  @Override
  @NonNull SignalGroupV2Record merge(@NonNull SignalGroupV2Record remote, @NonNull SignalGroupV2Record local, @NonNull StorageKeyGenerator keyGenerator) {
    byte[]  unknownFields  = remote.serializeUnknownFields();
    boolean blocked        = remote.isBlocked();
    boolean profileSharing = remote.isProfileSharingEnabled();
    boolean archived       = remote.isArchived();
    boolean forcedUnread   = remote.isForcedUnread();
    long    muteUntil      = remote.getMuteUntil();

    boolean matchesRemote = doParamsMatch(remote, unknownFields, blocked, profileSharing, archived, forcedUnread, muteUntil);
    boolean matchesLocal  = doParamsMatch(local, unknownFields, blocked, profileSharing, archived, forcedUnread, muteUntil);

    if (matchesRemote) {
      return remote;
    } else if (matchesLocal) {
      return local;
    } else {
      return new SignalGroupV2Record.Builder(keyGenerator.generate(), remote.getMasterKeyBytes())
                                    .setUnknownFields(unknownFields)
                                    .setBlocked(blocked)
                                    .setProfileSharingEnabled(blocked)
                                    .setArchived(archived)
                                    .setForcedUnread(forcedUnread)
                                    .setMuteUntil(muteUntil)
                                    .build();
    }
  }

  /**
   * This contains a pretty big compromise: In the event that the new GV2 group we learned about
   * was, in fact, a migrated V1 group we already knew about, we handle the migration here. This
   * isn't great because the migration will likely result in network activity. And because this is
   * all happening in a transaction, this could keep the transaction open for longer than we'd like.
   * However, given that nearly all V1 groups have already been migrated, we're at a point where
   * this event should be extraordinarily rare, and it didn't seem worth it to add a lot of
   * complexity to accommodate this specific scenario.
   */
  @Override
  void insertLocal(@NonNull SignalGroupV2Record record) throws IOException {
    GroupId.V2 actualV2Id   = GroupId.v2(record.getMasterKeyOrThrow());
    GroupId.V1 possibleV1Id = gv1GroupsByExpectedGv2Id.get(actualV2Id);

    if (possibleV1Id != null) {
      Log.i(TAG, "Discovered a new GV2 ID that is actually a migrated V1 group! Migrating now.");
      GroupsV1MigrationUtil.performLocalMigration(context, possibleV1Id);
    } else {
      recipientDatabase.applyStorageSyncGroupV2Insert(record);
    }
  }

  @Override
  void updateLocal(@NonNull StorageRecordUpdate<SignalGroupV2Record> update) {
    recipientDatabase.applyStorageSyncGroupV2Update(update);
  }

  @Override
  public int compare(@NonNull SignalGroupV2Record lhs, @NonNull SignalGroupV2Record rhs) {
    if (Arrays.equals(lhs.getMasterKeyBytes(), rhs.getMasterKeyBytes())) {
      return 0;
    } else {
      return 1;
    }
  }

  private boolean doParamsMatch(@NonNull SignalGroupV2Record group,
                                @Nullable byte[] unknownFields,
                                boolean blocked,
                                boolean profileSharing,
                                boolean archived,
                                boolean forcedUnread,
                                long muteUntil)
  {
    return Arrays.equals(unknownFields, group.serializeUnknownFields()) &&
           blocked == group.isBlocked()                                 &&
           profileSharing == group.isProfileSharingEnabled()            &&
           archived == group.isArchived()                               &&
           forcedUnread == group.isForcedUnread()                       &&
           muteUntil == group.getMuteUntil();
  }
}

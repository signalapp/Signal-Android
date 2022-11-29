package org.thoughtcrime.securesms.database;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;
import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.core.util.CursorUtil;
import org.signal.core.util.SQLiteDatabaseExtensionsKt;
import org.signal.core.util.SetUtil;
import org.signal.core.util.SqlUtil;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.EnabledState;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchSortOrder;
import org.thoughtcrime.securesms.contacts.paged.collections.ContactSearchIterator;
import org.thoughtcrime.securesms.crypto.SenderKeyUtil;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.BadGroupIdException;
import org.thoughtcrime.securesms.groups.GroupAccessControl;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange;
import org.thoughtcrime.securesms.groups.v2.processing.GroupsV2StateProcessor;
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.groupsv2.GroupChangeReconstruct;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.Closeable;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class GroupTable extends DatabaseTable implements RecipientIdDatabaseReference {

  private static final String TAG = Log.tag(GroupTable.class);

          static final String TABLE_NAME                  = "groups";
  private static final String ID                          = "_id";
          static final String GROUP_ID                    = "group_id";
  public  static final String RECIPIENT_ID                = "recipient_id";
  private static final String TITLE                       = "title";
          static final String MEMBERS                     = "members";
  private static final String AVATAR_ID                   = "avatar_id";
  private static final String AVATAR_KEY                  = "avatar_key";
  private static final String AVATAR_CONTENT_TYPE         = "avatar_content_type";
  private static final String AVATAR_RELAY                = "avatar_relay";
  private static final String AVATAR_DIGEST               = "avatar_digest";
  private static final String TIMESTAMP                   = "timestamp";
          static final String ACTIVE                      = "active";
          static final String MMS                         = "mms";
  private static final String EXPECTED_V2_ID              = "expected_v2_id";
  private static final String UNMIGRATED_V1_MEMBERS       = "former_v1_members";
  private static final String DISTRIBUTION_ID             = "distribution_id";
  private static final String SHOW_AS_STORY_STATE         = "display_as_story";
  private static final String LAST_FORCE_UPDATE_TIMESTAMP = "last_force_update_timestamp";

  /** Was temporarily used for PNP accept by pni but is no longer needed/updated */
  @Deprecated
  private static final String AUTH_SERVICE_ID       = "auth_service_id";


  /* V2 Group columns */
  /** 32 bytes serialized {@link GroupMasterKey} */
  public  static final String V2_MASTER_KEY       = "master_key";
  /** Increments with every change to the group */
  private static final String V2_REVISION         = "revision";
  /** Serialized {@link DecryptedGroup} protobuf */
  public  static final String V2_DECRYPTED_GROUP  = "decrypted_group";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID                          + " INTEGER PRIMARY KEY, " +
                                                                                  GROUP_ID                    + " TEXT, " +
                                                                                  RECIPIENT_ID                + " INTEGER, " +
                                                                                  TITLE                       + " TEXT, " +
                                                                                  MEMBERS                     + " TEXT, " +
                                                                                  AVATAR_ID                   + " INTEGER, " +
                                                                                  AVATAR_KEY                  + " BLOB, " +
                                                                                  AVATAR_CONTENT_TYPE         + " TEXT, " +
                                                                                  AVATAR_RELAY                + " TEXT, " +
                                                                                  TIMESTAMP                   + " INTEGER, " +
                                                                                  ACTIVE                      + " INTEGER DEFAULT 1, " +
                                                                                  AVATAR_DIGEST               + " BLOB, " +
                                                                                  MMS                         + " INTEGER DEFAULT 0, " +
                                                                                  V2_MASTER_KEY               + " BLOB, " +
                                                                                  V2_REVISION                 + " BLOB, " +
                                                                                  V2_DECRYPTED_GROUP          + " BLOB, " +
                                                                                  EXPECTED_V2_ID              + " TEXT DEFAULT NULL, " +
                                                                                  UNMIGRATED_V1_MEMBERS       + " TEXT DEFAULT NULL, " +
                                                                                  DISTRIBUTION_ID             + " TEXT DEFAULT NULL, " +
                                                                                  SHOW_AS_STORY_STATE         + " INTEGER DEFAULT 0, " +
                                                                                  AUTH_SERVICE_ID             + " TEXT DEFAULT NULL, " +
                                                                                  LAST_FORCE_UPDATE_TIMESTAMP + " INTEGER DEFAULT 0);";

  public static final String[] CREATE_INDEXS = {
      "CREATE UNIQUE INDEX IF NOT EXISTS group_id_index ON " + TABLE_NAME + " (" + GROUP_ID + ");",
      "CREATE UNIQUE INDEX IF NOT EXISTS group_recipient_id_index ON " + TABLE_NAME + " (" + RECIPIENT_ID + ");",
      "CREATE UNIQUE INDEX IF NOT EXISTS expected_v2_id_index ON " + TABLE_NAME + " (" + EXPECTED_V2_ID + ");",
      "CREATE UNIQUE INDEX IF NOT EXISTS group_distribution_id_index ON " + TABLE_NAME + "(" + DISTRIBUTION_ID + ");"
  };

  private static final String[] GROUP_PROJECTION = {
      GROUP_ID, RECIPIENT_ID, TITLE, MEMBERS, UNMIGRATED_V1_MEMBERS, AVATAR_ID, AVATAR_KEY, AVATAR_CONTENT_TYPE, AVATAR_RELAY, AVATAR_DIGEST,
      TIMESTAMP, ACTIVE, MMS, V2_MASTER_KEY, V2_REVISION, V2_DECRYPTED_GROUP, LAST_FORCE_UPDATE_TIMESTAMP
  };

  static final List<String> TYPED_GROUP_PROJECTION = Stream.of(GROUP_PROJECTION).map(columnName -> TABLE_NAME + "." + columnName).toList();

  public GroupTable(Context context, SignalDatabase databaseHelper) {
    super(context, databaseHelper);
  }

  public Optional<GroupRecord> getGroup(RecipientId recipientId) {
    try (Cursor cursor = databaseHelper.getSignalReadableDatabase().query(TABLE_NAME, null, RECIPIENT_ID + " = ?", new String[] { recipientId.serialize()}, null, null, null)) {
      if (cursor != null && cursor.moveToNext()) {
        return getGroup(cursor);
      }

      return Optional.empty();
    }
  }

  public Optional<GroupRecord> getGroup(@NonNull GroupId groupId) {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    try (Cursor cursor = db.query(TABLE_NAME, null, GROUP_ID + " = ?", SqlUtil.buildArgs(groupId.toString()), null, null, null)) {
      if (cursor != null && cursor.moveToNext()) {
        Optional<GroupRecord> groupRecord = getGroup(cursor);

        if (groupRecord.isPresent() && RemappedRecords.getInstance().areAnyRemapped(groupRecord.get().getMembers())) {
          String remaps = RemappedRecords.getInstance().buildRemapDescription(groupRecord.get().getMembers());
          Log.w(TAG, "Found a group with remapped recipients in it's membership list! Updating the list. GroupId: " + groupId + ", Remaps: " + remaps, true);

          Collection<RecipientId> remapped = RemappedRecords.getInstance().remap(groupRecord.get().getMembers());

          ContentValues values = new ContentValues();
          values.put(MEMBERS, RecipientId.toSerializedList(remapped));

          if (db.update(TABLE_NAME, values, GROUP_ID + " = ?", SqlUtil.buildArgs(groupId)) > 0) {
            return getGroup(groupId);
          } else {
            throw new IllegalStateException("Failed to update group with remapped recipients!");
          }
        }

        return getGroup(cursor);
      }

      return Optional.empty();
    }
  }

  public boolean groupExists(@NonNull GroupId groupId) {
    try (Cursor cursor = databaseHelper.getSignalReadableDatabase().query(TABLE_NAME, null, GROUP_ID + " = ?",
                                                                          new String[] {groupId.toString()},
                                                                          null, null, null))
    {
      return cursor.moveToNext();
    }
  }

  /**
   * @return A gv1 group whose expected v2 ID matches the one provided.
   */
  public Optional<GroupRecord> getGroupV1ByExpectedV2(@NonNull GroupId.V2 gv2Id) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();
    try (Cursor cursor = db.query(TABLE_NAME, GROUP_PROJECTION, EXPECTED_V2_ID + " = ?", SqlUtil.buildArgs(gv2Id), null, null, null)) {
      if (cursor.moveToFirst()) {
        return getGroup(cursor);
      } else {
        return Optional.empty();
      }
    }
  }

  public Optional<GroupRecord> getGroupByDistributionId(@NonNull DistributionId distributionId) {
    SQLiteDatabase db    = databaseHelper.getSignalReadableDatabase();
    String         query = DISTRIBUTION_ID + " = ?";
    String[]       args  = SqlUtil.buildArgs(distributionId);

    try (Cursor cursor = db.query(TABLE_NAME, null, query, args, null, null, null)) {
      if (cursor.moveToFirst()) {
        return getGroup(cursor);
      } else {
        return Optional.empty();
      }
    }
  }

  public void removeUnmigratedV1Members(@NonNull GroupId.V2 id) {
    Optional<GroupRecord> group = getGroup(id);

    if (!group.isPresent()) {
      Log.w(TAG, "Couldn't find the group!", new Throwable());
      return;
    }

    removeUnmigratedV1Members(id, group.get().getUnmigratedV1Members());
  }

  /**
   * Removes the specified members from the list of 'unmigrated V1 members' -- the list of members
   * that were either dropped or had to be invited when migrating the group from V1->V2.
   */
  public void removeUnmigratedV1Members(@NonNull GroupId.V2 id, @NonNull List<RecipientId> toRemove) {
    Optional<GroupRecord> group = getGroup(id);

    if (!group.isPresent()) {
      Log.w(TAG, "Couldn't find the group!", new Throwable());
      return;
    }

    List<RecipientId> newUnmigrated = group.get().getUnmigratedV1Members();
    newUnmigrated.removeAll(toRemove);

    ContentValues values = new ContentValues();
    values.put(UNMIGRATED_V1_MEMBERS, newUnmigrated.isEmpty() ? null : RecipientId.toSerializedList(newUnmigrated));

    databaseHelper.getSignalWritableDatabase().update(TABLE_NAME, values, GROUP_ID + " = ?", SqlUtil.buildArgs(id));

    Recipient.live(Recipient.externalGroupExact(id).getId()).refresh();
  }

  Optional<GroupRecord> getGroup(Cursor cursor) {
    Reader reader = new Reader(cursor);
    return Optional.ofNullable(reader.getCurrent());
  }

  /**
   * @return local db group revision or -1 if not present.
   */
  public int getGroupV2Revision(@NonNull GroupId.V2 groupId) {
    try (Cursor cursor = databaseHelper.getSignalReadableDatabase().query(TABLE_NAME, null, GROUP_ID + " = ?",
                                                                          new String[] {groupId.toString()},
                                                                          null, null, null))
    {
      if (cursor != null && cursor.moveToNext()) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(V2_REVISION));
      }

      return -1;
    }
  }

   /**
   * Call if you are sure this group should exist.
   * <p>
   * Finds group and throws if it cannot.
   */
  public @NonNull GroupRecord requireGroup(@NonNull GroupId groupId) {
    Optional<GroupRecord> group = getGroup(groupId);

    if (!group.isPresent()) {
      throw new AssertionError("Group not found");
    }

    return group.get();
  }

  public boolean isUnknownGroup(@NonNull GroupId groupId) {
    Optional<GroupRecord> group = getGroup(groupId);

    if (!group.isPresent()) {
      return true;
    }

    boolean noMetadata = !group.get().hasAvatar() && TextUtils.isEmpty(group.get().getTitle());
    boolean noMembers  = group.get().getMembers().isEmpty() || (group.get().getMembers().size() == 1 && group.get().getMembers().contains(Recipient.self().getId()));

    return noMetadata && noMembers;
  }

  public Reader queryGroupsByTitle(String inputQuery, boolean includeInactive, boolean excludeV1, boolean excludeMms) {
    SqlUtil.Query query  = getGroupQueryWhereStatement(inputQuery, includeInactive, excludeV1, excludeMms);
    Cursor        cursor = databaseHelper.getSignalReadableDatabase().query(TABLE_NAME, null, query.getWhere(), query.getWhereArgs(), null, null, TITLE + " COLLATE NOCASE ASC");

    return new Reader(cursor);
  }

  public Reader queryGroupsByMembership(@NonNull Set<RecipientId> recipientIds, boolean includeInactive, boolean excludeV1, boolean excludeMms) {
    if (recipientIds.isEmpty()) {
      return new Reader(null);
    }

    if (recipientIds.size() > 30) {
      Log.w(TAG, "[queryGroupsByMembership] Large set of recipientIds (" + recipientIds.size() + ")! Using the first 30.");
      recipientIds = recipientIds.stream().limit(30).collect(Collectors.toSet());
    }

    List<String> recipientLikeClauses = recipientIds.stream()
                                                    .map(RecipientId::toLong)
                                                    .map(id -> "(" + MEMBERS + " LIKE " + id + " || ',%' OR " + MEMBERS + " LIKE '%,' || " + id + " || ',%' OR " + MEMBERS + " LIKE '%,' || " + id + ")")
                                                    .collect(Collectors.toList());

    String   query;
    String[] queryArgs;

    String membershipQuery = "(" + Util.join(recipientLikeClauses, " OR ") + ")";

    if (includeInactive) {
      query     = membershipQuery + " AND (" + ACTIVE + " = ? OR " + RECIPIENT_ID + " IN (SELECT " + ThreadTable.RECIPIENT_ID + " FROM " + ThreadTable.TABLE_NAME + "))";
      queryArgs = SqlUtil.buildArgs(1);
    } else {
      query     = membershipQuery + " AND " + ACTIVE + " = ?";
      queryArgs = SqlUtil.buildArgs(1);
    }

    if (excludeV1) {
      query += " AND " + EXPECTED_V2_ID + " IS NULL";
    }

    if (excludeMms) {
      query += " AND " + MMS + " = 0";
    }

    return new Reader(getReadableDatabase().query(TABLE_NAME, null, query, queryArgs, null, null, null));
  }

  public Reader queryGroupsByRecency(@NonNull GroupQuery groupQuery) {
    SqlUtil.Query query = getGroupQueryWhereStatement(groupQuery.searchQuery, groupQuery.includeInactive, !groupQuery.includeV1, !groupQuery.includeMms);
    String sql = "SELECT * FROM " + TABLE_NAME +
                 " LEFT JOIN " + ThreadTable.TABLE_NAME + " ON " + RECIPIENT_ID + " = " + ThreadTable.TABLE_NAME + "." + ThreadTable.RECIPIENT_ID +
                 " WHERE " + query.getWhere() +
                 " ORDER BY " + ThreadTable.TABLE_NAME + "." + ThreadTable.DATE + " DESC";

    return new Reader(databaseHelper.getSignalReadableDatabase().rawQuery(sql, query.getWhereArgs()));
  }

  public Reader queryGroups(@NonNull GroupQuery groupQuery) {
    if (groupQuery.sortOrder == ContactSearchSortOrder.NATURAL) {
      return queryGroupsByTitle(groupQuery.searchQuery, groupQuery.includeInactive, !groupQuery.includeV1, !groupQuery.includeMms);
    } else {
      return queryGroupsByRecency(groupQuery);
    }
  }

  private @NonNull SqlUtil.Query getGroupQueryWhereStatement(String inputQuery, boolean includeInactive, boolean excludeV1, boolean excludeMms) {
    String   query;
    String[] queryArgs;

    String caseInsensitiveQuery = SqlUtil.buildCaseInsensitiveGlobPattern(inputQuery);

    if (includeInactive) {
      query     = TITLE + " GLOB ? AND (" + ACTIVE + " = ? OR " + RECIPIENT_ID + " IN (SELECT " + ThreadTable.RECIPIENT_ID + " FROM " + ThreadTable.TABLE_NAME + "))";
      queryArgs = SqlUtil.buildArgs(caseInsensitiveQuery, 1);
    } else {
      query     = TITLE + " GLOB ? AND " + ACTIVE + " = ?";
      queryArgs = SqlUtil.buildArgs(caseInsensitiveQuery, 1);
    }

    if (excludeV1) {
      query += " AND " + EXPECTED_V2_ID + " IS NULL";
    }

    if (excludeMms) {
      query += " AND " + MMS + " = 0";
    }

    return new SqlUtil.Query(query, queryArgs);
  }

  public @NonNull DistributionId getOrCreateDistributionId(@NonNull GroupId.V2 groupId) {
    SQLiteDatabase db    = databaseHelper.getSignalReadableDatabase();
    String         query = GROUP_ID + " = ?";
    String[]       args  = SqlUtil.buildArgs(groupId);

    try (Cursor cursor = databaseHelper.getSignalReadableDatabase().query(TABLE_NAME, new String[] { DISTRIBUTION_ID }, query, args, null, null, null)) {
      if (cursor.moveToFirst()) {
        Optional<String> serialized = CursorUtil.getString(cursor, DISTRIBUTION_ID);

        if (serialized.isPresent()) {
          return DistributionId.from(serialized.get());
        } else {
          Log.w(TAG, "Missing distributionId! Creating one.");

          DistributionId distributionId = DistributionId.create();

          ContentValues values = new ContentValues(1);
          values.put(DISTRIBUTION_ID, distributionId.toString());

          int count = db.update(TABLE_NAME, values, query, args);
          if (count < 1) {
            throw new IllegalStateException("Tried to create a distributionId for " + groupId + ", but it doesn't exist!");
          }

          return distributionId;
        }
      } else {
        throw new IllegalStateException("Group " + groupId + " doesn't exist!");
      }
    }
  }

  public GroupId.Mms getOrCreateMmsGroupForMembers(List<RecipientId> members) {
    Collections.sort(members);

    Cursor cursor = databaseHelper.getSignalReadableDatabase().query(TABLE_NAME, new String[] { GROUP_ID},
                                                               MEMBERS + " = ? AND " + MMS + " = ?",
                                                                     new String[] {RecipientId.toSerializedList(members), "1"},
                                                                     null, null, null);
    try {
      if (cursor != null && cursor.moveToNext()) {
        return GroupId.parseOrThrow(cursor.getString(cursor.getColumnIndexOrThrow(GROUP_ID)))
                      .requireMms();
      } else {
        GroupId.Mms groupId = GroupId.createMms(new SecureRandom());
        create(groupId, null, members);
        return groupId;
      }
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  @WorkerThread
  public List<String> getPushGroupNamesContainingMember(@NonNull RecipientId recipientId) {
    return Stream.of(getPushGroupsContainingMember(recipientId))
                 .map(groupRecord -> Recipient.resolved(groupRecord.getRecipientId()).getDisplayName(context))
                 .toList();
  }

  @WorkerThread
  public @NonNull List<GroupRecord> getPushGroupsContainingMember(@NonNull RecipientId recipientId) {
    return getGroupsContainingMember(recipientId, true);
  }

  public @NonNull List<GroupRecord> getGroupsContainingMember(@NonNull RecipientId recipientId, boolean pushOnly) {
    return getGroupsContainingMember(recipientId, pushOnly, false);
  }

  @WorkerThread
  public @NonNull List<GroupRecord> getGroupsContainingMember(@NonNull RecipientId recipientId, boolean pushOnly, boolean includeInactive) {
    SQLiteDatabase database   = databaseHelper.getSignalReadableDatabase();
    String         table      = TABLE_NAME + " INNER JOIN " + ThreadTable.TABLE_NAME + " ON " + TABLE_NAME + "." + RECIPIENT_ID + " = " + ThreadTable.TABLE_NAME + "." + ThreadTable.RECIPIENT_ID;
    String         query      = MEMBERS + " LIKE ?";
    String[]       args       = SqlUtil.buildArgs("%" + recipientId.serialize() + "%");
    String         orderBy    = ThreadTable.TABLE_NAME + "." + ThreadTable.DATE + " DESC";

    if (pushOnly) {
      query += " AND " + MMS + " = ?";
      args = SqlUtil.appendArg(args, "0");
    }

    if (!includeInactive) {
      query += " AND " + ACTIVE + " = ?";
      args = SqlUtil.appendArg(args, "1");
    }

    List<GroupRecord> groups = new LinkedList<>();

    try (Cursor cursor = database.query(table, null, query, args, null, null, orderBy)) {
      while (cursor != null && cursor.moveToNext()) {
        String serializedMembers = cursor.getString(cursor.getColumnIndexOrThrow(MEMBERS));

        if (RecipientId.serializedListContains(serializedMembers, recipientId)) {
          groups.add(new Reader(cursor).getCurrent());
        }
      }
    }

    return groups;
  }

  public Reader getGroups() {
    @SuppressLint("Recycle")
    Cursor cursor = databaseHelper.getSignalReadableDatabase().query(TABLE_NAME, null, null, null, null, null, null);
    return new Reader(cursor);
  }

  public int getActiveGroupCount() {
    SQLiteDatabase db    = databaseHelper.getSignalReadableDatabase();
    String[]       cols  = { "COUNT(*)" };
    String         query = ACTIVE + " = 1";

    try (Cursor cursor = db.query(TABLE_NAME, cols, query, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }

    return 0;
  }

  @WorkerThread
  public @NonNull List<RecipientId> getGroupMemberIds(@NonNull GroupId groupId, @NonNull MemberSet memberSet) {
    if (groupId.isV2()) {
      return getGroup(groupId).map(g -> g.requireV2GroupProperties().getMemberRecipientIds(memberSet))
                              .orElse(Collections.emptyList());
    } else {
      List<RecipientId> currentMembers = getCurrentMembers(groupId);

      if (!memberSet.includeSelf) {
        currentMembers.remove(Recipient.self().getId());
      }

      return currentMembers;
    }
  }

  @WorkerThread
  public @NonNull List<Recipient> getGroupMembers(@NonNull GroupId groupId, @NonNull MemberSet memberSet) {
    if (groupId.isV2()) {
      return getGroup(groupId).map(g -> g.requireV2GroupProperties().getMemberRecipients(memberSet))
                              .orElse(Collections.emptyList());
    } else {
      List<RecipientId> currentMembers = getCurrentMembers(groupId);
      List<Recipient>   recipients     = new ArrayList<>(currentMembers.size());

      for (RecipientId member : currentMembers) {
        Recipient resolved = Recipient.resolved(member);
        if (memberSet.includeSelf || !resolved.isSelf()) {
          recipients.add(resolved);
        }
      }

      return recipients;
    }
  }

  public void create(@NonNull GroupId.V1 groupId,
                     @Nullable String title,
                     @NonNull Collection<RecipientId> members,
                     @Nullable SignalServiceAttachmentPointer avatar,
                     @Nullable String relay)
  {
    if (groupExists(groupId.deriveV2MigrationGroupId())) {
      throw new LegacyGroupInsertException(groupId);
    }
    create(groupId, title, members, avatar, relay, null, null);
  }

  public void create(@NonNull GroupId.Mms groupId,
                     @Nullable String title,
                     @NonNull Collection<RecipientId> members)
  {
    create(groupId, Util.isEmpty(title) ? null : title, members, null, null, null, null);
  }

  public GroupId.V2 create(@NonNull GroupMasterKey groupMasterKey,
                           @NonNull DecryptedGroup groupState)
  {
    return create(groupMasterKey, groupState, false);
  }

  public GroupId.V2 create(@NonNull GroupMasterKey groupMasterKey,
                           @NonNull DecryptedGroup groupState,
                           boolean force)
  {
    GroupId.V2 groupId = GroupId.v2(groupMasterKey);

    if (!force && getGroupV1ByExpectedV2(groupId).isPresent()) {
      throw new MissedGroupMigrationInsertException(groupId);
    } else if (force) {
      Log.w(TAG, "Forcing the creation of a group even though we already have a V1 ID!");
    }

    create(groupId, groupState.getTitle(), Collections.emptyList(), null, null, groupMasterKey, groupState);

    return groupId;
  }

  /**
   * There was a point in time where we weren't properly responding to group creates on linked devices. This would result in us having a Recipient entry for the
   * group, but we'd either be missing the group entry, or that entry would be missing a master key. This method fixes this scenario.
   */
  public void fixMissingMasterKey(@Nullable ServiceId authServiceId, @NonNull GroupMasterKey groupMasterKey) {
    GroupId.V2 groupId = GroupId.v2(groupMasterKey);

    if (getGroupV1ByExpectedV2(groupId).isPresent()) {
      Log.w(TAG, "There already exists a V1 group that should be migrated into this group. But if the recipient already exists, there's not much we can do here.");
    }

    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    db.beginTransaction();
    try {
      String        query  = GROUP_ID + " = ?";
      String[]      args   = SqlUtil.buildArgs(groupId);
      ContentValues values = new ContentValues();

      values.put(V2_MASTER_KEY, groupMasterKey.serialize());

      int updated = db.update(TABLE_NAME, values, query, args);

      if (updated < 1) {
        Log.w(TAG, "No group entry. Creating restore placeholder for " + groupId);
        create(
            groupMasterKey,
               DecryptedGroup.newBuilder()
                             .setRevision(GroupsV2StateProcessor.RESTORE_PLACEHOLDER_REVISION)
                             .build(),
               true);
      } else {
        Log.w(TAG, "Had a group entry, but it was missing a master key. Updated.");
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    Log.w(TAG, "Scheduling request for latest group info for " + groupId);
    ApplicationDependencies.getJobManager().add(new RequestGroupV2InfoJob(groupId));
  }

  /**
   * @param groupMasterKey null for V1, must be non-null for V2 (presence dictates group version).
   */
  private void create(@NonNull GroupId groupId,
                      @Nullable String title,
                      @NonNull Collection<RecipientId> memberCollection,
                      @Nullable SignalServiceAttachmentPointer avatar,
                      @Nullable String relay,
                      @Nullable GroupMasterKey groupMasterKey,
                      @Nullable DecryptedGroup groupState)
  {
    RecipientTable    recipientTable   = SignalDatabase.recipients();
    RecipientId       groupRecipientId = recipientTable.getOrInsertFromGroupId(groupId);
    List<RecipientId> members          = new ArrayList<>(new HashSet<>(memberCollection));

    Collections.sort(members);

    ContentValues contentValues = new ContentValues();
    contentValues.put(RECIPIENT_ID, groupRecipientId.serialize());
    contentValues.put(GROUP_ID, groupId.toString());
    contentValues.put(TITLE, title);
    contentValues.put(MEMBERS, RecipientId.toSerializedList(members));

    if (avatar != null) {
      contentValues.put(AVATAR_ID, avatar.getRemoteId().getV2().get());
      contentValues.put(AVATAR_KEY, avatar.getKey());
      contentValues.put(AVATAR_CONTENT_TYPE, avatar.getContentType());
      contentValues.put(AVATAR_DIGEST, avatar.getDigest().orElse(null));
    } else {
      contentValues.put(AVATAR_ID, 0);
    }

    contentValues.put(AVATAR_RELAY, relay);
    contentValues.put(TIMESTAMP, System.currentTimeMillis());

    if (groupId.isV2()) {
      contentValues.put(ACTIVE, groupState != null && gv2GroupActive(groupState) ? 1 : 0);
      contentValues.put(DISTRIBUTION_ID, DistributionId.create().toString());
    } else if (groupId.isV1()) {
      contentValues.put(ACTIVE, 1);
      contentValues.put(EXPECTED_V2_ID, groupId.requireV1().deriveV2MigrationGroupId().toString());
    } else {
      contentValues.put(ACTIVE, 1);
    }

    contentValues.put(MMS, groupId.isMms());

    List<RecipientId> groupMembers = members;
    if (groupMasterKey != null) {
      if (groupState == null) {
        throw new AssertionError("V2 master key but no group state");
      }
      groupId.requireV2();
      groupMembers = getV2GroupMembers(groupState, true);
      contentValues.put(V2_MASTER_KEY, groupMasterKey.serialize());
      contentValues.put(V2_REVISION, groupState.getRevision());
      contentValues.put(V2_DECRYPTED_GROUP, groupState.toByteArray());
      contentValues.put(MEMBERS, RecipientId.toSerializedList(groupMembers));
    } else {
      if (groupId.isV2()) {
        throw new AssertionError("V2 group id but no master key");
      }
    }

    databaseHelper.getSignalWritableDatabase().insert(TABLE_NAME, null, contentValues);

    if (groupState != null && groupState.hasDisappearingMessagesTimer()) {
      recipientTable.setExpireMessages(groupRecipientId, groupState.getDisappearingMessagesTimer().getDuration());
    }

    if (groupMembers != null && (groupId.isMms() || Recipient.resolved(groupRecipientId).isProfileSharing())) {
      recipientTable.setHasGroupsInCommon(groupMembers);
    }

    Recipient.live(groupRecipientId).refresh();

    notifyConversationListListeners();
  }

  public void update(@NonNull GroupId.V1 groupId,
                     @Nullable String title,
                     @Nullable SignalServiceAttachmentPointer avatar)
  {
    ContentValues contentValues = new ContentValues();
    if (title != null) contentValues.put(TITLE, title);

    if (avatar != null) {
      contentValues.put(AVATAR_ID, avatar.getRemoteId().getV2().get());
      contentValues.put(AVATAR_CONTENT_TYPE, avatar.getContentType());
      contentValues.put(AVATAR_KEY, avatar.getKey());
      contentValues.put(AVATAR_DIGEST, avatar.getDigest().orElse(null));
    } else {
      contentValues.put(AVATAR_ID, 0);
    }

    databaseHelper.getSignalWritableDatabase().update(TABLE_NAME, contentValues,
                                                GROUP_ID + " = ?",
                                                new String[] {groupId.toString()});

    RecipientId groupRecipient = SignalDatabase.recipients().getOrInsertFromGroupId(groupId);
    Recipient.live(groupRecipient).refresh();

    notifyConversationListListeners();
  }

  /**
   * Migrates a V1 group to a V2 group.
   *
   * @param decryptedGroup The state that represents the group on the server. This will be used to
   *                       determine if we need to save our old membership list and stuff.
   */
  public @NonNull GroupId.V2 migrateToV2(long threadId,
                                         @NonNull GroupId.V1 groupIdV1,
                                         @NonNull DecryptedGroup decryptedGroup)
  {
    SQLiteDatabase db             = databaseHelper.getSignalWritableDatabase();
    GroupId.V2     groupIdV2      = groupIdV1.deriveV2MigrationGroupId();
    GroupMasterKey groupMasterKey = groupIdV1.deriveV2MigrationMasterKey();

    db.beginTransaction();
    try {
      GroupRecord record = getGroup(groupIdV1).get();

      ContentValues contentValues = new ContentValues();
      contentValues.put(GROUP_ID, groupIdV2.toString());
      contentValues.put(V2_MASTER_KEY, groupMasterKey.serialize());
      contentValues.put(DISTRIBUTION_ID, DistributionId.create().toString());
      contentValues.putNull(EXPECTED_V2_ID);

      List<RecipientId> newMembers     = uuidsToRecipientIds(DecryptedGroupUtil.membersToUuidList(decryptedGroup.getMembersList()));
      List<RecipientId> pendingMembers = uuidsToRecipientIds(DecryptedGroupUtil.pendingToUuidList(decryptedGroup.getPendingMembersList()));

      newMembers.addAll(pendingMembers);

      List<RecipientId> droppedMembers    = new ArrayList<>(SetUtil.difference(record.getMembers(), newMembers));
      List<RecipientId> unmigratedMembers = Util.concatenatedList(pendingMembers, droppedMembers);

      contentValues.put(UNMIGRATED_V1_MEMBERS, unmigratedMembers.isEmpty() ? null : RecipientId.toSerializedList(unmigratedMembers));

      int updated = db.update(TABLE_NAME, contentValues, GROUP_ID + " = ?", SqlUtil.buildArgs(groupIdV1.toString()));

      if (updated != 1) {
        throw new AssertionError();
      }

      SignalDatabase.recipients().updateGroupId(groupIdV1, groupIdV2);

      update(groupMasterKey, decryptedGroup);

      SignalDatabase.sms().insertGroupV1MigrationEvents(record.getRecipientId(),
                                                        threadId,
                                                        new GroupMigrationMembershipChange(pendingMembers, droppedMembers));

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    return groupIdV2;
  }

  public void update(@NonNull GroupMasterKey groupMasterKey, @NonNull DecryptedGroup decryptedGroup) {
    update(GroupId.v2(groupMasterKey), decryptedGroup);
  }

  public void update(@NonNull GroupId.V2 groupId, @NonNull DecryptedGroup decryptedGroup) {
    RecipientTable        recipientTable   = SignalDatabase.recipients();
    RecipientId           groupRecipientId = recipientTable.getOrInsertFromGroupId(groupId);
    Optional<GroupRecord> existingGroup    = getGroup(groupId);
    String                title               = decryptedGroup.getTitle();
    ContentValues         contentValues       = new ContentValues();

    if (existingGroup.isPresent() && existingGroup.get().getUnmigratedV1Members().size() > 0 && existingGroup.get().isV2Group()) {
      Set<RecipientId> unmigratedV1Members = new HashSet<>(existingGroup.get().getUnmigratedV1Members());

      DecryptedGroupChange change = GroupChangeReconstruct.reconstructGroupChange(existingGroup.get().requireV2GroupProperties().getDecryptedGroup(), decryptedGroup);

      List<RecipientId> addedMembers    = uuidsToRecipientIds(DecryptedGroupUtil.membersToUuidList(change.getNewMembersList()));
      List<RecipientId> removedMembers  = uuidsToRecipientIds(DecryptedGroupUtil.removedMembersUuidList(change));
      List<RecipientId> addedInvites    = uuidsToRecipientIds(DecryptedGroupUtil.pendingToUuidList(change.getNewPendingMembersList()));
      List<RecipientId> removedInvites  = uuidsToRecipientIds(DecryptedGroupUtil.removedPendingMembersUuidList(change));
      List<RecipientId> acceptedInvites = uuidsToRecipientIds(DecryptedGroupUtil.membersToUuidList(change.getPromotePendingMembersList()));

      unmigratedV1Members.removeAll(addedMembers);
      unmigratedV1Members.removeAll(removedMembers);
      unmigratedV1Members.removeAll(addedInvites);
      unmigratedV1Members.removeAll(removedInvites);
      unmigratedV1Members.removeAll(acceptedInvites);

      contentValues.put(UNMIGRATED_V1_MEMBERS, unmigratedV1Members.isEmpty() ? null : RecipientId.toSerializedList(unmigratedV1Members));
    }

    List<RecipientId> groupMembers = getV2GroupMembers(decryptedGroup, true);
    contentValues.put(TITLE, title);
    contentValues.put(V2_REVISION, decryptedGroup.getRevision());
    contentValues.put(V2_DECRYPTED_GROUP, decryptedGroup.toByteArray());
    contentValues.put(MEMBERS, RecipientId.toSerializedList(groupMembers));
    contentValues.put(ACTIVE, gv2GroupActive(decryptedGroup) ? 1 : 0);

    DistributionId distributionId = Objects.requireNonNull(existingGroup.get().getDistributionId());

    if (existingGroup.isPresent() && existingGroup.get().isV2Group()) {
      DecryptedGroupChange change  = GroupChangeReconstruct.reconstructGroupChange(existingGroup.get().requireV2GroupProperties().getDecryptedGroup(), decryptedGroup);
      List<UUID>           removed = DecryptedGroupUtil.removedMembersUuidList(change);

      if (removed.size() > 0) {
        Log.i(TAG, removed.size() + " members were removed from group " + groupId + ". Rotating the DistributionId " + distributionId);
        SenderKeyUtil.rotateOurKey(distributionId);
      }
    }

    databaseHelper.getSignalWritableDatabase().update(TABLE_NAME, contentValues,
                                                GROUP_ID + " = ?",
                                                new String[]{ groupId.toString() });

    if (decryptedGroup.hasDisappearingMessagesTimer()) {
      recipientTable.setExpireMessages(groupRecipientId, decryptedGroup.getDisappearingMessagesTimer().getDuration());
    }

    if (groupId.isMms() || Recipient.resolved(groupRecipientId).isProfileSharing()) {
      recipientTable.setHasGroupsInCommon(groupMembers);
    }

    Recipient.live(groupRecipientId).refresh();

    notifyConversationListListeners();
  }

  public void updateTitle(@NonNull GroupId.V1 groupId, String title) {
    updateTitle((GroupId) groupId, title);
  }

  public void updateTitle(@NonNull GroupId.Mms groupId, @Nullable String title) {
    updateTitle((GroupId) groupId, Util.isEmpty(title) ? null : title);
  }

  private void updateTitle(@NonNull GroupId groupId, String title) {
    if (!groupId.isV1() && !groupId.isMms()) {
      throw new AssertionError();
    }

    ContentValues contentValues = new ContentValues();
    contentValues.put(TITLE, title);
    databaseHelper.getSignalWritableDatabase().update(TABLE_NAME, contentValues, GROUP_ID +  " = ?",
                                                new String[] {groupId.toString()});

    RecipientId groupRecipient = SignalDatabase.recipients().getOrInsertFromGroupId(groupId);
    Recipient.live(groupRecipient).refresh();
  }

  /**
   * Used to bust the Glide cache when an avatar changes.
   */
  public void onAvatarUpdated(@NonNull GroupId groupId, boolean hasAvatar) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(AVATAR_ID, hasAvatar ? Math.abs(new SecureRandom().nextLong()) : 0);

    databaseHelper.getSignalWritableDatabase().update(TABLE_NAME, contentValues, GROUP_ID +  " = ?",
                                                new String[] {groupId.toString()});

    RecipientId groupRecipient = SignalDatabase.recipients().getOrInsertFromGroupId(groupId);
    Recipient.live(groupRecipient).refresh();
  }

  public void updateMembers(@NonNull GroupId groupId, List<RecipientId> members) {
    Collections.sort(members);

    ContentValues contents = new ContentValues();
    contents.put(MEMBERS, RecipientId.toSerializedList(members));
    contents.put(ACTIVE, 1);

    databaseHelper.getSignalWritableDatabase().update(TABLE_NAME, contents, GROUP_ID + " = ?",
                                                new String[] {groupId.toString()});

    RecipientId groupRecipient = SignalDatabase.recipients().getOrInsertFromGroupId(groupId);
    Recipient.live(groupRecipient).refresh();
  }

  public void remove(@NonNull GroupId groupId, RecipientId source) {
    List<RecipientId> currentMembers = getCurrentMembers(groupId);
    currentMembers.remove(source);

    ContentValues contents = new ContentValues();
    contents.put(MEMBERS, RecipientId.toSerializedList(currentMembers));

    databaseHelper.getSignalWritableDatabase().update(TABLE_NAME, contents, GROUP_ID + " = ?",
                                                new String[] {groupId.toString()});

    RecipientId groupRecipient = SignalDatabase.recipients().getOrInsertFromGroupId(groupId);
    Recipient.live(groupRecipient).refresh();
  }

  private static boolean gv2GroupActive(@NonNull DecryptedGroup decryptedGroup) {
    ACI aci = SignalStore.account().requireAci();

    return DecryptedGroupUtil.findMemberByUuid(decryptedGroup.getMembersList(), aci.uuid()).isPresent() ||
           DecryptedGroupUtil.findPendingByUuid(decryptedGroup.getPendingMembersList(), aci.uuid()).isPresent();
  }

  private List<RecipientId> getCurrentMembers(@NonNull GroupId groupId) {
    Cursor cursor = null;

    try {
      cursor = databaseHelper.getSignalReadableDatabase().query(TABLE_NAME, new String[] { MEMBERS},
                                                          GROUP_ID + " = ?",
                                                                new String[] {groupId.toString()},
                                                                null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        String serializedMembers = cursor.getString(cursor.getColumnIndexOrThrow(MEMBERS));
        return RecipientId.fromSerializedList(serializedMembers);
      }

      return new LinkedList<>();
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public boolean isActive(@NonNull GroupId groupId) {
    Optional<GroupRecord> record = getGroup(groupId);
    return record.isPresent() && record.get().isActive();
  }

  public void setActive(@NonNull GroupId groupId, boolean active) {
    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
    ContentValues  values   = new ContentValues();
    values.put(ACTIVE, active ? 1 : 0);
    database.update(TABLE_NAME, values, GROUP_ID + " = ?", new String[] {groupId.toString()});
  }

  public void setLastForceUpdateTimestamp(@NonNull GroupId groupId, long timestamp) {
    ContentValues values = new ContentValues();
    values.put(LAST_FORCE_UPDATE_TIMESTAMP, timestamp);
    getWritableDatabase().update(TABLE_NAME, values, GROUP_ID + " = ?", SqlUtil.buildArgs(groupId));
  }

  @WorkerThread
  public boolean isCurrentMember(@NonNull GroupId.Push groupId, @NonNull RecipientId recipientId) {
    SQLiteDatabase database = databaseHelper.getSignalReadableDatabase();

    try (Cursor cursor = database.query(TABLE_NAME, new String[] {MEMBERS},
                                        GROUP_ID + " = ?", new String[] {groupId.toString()},
                                        null, null, null))
    {
      if (cursor.moveToNext()) {
        String serializedMembers = cursor.getString(cursor.getColumnIndexOrThrow(MEMBERS));
        return RecipientId.serializedListContains(serializedMembers, recipientId);
      } else {
        return false;
      }
    }
  }


  private static @NonNull List<RecipientId> uuidsToRecipientIds(@NonNull List<UUID> uuids) {
    List<RecipientId> groupMembers = new ArrayList<>(uuids.size());

    for (UUID uuid : uuids) {
      if (UuidUtil.UNKNOWN_UUID.equals(uuid)) {
        Log.w(TAG, "Seen unknown UUID in members list");
      } else {
        RecipientId           id       = RecipientId.from(ServiceId.from(uuid));
        Optional<RecipientId> remapped = RemappedRecords.getInstance().getRecipient(id);

        if (remapped.isPresent()) {
          Log.w(TAG, "Saw that " + id + " remapped to " + remapped + ". Using the mapping.");
          groupMembers.add(remapped.get());
        } else {
          groupMembers.add(id);
        }
      }
    }

    Collections.sort(groupMembers);

    return groupMembers;
  }

  private static @NonNull List<RecipientId> getV2GroupMembers(@NonNull DecryptedGroup decryptedGroup, boolean shouldRetry) {
    List<UUID>        uuids = DecryptedGroupUtil.membersToUuidList(decryptedGroup.getMembersList());
    List<RecipientId> ids   = uuidsToRecipientIds(uuids);

    if (RemappedRecords.getInstance().areAnyRemapped(ids)) {
      if (shouldRetry) {
        Log.w(TAG, "Found remapped records where we shouldn't. Clearing cache and trying again.");
        RecipientId.clearCache();
        RemappedRecords.getInstance().resetCache();
        return getV2GroupMembers(decryptedGroup, false);
      } else {
        throw new IllegalStateException("Remapped records in group membership!");
      }
    } else {
      return ids;
    }
  }

  public @NonNull List<GroupId.V2> getAllGroupV2Ids() {
    List<GroupId.V2> result = new LinkedList<>();

    try (Cursor cursor = databaseHelper.getSignalReadableDatabase().query(TABLE_NAME, new String[]{ GROUP_ID }, null, null, null, null, null)) {
      while (cursor.moveToNext()) {
        GroupId groupId = GroupId.parseOrThrow(cursor.getString(cursor.getColumnIndexOrThrow(GROUP_ID)));
        if (groupId.isV2()) {
          result.add(groupId.requireV2());
        }
      }
    }

    return result;
  }

  /**
   * Key: The 'expected' V2 ID (i.e. what a V1 ID would map to when migrated)
   * Value: The matching V1 ID
   */
  public @NonNull Map<GroupId.V2, GroupId.V1> getAllExpectedV2Ids() {
    Map<GroupId.V2, GroupId.V1> result = new HashMap<>();

    String[] projection = new String[]{ GROUP_ID, EXPECTED_V2_ID };
    String   query      = EXPECTED_V2_ID + " NOT NULL";

    try (Cursor cursor = databaseHelper.getSignalReadableDatabase().query(TABLE_NAME, projection, query, null, null, null, null)) {
      while (cursor.moveToNext()) {
        GroupId.V1 groupId    = GroupId.parseOrThrow(cursor.getString(cursor.getColumnIndexOrThrow(GROUP_ID))).requireV1();
        GroupId.V2 expectedId = GroupId.parseOrThrow(cursor.getString(cursor.getColumnIndexOrThrow(EXPECTED_V2_ID))).requireV2();

        result.put(expectedId, groupId);
      }
    }

    return result;
  }

  @Override
  public void remapRecipient(@NonNull RecipientId fromId, @NonNull RecipientId toId) {
    for (GroupRecord group : getGroupsContainingMember(fromId, false, true)) {
      Set<RecipientId> newMembers = new LinkedHashSet<>(group.getMembers());
      newMembers.remove(fromId);
      newMembers.add(toId);

      ContentValues groupValues = new ContentValues();
      groupValues.put(GroupTable.MEMBERS, RecipientId.toSerializedList(newMembers));

      getWritableDatabase().update(GroupTable.TABLE_NAME, groupValues, GroupTable.RECIPIENT_ID + " = ?", SqlUtil.buildArgs(group.recipientId));

      if (group.isV2Group()) {
        removeUnmigratedV1Members(group.id.requireV2(), Collections.singletonList(fromId));
      }
    }
  }

  public static class Reader implements Closeable, ContactSearchIterator<GroupRecord> {

    public final Cursor cursor;

    public Reader(Cursor cursor) {
      this.cursor = cursor;
    }

    public @Nullable GroupRecord getNext() {
      if (cursor == null || !cursor.moveToNext()) {
        return null;
      }

      return getCurrent();
    }

    public int getCount() {
      if (cursor == null) {
        return 0;
      } else {
        return cursor.getCount();
      }
    }

    public @Nullable GroupRecord getCurrent() {
      if (cursor == null || cursor.getString(cursor.getColumnIndexOrThrow(GROUP_ID)) == null || cursor.getLong(cursor.getColumnIndexOrThrow(RECIPIENT_ID)) == 0) {
        return null;
      }

      return new GroupRecord(GroupId.parseOrThrow(CursorUtil.requireString(cursor, GROUP_ID)),
                             RecipientId.from(CursorUtil.requireString(cursor, RECIPIENT_ID)),
                             CursorUtil.requireString(cursor, TITLE),
                             CursorUtil.requireString(cursor, MEMBERS),
                             CursorUtil.requireString(cursor, UNMIGRATED_V1_MEMBERS),
                             CursorUtil.requireLong(cursor, AVATAR_ID),
                             CursorUtil.requireBlob(cursor, AVATAR_KEY),
                             CursorUtil.requireString(cursor, AVATAR_CONTENT_TYPE),
                             CursorUtil.requireString(cursor, AVATAR_RELAY),
                             CursorUtil.requireBoolean(cursor, ACTIVE),
                             CursorUtil.requireBlob(cursor, AVATAR_DIGEST),
                             CursorUtil.requireBoolean(cursor, MMS),
                             CursorUtil.requireBlob(cursor, V2_MASTER_KEY),
                             CursorUtil.requireInt(cursor, V2_REVISION),
                             CursorUtil.requireBlob(cursor, V2_DECRYPTED_GROUP),
                             CursorUtil.getString(cursor, DISTRIBUTION_ID).map(DistributionId::from).orElse(null),
                             CursorUtil.requireLong(cursor, LAST_FORCE_UPDATE_TIMESTAMP));
    }

    @Override
    public void close() {
      if (this.cursor != null)
        this.cursor.close();
    }

    @Override
    public void moveToPosition(int n) {
      cursor.moveToPosition(n);
    }

    @Override
    public boolean hasNext() {
      return !cursor.isLast() && !cursor.isAfterLast();
    }

    @Override
    public GroupRecord next() {
      return getNext();
    }
  }

  public static class GroupRecord {

              private final GroupId           id;
              private final RecipientId       recipientId;
              private final String            title;
              private final List<RecipientId> members;
              private final List<RecipientId> unmigratedV1Members;
              private final long              avatarId;
              private final byte[]            avatarKey;
              private final byte[]            avatarDigest;
              private final String            avatarContentType;
              private final String            relay;
              private final boolean           active;
              private final boolean           mms;
    @Nullable private final V2GroupProperties v2GroupProperties;
              private final DistributionId    distributionId;
              private final long              lastForceUpdateTimestamp;

    public GroupRecord(@NonNull GroupId id,
                       @NonNull RecipientId recipientId,
                       String title,
                       String members,
                       @Nullable String unmigratedV1Members,
                       long avatarId,
                       byte[] avatarKey,
                       String avatarContentType,
                       String relay,
                       boolean active,
                       byte[] avatarDigest,
                       boolean mms,
                       @Nullable byte[] groupMasterKeyBytes,
                       int groupRevision,
                       @Nullable byte[] decryptedGroupBytes,
                       @Nullable DistributionId distributionId,
                       long lastForceUpdateTimestamp)
    {
      this.id                       = id;
      this.recipientId              = recipientId;
      this.title                    = title;
      this.avatarId                 = avatarId;
      this.avatarKey                = avatarKey;
      this.avatarDigest             = avatarDigest;
      this.avatarContentType        = avatarContentType;
      this.relay                    = relay;
      this.active                   = active;
      this.mms                      = mms;
      this.distributionId           = distributionId;
      this.lastForceUpdateTimestamp = lastForceUpdateTimestamp;

      V2GroupProperties v2GroupProperties = null;
      if (groupMasterKeyBytes != null && decryptedGroupBytes != null) {
        GroupMasterKey groupMasterKey;
        try {
          groupMasterKey = new GroupMasterKey(groupMasterKeyBytes);
        } catch (InvalidInputException e) {
          throw new AssertionError(e);
        }
        v2GroupProperties = new V2GroupProperties(groupMasterKey, groupRevision, decryptedGroupBytes);
      }
      this.v2GroupProperties = v2GroupProperties;

      if (!TextUtils.isEmpty(members)) {
        this.members = RecipientId.fromSerializedList(members);
      } else {
        this.members = Collections.emptyList();
      }

      if (!TextUtils.isEmpty(unmigratedV1Members)) {
        this.unmigratedV1Members = RecipientId.fromSerializedList(unmigratedV1Members);
      } else {
        this.unmigratedV1Members = Collections.emptyList();
      }
    }

    public GroupId getId() {
      return id;
    }

    public @NonNull RecipientId getRecipientId() {
      return recipientId;
    }

    public String getTitle() {
      return title;
    }

    public @NonNull String getDescription() {
      if (v2GroupProperties != null) {
        return v2GroupProperties.getDecryptedGroup().getDescription();
      } else {
        return "";
      }
    }

    public boolean isAnnouncementGroup() {
      if (v2GroupProperties != null) {
        return v2GroupProperties.getDecryptedGroup().getIsAnnouncementGroup() == EnabledState.ENABLED;
      } else {
        return false;
      }
    }

    public @NonNull List<RecipientId> getMembers() {
      return members;
    }

    @WorkerThread
    public @NonNull List<Recipient> getAdmins() {
      if (v2GroupProperties != null) {
        return v2GroupProperties.getAdmins(members.stream().map(Recipient::resolved).collect(Collectors.toList()));
      } else {
        return Collections.emptyList();
      }
    }

    /** V1 members that were lost during the V1->V2 migration */
    public @NonNull List<RecipientId> getUnmigratedV1Members() {
      return unmigratedV1Members;
    }

    public boolean hasAvatar() {
      return avatarId != 0;
    }

    public long getAvatarId() {
      return avatarId;
    }

    public byte[] getAvatarKey() {
      return avatarKey;
    }

    public byte[] getAvatarDigest() {
      return avatarDigest;
    }

    public String getAvatarContentType() {
      return avatarContentType;
    }

    public String getRelay() {
      return relay;
    }

    public boolean isActive() {
      return active;
    }

    public boolean isMms() {
      return mms;
    }

    public @Nullable DistributionId getDistributionId() {
      return distributionId;
    }

    public long getLastForceUpdateTimestamp() {
      return lastForceUpdateTimestamp;
    }

    public boolean isV1Group() {
      return !mms && !isV2Group();
    }

    public boolean isV2Group() {
      return v2GroupProperties != null;
    }

    public @NonNull V2GroupProperties requireV2GroupProperties() {
      if (v2GroupProperties == null) {
        throw new AssertionError();
      }

      return v2GroupProperties;
    }

    public boolean isAdmin(@NonNull Recipient recipient) {
      return isV2Group() && requireV2GroupProperties().isAdmin(recipient);
    }

    public MemberLevel memberLevel(@NonNull Recipient recipient) {
      if (isV2Group()) {
        MemberLevel memberLevel = requireV2GroupProperties().memberLevel(recipient.getServiceId());
        if (recipient.isSelf() && memberLevel == MemberLevel.NOT_A_MEMBER) {
          memberLevel = requireV2GroupProperties().memberLevel(Optional.ofNullable(SignalStore.account().getPni()));
        }
        return memberLevel;
      } else if (isMms() && recipient.isSelf()) {
        return MemberLevel.FULL_MEMBER;
      } else {
        return members.contains(recipient.getId()) ? MemberLevel.FULL_MEMBER
                                                   : MemberLevel.NOT_A_MEMBER;
      }
    }

    /**
     * Who is allowed to add to the membership of this group.
     */
    public GroupAccessControl getMembershipAdditionAccessControl() {
      if (isV2Group()) {
        if (requireV2GroupProperties().getDecryptedGroup().getAccessControl().getMembers() == AccessControl.AccessRequired.MEMBER) {
          return GroupAccessControl.ALL_MEMBERS;
        }
        return GroupAccessControl.ONLY_ADMINS;
      } else if (isV1Group()) {
        return GroupAccessControl.NO_ONE;
      } else {
        return id.isV1() ? GroupAccessControl.ALL_MEMBERS : GroupAccessControl.ONLY_ADMINS;
      }
    }

    /**
     * Who is allowed to modify the attributes of this group, name/avatar/timer etc.
     */
    public GroupAccessControl getAttributesAccessControl() {
      if (isV2Group()) {
        if (requireV2GroupProperties().getDecryptedGroup().getAccessControl().getAttributes() == AccessControl.AccessRequired.MEMBER) {
          return GroupAccessControl.ALL_MEMBERS;
        }
        return GroupAccessControl.ONLY_ADMINS;
      } else if (isV1Group()) {
        return GroupAccessControl.NO_ONE;
      } else {
        return GroupAccessControl.ALL_MEMBERS;
      }
    }

    /**
     * Whether or not the recipient is a pending member.
     */
    public boolean isPendingMember(@NonNull Recipient recipient) {
      if (isV2Group()) {
        Optional<ServiceId> serviceId = recipient.getServiceId();
        if (serviceId.isPresent()) {
          return DecryptedGroupUtil.findPendingByUuid(requireV2GroupProperties().getDecryptedGroup().getPendingMembersList(), serviceId.get().uuid())
                                   .isPresent();
        }
      }
      return false;
    }
  }

  public static class V2GroupProperties {

    @NonNull private final GroupMasterKey groupMasterKey;
             private final int            groupRevision;
    @NonNull private final byte[]         decryptedGroupBytes;
             private       DecryptedGroup decryptedGroup;

    private V2GroupProperties(@NonNull GroupMasterKey groupMasterKey, int groupRevision, @NonNull byte[] decryptedGroup) {
      this.groupMasterKey      = groupMasterKey;
      this.groupRevision       = groupRevision;
      this.decryptedGroupBytes = decryptedGroup;
    }

    public @NonNull GroupMasterKey getGroupMasterKey() {
      return groupMasterKey;
    }

    public int getGroupRevision() {
      return groupRevision;
    }

    public @NonNull DecryptedGroup getDecryptedGroup() {
      try {
        if (decryptedGroup == null) {
          decryptedGroup = DecryptedGroup.parseFrom(decryptedGroupBytes);
        }
        return decryptedGroup;
      } catch (InvalidProtocolBufferException e) {
        throw new AssertionError(e);
      }
    }

    public boolean isAdmin(@NonNull Recipient recipient) {
      Optional<ServiceId> serviceId = recipient.getServiceId();

      if (!serviceId.isPresent()) {
        return false;
      }

      return DecryptedGroupUtil.findMemberByUuid(getDecryptedGroup().getMembersList(), serviceId.get().uuid())
                               .map(t -> t.getRole() == Member.Role.ADMINISTRATOR)
                               .orElse(false);
    }

    public @NonNull List<Recipient> getAdmins(@NonNull List<Recipient> members) {
      return members.stream().filter(this::isAdmin).collect(Collectors.toList());
    }

    public MemberLevel memberLevel(@NonNull Optional<ServiceId> serviceId) {
      if (!serviceId.isPresent()) {
        return MemberLevel.NOT_A_MEMBER;
      }

      DecryptedGroup decryptedGroup = getDecryptedGroup();

      return DecryptedGroupUtil.findMemberByUuid(decryptedGroup.getMembersList(), serviceId.get().uuid())
                               .map(member -> member.getRole() == Member.Role.ADMINISTRATOR
                                                    ? MemberLevel.ADMINISTRATOR
                                                    : MemberLevel.FULL_MEMBER)
                               .orElse(DecryptedGroupUtil.findPendingByUuid(decryptedGroup.getPendingMembersList(), serviceId.get().uuid())
                                                         .map(m -> MemberLevel.PENDING_MEMBER)
                                                         .orElse(DecryptedGroupUtil.findRequestingByUuid(decryptedGroup.getRequestingMembersList(), serviceId.get().uuid())
                                                                                   .map(m -> MemberLevel.REQUESTING_MEMBER)
                                                                                   .orElse(MemberLevel.NOT_A_MEMBER)));
    }

    public List<Recipient> getMemberRecipients(@NonNull MemberSet memberSet) {
      return Recipient.resolvedList(getMemberRecipientIds(memberSet));
    }

    public List<RecipientId> getMemberRecipientIds(@NonNull MemberSet memberSet) {
      boolean           includeSelf    = memberSet.includeSelf;
      DecryptedGroup    groupV2        = getDecryptedGroup();
      UUID              selfUuid       = SignalStore.account().requireAci().uuid();
      List<RecipientId> recipients     = new ArrayList<>(groupV2.getMembersCount() + groupV2.getPendingMembersCount());
      int               unknownMembers = 0;
      int               unknownPending = 0;

      for (UUID uuid : DecryptedGroupUtil.toUuidList(groupV2.getMembersList())) {
        if (UuidUtil.UNKNOWN_UUID.equals(uuid)) {
          unknownMembers++;
        } else if (includeSelf || !selfUuid.equals(uuid)) {
          recipients.add(RecipientId.from(ServiceId.from(uuid)));
        }
      }
      if (memberSet.includePending) {
        for (UUID uuid : DecryptedGroupUtil.pendingToUuidList(groupV2.getPendingMembersList())) {
          if (UuidUtil.UNKNOWN_UUID.equals(uuid)) {
            unknownPending++;
          } else if (includeSelf || !selfUuid.equals(uuid)) {
            recipients.add(RecipientId.from(ServiceId.from(uuid)));
          }
        }
      }

      if ((unknownMembers + unknownPending) > 0) {
        Log.w(TAG, String.format(Locale.US, "Group contains %d + %d unknown pending and full members", unknownPending, unknownMembers));
      }

      return recipients;
    }

    public @NonNull Set<UUID> getBannedMembers() {
      return DecryptedGroupUtil.bannedMembersToUuidSet(getDecryptedGroup().getBannedMembersList());
    }
  }

  public @NonNull List<GroupId> getGroupsToDisplayAsStories() throws BadGroupIdException {
    String query = "SELECT " + GROUP_ID + ", (" +
                   "SELECT " + MmsTable.TABLE_NAME + "." + MmsTable.DATE_RECEIVED + " FROM " + MmsTable.TABLE_NAME +
                   " WHERE " + MmsTable.TABLE_NAME + "." + MmsTable.RECIPIENT_ID + " = " + ThreadTable.TABLE_NAME + "." + ThreadTable.RECIPIENT_ID +
                   " AND " + MmsTable.STORY_TYPE + " > 1 ORDER BY " + MmsTable.TABLE_NAME + "." + MmsTable.DATE_RECEIVED + " DESC LIMIT 1" +
                   ") as active_timestamp" +
                   " FROM " + TABLE_NAME +
                   " INNER JOIN " + ThreadTable.TABLE_NAME + " ON " + ThreadTable.TABLE_NAME + "." + ThreadTable.RECIPIENT_ID + " = " + TABLE_NAME + "." + RECIPIENT_ID +
                   " WHERE " + ACTIVE + " = 1 " +
                   " AND (" +
                   SHOW_AS_STORY_STATE + " = " + ShowAsStoryState.ALWAYS.code +
                   " OR (" + SHOW_AS_STORY_STATE + " = " + ShowAsStoryState.IF_ACTIVE.code + " AND active_timestamp IS NOT NULL)" +
                   ") ORDER BY active_timestamp DESC";

    try (Cursor cursor = getReadableDatabase().query(query)) {
      if (cursor == null || cursor.getCount() == 0) {
        return Collections.emptyList();
      }

      List<GroupId> results = new ArrayList<>(cursor.getCount());
      while (cursor.moveToNext()) {
        results.add(GroupId.parse(CursorUtil.requireString(cursor, GROUP_ID)));
      }

      return results;
    }
  }

  public @NonNull ShowAsStoryState getShowAsStoryState(@NonNull GroupId groupId) {
    String[] projection = SqlUtil.buildArgs(SHOW_AS_STORY_STATE);
    String   where      = GROUP_ID + " = ?";
    String[] whereArgs  = SqlUtil.buildArgs(groupId.toString());

    try (Cursor cursor = getReadableDatabase().query(TABLE_NAME, projection, where, whereArgs, null, null, null)) {
      if (!cursor.moveToFirst()) {
        throw new AssertionError("Group does not exist.");
      }

      int serializedState = CursorUtil.requireInt(cursor, SHOW_AS_STORY_STATE);
      return ShowAsStoryState.deserialize(serializedState);
    }
  }

  public void setShowAsStoryState(@NonNull GroupId groupId, @NonNull ShowAsStoryState showAsStoryState) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(SHOW_AS_STORY_STATE, showAsStoryState.code);

    getWritableDatabase().update(TABLE_NAME, contentValues, GROUP_ID + " = ?", SqlUtil.buildArgs(groupId.toString()));
  }

  public void setShowAsStoryState(@NonNull Collection<RecipientId> recipientIds, @NonNull ShowAsStoryState showAsStoryState) {
    ContentValues       contentValues = new ContentValues(1);
    List<SqlUtil.Query> queries       = SqlUtil.buildCollectionQuery(RECIPIENT_ID, recipientIds);

    contentValues.put(SHOW_AS_STORY_STATE, showAsStoryState.code);

    SQLiteDatabaseExtensionsKt.withinTransaction(getWritableDatabase(), db -> {
        for (SqlUtil.Query query : queries) {
          db.update(TABLE_NAME, contentValues, query.getWhere(), query.getWhereArgs());
        }

        return null;
    });
  }

  public enum MemberSet {
    FULL_MEMBERS_INCLUDING_SELF(true, false),
    FULL_MEMBERS_EXCLUDING_SELF(false, false),
    FULL_MEMBERS_AND_PENDING_INCLUDING_SELF(true, true),
    FULL_MEMBERS_AND_PENDING_EXCLUDING_SELF(false, true);

    private final boolean includeSelf;
    private final boolean includePending;

    MemberSet(boolean includeSelf, boolean includePending) {
      this.includeSelf    = includeSelf;
      this.includePending = includePending;
    }
  }

  /**
   * State object describing whether or not to display a story in a list.
   */
  public enum ShowAsStoryState {
    /**
     * The default value. Display the group as a story if the group has stories in it currently.
     */
    IF_ACTIVE(0),
    /**
     * Always display the group as a story unless explicitly removed. This state is entered if the
     * user sends a story to a group or otherwise explicitly selects it to appear.
     */
    ALWAYS(1),
    /**
     * Never display the story as a group. This state is entered if the user removes the group from
     * their list, and is only navigated away from if the user explicitly adds the group again.
     */
    NEVER(2);

    private final int code;

    ShowAsStoryState(int code) {
      this.code = code;
    }

    private static @NonNull ShowAsStoryState deserialize(int code) {
      switch (code) {
        case 0:
          return IF_ACTIVE;
        case 1:
          return ALWAYS;
        case 2:
          return NEVER;
        default:
          throw new IllegalArgumentException("Unknown code: " + code);
      }
    }
  }

  public enum MemberLevel {
    NOT_A_MEMBER(false),
    PENDING_MEMBER(false),
    REQUESTING_MEMBER(false),
    FULL_MEMBER(true),
    ADMINISTRATOR(true);

    private final boolean inGroup;

    MemberLevel(boolean inGroup){
      this.inGroup = inGroup;
    }

    public boolean isInGroup() {
      return inGroup;
    }
  }

  public static class GroupQuery {
    private final String  searchQuery;
    private final boolean includeInactive;
    private final boolean                includeV1;
    private final boolean                includeMms;
    private final ContactSearchSortOrder sortOrder;

    private GroupQuery(@NonNull Builder builder) {
      this.searchQuery     = builder.searchQuery;
      this.includeInactive = builder.includeInactive;
      this.includeV1       = builder.includeV1;
      this.includeMms      = builder.includeMms;
      this.sortOrder       = builder.sortOrder;
    }

    public static class Builder {
      private String                 searchQuery     = "";
      private boolean                includeInactive = false;
      private boolean                includeV1       = false;
      private boolean                includeMms      = false;
      private ContactSearchSortOrder sortOrder       = ContactSearchSortOrder.NATURAL;

      public @NonNull Builder withSearchQuery(@Nullable String query) {
        this.searchQuery = TextUtils.isEmpty(query) ? "" : query;
        return this;
      }

      public @NonNull Builder withInactiveGroups(boolean includeInactive) {
        this.includeInactive = includeInactive;
        return this;
      }

      public @NonNull Builder withV1Groups(boolean includeV1Groups) {
        this.includeV1 = includeV1Groups;
        return this;
      }

      public @NonNull Builder withMmsGroups(boolean includeMmsGroups) {
        this.includeMms = includeMmsGroups;
        return this;
      }

      public @NonNull Builder withSortOrder(@NonNull ContactSearchSortOrder sortOrder) {
        this.sortOrder = sortOrder;
        return this;
      }

      public GroupQuery build() {
        return new GroupQuery(this);
      }
    }
  }

  public static class LegacyGroupInsertException extends IllegalStateException {
    public LegacyGroupInsertException(@Nullable GroupId id) {
      super("Tried to create a new GV1 entry when we already had a migrated GV2! " + id);
    }
  }

  public static class MissedGroupMigrationInsertException extends IllegalStateException {
    public MissedGroupMigrationInsertException(@Nullable GroupId id) {
      super("Tried to create a new GV2 entry when we already had a V1 group that mapped to the new ID! " + id);
    }
  }
}

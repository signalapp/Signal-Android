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

import net.sqlcipher.database.SQLiteDatabase;

import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.Closeable;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public final class GroupDatabase extends Database {

  private static final String TAG = Log.tag(GroupDatabase.class);

          static final String TABLE_NAME          = "groups";
  private static final String ID                  = "_id";
          static final String GROUP_ID            = "group_id";
          static final String RECIPIENT_ID        = "recipient_id";
  private static final String TITLE               = "title";
  private static final String MEMBERS             = "members";
  private static final String AVATAR_ID           = "avatar_id";
  private static final String AVATAR_KEY          = "avatar_key";
  private static final String AVATAR_CONTENT_TYPE = "avatar_content_type";
  private static final String AVATAR_RELAY        = "avatar_relay";
  private static final String AVATAR_DIGEST       = "avatar_digest";
  private static final String TIMESTAMP           = "timestamp";
          static final String ACTIVE              = "active";
          static final String MMS                 = "mms";

  /* V2 Group columns */
  /** 32 bytes serialized {@link GroupMasterKey} */
  private static final String V2_MASTER_KEY       = "master_key";
  /** Increments with every change to the group */
  private static final String V2_REVISION         = "revision";
  /** Serialized {@link DecryptedGroup} protobuf */
  private static final String V2_DECRYPTED_GROUP  = "decrypted_group";

  public static final String CREATE_TABLE =
      "CREATE TABLE " + TABLE_NAME +
          " (" + ID + " INTEGER PRIMARY KEY, " +
          GROUP_ID + " TEXT, " +
          RECIPIENT_ID + " INTEGER, " +
          TITLE + " TEXT, " +
          MEMBERS + " TEXT, " +
          AVATAR_ID + " INTEGER, " +
          AVATAR_KEY + " BLOB, " +
          AVATAR_CONTENT_TYPE + " TEXT, " +
          AVATAR_RELAY + " TEXT, " +
          TIMESTAMP + " INTEGER, " +
          ACTIVE + " INTEGER DEFAULT 1, " +
          AVATAR_DIGEST + " BLOB, " +
          MMS + " INTEGER DEFAULT 0, " +
          V2_MASTER_KEY + " BLOB, " +
          V2_REVISION + " BLOB, " +
          V2_DECRYPTED_GROUP + " BLOB);";

  public static final String[] CREATE_INDEXS = {
      "CREATE UNIQUE INDEX IF NOT EXISTS group_id_index ON " + TABLE_NAME + " (" + GROUP_ID + ");",
      "CREATE UNIQUE INDEX IF NOT EXISTS group_recipient_id_index ON " + TABLE_NAME + " (" + RECIPIENT_ID + ");",
  };

  private static final String[] GROUP_PROJECTION = {
      GROUP_ID, RECIPIENT_ID, TITLE, MEMBERS, AVATAR_ID, AVATAR_KEY, AVATAR_CONTENT_TYPE, AVATAR_RELAY, AVATAR_DIGEST,
      TIMESTAMP, ACTIVE, MMS
  };

  static final List<String> TYPED_GROUP_PROJECTION = Stream.of(GROUP_PROJECTION).map(columnName -> TABLE_NAME + "." + columnName).toList();

  public GroupDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public Optional<GroupRecord> getGroup(RecipientId recipientId) {
    try (Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, RECIPIENT_ID + " = ?", new String[] {recipientId.serialize()}, null, null, null)) {
      if (cursor != null && cursor.moveToNext()) {
        return getGroup(cursor);
      }

      return Optional.absent();
    }
  }

  public Optional<GroupRecord> getGroup(@NonNull GroupId groupId) {
    try (Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, GROUP_ID + " = ?",
                                                                    new String[] {groupId.toString()},
                                                                    null, null, null))
    {
      if (cursor != null && cursor.moveToNext()) {
        return getGroup(cursor);
      }

      return Optional.absent();
    }
  }

  Optional<GroupRecord> getGroup(Cursor cursor) {
    Reader reader = new Reader(cursor);
    return Optional.fromNullable(reader.getCurrent());
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

  public Reader getGroupsFilteredByTitle(String constraint, boolean includeInactive) {
    String   query;
    String[] queryArgs;

    if (includeInactive) {
      query     = TITLE + " LIKE ? AND (" + ACTIVE + " = ? OR " + RECIPIENT_ID + " IN (SELECT " + ThreadDatabase.RECIPIENT_ID + " FROM " + ThreadDatabase.TABLE_NAME + "))";
      queryArgs = new String[]{"%" + constraint + "%", "1"};
    } else {
      query     = TITLE + " LIKE ? AND " + ACTIVE + " = ?";
      queryArgs = new String[]{"%" + constraint + "%", "1"};
    }

    Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, query, queryArgs, null, null, TITLE + " COLLATE NOCASE ASC");

    return new Reader(cursor);
  }

  public GroupId.Mms getOrCreateMmsGroupForMembers(List<RecipientId> members) {
    Collections.sort(members);

    Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, new String[] {GROUP_ID},
                                                               MEMBERS + " = ? AND " + MMS + " = ?",
                                                               new String[] {RecipientId.toSerializedList(members), "1"},
                                                               null, null, null);
    try {
      if (cursor != null && cursor.moveToNext()) {
        return GroupId.parseOrThrow(cursor.getString(cursor.getColumnIndexOrThrow(GROUP_ID)))
                      .requireMms();
      } else {
        GroupId.Mms groupId = GroupId.createMms(new SecureRandom());
        create(groupId, members);
        return groupId;
      }
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  public List<String> getPushGroupNamesContainingMember(RecipientId recipientId) {
    return Stream.of(getPushGroupsContainingMember(recipientId))
                 .map(GroupRecord::getTitle)
                 .toList();
  }

  public List<GroupRecord> getPushGroupsContainingMember(RecipientId recipientId) {
    SQLiteDatabase database   = databaseHelper.getReadableDatabase();
    String         table      = TABLE_NAME + " INNER JOIN " + ThreadDatabase.TABLE_NAME + " ON " + TABLE_NAME + "." + RECIPIENT_ID + " = " + ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.RECIPIENT_ID;
    String         query      = MEMBERS + " LIKE ? AND " + MMS + " = ?";
    String[]       args       = new String[]{"%" + recipientId.serialize() + "%", "0"};
    String         orderBy    = ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.DATE + " DESC";

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
    Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, null, null, null, null, null);
    return new Reader(cursor);
  }

  @WorkerThread
  public @NonNull List<Recipient> getGroupMembers(@NonNull GroupId groupId, @NonNull MemberSet memberSet) {
    if (groupId.isV2()) {
      return getGroup(groupId).transform(g -> g.requireV2GroupProperties().getMembers(context, memberSet))
                              .or(Collections.emptyList());
    } else {
      List<RecipientId> currentMembers = getCurrentMembers(groupId);
      List<Recipient>   recipients     = new ArrayList<>(currentMembers.size());

      for (RecipientId member : currentMembers) {
        if (memberSet.includeSelf || !Recipient.resolved(member).isLocalNumber()) {
          recipients.add(Recipient.resolved(member));
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
    create(groupId, title, members, avatar, relay, null, null);
  }

  public void create(@NonNull GroupId.Mms groupId,
                     @NonNull Collection<RecipientId> members)
  {
    create(groupId, null, members, null, null, null, null);
  }

  public void create(@NonNull GroupId.V2 groupId,
                     @Nullable SignalServiceAttachmentPointer avatar,
                     @Nullable String relay,
                     @NonNull GroupMasterKey groupMasterKey,
                     @NonNull DecryptedGroup groupState)
  {
    create(groupId, groupState.getTitle(), Collections.emptyList(), avatar, relay, groupMasterKey, groupState);
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
    List<RecipientId> members = new ArrayList<>(new HashSet<>(memberCollection));

    Collections.sort(members);

    ContentValues contentValues = new ContentValues();
    contentValues.put(RECIPIENT_ID, DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId).serialize());
    contentValues.put(GROUP_ID, groupId.toString());
    contentValues.put(TITLE, title);
    contentValues.put(MEMBERS, RecipientId.toSerializedList(members));

    if (avatar != null) {
      contentValues.put(AVATAR_ID, avatar.getRemoteId().getV2().get());
      contentValues.put(AVATAR_KEY, avatar.getKey());
      contentValues.put(AVATAR_CONTENT_TYPE, avatar.getContentType());
      contentValues.put(AVATAR_DIGEST, avatar.getDigest().orNull());
    } else {
      contentValues.put(AVATAR_ID, 0);
    }

    contentValues.put(AVATAR_RELAY, relay);
    contentValues.put(TIMESTAMP, System.currentTimeMillis());
    contentValues.put(ACTIVE, 1);
    contentValues.put(MMS, groupId.isMms());

    if (groupMasterKey != null) {
      if (groupState == null) {
        throw new AssertionError("V2 master key but no group state");
      }
      groupId.requireV2();
      contentValues.put(V2_MASTER_KEY, groupMasterKey.serialize());
      contentValues.put(V2_REVISION, groupState.getVersion());
      contentValues.put(V2_DECRYPTED_GROUP, groupState.toByteArray());
      contentValues.put(MEMBERS, serializeV2GroupMembers(context, groupState));
    } else {
      if (groupId.isV2()) {
        throw new AssertionError("V2 group id but no master key");
      }
    }

    databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);

    RecipientId groupRecipient = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
    Recipient.live(groupRecipient).refresh();

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
      contentValues.put(AVATAR_DIGEST, avatar.getDigest().orNull());
    } else {
      contentValues.put(AVATAR_ID, 0);
    }

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues,
                                                GROUP_ID + " = ?",
                                                new String[] {groupId.toString()});

    RecipientId groupRecipient = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
    Recipient.live(groupRecipient).refresh();

    notifyConversationListListeners();
  }

  public void update(@NonNull GroupMasterKey groupMasterKey, @NonNull DecryptedGroup decryptedGroup) {
    update(GroupId.v2(groupMasterKey), decryptedGroup);
  }

  public void update(@NonNull GroupId.V2 groupId, @NonNull DecryptedGroup decryptedGroup) {
    String        title         = decryptedGroup.getTitle();
    ContentValues contentValues = new ContentValues();

    contentValues.put(TITLE, title);
    contentValues.put(V2_REVISION, decryptedGroup.getVersion());
    contentValues.put(V2_DECRYPTED_GROUP, decryptedGroup.toByteArray());
    contentValues.put(MEMBERS, serializeV2GroupMembers(context, decryptedGroup));

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues,
                                                GROUP_ID + " = ?",
                                                new String[]{ groupId.toString() });

    RecipientId groupRecipient = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
    Recipient.live(groupRecipient).refresh();

    notifyConversationListListeners();
  }

  public void updateTitle(@NonNull GroupId.V1 groupId, String title) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(TITLE, title);
    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, GROUP_ID +  " = ?",
                                                new String[] {groupId.toString()});

    RecipientId groupRecipient = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
    Recipient.live(groupRecipient).refresh();
  }

  /**
   * Used to bust the Glide cache when an avatar changes.
   */
  public void onAvatarUpdated(@NonNull GroupId.Push groupId, boolean hasAvatar) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(AVATAR_ID, hasAvatar ? Math.abs(new SecureRandom().nextLong()) : 0);

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, GROUP_ID +  " = ?",
                                                new String[] {groupId.toString()});

    RecipientId groupRecipient = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
    Recipient.live(groupRecipient).refresh();
  }

  public void updateMembers(@NonNull GroupId groupId, List<RecipientId> members) {
    Collections.sort(members);

    ContentValues contents = new ContentValues();
    contents.put(MEMBERS, RecipientId.toSerializedList(members));
    contents.put(ACTIVE, 1);

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contents, GROUP_ID + " = ?",
                                                new String[] {groupId.toString()});

    RecipientId groupRecipient = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
    Recipient.live(groupRecipient).refresh();
  }

  public void remove(@NonNull GroupId groupId, RecipientId source) {
    List<RecipientId> currentMembers = getCurrentMembers(groupId);
    currentMembers.remove(source);

    ContentValues contents = new ContentValues();
    contents.put(MEMBERS, RecipientId.toSerializedList(currentMembers));

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contents, GROUP_ID + " = ?",
                                                new String[] {groupId.toString()});

    RecipientId groupRecipient = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
    Recipient.live(groupRecipient).refresh();
  }

  private List<RecipientId> getCurrentMembers(@NonNull GroupId groupId) {
    Cursor cursor = null;

    try {
      cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, new String[] {MEMBERS},
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
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    ContentValues  values   = new ContentValues();
    values.put(ACTIVE, active ? 1 : 0);
    database.update(TABLE_NAME, values, GROUP_ID + " = ?", new String[] {groupId.toString()});
  }

  @WorkerThread
  public boolean isCurrentMember(@NonNull GroupId.Push groupId, @NonNull RecipientId recipientId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();

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

  private static String serializeV2GroupMembers(@NonNull Context context, @NonNull DecryptedGroup decryptedGroup) {
    List<RecipientId> groupMembers  = new ArrayList<>(decryptedGroup.getMembersCount());

    for (DecryptedMember member : decryptedGroup.getMembersList()) {
      Recipient recipient = Recipient.externalPush(context, new SignalServiceAddress(UuidUtil.fromByteString(member.getUuid()), null));

      groupMembers.add(recipient.getId());
    }

    Collections.sort(groupMembers);

    return RecipientId.toSerializedList(groupMembers);
  }

  public static class Reader implements Closeable {

    private final Cursor cursor;

    public Reader(Cursor cursor) {
      this.cursor = cursor;
    }

    public @Nullable GroupRecord getNext() {
      if (cursor == null || !cursor.moveToNext()) {
        return null;
      }

      return getCurrent();
    }

    public @Nullable GroupRecord getCurrent() {
      if (cursor == null || cursor.getString(cursor.getColumnIndexOrThrow(GROUP_ID)) == null) {
        return null;
      }

      return new GroupRecord(GroupId.parseOrThrow(cursor.getString(cursor.getColumnIndexOrThrow(GROUP_ID))),
                             RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(RECIPIENT_ID))),
                             cursor.getString(cursor.getColumnIndexOrThrow(TITLE)),
                             cursor.getString(cursor.getColumnIndexOrThrow(MEMBERS)),
                             cursor.getLong(cursor.getColumnIndexOrThrow(AVATAR_ID)),
                             cursor.getBlob(cursor.getColumnIndexOrThrow(AVATAR_KEY)),
                             cursor.getString(cursor.getColumnIndexOrThrow(AVATAR_CONTENT_TYPE)),
                             cursor.getString(cursor.getColumnIndexOrThrow(AVATAR_RELAY)),
                             cursor.getInt(cursor.getColumnIndexOrThrow(ACTIVE)) == 1,
                             cursor.getBlob(cursor.getColumnIndexOrThrow(AVATAR_DIGEST)),
                             cursor.getInt(cursor.getColumnIndexOrThrow(MMS)) == 1,
                             cursor.getBlob(cursor.getColumnIndexOrThrow(V2_MASTER_KEY)),
                             cursor.getInt(cursor.getColumnIndexOrThrow(V2_REVISION)),
                             cursor.getBlob(cursor.getColumnIndexOrThrow(V2_DECRYPTED_GROUP)));
    }

    @Override
    public void close() {
      if (this.cursor != null)
        this.cursor.close();
    }
  }

  public static class GroupRecord {

              private final GroupId           id;
              private final RecipientId       recipientId;
              private final String            title;
              private final List<RecipientId> members;
              private final long              avatarId;
              private final byte[]            avatarKey;
              private final byte[]            avatarDigest;
              private final String            avatarContentType;
              private final String            relay;
              private final boolean           active;
              private final boolean           mms;
    @Nullable private final V2GroupProperties v2GroupProperties;

    public GroupRecord(@NonNull GroupId id, @NonNull RecipientId recipientId, String title, String members,
                       long avatarId, byte[] avatarKey, String avatarContentType,
                       String relay, boolean active, byte[] avatarDigest, boolean mms,
                       @Nullable byte[] groupMasterKeyBytes, int groupRevision, @Nullable byte[] decryptedGroupBytes)
    {
      this.id                = id;
      this.recipientId       = recipientId;
      this.title             = title;
      this.avatarId          = avatarId;
      this.avatarKey         = avatarKey;
      this.avatarDigest      = avatarDigest;
      this.avatarContentType = avatarContentType;
      this.relay             = relay;
      this.active            = active;
      this.mms               = mms;

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

      if (!TextUtils.isEmpty(members)) this.members = RecipientId.fromSerializedList(members);
      else                             this.members = new LinkedList<>();
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

    public List<RecipientId> getMembers() {
      return members;
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
      return DecryptedGroupUtil.findMemberByUuid(getDecryptedGroup().getMembersList(), recipient.getUuid().get())
                               .transform(t -> t.getRole() == Member.Role.ADMINISTRATOR)
                               .or(false);
    }

    public List<Recipient> getMembers(@NonNull Context context, @NonNull MemberSet memberSet) {
      boolean         includeSelf = memberSet.includeSelf;
      DecryptedGroup  groupV2     = getDecryptedGroup();
      UUID            selfUuid    = Recipient.self().getUuid().get();
      List<Recipient> recipients  = new ArrayList<>(groupV2.getMembersCount() + groupV2.getPendingMembersCount());

      for (UUID uuid : DecryptedGroupUtil.toUuidList(groupV2.getMembersList())) {
        if (includeSelf || !selfUuid.equals(uuid)) {
          recipients.add(Recipient.externalPush(context, uuid, null));
        }
      }
      if (memberSet.includePending) {
        for (UUID uuid : DecryptedGroupUtil.pendingToUuidList(groupV2.getPendingMembersList())) {
          if (includeSelf || !selfUuid.equals(uuid)) {
            recipients.add(Recipient.externalPush(context, uuid, null));
          }
        }
      }

      return recipients;
    }
  }

  public enum MemberSet {
    FULL_MEMBERS_INCLUDING_SELF(true, false),
    FULL_MEMBERS_EXCLUDING_SELF(false, false),
    FULL_MEMBERS_AND_PENDING_INCLUDING_SELF(true, true),
    FULL_MEMBERS_AND_PENDING_EXCLUDING_SELF(false, true);

    private boolean includeSelf;
    private boolean includePending;

    MemberSet(boolean includeSelf, boolean includePending) {
      this.includeSelf    = includeSelf;
      this.includePending = includePending;
    }
  }
}

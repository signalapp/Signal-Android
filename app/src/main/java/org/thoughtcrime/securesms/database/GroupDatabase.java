package org.thoughtcrime.securesms.database;


import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.util.BitmapUtil;

import org.session.libsession.utilities.recipients.Recipient;
import org.session.libsession.utilities.Address;
import org.session.libsession.utilities.GroupRecord;
import org.session.libsession.utilities.Util;

import org.session.libsignal.utilities.guava.Optional;
import org.session.libsignal.messages.SignalServiceAttachmentPointer;
import org.session.libsignal.database.LokiOpenGroupDatabaseProtocol;

import java.io.Closeable;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class GroupDatabase extends Database implements LokiOpenGroupDatabaseProtocol {

  @SuppressWarnings("unused")
  private static final String TAG = GroupDatabase.class.getSimpleName();

          static final String TABLE_NAME          = "groups";
  private static final String ID                  = "_id";
          static final String GROUP_ID            = "group_id";
  private static final String TITLE               = "title";
  private static final String MEMBERS             = "members";
  private static final String ZOMBIE_MEMBERS      = "zombie_members";
  private static final String AVATAR              = "avatar";
  private static final String AVATAR_ID           = "avatar_id";
  private static final String AVATAR_KEY          = "avatar_key";
  private static final String AVATAR_CONTENT_TYPE = "avatar_content_type";
  private static final String AVATAR_RELAY        = "avatar_relay";
  private static final String AVATAR_DIGEST       = "avatar_digest";
  private static final String TIMESTAMP           = "timestamp";
  private static final String ACTIVE              = "active";
  private static final String MMS                 = "mms";

  // Loki
  private static final String AVATAR_URL          = "avatar_url";
  private static final String ADMINS              = "admins";

  public static final String CREATE_TABLE =
      "CREATE TABLE " + TABLE_NAME +
          " (" + ID + " INTEGER PRIMARY KEY, " +
          GROUP_ID + " TEXT, " +
          TITLE + " TEXT, " +
          MEMBERS + " TEXT, " +
          ZOMBIE_MEMBERS + " TEXT, " +
          AVATAR + " BLOB, " +
          AVATAR_ID + " INTEGER, " +
          AVATAR_KEY + " BLOB, " +
          AVATAR_CONTENT_TYPE + " TEXT, " +
          AVATAR_RELAY + " TEXT, " +
          TIMESTAMP + " INTEGER, " +
          ACTIVE + " INTEGER DEFAULT 1, " +
          AVATAR_DIGEST + " BLOB, " +
          AVATAR_URL + " TEXT, " +
          ADMINS + " TEXT, " +
          MMS + " INTEGER DEFAULT 0);";

  public static final String[] CREATE_INDEXS = {
      "CREATE UNIQUE INDEX IF NOT EXISTS group_id_index ON " + TABLE_NAME + " (" + GROUP_ID + ");",
  };

  private static final String[] GROUP_PROJECTION = {
      GROUP_ID, TITLE, MEMBERS, ZOMBIE_MEMBERS, AVATAR, AVATAR_ID, AVATAR_KEY, AVATAR_CONTENT_TYPE, AVATAR_RELAY, AVATAR_DIGEST,
      TIMESTAMP, ACTIVE, MMS, AVATAR_URL, ADMINS
  };

  static final List<String> TYPED_GROUP_PROJECTION = Stream.of(GROUP_PROJECTION).map(columnName -> TABLE_NAME + "." + columnName).toList();

  public GroupDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public Optional<GroupRecord> getGroup(String groupId) {
    try (Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, GROUP_ID + " = ?",
                                                                    new String[] {groupId},
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

  public boolean isUnknownGroup(String groupId) {
    return !getGroup(groupId).isPresent();
  }

  public Reader getGroupsFilteredByTitle(String constraint) {
    @SuppressLint("Recycle")
    Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, TITLE + " LIKE ?",
                                                                                        new String[]{"%" + constraint + "%"},
                                                                                        null, null, null);

    return new Reader(cursor);
  }

  public Reader getGroups() {
    @SuppressLint("Recycle")
    Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, null, null, null, null, null);
    return new Reader(cursor);
  }

  public List<GroupRecord> getAllGroups() {
    Reader reader = getGroups();
    GroupRecord record;
    List<GroupRecord> groups = new LinkedList<>();
    while ((record = reader.getNext()) != null) {
      if (record.isActive()) { groups.add(record); }
    }
    reader.close();
    return groups;
  }

  public @NonNull List<Recipient> getGroupMembers(String groupId, boolean includeSelf) {
    List<Address>   members     = getCurrentMembers(groupId, false);
    List<Recipient> recipients  = new LinkedList<>();

    for (Address member : members) {
      if (!includeSelf && Util.isOwnNumber(context, member.serialize()))
        continue;

      if (member.isContact()) {
        recipients.add(Recipient.from(context, member, false));
      }
    }

    return recipients;
  }

  public @NonNull List<Recipient> getGroupZombieMembers(String groupId) {
    List<Address>   members     = getCurrentZombieMembers(groupId);
    List<Recipient> recipients  = new LinkedList<>();

    for (Address member : members) {
        recipients.add(Recipient.from(context, member, false));
    }

    return recipients;
  }

  public long create(@NonNull String groupId, @Nullable String title, @NonNull List<Address> members,
                     @Nullable SignalServiceAttachmentPointer avatar, @Nullable String relay, @Nullable List<Address> admins, @NonNull Long formationTimestamp)
  {
    Collections.sort(members);

    ContentValues contentValues = new ContentValues();
    contentValues.put(GROUP_ID, groupId);
    contentValues.put(TITLE, title);
    contentValues.put(MEMBERS, Address.toSerializedList(members, ','));

    if (avatar != null) {
      contentValues.put(AVATAR_ID, avatar.getId());
      contentValues.put(AVATAR_KEY, avatar.getKey());
      contentValues.put(AVATAR_CONTENT_TYPE, avatar.getContentType());
      contentValues.put(AVATAR_DIGEST, avatar.getDigest().orNull());
      contentValues.put(AVATAR_URL, avatar.getUrl());
    }

    contentValues.put(AVATAR_RELAY, relay);
    contentValues.put(TIMESTAMP, formationTimestamp);
    contentValues.put(ACTIVE, 1);
    contentValues.put(MMS, false);

    if (admins != null) {
      contentValues.put(ADMINS, Address.toSerializedList(admins, ','));
    }

    long threadId = databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);

    Recipient.applyCached(Address.fromSerialized(groupId), recipient -> {
      recipient.setName(title);
      recipient.setGroupAvatarId(avatar != null ? avatar.getId() : null);
      recipient.setParticipants(Stream.of(members).map(memberAddress -> Recipient.from(context, memberAddress, true)).toList());
    });

    notifyConversationListListeners();
    return threadId;
  }

  public boolean delete(@NonNull String groupId) {
    int result = databaseHelper.getWritableDatabase().delete(TABLE_NAME, GROUP_ID + " = ?", new String[]{groupId});

    if (result > 0) {
      Recipient.removeCached(Address.fromSerialized(groupId));
      notifyConversationListListeners();
      return true;
    } else {
      return false;
    }
  }

  public void update(String groupId, String title, SignalServiceAttachmentPointer avatar) {
    ContentValues contentValues = new ContentValues();
    if (title != null) contentValues.put(TITLE, title);

    if (avatar != null) {
      contentValues.put(AVATAR_ID, avatar.getId());
      contentValues.put(AVATAR_CONTENT_TYPE, avatar.getContentType());
      contentValues.put(AVATAR_KEY, avatar.getKey());
      contentValues.put(AVATAR_DIGEST, avatar.getDigest().orNull());
      contentValues.put(AVATAR_URL, avatar.getUrl());
    }

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues,
                                                GROUP_ID + " = ?",
                                                new String[] {groupId});

    Recipient.applyCached(Address.fromSerialized(groupId), recipient -> {
      recipient.setName(title);
      recipient.setGroupAvatarId(avatar != null ? avatar.getId() : null);
    });

    notifyConversationListListeners();
  }

  @Override
  public void updateTitle(String groupID, String newValue) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(TITLE, newValue);
    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, GROUP_ID +  " = ?",
                                                new String[] {groupID});

    Recipient recipient = Recipient.from(context, Address.fromSerialized(groupID), false);
    recipient.setName(newValue);
  }

  public void updateProfilePicture(String groupID, Bitmap newValue) {
    updateProfilePicture(groupID, BitmapUtil.toByteArray(newValue));
  }

  @Override
  public void updateProfilePicture(String groupID, byte[] newValue) {
    long avatarId;

    if (newValue != null) avatarId = Math.abs(new SecureRandom().nextLong());
    else                  avatarId = 0;


    ContentValues contentValues = new ContentValues(2);
    contentValues.put(AVATAR, newValue);
    contentValues.put(AVATAR_ID, avatarId);

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, GROUP_ID +  " = ?",
                                                new String[] {groupID});

    Recipient.applyCached(Address.fromSerialized(groupID), recipient -> recipient.setGroupAvatarId(avatarId == 0 ? null : avatarId));
  }

  public void updateMembers(String groupId, List<Address> members) {
    Collections.sort(members);

    ContentValues contents = new ContentValues();
    contents.put(MEMBERS, Address.toSerializedList(members, ','));
    contents.put(ACTIVE, 1);

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contents, GROUP_ID + " = ?",
                                                new String[] {groupId});

    Recipient.applyCached(Address.fromSerialized(groupId), recipient -> {
      recipient.setParticipants(Stream.of(members).map(a -> Recipient.from(context, a, false)).toList());
    });
  }

  public void updateZombieMembers(String groupId, List<Address> members) {
    Collections.sort(members);

    ContentValues contents = new ContentValues();
    contents.put(ZOMBIE_MEMBERS, Address.toSerializedList(members, ','));
    contents.put(ACTIVE, 1);
    databaseHelper.getWritableDatabase().update(TABLE_NAME, contents, GROUP_ID + " = ?",
            new String[] {groupId});
  }

  public void updateAdmins(String groupId, List<Address> admins) {
    Collections.sort(admins);

    ContentValues contents = new ContentValues();
    contents.put(ADMINS, Address.toSerializedList(admins, ','));
    contents.put(ACTIVE, 1);

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contents, GROUP_ID + " = ?", new String[] {groupId});
  }

  public void removeMember(String groupId, Address source) {
    List<Address> currentMembers = getCurrentMembers(groupId, false);
    currentMembers.remove(source);

    ContentValues contents = new ContentValues();
    contents.put(MEMBERS, Address.toSerializedList(currentMembers, ','));

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contents, GROUP_ID + " = ?",
                                                new String[] {groupId});

    Recipient.applyCached(Address.fromSerialized(groupId), recipient -> {
      List<Recipient> current = recipient.getParticipants();
      Recipient       removal = Recipient.from(context, source, false);

      current.remove(removal);
      recipient.setParticipants(current);
    });
  }

  private List<Address> getCurrentMembers(String groupId, boolean zombieMembers) {
    Cursor cursor = null;

    String membersColumn = MEMBERS;
    if (zombieMembers) membersColumn = ZOMBIE_MEMBERS;

    try {
      cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, new String[] {membersColumn},
                                                          GROUP_ID + " = ?",
                                                          new String[] {groupId},
                                                          null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        String serializedMembers = cursor.getString(cursor.getColumnIndexOrThrow(membersColumn));
        if (serializedMembers != null && !serializedMembers.isEmpty())
          return Address.fromSerializedList(serializedMembers, ',');
      }

      return new LinkedList<>();
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  private List<Address> getCurrentZombieMembers(String groupId) {
    return getCurrentMembers(groupId, true);
  }

  public boolean isActive(String groupId) {
    Optional<GroupRecord> record = getGroup(groupId);
    return record.isPresent() && record.get().isActive();
  }

  public void setActive(String groupId, boolean active) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    ContentValues  values   = new ContentValues();
    values.put(ACTIVE, active ? 1 : 0);
    database.update(TABLE_NAME, values, GROUP_ID + " = ?", new String[] {groupId});
  }

  public byte[] allocateGroupId() {
    byte[] groupId = new byte[16];
    new SecureRandom().nextBytes(groupId);
    return groupId;
  }

  public boolean hasGroup(@NonNull String groupId) {
    try (Cursor cursor = databaseHelper.getReadableDatabase().rawQuery(
            "SELECT 1 FROM " + TABLE_NAME + " WHERE " + GROUP_ID + " = ? LIMIT 1",
            new String[]{groupId}
    )) {
      return cursor.getCount() > 0;
    }
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

      return new GroupRecord(cursor.getString(cursor.getColumnIndexOrThrow(GROUP_ID)),
                             cursor.getString(cursor.getColumnIndexOrThrow(TITLE)),
                             cursor.getString(cursor.getColumnIndexOrThrow(MEMBERS)),
                             cursor.getBlob(cursor.getColumnIndexOrThrow(AVATAR)),
                             cursor.getLong(cursor.getColumnIndexOrThrow(AVATAR_ID)),
                             cursor.getBlob(cursor.getColumnIndexOrThrow(AVATAR_KEY)),
                             cursor.getString(cursor.getColumnIndexOrThrow(AVATAR_CONTENT_TYPE)),
                             cursor.getString(cursor.getColumnIndexOrThrow(AVATAR_RELAY)),
                             cursor.getInt(cursor.getColumnIndexOrThrow(ACTIVE)) == 1,
                             cursor.getBlob(cursor.getColumnIndexOrThrow(AVATAR_DIGEST)),
                             cursor.getInt(cursor.getColumnIndexOrThrow(MMS)) == 1,
                             cursor.getString(cursor.getColumnIndexOrThrow(AVATAR_URL)),
                              cursor.getString(cursor.getColumnIndexOrThrow(ADMINS)),
                              cursor.getLong(cursor.getColumnIndexOrThrow(TIMESTAMP)));
    }

    @Override
    public void close() {
      if (this.cursor != null)
        this.cursor.close();
    }
  }
}

package org.thoughtcrime.securesms.database;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;

import org.thoughtcrime.securesms.util.BitmapUtil;
import org.whispersystems.textsecure.util.Hex;
import org.whispersystems.textsecure.util.Util;

import java.util.LinkedList;
import java.util.List;

import static org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent.AttachmentPointer;

public class GroupDatabase extends Database {

  private static final String TABLE_NAME          = "groups";
  private static final String ID                  = "_id";
  private static final String GROUP_ID            = "group_id";
  private static final String OWNER               = "owner";
  private static final String TITLE               = "title";
  private static final String MEMBERS             = "members";
  private static final String AVATAR              = "avatar";
  private static final String AVATAR_ID           = "avatar_id";
  private static final String AVATAR_KEY          = "avatar_key";
  private static final String AVATAR_CONTENT_TYPE = "avatar_content_type";
  private static final String RELAY               = "relay";
  private static final String TIMESTAMP           = "timestamp";

  public static final String CREATE_TABLE =
      "CREATE TABLE " + TABLE_NAME +
          " (" + ID + " INTEGER PRIMARY KEY, " +
          GROUP_ID + " TEXT, " +
          OWNER + " TEXT, " +
          TITLE + " TEXT, " +
          MEMBERS + " TEXT, " +
          AVATAR + " BLOB, " +
          AVATAR_ID + " INTEGER, " +
          AVATAR_KEY + " BLOB, " +
          AVATAR_CONTENT_TYPE + " TEXT, " +
          TIMESTAMP + " INTEGER);";

  public static final String[] CREATE_INDEXS = {
      "CREATE UNIQUE INDEX IF NOT EXISTS group_id_index ON " + TABLE_NAME + " (" + GROUP_ID + ");",
  };

  public GroupDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public Reader getGroup(String groupId) {
    Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, GROUP_ID + " = ?",
                                                               new String[] {groupId}, null, null, null);

    return new Reader(cursor);
  }

  public void create(byte[] groupId, String owner, String title,
                     List<String> members, AttachmentPointer avatar,
                     String relay)
  {
    ContentValues contentValues = new ContentValues();
    contentValues.put(GROUP_ID, Hex.toString(groupId));
    contentValues.put(OWNER, owner);
    contentValues.put(TITLE, title);
    contentValues.put(MEMBERS, Util.join(members, ","));

    if (avatar != null) {
      contentValues.put(AVATAR_ID, avatar.getId());
      contentValues.put(AVATAR_KEY, avatar.getKey().toByteArray());
      contentValues.put(AVATAR_CONTENT_TYPE, avatar.getContentType());
    }

    contentValues.put(RELAY, relay);
    contentValues.put(TIMESTAMP, System.currentTimeMillis());

    databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
  }

  public void update(byte[] groupId, String source, String title, AttachmentPointer avatar) {
    ContentValues contentValues = new ContentValues();
    if (title != null)  contentValues.put(TITLE, title);

    if (avatar != null) {
      contentValues.put(AVATAR_ID, avatar.getId());
      contentValues.put(AVATAR_CONTENT_TYPE, avatar.getContentType());
      contentValues.put(AVATAR_KEY, avatar.getKey().toByteArray());
    }

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues,
                                                GROUP_ID + " = ? AND " + OWNER + " = ?",
                                                new String[] {Hex.toString(groupId), source});
  }

  public void updateAvatar(String groupId, Bitmap avatar) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(AVATAR, BitmapUtil.toByteArray(avatar));

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, GROUP_ID +  " = ?", new String[] {groupId});
  }


  public void add(byte[] id, String source, List<String> members) {
    List<String> currentMembers = getCurrentMembers(id);

    for (String currentMember : currentMembers) {
      if (currentMember.equals(source)) {
        List<String> concatenatedMembers = new LinkedList<String>(currentMembers);
        concatenatedMembers.addAll(members);

        ContentValues contents = new ContentValues();
        contents.put(MEMBERS, Util.join(concatenatedMembers, ","));

        databaseHelper.getWritableDatabase().update(TABLE_NAME, contents, GROUP_ID + " = ?",
                                                    new String[] {Hex.toString(id)});
      }
    }
  }

  public void remove(byte[] id, String source) {
    List<String> currentMembers = getCurrentMembers(id);
    currentMembers.remove(source);

    ContentValues contents = new ContentValues();
    contents.put(MEMBERS, Util.join(currentMembers, ","));

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contents, GROUP_ID + " = ?",
                                                new String[]{Hex.toString(id)});
  }

  private List<String> getCurrentMembers(byte[] id) {
    Cursor cursor = null;

    try {
      cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, new String[] {MEMBERS},
                                                          GROUP_ID + " = ?", new String[] {Hex.toString(id)},
                                                          null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return Util.split(cursor.getString(cursor.getColumnIndexOrThrow(MEMBERS)), ",");
      }

      return new LinkedList<String>();
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }


  public static class Reader {

    private final Cursor cursor;

    public Reader(Cursor cursor) {
      this.cursor = cursor;
    }

    public GroupRecord getNext() {
      if (cursor == null || !cursor.moveToNext()) {
        return null;
      }

      return new GroupRecord(cursor.getString(cursor.getColumnIndexOrThrow(GROUP_ID)),
                             cursor.getString(cursor.getColumnIndexOrThrow(TITLE)),
                             cursor.getString(cursor.getColumnIndexOrThrow(MEMBERS)),
                             cursor.getBlob(cursor.getColumnIndexOrThrow(AVATAR)),
                             cursor.getLong(cursor.getColumnIndexOrThrow(AVATAR_ID)),
                             cursor.getBlob(cursor.getColumnIndexOrThrow(AVATAR_KEY)),
                             cursor.getString(cursor.getColumnIndexOrThrow(AVATAR_CONTENT_TYPE)),
                             cursor.getString(cursor.getColumnIndexOrThrow(RELAY)));
    }

    public void close() {
      if (this.cursor != null)
        this.cursor.close();
    }
  }

  public static class GroupRecord {

    private final String       id;
    private final String       title;
    private final List<String> members;
    private final byte[]       avatar;
    private final long         avatarId;
    private final byte[]       avatarKey;
    private final String       avatarContentType;
    private final String       relay;

    public GroupRecord(String id, String title, String members, byte[] avatar,
                       long avatarId, byte[] avatarKey, String avatarContentType,
                       String relay)
    {
      this.id                = id;
      this.title             = title;
      this.members           = Util.split(members, ",");
      this.avatar            = avatar;
      this.avatarId          = avatarId;
      this.avatarKey         = avatarKey;
      this.avatarContentType = avatarContentType;
      this.relay             = relay;
    }

    public String getId() {
      return id;
    }

    public String getTitle() {
      return title;
    }

    public List<String> getMembers() {
      return members;
    }

    public byte[] getAvatar() {
      return avatar;
    }

    public long getAvatarId() {
      return avatarId;
    }

    public byte[] getAvatarKey() {
      return avatarKey;
    }

    public String getAvatarContentType() {
      return avatarContentType;
    }

    public String getRelay() {
      return relay;
    }
  }
}

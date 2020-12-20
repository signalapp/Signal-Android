package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.StreamUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.AttachmentSecret;
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.database.model.IncomingSticker;
import org.thoughtcrime.securesms.database.model.StickerPackRecord;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.stickers.BlessedPacks;
import org.thoughtcrime.securesms.stickers.StickerPackInstallEvent;
import org.thoughtcrime.securesms.util.CursorUtil;
import org.thoughtcrime.securesms.util.SqlUtil;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StickerDatabase extends Database {

  private static final String TAG = Log.tag(StickerDatabase.class);

  public  static final String TABLE_NAME   = "sticker";
  public  static final String _ID          = "_id";
          static final String PACK_ID      = "pack_id";
  private static final String PACK_KEY     = "pack_key";
  private static final String PACK_TITLE   = "pack_title";
  private static final String PACK_AUTHOR  = "pack_author";
  private static final String STICKER_ID   = "sticker_id";
  private static final String EMOJI        = "emoji";
  public  static final String CONTENT_TYPE = "content_type";
  private static final String COVER        = "cover";
  private static final String PACK_ORDER   = "pack_order";
  private static final String INSTALLED    = "installed";
  private static final String LAST_USED    = "last_used";
  public  static final String FILE_PATH    = "file_path";
  public  static final String FILE_LENGTH  = "file_length";
  public  static final String FILE_RANDOM  = "file_random";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + _ID          + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                  PACK_ID      + " TEXT NOT NULL, " +
                                                                                  PACK_KEY     + " TEXT NOT NULL, " +
                                                                                  PACK_TITLE   + " TEXT NOT NULL, " +
                                                                                  PACK_AUTHOR  + " TEXT NOT NULL, " +
                                                                                  STICKER_ID   + " INTEGER, " +
                                                                                  COVER        + " INTEGER, " +
                                                                                  PACK_ORDER   + " INTEGER, " +
                                                                                  EMOJI        + " TEXT NOT NULL, " +
                                                                                  CONTENT_TYPE + " TEXT DEFAULT NULL, " +
                                                                                  LAST_USED    + " INTEGER, " +
                                                                                  INSTALLED    + " INTEGER," +
                                                                                  FILE_PATH    + " TEXT NOT NULL, " +
                                                                                  FILE_LENGTH  + " INTEGER, " +
                                                                                  FILE_RANDOM  + " BLOB, " +
                                                                                  "UNIQUE(" + PACK_ID + ", " + STICKER_ID + ", " + COVER + ") ON CONFLICT IGNORE)";

  public static final String[] CREATE_INDEXES = {
      "CREATE INDEX IF NOT EXISTS sticker_pack_id_index ON " + TABLE_NAME + " (" + PACK_ID + ");",
      "CREATE INDEX IF NOT EXISTS sticker_sticker_id_index ON " + TABLE_NAME + " (" + STICKER_ID + ");"
  };

  public static final String DIRECTORY = "stickers";

  private final AttachmentSecret attachmentSecret;

  public StickerDatabase(Context context, SQLCipherOpenHelper databaseHelper, AttachmentSecret attachmentSecret) {
    super(context, databaseHelper);
    this.attachmentSecret = attachmentSecret;
  }

  public void insertSticker(@NonNull IncomingSticker sticker, @NonNull InputStream dataStream, boolean notify) throws IOException {
    FileInfo      fileInfo      = saveStickerImage(dataStream);
    ContentValues contentValues = new ContentValues();

    contentValues.put(PACK_ID, sticker.getPackId());
    contentValues.put(PACK_KEY, sticker.getPackKey());
    contentValues.put(PACK_TITLE, sticker.getPackTitle());
    contentValues.put(PACK_AUTHOR, sticker.getPackAuthor());
    contentValues.put(STICKER_ID, sticker.getStickerId());
    contentValues.put(EMOJI, sticker.getEmoji());
    contentValues.put(CONTENT_TYPE, sticker.getContentType());
    contentValues.put(COVER, sticker.isCover() ? 1 : 0);
    contentValues.put(INSTALLED, sticker.isInstalled() ? 1 : 0);
    contentValues.put(FILE_PATH, fileInfo.getFile().getAbsolutePath());
    contentValues.put(FILE_LENGTH, fileInfo.getLength());
    contentValues.put(FILE_RANDOM, fileInfo.getRandom());

    long id = databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
    if (id == -1) {
      String   selection = PACK_ID + " = ? AND " + STICKER_ID + " = ? AND " + COVER + " = ?";
      String[] args      = SqlUtil.buildArgs(sticker.getPackId(), sticker.getStickerId(), (sticker.isCover() ? 1 : 0));

      id = databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, selection, args);
    }

    if (id > 0) {
      notifyStickerListeners();

      if (sticker.isCover()) {
        notifyStickerPackListeners();

        if (sticker.isInstalled() && notify) {
          broadcastInstallEvent(sticker.getPackId());
        }
      }
    }
  }

  public @Nullable StickerRecord getSticker(@NonNull String packId, int stickerId, boolean isCover) {
    String   selection = PACK_ID + " = ? AND " + STICKER_ID + " = ? AND " + COVER + " = ?";
    String[] args      = new String[] { packId, String.valueOf(stickerId), String.valueOf(isCover ? 1 : 0) };

    try (Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, selection, args, null, null, "1")) {
      return new StickerRecordReader(cursor).getNext();
    }
  }

  public @Nullable StickerPackRecord getStickerPack(@NonNull String packId) {
    String   query = PACK_ID + " = ? AND " + COVER + " = ?";
    String[] args  = new String[] { packId, "1" };

    try (Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, query, args, null, null, null, "1")) {
      return new StickerPackRecordReader(cursor).getNext();
    }
  }

  public @Nullable Cursor getInstalledStickerPacks() {
    String   selection = COVER + " = ? AND " + INSTALLED + " = ?";
    String[] args      = new String[] { "1", "1" };
    Cursor   cursor    = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, selection, args, null, null, PACK_ORDER + " ASC");

    setNotifyStickerPackListeners(cursor);
    return cursor;
  }

  public @Nullable Cursor getStickersByEmoji(@NonNull String emoji) {
    String   selection = EMOJI + " LIKE ? AND " + COVER + " = ?";
    String[] args      = new String[] { "%"+emoji+"%", "0" };

    Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, selection, args, null, null, null);
    setNotifyStickerListeners(cursor);

    return cursor;
  }

  public @Nullable Cursor getAllStickerPacks() {
    return getAllStickerPacks(null);
  }

  public @Nullable Cursor getAllStickerPacks(@Nullable String limit) {
    String   query  = COVER + " = ?";
    String[] args   = new String[] { "1" };
    Cursor   cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, query, args, null, null, PACK_ORDER + " ASC", limit);
    setNotifyStickerPackListeners(cursor);

    return cursor;
  }

  public @Nullable Cursor getStickersForPack(@NonNull String packId) {
    SQLiteDatabase db        = databaseHelper.getReadableDatabase();
    String         selection = PACK_ID + " = ? AND " + COVER + " = ?";
    String[]       args      = new String[] { packId, "0" };

    Cursor cursor = db.query(TABLE_NAME, null, selection, args, null, null, null);
    setNotifyStickerListeners(cursor);

    return cursor;
  }

  public @Nullable Cursor getRecentlyUsedStickers(int limit) {
    SQLiteDatabase db        = databaseHelper.getReadableDatabase();
    String         selection = LAST_USED + " > ? AND " + COVER + " = ?";
    String[]       args      = new String[] { "0", "0" };

    Cursor cursor = db.query(TABLE_NAME, null, selection, args, null, null, LAST_USED + " DESC", String.valueOf(limit));
    setNotifyStickerListeners(cursor);

    return cursor;
  }

  public @NonNull Set<String> getAllStickerFiles() {
    SQLiteDatabase db        = databaseHelper.getReadableDatabase();

    Set<String> files = new HashSet<>();
    try (Cursor cursor = db.query(TABLE_NAME, new String[] { FILE_PATH }, null, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        files.add(CursorUtil.requireString(cursor, FILE_PATH));
      }
    }

    return files;
  }

  public @Nullable InputStream getStickerStream(long rowId) throws IOException {
    String   selection = _ID + " = ?";
    String[] args      = new String[] { String.valueOf(rowId) };

    try (Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, null, selection, args, null, null, null)) {
      if (cursor != null && cursor.moveToNext()) {
        String path   = cursor.getString(cursor.getColumnIndexOrThrow(FILE_PATH));
        byte[] random = cursor.getBlob(cursor.getColumnIndexOrThrow(FILE_RANDOM));

        if (path != null) {
          return ModernDecryptingPartInputStream.createFor(attachmentSecret, random, new File(path), 0);
        } else {
          Log.w(TAG, "getStickerStream("+rowId+") - No sticker data");
        }
      } else {
        Log.i(TAG, "getStickerStream("+rowId+") - Sticker not found.");
      }
    }

    return null;
  }

  public boolean isPackInstalled(@NonNull String packId) {
    StickerPackRecord record = getStickerPack(packId);

    return (record != null && record.isInstalled());
  }

  public boolean isPackAvailableAsReference(@NonNull String packId) {
    return getStickerPack(packId) != null;
  }

  public void updateStickerLastUsedTime(long rowId, long lastUsed) {
    String        selection = _ID + " = ?";
    String[]      args      = new String[] { String.valueOf(rowId) };
    ContentValues values    = new ContentValues();

    values.put(LAST_USED, lastUsed);

    databaseHelper.getWritableDatabase().update(TABLE_NAME, values, selection, args);

    notifyStickerListeners();
    notifyStickerPackListeners();
  }

  public void markPackAsInstalled(@NonNull String packKey, boolean notify) {
    updatePackInstalled(databaseHelper.getWritableDatabase(), packKey, true, notify);
    notifyStickerPackListeners();
  }

  public void deleteOrphanedPacks() {
    SQLiteDatabase db    = databaseHelper.getWritableDatabase();
    String         query = "SELECT " + PACK_ID + " FROM " + TABLE_NAME + " WHERE " + INSTALLED + " = ? AND " +
                           PACK_ID + " NOT IN (" +
                             "SELECT DISTINCT " + AttachmentDatabase.STICKER_PACK_ID + " FROM " + AttachmentDatabase.TABLE_NAME + " " +
                             "WHERE " + AttachmentDatabase.STICKER_PACK_ID + " NOT NULL" +
                           ")";
    String[]      args = new String[] { "0" };

    db.beginTransaction();

    try {
      boolean performedDelete = false;

      try (Cursor cursor = db.rawQuery(query, args)) {
        while (cursor != null && cursor.moveToNext()) {
          String packId = cursor.getString(cursor.getColumnIndexOrThrow(PACK_ID));

          if (!BlessedPacks.contains(packId)) {
            deletePack(db, packId);
            performedDelete = true;
          }
        }
      }

      db.setTransactionSuccessful();

      if (performedDelete) {
        notifyStickerPackListeners();
        notifyStickerListeners();
      }
    } finally {
      db.endTransaction();
    }
  }

  public void uninstallPack(@NonNull String packId) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();
    try {
      updatePackInstalled(db, packId, false, false);
      deleteStickersInPackExceptCover(db, packId);

      db.setTransactionSuccessful();
      notifyStickerPackListeners();
      notifyStickerListeners();
    } finally {
      db.endTransaction();
    }
  }

  public void updatePackOrder(@NonNull List<StickerPackRecord> packsInOrder) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();
    try {
      String selection = PACK_ID + " = ? AND " + COVER + " = ?";

      for (int i = 0; i < packsInOrder.size(); i++) {
        String[]      args   = new String[]{ packsInOrder.get(i).getPackId(), "1" };
        ContentValues values = new ContentValues();

        values.put(PACK_ORDER, i);

        db.update(TABLE_NAME, values, selection, args);
      }

      db.setTransactionSuccessful();
      notifyStickerPackListeners();
    } finally {
      db.endTransaction();
    }
  }

  private void updatePackInstalled(@NonNull SQLiteDatabase db, @NonNull String packId, boolean installed, boolean notify) {
    StickerPackRecord existing = getStickerPack(packId);

    if (existing != null && existing.isInstalled() == installed) {
      return;
    }

    String        selection = PACK_ID + " = ?";
    String[]      args      = new String[]{ packId };
    ContentValues values    = new ContentValues(1);

    values.put(INSTALLED, installed ? 1 : 0);
    db.update(TABLE_NAME, values, selection, args);

    if (installed && notify) {
      broadcastInstallEvent(packId);
    }
  }

  private FileInfo saveStickerImage(@NonNull InputStream inputStream) throws IOException {
    File                       partsDirectory = context.getDir(DIRECTORY, Context.MODE_PRIVATE);
    File                       file           = File.createTempFile("sticker", ".mms", partsDirectory);
    Pair<byte[], OutputStream> out            = ModernEncryptingPartOutputStream.createFor(attachmentSecret, file, false);
    long                       length         = StreamUtil.copy(inputStream, out.second);

    return new FileInfo(file, length, out.first);
  }

  private void deleteSticker(@NonNull SQLiteDatabase db, long rowId, @Nullable String filePath) {
    String   selection = _ID + " = ?";
    String[] args      = new String[] { String.valueOf(rowId) };

    db.delete(TABLE_NAME, selection, args);

    if (!TextUtils.isEmpty(filePath)) {
      new File(filePath).delete();
    }
  }

  private void deletePack(@NonNull SQLiteDatabase db, @NonNull String packId) {
    String   selection = PACK_ID + " = ?";
    String[] args      = new String[] { packId };

    db.delete(TABLE_NAME, selection, args);

    deleteStickersInPack(db, packId);
  }

  private void deleteStickersInPack(@NonNull SQLiteDatabase db, @NonNull String packId) {
    String   selection = PACK_ID + " = ?";
    String[] args      = new String[] { packId };

    db.beginTransaction();

    try {
      try (Cursor cursor = db.query(TABLE_NAME, null, selection, args, null, null, null)) {
        while (cursor != null && cursor.moveToNext()) {
          String  filePath = cursor.getString(cursor.getColumnIndexOrThrow(FILE_PATH));
          long    rowId    = cursor.getLong(cursor.getColumnIndexOrThrow(_ID));

          deleteSticker(db, rowId, filePath);
        }
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    db.delete(TABLE_NAME, selection, args);
  }

  private void deleteStickersInPackExceptCover(@NonNull SQLiteDatabase db, @NonNull String packId) {
    String   selection = PACK_ID + " = ? AND " + COVER + " = ?";
    String[] args      = new String[] { packId, "0" };

    db.beginTransaction();

    try {
      try (Cursor cursor = db.query(TABLE_NAME, null, selection, args, null, null, null)) {
        while (cursor != null && cursor.moveToNext()) {
          long    rowId     = cursor.getLong(cursor.getColumnIndexOrThrow(_ID));
          String  filePath  = cursor.getString(cursor.getColumnIndexOrThrow(FILE_PATH));

          deleteSticker(db, rowId, filePath);
        }
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  private void broadcastInstallEvent(@NonNull String packId) {
    StickerPackRecord pack = getStickerPack(packId);

    if (pack != null) {
      EventBus.getDefault().postSticky(new StickerPackInstallEvent(new DecryptableUri(pack.getCover().getUri())));
    }
  }

  private static final class FileInfo {
    private final File   file;
    private final long   length;
    private final byte[] random;

    private FileInfo(@NonNull File file, long length, @NonNull byte[] random) {
      this.file   = file;
      this.length = length;
      this.random = random;
    }

    public File getFile() {
      return file;
    }

    public long getLength() {
      return length;
    }

    public byte[] getRandom() {
      return random;
    }
  }

  public static final class StickerRecordReader implements Closeable {

    private final Cursor cursor;

    public StickerRecordReader(@Nullable Cursor cursor) {
      this.cursor = cursor;
    }

    public @Nullable StickerRecord getNext() {
      if (cursor == null || !cursor.moveToNext()) {
        return null;
      }

      return getCurrent();
    }

    public @NonNull StickerRecord getCurrent() {
      return new StickerRecord(cursor.getLong(cursor.getColumnIndexOrThrow(_ID)),
                               cursor.getString(cursor.getColumnIndexOrThrow(PACK_ID)),
                               cursor.getString(cursor.getColumnIndexOrThrow(PACK_KEY)),
                               cursor.getInt(cursor.getColumnIndexOrThrow(STICKER_ID)),
                               cursor.getString(cursor.getColumnIndexOrThrow(EMOJI)),
                               cursor.getString(cursor.getColumnIndexOrThrow(CONTENT_TYPE)),
                               cursor.getLong(cursor.getColumnIndexOrThrow(FILE_LENGTH)),
                               cursor.getInt(cursor.getColumnIndexOrThrow(COVER)) == 1);
    }

    @Override
    public void close() {
      if (cursor != null) {
        cursor.close();
      }
    }
  }

  public static final class StickerPackRecordReader implements Closeable {

    private final Cursor cursor;

    public StickerPackRecordReader(@Nullable Cursor cursor) {
      this.cursor = cursor;
    }

    public @Nullable StickerPackRecord getNext() {
      if (cursor == null || !cursor.moveToNext()) {
        return null;
      }

      return getCurrent();
    }

    public @NonNull StickerPackRecord getCurrent() {
      StickerRecord cover = new StickerRecordReader(cursor).getCurrent();

      return new StickerPackRecord(cursor.getString(cursor.getColumnIndexOrThrow(PACK_ID)),
                                   cursor.getString(cursor.getColumnIndexOrThrow(PACK_KEY)),
                                   cursor.getString(cursor.getColumnIndexOrThrow(PACK_TITLE)),
                                   cursor.getString(cursor.getColumnIndexOrThrow(PACK_AUTHOR)),
                                   cover,
                                   cursor.getInt(cursor.getColumnIndexOrThrow(INSTALLED)) == 1);
    }

    @Override
    public void close() {
      if (cursor != null) {
        cursor.close();
      }
    }
  }
}
